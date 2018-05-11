package com.github.h0tk3y.compilersCourse.x86

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.stack.*
import java.util.*
import kotlin.math.absoluteValue

enum class TargetPlatform { UNIX, WIN }

class StackToX86Compiler(val targetPlatform: TargetPlatform) : Compiler<StackProgram, String> {

    private fun formatFunctionName(name: String) = when (targetPlatform) {
        TargetPlatform.UNIX -> name
        TargetPlatform.WIN -> "_$name"
    }

    class CompilationEnvironment(val program: StackProgram) {
        val result = mutableListOf<AsmDirective>()

        @Deprecated("just don't")
        fun emit(i: String): Unit = TODO()

        fun emit(i: AsmDirective) {
            result.add(i)
        }

        fun pushSsType(type: Type) = pushSs(FromConstant(type.flag))

        @Deprecated("just don't")
        fun pushSs(src: String): String = TODO()

        fun pushSs(src: AsmLocation): SymbolicStackLocation {
            if (src is SymbolicStackRegister) {
                check(src !in symbolicStack)
                symbolicStack.push(src)
                emit(CommentLine("pseudo push: ${prettyPrintStack(1)}"))
                return src
            } else {
                val location = allocOnSymStack()
                if (location is SymbolicStackRegister) {
                    emit(Movl(src, location, prettyPrintStack(1)))
                    return location
                } else {
                    emit(Pushl(src, prettyPrintStack(1)))
                    return programStack
                }
            }
        }

        fun popSs(dst: AsmLocation?) {
            val location = symbolicStack.pop()
            if (dst == null) when (location) {
                programStack -> emit(Addl(FromConstant(4), esp, prettyPrintStack(-1)))
                else -> emit(CommentLine("dropping stack item, ${prettyPrintStack(-1)}"))
            } else when (location) {
                programStack -> emit(Popl(dst, prettyPrintStack(-1)))
                else -> emit(Movl(location, dst, prettyPrintStack(-1)))
            }
        }

        fun dropTypePopSsToReg1(toRegWhenNotSuitable: AsmRegister = eax, requires8BitOps: Boolean = false): AsmRegister {
            val reg = popSsToReg1(toRegWhenNotSuitable, requires8BitOps)
            return popSsToReg1(reg, requires8BitOps)
        }

        fun popSsToReg1(toRegWhenNotSuitable: AsmRegister = eax, requires8BitOps: Boolean = false): AsmRegister {
            val currentLocation = symbolicStack.peek()
            return if (
                currentLocation == programStack ||
                currentLocation is SymbolicStackRegister && requires8BitOps && !currentLocation.supports8Bits
            ) {
                popSs(toRegWhenNotSuitable)
                toRegWhenNotSuitable
            } else {
                emit(CommentLine("virtual pop: ${prettyPrintStack(-1)}"))
                return symbolicStack.pop() as SymbolicStackRegister
            }
        }

        fun dropTypesPopToTwoRegs(require8BitOps: Boolean) =
            dropTypePopSsToReg1(ebx, require8BitOps) to dropTypePopSsToReg1(eax, require8BitOps)

        private fun allocOnSymStack(): SymbolicStackLocation {
            val nextItem = SymbolicStackRegister.values().firstOrNull { it !in symbolicStack } ?: programStack
            symbolicStack.push(nextItem)
            return nextItem
        }

        val symbolicStackAtInsn = sortedMapOf<Int, Stack<SymbolicStackLocation>>()

        var symbolicStack = Stack<SymbolicStackLocation>()

        private fun prettyPrintStack(lastSizeChange: Int) = when {
            lastSizeChange == 0 -> symbolicStack.joinToString(" ")
            lastSizeChange > 0 -> symbolicStack.dropLast(1).joinToString(" ") { it.render() } +
                                  " -> " +
                                  symbolicStack.lastOrNull()?.render().orEmpty()

            else -> symbolicStack.joinToString(" ") { it.render() } + " <- "
        }
    }

    enum class Type(val flag: Int) {
        SCALAR(0), SCALAR_ARRAY(1), BOXED_ARRAY(2)
    }

    private fun CompilationEnvironment.compileFunction(functionDeclaration: FunctionDeclaration, source: List<StackStatement>) {
        val intrinsicRefIncreaseName = formatFunctionName("ref_increase")
        val intrinsicRefDecreaseName = formatFunctionName("ref_decrease")

        functionDeclaration.name.let { fName ->
            val labelName = formatFunctionName(fName)
            val label = Label(labelName)
            emit(Globl(label))
            emit(label)
        }

        symbolicStackAtInsn.clear()

        emit(Pushl(ebp))
        emit(Movl(esp, ebp))

        val functionScope = (collectVariables(source)).distinct()
        val locals = functionScope - functionDeclaration.parameters

        val localOffsets = locals.asSequence()
            .zip(generateSequence(-4) { it - 8 }) { local, offset -> local to Indirect(ebp, offset) }
            .toMap()

        val localTypeOffsets = locals.asSequence()
            .zip(generateSequence(-8) { it - 8 }) { local, offset -> local to Indirect(ebp, offset) }
            .toMap()

        val paramsBoundary = 2 * 4 // 2 words for the previous frame's EBP and the return address

        val paramOffsets = functionDeclaration.parameters.asSequence()
            .zip(generateSequence(paramsBoundary + 4) { it + 8 }) { param, offset -> param to Indirect(ebp, offset) }
            .toMap()

        val paramTypeOffsets = functionDeclaration.parameters.asSequence()
            .zip(generateSequence(paramsBoundary) { it + 8 }) { param, offset -> param to Indirect(ebp, offset) }
            .toMap()

        fun emitRefIncrease(refLocation: AsmLocation, typeLocation: AsmLocation) {
            emit(Pushl(ecx))
            emit(Pushl(edx))
            emit(Pushl(refLocation))
            emit(Pushl(typeLocation))
            emit(CallLabel(Label(intrinsicRefIncreaseName)))
            emit(Addl(FromConstant(8), esp))
            emit(Popl(edx))
            emit(Popl(ecx))
        }

        fun emitRefDecrease(refLocation: AsmLocation, typeLocation: AsmLocation) {
            emit(Pushl(ecx))
            emit(Pushl(edx))
            emit(Pushl(refLocation))
            emit(Pushl(typeLocation))
            emit(CallLabel(Label(intrinsicRefDecreaseName)))
            emit(Addl(FromConstant(8), esp))
            emit(Popl(edx))
            emit(Popl(ecx))
        }

        functionDeclaration.parameters.forEach {
            emit(CommentLine("# value offset for param $it: ${paramOffsets[it]!!.offset}"))
            emit(CommentLine("# type offset for param $it: ${paramTypeOffsets[it]!!.offset}"))
            emitRefIncrease(paramOffsets[it]!!, paramTypeOffsets[it]!!)
        }

        locals.forEach {
            emit(Pushl(FromConstant(0), "value offset for $it: ${localOffsets[it]!!.offset}"))
            emit(Pushl(FromConstant(0), "type offset for $it: ${localTypeOffsets[it]!!.offset}"))
        }

        val valueOffsets = localOffsets + paramOffsets
        val typeOffsets = localTypeOffsets + paramTypeOffsets

        val lastLocalEbpOffset = (localOffsets.values + localTypeOffsets.values).map(Indirect::offset).min() ?: 0

        var insnCanContinue: Boolean

        fun lineLabel(i: Int, s: StackStatement? = null): Label =
            Label("${functionDeclaration.name}_l$i", s?.toString())

        fun compileInstruction(i: Int, s: StackStatement) {
            insnCanContinue = true

            if (i in symbolicStackAtInsn) {
                symbolicStack = symbolicStackAtInsn[i]!!
            }

            when (s) {
                Nop -> Unit
                is Push -> {
                    pushSs(FromConstant(s.constant.value))
                    pushSsType(Type.SCALAR)
                }
                is PushPooled -> {
                    pushSs(FromConstant(pooledStringLabel(s.id).name))
                    pushSsType(Type.SCALAR)
                }
                is Ld -> {
                    pushSs(valueOffsets[s.v]!!)
                    pushSs(typeOffsets[s.v]!!)
                }
                is St -> {
                    val varValueOffset = valueOffsets[s.v]!!
                    val varTypeOffset = typeOffsets[s.v]!!
                    emitRefDecrease(varValueOffset, varTypeOffset)
                    popSs(varTypeOffset)
                    popSs(varValueOffset)
                    emitRefIncrease(varValueOffset, varTypeOffset)
                }
                is Unop -> when (s.kind) {
                    Not -> {
                        val reg = dropTypePopSsToReg1()
                        emit(Cmp(FromConstant(0), reg))
                        val labelNz = Label("${functionDeclaration.name}_l${i}_nz")
                        emit(JnzLabel(labelNz))
                        val stackLocation = pushSs(FromConstant(1))
                        val targetLoc = when (stackLocation) {
                            is SymbolicStackRegister -> stackLocation
                            is programStack -> Indirect(esp, 0)
                        }
                        pushSsType(Type.SCALAR)
                        val labelAfter = Label("${functionDeclaration.name}_l${i}_after")
                        emit(JmpLabel(labelAfter))
                        emit(labelNz)
                        emit(Movl(FromConstant(0), targetLoc, "replace 1 with 0"))
                        emit(labelAfter)
                    }
                }
                is Binop -> {
                    val (opB, opA) = dropTypesPopToTwoRegs(opRequires8Bits(s.kind))
                    var resultRegister = opB

                    when (s.kind) {
                        Plus -> emit(Addl(opA, opB))
                        Minus -> {
                            resultRegister = opA
                            emit(Subl(opB, opA))
                        }
                        Times -> emit(Imul(opA, opB))
                        Div, Rem -> {
                            resultRegister = eax
                            emit(Pushl(edx))
                            emit(Movl(opA, eax))
                            emit(Movl(opB, ebx))
                            emit(Cltd)
                            emit(Idiv(ebx))
                            if (s.kind == Rem)
                                emit(Movl(edx, eax))
                            emit(Popl(edx))
                        }
                        And, Or -> {
                            emit(Andl(opA, opA))
                            emit(Set8Bit(ComparisonOperation.nz, lower8Bits(opA)))
                            emit(Andl(FromConstant(1), opA))
                            emit(Andl(opB, opB))
                            emit(Set8Bit(ComparisonOperation.nz, lower8Bits(opB)))
                            emit(Andl(FromConstant(1), opB))
                            emit(
                                if (s.kind == And)
                                    Andl(opA, opB) else
                                    Orl(opA, opB)
                            )
                        }
                        Eq, Neq, Gt, Lt, Leq, Geq -> {
                            emit(Subl(opA, opB))
                            emit(Set8Bit(setComparisonOp[s.kind]!!, lower8Bits(opB)))
                            emit(Andl(FromConstant(1), opB))
                        }
                    }
                    pushSs(resultRegister)
                    pushSsType(Type.SCALAR)
                }
                is Jmp -> {
                    symbolicStackAtInsn[s.nextInstruction] = Stack<SymbolicStackLocation>().apply { addAll(symbolicStack) }
                    insnCanContinue = false
                    emit(JmpLabel(lineLabel(s.nextInstruction)))
                }
                is Jz -> {
                    val op = dropTypePopSsToReg1()
                    symbolicStackAtInsn[s.nextInstruction] = Stack<SymbolicStackLocation>().apply { addAll(symbolicStack) }
                    emit(Cmp(FromConstant(0), op))
                    emit(JumpConditional(ComparisonOperation.z, lineLabel(s.nextInstruction)))
                }
                is Call -> {
                    val fName = functionBackEndName(s.function)
                    val existingProgramStackItemsCount = symbolicStack.filter { it == programStack }.count()
                    val lastTakenEbpOffset = existingProgramStackItemsCount * 4 + lastLocalEbpOffset.absoluteValue
                    val regItemsCount = symbolicStack.size - existingProgramStackItemsCount
                    val programStackEbpOffsetBySymbolicStackIndex = symbolicStack.withIndex().associate { (index, stackItem) ->
                        val offset =
                            if (stackItem is SymbolicStackRegister) {
                                emit(Pushl(stackItem))
                                -lastTakenEbpOffset - 4 * (1 + index)
                            } else {
                                -lastTakenEbpOffset - 4 * (1 + index - regItemsCount - existingProgramStackItemsCount)
                            }
                        index to Indirect(ebp, offset)
                    }

                    val specialItemsForCalleeOnStack = if (s.function.canThrow) {
                        emit(Pushl(FromConstant(0))) // thrown exception id
                        1
                    } else 0

                    val nParamStackItems = s.function.parameters.size * 2 // value and type for each parameter
                    val paramStackIndices = symbolicStack.indices.toList().takeLast(nParamStackItems)
                        .reversed()
                        .chunked(2) { it.reversed() }
                        .flatten()

                    for (paramStackIndex in paramStackIndices) {
                        emit(Pushl(programStackEbpOffsetBySymbolicStackIndex[paramStackIndex]!!))
                    }
                    emit(CallLabel(Label(formatFunctionName(fName))))

                    (nParamStackItems * 4).let { argBytesOnStack ->
                        if (argBytesOnStack > 0) emit(Addl(FromConstant(argBytesOnStack), esp))
                    }

                    if (s.function.canThrow) {
                        emit(Pushl(eax))
                        emit(Movl(Indirect(esp, 4), eax))
                        emit(Cmp(FromConstant(0), eax))
                        val labelWhenThrown = Label("whenThrown_${functionDeclaration.name}_$i")
                        val labelAfterCall = Label("afterCall_${functionDeclaration.name}_$i")
                        emit(JumpConditional(ComparisonOperation.g, labelWhenThrown))
                        emit(JmpLabel(labelAfterCall)) //todo optimize jumps
                        emit(labelWhenThrown)

                        pushSs(Indirect(esp, 4))
                        pushSsType(Type.SCALAR)

                        compileInstruction(i, St(thrownExceptionVariable))

                        emit(labelAfterCall)
                        emit(Popl(eax))
                    }

                    (specialItemsForCalleeOnStack * 4).let { specialItemBytesOnStack ->
                        if (specialItemBytesOnStack > 0)
                            emit(Addl(FromConstant(specialItemBytesOnStack), esp))
                    }

                    for (loc in symbolicStack.filter { it is SymbolicStackRegister }.reversed()) { // restore symstack registers
                        emit(Popl(loc))
                    }
                    s.function.parameters.forEach {
                        popSs(null) // drop the parameters from the stack
                        popSs(null)
                    }
                    pushSs(eax)
                    pushSs(ebx) // push type returned in ebx from the function
                }
                TransEx -> {
                    if (functionDeclaration.name != "main") {
                        pushSs(valueOffsets[currentExceptionVariable]!!)
                        val offsetBeyondParams = 0
                        val parentFrameThrownExOffset =
                            paramOffsets.values.map(Indirect::offset).max()?.plus(4) ?: paramsBoundary+
                            offsetBeyondParams
                        popSs(Indirect(ebp, parentFrameThrownExOffset))
                    }
                }
                Ret0, Ret1 -> {
                    for ((k, valueOffset) in valueOffsets) {
                        if (k == exceptionDataVariable)
                            continue
                        val typeOffset = typeOffsets[k]!!
                        emitRefDecrease(valueOffset, typeOffset)
                    }

                    if (s == Ret1) {
                        popSs(ebx)
                        popSs(eax)
                    } else {
                        emit(Movl(FromConstant(Type.SCALAR.flag), ebx))
                        emit(Movl(FromConstant(0), eax))
                    }
                    emit(Leave)
                    if (functionDeclaration.name == "main") {
                        // Make main always return 0
                        emit(Movl(FromConstant(0), eax))
                    }
                    emit(Ret)
                }
                Pop -> {
                    compileInstruction(i, St(poppedUnusedValueVariable))
                }
            }

            if (i + 1 in symbolicStackAtInsn && insnCanContinue) {
                check(symbolicStackAtInsn[i + 1] == symbolicStack)
            }
        }

        check(symbolicStack.isEmpty())
        for ((i, s) in source.withIndex()) {
            emit(lineLabel(i, s))
            compileInstruction(i, s)
        }
        check(symbolicStack.isEmpty())
    }

    override fun compile(source: StackProgram): String {
        val compilationEnvironment = CompilationEnvironment(source)
        with(compilationEnvironment) {
            emit(TextSection)

            for ((f, fCode) in source.functions) {
                compileFunction(f, fCode)
            }

            emit(SectionRodata)

            for ((i, l) in source.literalPool.withIndex()) {
                emit(StringData(i, String(l)))
            }
        }
        return compilationEnvironment.result.joinToString("\n") { it.renderDirective() } + "\n"
    }
}

private val setComparisonOp = mapOf(
    Eq to ComparisonOperation.z,
    Neq to ComparisonOperation.nz,
    Gt to ComparisonOperation.l,
    Lt to ComparisonOperation.g,
    Leq to ComparisonOperation.ge,
    Geq to ComparisonOperation.le
)

private fun opRequires8Bits(binaryOperationKind: BinaryOperationKind) = when (binaryOperationKind) {
    And, Or, Eq, Neq, Gt, Lt, Leq, Geq -> true
    else -> false
}

private fun functionBackEndName(functionDeclaration: FunctionDeclaration): String =
    if (functionDeclaration is Intrinsic) {
        when (functionDeclaration) {
            Intrinsic.STRLEN -> "strlen_2"
            Intrinsic.STRDUP -> "strdup_2"
            Intrinsic.STRCMP -> "strcmp_4"
            Intrinsic.STRCAT -> "strcat_4"
            else -> functionDeclaration.name
        }
    } else functionDeclaration.name
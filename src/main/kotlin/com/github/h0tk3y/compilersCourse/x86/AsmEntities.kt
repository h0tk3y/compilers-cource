package com.github.h0tk3y.compilersCourse.x86

sealed class AsmDirective {
    open val comment: String? = null
}

class CommentLine(override val comment: String) : AsmDirective()
object TextSection : AsmDirective()
class Movl(val from: AsmLocation, val to: AsmLocation, override val comment: String? = null) : AsmDirective()
class Pushl(val from: AsmLocation, override val comment: String? = null) : AsmDirective()
class Popl(val to: AsmLocation, override val comment: String? = null) : AsmDirective()
class Addl(val from: AsmLocation, val to: AsmLocation, override val comment: String? = null) : AsmDirective()
class Andl(val from: AsmLocation, val to: AsmLocation, override val comment: String? = null) : AsmDirective()
class Orl(val from: AsmLocation, val to: AsmLocation, override val comment: String? = null) : AsmDirective()
class Subl(val from: AsmLocation, val to: AsmLocation, override val comment: String? = null) : AsmDirective()
class Imul(val from: AsmLocation, val to: AsmLocation, override val comment: String? = null) : AsmDirective()
class Idiv(val divisor: AsmLocation) : AsmDirective()
class Cmp(val left: AsmLocation, val right: AsmLocation, override val comment: String? = null) : AsmDirective()
object Cltd : AsmDirective()
class JmpLabel(val label: Label) : AsmDirective()
class JumpConditional(val condition: ComparisonOperation, val label: Label) : AsmDirective()
class Set8Bit(val operation: ComparisonOperation, val low8Bits: Low8Bits) : AsmDirective()
class JnzLabel(val label: Label) : AsmDirective()
class CallLabel(val label: Label) : AsmDirective()
object Leave : AsmDirective()
object Ret : AsmDirective()
object SectionRodata : AsmDirective()
class Globl(val label: Label) : AsmDirective()
class Label(val name: String, override val comment: String? = null) : AsmDirective(), AsmLocation

enum class ComparisonOperation {
    z, nz, l, g, ge, le
}

fun pooledStringLabel(id: Int) = Label("_pooled_string_$id")

class StringData(val id: Int, val string: String) : AsmDirective()

interface AsmLocation

interface AsmRegister : AsmLocation

class FromConstant(val value: String) : AsmLocation {
    constructor(intValue: Int) : this(intValue.toString())
}

sealed class SymbolicStackLocation : AsmLocation

sealed class SymbolicStackRegister(val supports8Bits: Boolean = true) : SymbolicStackLocation(), AsmRegister {
    companion object {
        fun values() = listOf(ecx, edx, esi, edi)
    }
}

object ecx : SymbolicStackRegister()
object edx : SymbolicStackRegister()
object esi : SymbolicStackRegister(supports8Bits = false)
object edi : SymbolicStackRegister(supports8Bits = false)

object eax : AsmRegister
object ebx : AsmRegister
object esp : AsmRegister
object ebp : AsmRegister

enum class Low8Bits {
    al, bl, cl, dl, si, di
}

fun lower8Bits(register: AsmRegister): Low8Bits = when (register) {
    eax -> Low8Bits.al
    ebx -> Low8Bits.bl
    ecx -> Low8Bits.cl
    edx -> Low8Bits.dl
    esi -> Low8Bits.si
    edi -> Low8Bits.di
    else -> error("Register $register should not be used as 8 bits register")
}

object programStack : SymbolicStackLocation()

class Indirect(val reference: AsmLocation, val offset: Int = 0) : AsmLocation
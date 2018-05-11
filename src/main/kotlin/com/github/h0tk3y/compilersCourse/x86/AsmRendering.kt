package com.github.h0tk3y.compilersCourse.x86

fun AsmDirective.renderDirective(): String {
    val asmLine = when (this) {
        is CommentLine -> ""
        is Movl -> "movl ${from.render()}, ${to.render()}"
        is Pushl -> "pushl ${from.render()}"
        is Popl -> "popl ${to.render()}"
        is Addl -> "addl ${from.render()}, ${to.render()}"
        is Andl -> "andl ${from.render()}, ${to.render()}"
        is Orl -> "orl ${from.render()}, ${to.render()}"
        is Subl -> "subl ${from.render()}, ${to.render()}"
        is Imul -> "imul ${from.render()}, ${to.render()}"
        is Idiv -> "idiv ${divisor.render()}"
        is Cmp -> "cmp ${left.render()}, ${right.render()}"
        Cltd -> "cltd"
        is JmpLabel -> "jmp ${label.render()}"
        is JumpConditional -> "j${condition.name} ${label.render()}"
        is Set8Bit -> "set${operation.name} %${low8Bits.name}"
        is JnzLabel -> "jnz ${label.render()}"
        is CallLabel -> "call ${label.render()}"
        Leave -> "leave"
        Ret -> "ret"
        TextSection -> ".text"
        SectionRodata -> ".section .rodata"
        is StringData -> "${pooledStringLabel(id).render()}: .string \"$string\""
        is Label -> "$name:"
        is Globl -> ".globl ${label.render()}"
    }
    return asmLine + comment?.let { " # $it" }.orEmpty()
}

fun AsmLocation.render(): String = when(this) {
    is Label -> name
    is AsmRegister -> "%" + this::class.simpleName
    is FromConstant -> "$$value"
    is Indirect -> "${if (offset != 0) "$offset" else ""}(${reference.render()})"
    programStack -> "ps"
    else -> error("Unknown AsmLocation")
}
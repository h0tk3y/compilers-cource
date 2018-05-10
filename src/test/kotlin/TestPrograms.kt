import com.github.h0tk3y.compilersCourse.language.*

val v1 = Variable("v1")
val v2 = Variable("v2")
val v3 = Variable("v3")

val factorial = chainOf(
        Assign(v1, Read),
        Assign(v2, Const(1)),
        While(v1 greaterThan Const(1), chainOf(
                Assign(v2, v2 * v1),
                Assign(v1, v1 - Const(1)),
                Write(v2)
        )))

val fastPow = chainOf(
        Assign(v3, Const(1)),
        Assign(v1, Read),
        Assign(v2, Read),
        While(v2 greaterThan Const(0), chainOf(
                If(v2 % Const(2) eq Const(1), chainOf(
                        Assign(v3, v3 * v1),
                        Assign(v2, v2 - Const(1))
                ), Skip),
                Assign(v1, v1 * v1),
                Assign(v2, v2 / Const(2))
        )),
        Write(v3))

val addInts = FunctionDeclaration("addInts", listOf(v1, v2), Return(BinaryOperation(v1, v2, Plus)))

val addIntsTest = chainOf(
        Assign(v1, Read), // v1 = x
        Assign(v2, Read), // v2 = y
        Assign(v3, FunctionCall(addInts, listOf(v1, v2))), // v3 = x + y
        Assign(v1, FunctionCall(addInts, listOf(v2, v3))), // v1 = x + 2y
        Assign(v2, FunctionCall(addInts, listOf(v3, v1))), // v2 = 2x + 3y
        Assign(v3, FunctionCall(addInts, listOf(v1, v2))), // v3 = 3x + 5y
        Write(v3)) // expected: 3 * v1 + 5 * v2

fun programOf(statement: Statement, functions: List<FunctionDeclaration> = emptyList()): Program {
    val functionDeclaration = FunctionDeclaration("main", emptyList(), statement)
    return Program(functions + functionDeclaration, functionDeclaration)
}
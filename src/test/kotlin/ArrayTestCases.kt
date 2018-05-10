import org.junit.runner.RunWith
import org.junit.runners.Parameterized

val arrayTestCases = mutableListOf<TestCase>()
fun TestCase.registerArray() = also { arrayTestCases.add(it) }
fun Iterable<TestCase>.registerArray() = apply { forEach { arrayTestCases.add(it) } }

@RunWith(Parameterized::class)
class RunArrayTestCases : RunAllTestCases() {
    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = arrayTestCases
    }
}

val arrsExample = ParsedTestCaseMatchOutput(
    "arrsExample", """
        fun writes(i) begin
            write(i)
            return i
        end

        arr = [writes(101), writes(102), writes(103), writes(104)]
        arr[0] := 1
        arr[1] := 2
        arr[2] := 3
        write(arr[1])
        write(arr[2])
        write(arr[3])
        write(arrlen(arr))
    """.trimIndent(), listOf(), listOf(101, 102, 103, 104, 2, 3, 104, 4)
).registerArray()

val boxedArrSimple = ParsedTestCaseMatchOutput(
    "boxedArrSimple", """
        A = {[1, 2, 3], [4, 5, 6], [7, 8, 9], 999}
        for i := 0, i < 3, i := i + 1 do
            for j := 0, j < 3, j := j + 1 do
                write(A[i][j])
            od
        od;
        A[0] = [-1, -2, -3]
        A[1][1] = 100
        for i := 0, i < 3, i := i + 1 do
            write(A[0][i])
        od
        write(A[1][1])
        write(A[3])
        write(arrlen(A))
        write(arrlen(A[0]))
    """.trimIndent(), listOf(), listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -2, -3, 100, 999, 4, 3)
).registerArray()

val refSemanticsTest = ParsedTestCaseMatchOutput(
    "refSemantics", """
        A = [0, 1, 2, 3]
        B = {A, A, A}
        C = Arrmake(3, A)
        A[0] = -999
        A[3] = 999
        for i := 0, i < 3, i := i + 1 do
            write(B[i][0])
            write(C[i][0])
        od
        for i := 0, i < 3, i := i + 1 do
            write(B[i][3])
            write(C[i][3])
        od
    """.trimIndent(), listOf(), listOf(-999, -999, -999, -999, -999, -999, 999, 999, 999, 999, 999, 999)
).registerArray()

val circularReference = ParsedTestCaseMatchOutput(
    "circularReference", """
        A = {0, 1}
        B = {A, 2}
        C = {B, 3}
        A[0] = C

        X = A
        for i := 0, i < 4, i := i + 1 do
            write(X[1])
            X = X[0]
        od
    """.trimIndent(), listOf(), listOf(1, 3, 2, 1)
).registerArray()

val passArrayToFunction = ParsedTestCaseMatchOutput(
    "passArrayToFunction", """
        fun printArrItems(a) begin
            len = arrlen(a)
            for i := 0, i < len, i := i + 1 do
                write(a[i])
            od
        end

        A = [1, 2, 3]
        printArrItems(A)
        printArrItems([123, 456, 789])
    """.trimIndent(), listOf(), listOf(1, 2, 3, 123, 456, 789)
).registerArray()

val returnArrayFromFunction = ParsedTestCaseMatchOutput(
    "returnArrayFromFunction", """
        fun createRange(a, b) begin
            len = b - a + 1
            A = arrmake(len, 0)
            for i := 0, i < len, i := i + 1 do
                A[i] := i + a
            od
            return A
        end

        fun wrapTwoArrs(A, B) begin
            Result = {A, B}
            return Result
        end

        X = createRange(3, 3)
        Y = createRange(5, 10)
        Arrs = wrapTwoArrs(X, Y)
        for i := 0, i < arrlen(Arrs), i := i + 1 do
            for j := 0, j < arrlen(Arrs[i]), j := j + 1 do
                write(Arrs[i][j])
            od
        od
    """.trimIndent(), listOf(), listOf(3, 5, 6, 7, 8, 9, 10)
).registerArray()

val arrayMemoryStressTest = object : ParsedTestCaseMatchOutput(
    "arrayMemoryStressTest", """
        fun consume(Arr, i) begin
            Arr[0] = [0]
            Arr[50000000] = arrmake(100000000, i)
            Arr[99999999] = [0]
        end

        for i := 0, i < 20, i := i + 1 do
            A = Arrmake(100000000, 0)
            consume(A, i)
            write(A[50000000][99999999])
        od

        write(0)
    """.trimIndent(), listOf(), (0..19).toList() + 0
) {
    override fun canRunOnRunner(testCaseRunner: TestCaseRunner): Boolean = testCaseRunner is X86Runner
}.registerArray()
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

val exceptionTestCases = mutableListOf<TestCase>()
fun TestCase.registerExceptionTestCase() = also { exceptionTestCases.add(it) }

@RunWith(Parameterized::class)
class RunExceptionTestCases : RunAllTestCases() {
    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = exceptionTestCases
    }
}

val testSimpleThrowCatch = ParsedTestCaseMatchOutput("simple throw catch", """
    fun alwaysThrows()
    begin
        throw (MyException 123)
    end

    try
        alwaysThrows()
    catch (MyException data)
        write(data)
    finally
        write(321)
    yrt
    """.trimIndent(), listOf(), listOf(123, 321)).registerExceptionTestCase()

val testThrowThrough = ParsedTestCaseMatchOutput("throw through", """
    fun throwsWhen0(x)
    begin
        write(x)
        if x == 0 then throw (MyException 123) else throwsWhen0(x - 1) fi
        write(-1)
    end

    try
        throwsWhen0(5)
    catch (MyException data)
        write(data)
    yrt
    """.trimIndent(), listOf(), listOf(5, 4, 3, 2, 1, 0, 123)).registerExceptionTestCase()

val deepCatch = ParsedTestCaseMatchOutput("deep catch", """
    fun throwsWhen0(x)
    begin
        write(x);
        if x == 0
        then
            throw (MyException 123)
        else
            try
                throwsWhen0(x - 1)
            catch (MyException data)
                write(data)
                throw (MyException data - 1)
            yrt
        fi
    end

    throwsWhen0(5)
    """.trimIndent(), listOf(), listOf(5, 4, 3, 2, 1, 0, 123, 122, 121, 120, 119)).registerExceptionTestCase()

val selectException = ParsedTestCaseMatchOutput("select exception", """
    fun throwsCase(x)
    begin
        if x == 1 then
            throw (ExceptionOne 1)
        elif x == 2 then
            throw (ExceptionTwo 2)
        elif x == 3 then
            throw (ExceptionThree 3)
        else
            return 4
        fi
    end

    for i := 1, i <= 4, i := i + 1 do
        try
            throwsCase(i)
        catch (ExceptionOne one)
            write(one)
        catch (ExceptionTwo two)
            write(two)
        catch (ExceptionThree three)
            write(three)
        finally
            write(0)
        yrt
    od
    write(123)
    """.trimIndent(), listOf(), listOf(1, 0, 2, 0, 3, 0, 0, 123)).registerExceptionTestCase()

val throwFromCatch = ParsedTestCaseMatchOutput("throw from catch", """
    fun throwsOne() begin
        throw (ExceptionOne 1)
    end

    fun throwsSomething() begin
        try
            throwsOne()
        catch (ExceptionOne one)
            throw (ExceptionTwo one + one)
            write(-1)
        yrt
        write(-1)
    end

    try
        throwsSomething()
    catch (ExceptionTwo two)
        write(two)
    yrt
    """, listOf(), listOf(2)).registerExceptionTestCase()

val throwFromFinally = ParsedTestCaseMatchOutput("throw from finally", """
    fun throwsOne() begin
        throw (ExceptionOne 1)
    end

    fun throwsSomething() begin
        try
            throwsOne()
        catch (ExceptionOne one)
            throw (ExceptionOne -1)
        finally
            throw (ExceptionTwo 2)
        yrt
        write(-1)
    end

    try
        throwsSomething()
    catch (ExceptionTwo two)
        write(two)
    yrt
    """, listOf(), listOf(2)).registerExceptionTestCase()

val returnFromFinally = ParsedTestCaseMatchOutput("return from finally", """
    fun throwsOne() begin
        throw (ExceptionOne 1)
        write(-1)
    end

    fun throwsSomething() begin
        try
            throwsOne()
            write(-1)
        finally
            return 2
        yrt
        write(-1)
    end

    try
        write(throwsSomething())
        write(3)
    catch (ExceptionOne one)
        write(one)
    yrt
    """, listOf(), listOf(2, 3)).registerExceptionTestCase()

val throwFinallyNoCatch = ParsedTestCaseMatchOutput("throw finally no catch", """
    fun throwsOne() begin
        throw (ExceptionOne 1)
    end

    fun throwsSomething() begin
        try
            throwsOne()
        finally
            write(2)
        yrt
    end

    try
        throwsSomething()
    catch (ExceptionOne one)
        write(one)
    yrt
    """, listOf(), listOf(2, 1)).registerExceptionTestCase()

val catchThrowFinallyThrow = ParsedTestCaseMatchOutput("throw in finally after throw in catch", """
    fun throwsOne() begin
        throw (ExceptionOne 1)
    end

    fun throwsSomething() begin
        try
            throwsOne()
        catch (ExceptionOne one)
            write(one)
            throw (ExceptionTwo one + one)
        finally
            throw (ExceptionThree 3)
        yrt
    end

    try
        throwsSomething()
    catch (ExceptionThree three)
        write(three)
    yrt
    """, listOf(), listOf(1, 3)).registerExceptionTestCase()

val nestedThrowSkips = ParsedTestCaseMatchOutput("nested throw skips", """
    try
        try
            try
                throw (ExceptionOne 1)
            catch (ExceptionTwo two)
                write(-1)
            yrt
            write(-1)
        catch (ExceptionThree three)
            write(-1)
        yrt
        write(-1)
    catch (ExceptionOne one)
        write(one)
    yrt
    """.trimIndent(), listOf(), listOf(1)).registerExceptionTestCase()

val callThrowingFunctionInBinop = ParsedTestCaseMatchOutput("call throwing function in binop", """
    fun throwsOne() begin
        throw (ExceptionOne 1)
    end

    fun writesTwo() begin
        write(2)
    end

    try
        write(writesTwo() + throwsOne() + writesTwo())
    catch (ExceptionOne one)
        write(one)
    yrt
    write(123)
    """.trimIndent(), listOf(), listOf(2, 1, 123)).registerExceptionTestCase()
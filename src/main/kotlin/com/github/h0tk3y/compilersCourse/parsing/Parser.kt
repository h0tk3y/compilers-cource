package com.github.h0tk3y.compilersCourse.parsing

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.parser.Parser
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.languageUtils.resolveCalls

//region tokens

object ProgramGrammar : Grammar<Program>() {

    private val LPAR by token("\\(")
    private val RPAR by token("\\)")

    private val LSQ by token("\\[")
    private val RSQ by token("\\]")

    private val PLUS by token("\\+")
    private val MINUS by token("\\-")
    private val DIV by token("/")
    private val MOD by token("%")
    private val TIMES by token("\\*")
    private val OR by token("!!")
    private val AND by token("&&")
    private val EQU by token("==")
    private val NEQ by token("!=")
    private val LEQ by token("<=")
    private val GEQ by token(">=")
    private val LT by token("<")
    private val GT by token(">")

    private val NOT by token("!")

    private val COMMA by token(",")
    private val SEMI by token(";")
    private val ASGN by token(":=")

    private val IF by token("if\\b")
    private val THEN by token("then\\b")
    private val ELIF by token("elif\\b")
    private val ELSE by token("else\\b")
    private val FI by token("fi\\b")

    private val WHILE by token("while\\b")
    private val FOR by token("for\\b")
    private val DO by token("do\\b")
    private val OD by token("od\\b")

    private val SKIP by token("skip\\b")

    private val REPEAT by token("repeat\\b")
    private val UNTIL by token("until\\b")

    private val BEGIN by token("begin\\b")
    private val END by token("end\\b")

    private val FUN by token("fun\\b")
    private val RETURN by token("return\\b")

    private val TRUE by token("true\\b")
    private val FALSE by token("false\\b")

    private val NUMBER by token("\\d+")
    private val CHARLIT by token("'.'")
    private val STRINGLIT by token("\".*?\"")
    private val ID by token("\\w+")

    private val WS by token("\\s+", ignore = true)
    private val NEWLINE by token("[\r\n]+", ignore = true)

    private val signToKind = mapOf(
            OR to Or,
            AND to And,
            LT to Lt,
            GT to Gt,
            EQU to Eq,
            NEQ to Neq,
            LEQ to Leq,
            GEQ to Geq,
            PLUS to Plus,
            MINUS to Minus,
            TIMES to Times,
            DIV to Div,
            MOD to Rem)

    private val const = NUMBER.map { Const(it.text.toInt()) } or CHARLIT.map { Const(it.text[1].toInt()) } or TRUE.map { Const(1) } or FALSE.map { Const(0) }

    private val funCall: Parser<FunctionCall> =
            (ID and skip(LPAR) and separatedTerms(parser(this::expr), COMMA, acceptZero = true) and skip(RPAR))
                    .map { (name, args) -> FunctionCall(UnresolvedFunction(name.text, args.size), args) }

    private val variable = ID use { Variable(text) }

    private val stringLiteral = STRINGLIT use { StringLiteral(text.removeSurrounding("\"", "\"")) }

    private val notTerm = (skip(NOT) and parser(this::term)) use { UnaryOperation(t1, Not) }
    private val parenTerm = skip(LPAR) and parser(this::expr) and skip(RPAR) use { t1 }

    private val term: Parser<Expression> = const or funCall or notTerm or variable or parenTerm or stringLiteral

    val multiplicationOperator = TIMES or DIV or MOD
    val multiplicationOrTerm = leftAssociative(term, multiplicationOperator) { l, o, r ->
        BinaryOperation(l, r, signToKind[o.type]!!)
    }

    val sumOperator = PLUS or MINUS
    val math: Parser<Expression> = leftAssociative(multiplicationOrTerm, sumOperator) { l, o, r ->
        BinaryOperation(l, r, signToKind[o.type]!!)
    }

    val comparisonOperator = EQU or NEQ or LT or GT or LEQ or GEQ
    val comparisonOrMath: Parser<Expression> = (math and optional(comparisonOperator and math))
            .map { (left, tail) -> tail?.let { (op, r) -> BinaryOperation(left, r, signToKind[op.type]!!) } ?: left }

    private val andChain = leftAssociative(comparisonOrMath, AND, { l, _, r -> BinaryOperation(l, r, And) })
    private val orChain = leftAssociative(andChain, OR, { l, _, r -> BinaryOperation(l, r, Or) })
    private val expr: Parser<Expression> = orChain

    private val skipStatement: Parser<Skip> = SKIP.map { Skip }

    private val functionCallStatement: Parser<FunctionCallStatement> = funCall.map { FunctionCallStatement(it) }

    private val assignmentStatement: Parser<Assign> = (variable and skip(ASGN) and expr).map { (v, e) -> Assign(v, e) }

private val ifStatement: Parser<If> =
        (skip(IF) and expr and skip(THEN) and parser { statementsChain } and
                zeroOrMore(skip(ELIF) and expr and skip(THEN) and parser { statementsChain }) and
                optional(skip(ELSE) and parser { statementsChain }).map { it?.t1 ?: Skip } and skip(FI))
                .map { (condExpr, thenBody, elifs, elseBody) ->
                    val elses = elifs.foldRight(elseBody) { (elifC, elifB), el -> If(elifC, elifB, el) }
                    If(condExpr, thenBody, elses)
                }

    private val forStatement: Parser<Chain> = skip(FOR) and parser { statement } and skip(COMMA) and
            parser { expr } and skip(COMMA) and
            parser { statement } and skip(DO) and
            parser { statementsChain } and skip(OD) map {
        val (init, condition, doAfter, body) = it
        Chain(init, While(condition, Chain(body, doAfter)))
    }

    private val whileStatement: Parser<While> = (skip(WHILE) and expr and skip(DO) and parser { statementsChain } and skip(OD))
            .map { (cond, body) -> While(cond, body) }

    private val repeatStatement: Parser<Chain> = (skip(REPEAT) and parser { statementsChain } and skip(UNTIL) and expr).map { (body, cond) ->
        Chain(body, While(UnaryOperation(cond, Not), body))
    }

    private val returnStatement: Parser<Return> = skip(RETURN) and expr use { Return(t1) }

    private val statement: Parser<Statement> = skipStatement or
            functionCallStatement or
            assignmentStatement or
            ifStatement or
            whileStatement or
            forStatement or
            repeatStatement or
            returnStatement

    private val functionDeclaration: Parser<FunctionDeclaration> =
            (skip(FUN) and ID and skip(LPAR) and separatedTerms(ID, COMMA, acceptZero = true) and skip (RPAR) and skip(BEGIN) and parser { statementsChain } and skip(END))
                    .map { (name, paramNames, body) ->
                        FunctionDeclaration(name.text, paramNames.map { Variable(it.text) }, body)
                    }

    private val statementsChain: Parser<Statement> = separated(statement, SEMI) and skip(optional(SEMI)) use { chainOf(*t1.terms.toTypedArray()) }

    override val rootParser: Parser<Program> = oneOrMore(functionDeclaration or (statement and optional(SEMI) use { t1 })).map {
        val functions = it.filterIsInstance<FunctionDeclaration>()
        val statements = it.filterIsInstance<Statement>().let { if (it.isEmpty()) listOf(Skip) else it }
        val rootFunc = FunctionDeclaration("main", listOf(), chainOf(*statements.toTypedArray()))
        Program(functions + rootFunc, rootFunc)
    }
}

internal fun readProgram(text: String): Program {
    val parsed = ProgramGrammar.parseToEnd(text)
    return resolveCalls(parsed)
}
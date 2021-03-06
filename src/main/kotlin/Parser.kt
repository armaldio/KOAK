import java.util.ArrayList
import kotlin.system.exitProcess

internal class Parser(private val tokens: List<Token>, val source: String, val filename: String) {
    private var current = 0

    private val typeList = arrayListOf(
            TokenType.CHAR_TYPE,
            TokenType.VOID_TYPE,
            TokenType.STRING_TYPE,
            TokenType.INT_TYPE,
            TokenType.DOUBLE_TYPE
    )
    /**
     * Return true at the end of the file
     */
    private val isAtEnd: Boolean
        get() = peek().type === TokenType.EOF

    private class ParseError : RuntimeException()

    /**
     * Get a list of statements from the file
     */
    fun parse(): List<Stmt?> {
        val statements = ArrayList<Stmt?>()
        while (!isAtEnd) {
            statements.add(declaration())
            //println(statements[statements.size - 1])
        }
        return statements
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun declaration(): Stmt? {
        return try {
            when {
                match(TokenType.CLASS) -> classStmt()
                match(TokenType.DEF) -> functionStmt()
                match(TokenType.EXTERN) -> externStmt()
                match(TokenType.STRING_TYPE) -> varDeclarationStmt(TokenType.STRING_TYPE)
                match(TokenType.INT_TYPE) -> varDeclarationStmt(TokenType.INT_TYPE)
                match(TokenType.DOUBLE_TYPE) -> varDeclarationStmt(TokenType.DOUBLE_TYPE)
                else -> statement()
            }
        } catch (error: ParseError) {
            synchronize()
            null
        }

    }

    private fun statement(): Stmt {
        return when {
            match(TokenType.FOR) -> forStmt()
            match(TokenType.IF) -> ifStmt()
            match(TokenType.PRINT) -> printStmt()
            match(TokenType.RETURN) -> returnStmt()
            match(TokenType.WHILE) -> whileStmt()
            match(TokenType.THEN) -> statement()
            else -> if (match(TokenType.LEFT_BRACE)) Stmt.Block(block()) else expressionStmt()
        }
    }

    private fun ifStmt(): Stmt {
        val condition = expression()

        consume(TokenType.THEN, "Expect then")
        val thenBranch: List<Stmt> = block()

        var elseBranch: List<Stmt> = listOf()
        if (match(TokenType.ELSE)) {
            elseBranch = block()
        }

        return Stmt.If(condition, thenBranch, elseBranch)
    }


    private fun printStmt(): Stmt {
        val value = expression()
        when {
            match(TokenType.EOL) -> consume(TokenType.EOL, "Expect 'EOL' after value.")
            match(TokenType.EOF) -> consume(TokenType.EOF, "Expect 'EOF' after value.")
        }
        return Stmt.Print(value)
    }

    private fun returnStmt(): Stmt {
        val keyword = previous()
        var value: Expr? = null
        if (!check(TokenType.EOL)) {
            value = expression()
        }

        consume(TokenType.EOL, "Expect 'EOL' after return value.")
        return Stmt.Return(keyword, value)
    }

    private fun varDeclarationStmt(type: TokenType): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")

        var initializer: Expr? = null
        if (match(TokenType.EQUAL)) {
            initializer = expression()
        }

        return Stmt.VariableDefinition(ToType(Token(type, "", "", 0, 0)), name, initializer)
    }

    private fun forStmt(): Stmt {
        val identifierAssigned = consume(TokenType.IDENTIFIER, "Expect identifier")
        consume(TokenType.EQUAL, "Expect '=' after identifier")
        val condition = expression()
        consume(TokenType.COMMA, "Expect ','")
        val identifierValue = consume(TokenType.IDENTIFIER, "Expect identifier")
        consume(TokenType.LESS, "Expect less")
        val condition2 = expression()
        consume(TokenType.COMMA, "Expect ','")
        val condition3 = expression()
        consume(TokenType.IN, "Expect 'in' after for")
        val body = this.block()

        val tokens = listOf(identifierAssigned, identifierValue)
        val conditions = listOf(condition, condition2, condition3)

        return Stmt.For(tokens, conditions, body)
    }

    private fun whileStmt(): Stmt {
        val condition = expression()
        consume(TokenType.DO, "Expect 'do' after condition")
        val body = this.block()

        return Stmt.While(condition, body)
    }

    private fun expressionStmt(): Stmt {
        val expr = expression()
        return Stmt.Expression(expr)
    }

    private fun classStmt(): Stmt? {
        return null
    }

    private fun externStmt(): Stmt.Extern {
        val name = consume(TokenType.IDENTIFIER, "Expect function name")

        consume(TokenType.LEFT_PAREN, "Expect '(' after function name")
        val parameters = ArrayList<Stmt.Parameter>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 8) {
                    error(peek(), "Cannot have more than 8 parameters.")
                }

                val paramname = consume(TokenType.IDENTIFIER, "Expect parameter name.")
                consume(TokenType.COLON, "Expect ':' after parameter name")
                val type = consume(this.typeList, "Expect parameter type.")

                val param = Stmt.Parameter(paramname, type)
                parameters.add(param)
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")
        consume(TokenType.COLON, "Expect ':' after function definition")
        val returntype = consume(this.typeList, "Expect parameter type.")
        consume(TokenType.SEMICOLON, "Expect ';' after function body")
        return Stmt.Extern(name, parameters, returntype)
    }

    private fun functionStmt(): Stmt.Function {
        val name = consume(TokenType.IDENTIFIER, "Expect function name")

        consume(TokenType.LEFT_PAREN, "Expect '(' after function name")
        val parameters = ArrayList<Stmt.Parameter>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 8) {
                    error(peek(), "Cannot have more than 8 parameters.")
                }

                val paramname = consume(TokenType.IDENTIFIER, "Expect parameter name.")
                consume(TokenType.COLON, "Expect ':' after parameter name")
                val type = consume(typeList, "Expect parameter type.")

                val param = Stmt.Parameter(paramname, type)
                parameters.add(param)
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")
        consume(TokenType.COLON, "Expect ':' after function definition")
        val returntype = consume(this.typeList, "Expect parameter type.")

        val body: MutableList<Stmt> = block()

        consume(TokenType.SEMICOLON, "Expect ';' after function body")

        val returnStatement = body.last()
        body.removeAt(body.size - 1)

        val ret: Expr.ReturnValue = Expr.ReturnValue(returnStatement, this.ToType(returntype))
        return Stmt.Function(name, parameters, body, ret)
    }

    private fun ToType(returntype: Token): Type {
        return when (returntype.type) {
            TokenType.CHAR_TYPE -> Type.Char()
            TokenType.VOID_TYPE -> Type.Void()
            TokenType.STRING_TYPE -> Type.Str()
            TokenType.INT_TYPE -> Type.Int()
            TokenType.DOUBLE_TYPE -> Type.Double()
            else -> Type.Void()
        }
    }

    private fun block(): MutableList<Stmt> {
        val statements = ArrayList<Stmt>()

        while (!this.isAtEnd) {
            statements.add(this.declaration()!!)

            // if no more expressions, exit
            if (!match(TokenType.COLON))
                break
        }

        return statements
    }


    private fun assignment(): Expr {
        val expr = or()

        if (match(TokenType.EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is Expr.Variable) {
                val name = expr.name
                return Expr.Assign(name, value)

            } else if (expr is Expr.Get) {
                return Expr.Set(expr.`object`, expr.name, value)

            }

            error(equals, "Invalid assignment target.")
        }

        return expr
    }


    private fun or(): Expr {
        var expr = and()

        while (match(TokenType.OR)) {
            val operator = previous()
            val right = and()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }


    private fun and(): Expr {
        var expr = equality()

        while (match(TokenType.AND)) {
            val operator = previous()
            val right = equality()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }


    private fun equality(): Expr {
        var expr = comparison()

        while (match(TokenType.NOT_EQUAL, TokenType.EQUAL_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }


    private fun comparison(): Expr {
        var expr = addition()

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val operator = previous()
            val right = addition()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }


    private fun addition(): Expr {
        var expr = multiplication()

        while (match(TokenType.MINUS, TokenType.PLUS)) {
            val operator = previous()
            val right = multiplication()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun multiplication(): Expr {
        var expr = unary()

        while (match(TokenType.SLASH, TokenType.STAR)) {
            val operator = previous()
            val right = unary()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }


    private fun unary(): Expr {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return call()

    }


    private fun finishCall(callee: Expr): Expr {
        val arguments = ArrayList<Expr>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {

                if (arguments.size >= 8) {
                    error(peek(), "Cannot have more than 8 arguments.")
                }

                arguments.add(expression())
            } while (match(TokenType.COMMA))
        }

        val paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.")

        return Expr.Call(callee, paren, arguments)
    }


    private fun call(): Expr {
        var expr = primary()

        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr)

            } else if (match(TokenType.DOT)) {
                val name = consume(TokenType.IDENTIFIER,
                        "Expect property name after '.'.")
                expr = Expr.Get(expr, name)

            } else {
                break
            }
        }

        return expr
    }


    private fun primary(): Expr {
        when {
            match(TokenType.FALSE) -> return Expr.Literal(false)
            match(TokenType.TRUE) -> return Expr.Literal(true)
            match(TokenType.NULL) -> return Expr.Literal("null")
            match(TokenType.NUMBER, TokenType.STRING) -> return Expr.Literal(previous().literal!!)
            match(TokenType.SUPER) -> {
                val keyword = previous()
                consume(TokenType.DOT, "Expect '.' after 'super'.")
                val method = consume(TokenType.IDENTIFIER,
                        "Expect superclass method name.")
                return Expr.Super(keyword, method)
            }
            match(TokenType.THIS) -> return Expr.This(previous())
            match(TokenType.IDENTIFIER) -> return Expr.Variable(previous())
            match(TokenType.LEFT_PAREN) -> {
                val expr = expression()
                consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
                return Expr.Grouping(expr)
            }
            match(TokenType.SEMICOLON) -> Expr.Literal(previous())
            match(TokenType.EOL) -> advance()
        }

        throw error(peek(), "Expect expression")
    }

    private fun report(line: Int, column: Int, message: String) {
        System.err.println("[line $line] Error: $message")
        System.err.println(source.split("\n")[line])
        for (i in 1..(column - 1)) System.err.print(" ")
        System.err.println("^")
        throw Exception(message)
    }


    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }


    /**
     * Check if the next char is of type type
     */
    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()

        throw error(peek(), message)
    }

    /**
     * Check if the next char is one of the type from types
     */
    private fun consume(types: ArrayList<TokenType>, message: String): Token {
        types.forEach { if (check(it)) return advance() }
        throw error(peek(), message)
    }

    /**
     * Check if current char is type of toeknType
     * @property tokenType the type of the token
     */
    private fun check(tokenType: TokenType): Boolean {
        return if (isAtEnd) false else peek().type === tokenType
    }

    /**
     * If not at the end, advance and return previous token
     */
    private fun advance(): Token {
        if (!isAtEnd) current++
        return previous()
    }

    /**
     * Return current token
     */
    private fun peek(): Token {
        return tokens[current]
    }

    /**
     * Return previous token
     */
    private fun previous(): Token {
        return tokens[current - 1]
    }


    private fun error(token: Token, message: String): ParseError {
        report(token.line, token.column, "$message instead got ${token.type} $token")
        return ParseError()
    }

    private fun synchronize() {
        advance()

        while (!isAtEnd) {
            if (previous().type === TokenType.EOL) return

            if (
                    peek().type == TokenType.CLASS ||
                    peek().type == TokenType.DEF ||
                    peek().type == TokenType.INT_TYPE ||
                    peek().type == TokenType.STRING_TYPE ||
                    peek().type == TokenType.FOR ||
                    peek().type == TokenType.IF ||
                    peek().type == TokenType.WHILE ||
                    peek().type == TokenType.PRINT ||
                    peek().type == TokenType.RETURN
            )
                return

            advance()
        }
    }
}

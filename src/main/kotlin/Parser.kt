import java.util.ArrayList

internal class Parser(private val tokens: List<Token>) {
    private var current = 0

    private val isAtEnd: Boolean
        get() = peek().type === TokenType.EOF

    private class ParseError : RuntimeException()

    fun parse(): List<Stmt?> {
        val statements = ArrayList<Stmt?>()
        while (!isAtEnd) {
            statements.add(declaration())
        }
        return statements
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun declaration(): Stmt? {
        return try {
            when {
            //match(TokenType.CLASS) -> classDeclaration()
                match(TokenType.DEF) -> function("function")
                match(TokenType.COMMENT) -> {
                    consume(TokenType.EOL, "Single line comment")
                    null
                }
                match(TokenType.STRING_TYPE) -> varDeclaration("string")
                match(TokenType.INT_TYPE) -> varDeclaration("int")
                else -> statement()
            }
        } catch (error: ParseError) {
            synchronize()
            null
        }

    }


    /*private fun classDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect class name.")


        var superclass: Expr? = null
        if (match(TokenType.LESS)) {
            consume(TokenType.IDENTIFIER, "Expect superclass name.")
            superclass = Expr.Variable(previous())
        }


        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.")

        val methods = ArrayList<Stmt.Function>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd) {
            methods.add(function("method"))
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.")

        return Stmt.Class(name, superclass, methods)

    }*/


    private fun statement(): Stmt {
        when {
        //match(TokenType.FOR) -> return forStmt()
            match(TokenType.IF) -> return ifStmt()
            match(TokenType.PRINT) -> return printStmt()
            match(TokenType.RETURN) -> return returnStmt()
            match(TokenType.WHILE) -> return whileStmt()
            else -> return if (match(TokenType.LEFT_BRACE)) Stmt.Block(block()) else expressionStmt()
        }
    }


    /*private fun forStmt(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.")

        val initializer: Stmt?
        if (match(TokenType.EOL)) {
            initializer = null
        } else if (match(TokenType.VAR)) {
            initializer = varDeclaration()
        } else {
            initializer = expressionStmt()
        }



        var condition: Expr? = null
        if (!check(TokenType.EOL)) {
            condition = expression()
        }
        consume(TokenType.EOL, "Expect 'EOL' after loop condition.")



        var increment: Expr? = null
        if (!check(TokenType.RIGHT_PAREN)) {
            increment = expression()
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.")


        var body = statement()


        if (increment != null) {
            body = Stmt.Block(Arrays.asList(
                    body,
                    Stmt.Expression(increment)))
        }



        if (condition == null) condition = Expr.Literal(true)
        body = Stmt.While(condition, body)



        if (initializer != null) {
            body = Stmt.Block(Arrays.asList(initializer, body))
        }


        return body

    }*/


    private fun ifStmt(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.") // [parens]

        val thenBranch = statement()
        var elseBranch: Stmt? = null
        if (match(TokenType.ELSE)) {
            elseBranch = statement()
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


    private fun varDeclaration(type: String): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")

        var initializer: Expr? = null
        if (match(TokenType.EQUAL)) {
            initializer = expression()
        }

        consume(TokenType.EOL, "Expect 'EOL' after variable declaration.")
        return Stmt.Var(type, name, initializer)
    }


    private fun whileStmt(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.")
        val body = statement()

        return Stmt.While(condition, body)
    }


    private fun expressionStmt(): Stmt {
        val expr = expression()
        consume(TokenType.EOL, "Expect 'EOL' after expression.")
        return Stmt.Expression(expr)
    }


    private fun function(kind: String): Stmt.Function {
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name.")

        consume(TokenType.LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = ArrayList<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 8) {
                    error(peek(), "Cannot have more than 8 parameters.")
                }

                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")



        consume(TokenType.LEFT_BRACE, "Expect '{' before $kind body.")
        val body = block()
        return Stmt.Function(name, parameters, body)

    }


    private fun block(): List<Stmt> {
        val statements = ArrayList<Stmt>()

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd) {
            statements.add(this.declaration()!!)
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }


    private fun assignment(): Expr {
        /* Stmts and State parse-assignment < Control Flow or-in-assignment
    Expr expr = equality();
*/

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

        /* Parsing Expressions unary < Functions unary-call
    return primary();
*/

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
        if (match(TokenType.FALSE)) return Expr.Literal(false)
        if (match(TokenType.TRUE)) return Expr.Literal(true)
        if (match(TokenType.NULL)) return Expr.Literal(null!!)

        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return Expr.Literal(previous().literal!!)
        }


        if (match(TokenType.SUPER)) {
            val keyword = previous()
            consume(TokenType.DOT, "Expect '.' after 'super'.")
            val method = consume(TokenType.IDENTIFIER,
                    "Expect superclass method name.")
            return Expr.Super(keyword, method)
        }



        if (match(TokenType.THIS)) return Expr.This(previous())



        if (match(TokenType.IDENTIFIER)) {
            return Expr.Variable(previous())
        }


        if (match(TokenType.LEFT_PAREN)) {
            val expr = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }


        throw error(peek(), "Expect expression.")

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


    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()

        throw error(peek(), message)
    }


    private fun check(tokenType: TokenType): Boolean {
        return if (isAtEnd) false else peek().type === tokenType
    }


    private fun advance(): Token {
        if (!isAtEnd) current++
        return previous()
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }


    private fun error(token: Token, message: String): ParseError {
        println("Error: $message instead got $token")
        return ParseError()
    }


    private fun synchronize() {
        advance()

        while (!isAtEnd) {
            if (previous().type === TokenType.EOL) return

            when (peek().type) {
                TokenType.CLASS, TokenType.DEF, TokenType.INT_TYPE, TokenType.STRING_TYPE, TokenType.FOR, TokenType.IF, TokenType.WHILE, TokenType.PRINT, TokenType.RETURN -> return
            }

            advance()
        }
    }

}
class Scanner(private val source: String) {
    private val tokens = mutableListOf<Token>()

    private var start = 0
    private var current = 0
    private var line = 1

    private var hadError = false

    fun scanTokens(): MutableList<Token> {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }

    private fun error(line: Int, message: String) {
        report(line, "", message)
    }

    private fun report(line: Int, where: String, message: String) {
        System.err.println(
                "[line $line] Error$where: $message")
        hadError = true
    }

    private fun isAtEnd(): Boolean {
        return current >= source.length
    }

    private fun advance(): Char {
        current++
        return source[(current - 1)]
    }

    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)

        val token = Token(type, text, literal, line)

        tokens.add(token)
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[(current)] != expected) return false

        current++
        return true
    }

    private fun peek(): Char {
        return if (isAtEnd()) '\u0000' else source[(current)]
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        // Unterminated string.
        if (isAtEnd()) {
            this.error(line, "Unterminated string.")
            return
        }

        // The closing ".
        advance()

        // Trim the surrounding quotes.
        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }

    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    private fun number() {
        while (isDigit(peek())) advance()

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance()

            while (isDigit(peek())) advance()
        }

        addToken(TokenType.NUMBER,
                java.lang.Double.parseDouble(source.substring(start, current)))
    }

    private fun peekNext(): Char {
        return if (current + 1 >= source.length) '\u0000' else source[(current + 1)]
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()

        addToken(TokenType.IDENTIFIER)
    }

    private fun isAlpha(c: Char): Boolean {
        return c in 'a'..'z' ||
                c in 'A'..'Z' ||
                c == '_'
    }

    private fun isAlphaNumeric(c: Char): Boolean {
        return isAlpha(c) || isDigit(c)
    }

    private fun scanToken() {
        val c = advance()
        when (c) {
            '(' -> addToken(TokenType.LEFT_PAREN)
            ')' -> addToken(TokenType.RIGHT_PAREN)
            '{' -> addToken(TokenType.LEFT_BRACE)
            '}' -> addToken(TokenType.RIGHT_BRACE)
            ',' -> addToken(TokenType.COMMA)
            '.' -> addToken(TokenType.DOT)
            '-' -> addToken(TokenType.MINUS)
            '+' -> addToken(TokenType.PLUS)
            ';' -> addToken(TokenType.SEMICOLON)
            '*' -> addToken(TokenType.STAR)
            '/' -> if (match('/')) while (peek() != '\n' && !isAtEnd()) advance() else addToken(TokenType.SLASH)

            '!' -> addToken(if (this.match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> addToken(if (this.match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> addToken(if (this.match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> addToken(if (this.match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)

            ' ', '\r', '\t' -> Unit

            '\n' -> line++

            '"' -> string()

            else -> when {
                isDigit(c) -> number()
                isAlpha(c) -> identifier()
                else -> this.error(line, "Unexpected character.")
            }

        }
    }

}
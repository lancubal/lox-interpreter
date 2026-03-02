package com.craftinginterpreters.lox;

import java.util.List;
import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    // Grammar rules

    private Expr expression() {
        return equality();
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return primary();
    }

    private Expr primary() {
        if (match(FALSE))
            return new Expr.Literal(false);
        if (match(TRUE))
            return new Expr.Literal(true);
        if (match(NIL))
            return new Expr.Literal(null);
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // Error handling for unexpected tokens.
        throw error(peek(), "Expect expression.");
    }

    // Helper methods

    // Check if the current token matches any of the given types. If so, consume it
    // and return true.
    // Otherwise, return false.
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // Check if the current token is of the given type.
    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().type == type;
    }

    // Consume the current token and return it.
    private Token advance() {
        if (!isAtEnd())
            current++;
        return previous();
    }

    // Consume a token of the given type, or throw an error if it doesn't match.
    private Token consume(TokenType type, String message) {
        if (check(type))
            return advance();
        throw error(peek(), message);
    }

    // Check if the end of the source code has been reached.
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    // Peek at the current token without consuming it.
    private Token peek() {
        return tokens.get(current);
    }

    // Get the most recently consumed token.
    private Token previous() {
        return tokens.get(current - 1);
    }

    // Error handling
    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    // Synchronize the parser after an error.
    // It discards tokens until it thinks it has found a statement boundary.
    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON)
                return;
            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }

}

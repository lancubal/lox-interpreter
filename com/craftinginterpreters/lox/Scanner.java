package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.craftinginterpreters.lox.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    private static final Map<String, TokenType> keywords;
    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("break", BREAK);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("inner", INNER);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
        keywords.put("with", WITH);
    }

    Scanner(String source) {
        this.source = source;
    }

    // Scan the source code and return a list of tokens.
    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    // Scan a single token and add it to the list.
    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(':
                addToken(LEFT_PAREN);
                break;
            case ')':
                addToken(RIGHT_PAREN);
                break;
            case '{':
                addToken(LEFT_BRACE);
                break;
            case '}':
                addToken(RIGHT_BRACE);
                break;
            case '[':
                addToken(LEFT_BRACKET);
                break;
            case ']':
                addToken(RIGHT_BRACKET);
                break;
            case ',':
                addToken(COMMA);
                break;
            case '.':
                addToken(DOT);
                break;
            case '-':
                addToken(MINUS);
                break;
            case '+':
                addToken(PLUS);
                break;
            case ';':
                addToken(SEMICOLON);
                break;
            case '*':
                addToken(STAR);
                break;
            case '?':
                addToken(QUESTION);
                break;
            case ':':
                addToken(COLON);
                break;
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd())
                        advance();
                } else if (match('*')) {
                    // C-style block comments (supporting nesting)
                    int depth = 1;
                    while (depth > 0 && !isAtEnd()) {
                        if (peek() == '/' && peekNext() == '*') {
                            advance();
                            advance();
                            depth++;
                        } else if (peek() == '*' && peekNext() == '/') {
                            advance();
                            advance();
                            depth--;
                        } else {
                            if (peek() == '\n') {
                                line++;
                            }
                            advance();
                        }
                    }
                    if (depth > 0) {
                        Lox.error(line, "Unterminated block comment.");
                    }
                } else {
                    addToken(SLASH);
                }
                break;

            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;

            case '\n':
                line++;
                break;

            case '"':
                string();
                break;

            default:
                if (isDigit(c)) {
                    // Handle number literals.
                    number();
                } else if (isAlpha(c)) {
                    // Handle identifiers.
                    identifier();
                } else {
                    // Error handling for unexpected characters.
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    // Is the end of the source code reached?
    private boolean isAtEnd() {
        return current >= source.length();
    }

    // Consume the next character and return it.
    private char advance() {
        return source.charAt(current++);
    }

    // Advance the current position without consuming the character.
    private char peek() {
        if (isAtEnd())
            return '\0';
        return source.charAt(current);
    }

    // Peek at the next character without consuming it.
    // The scanner looks ahead at most two characters.
    private char peekNext() {
        if (current + 1 >= source.length())
            return '\0';
        return source.charAt(current + 1);
    }

    // Check if a character is a digit.
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // Check if a character is an alphabetic character.
    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    // Check if a character is alphanumeric.
    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    // Add a token to the list.
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    // Add a token to the list with a literal value.
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    // Check if the next character matches the expected character.
    private boolean match(char expected) {
        if (isAtEnd())
            return false;
        if (source.charAt(current) != expected)
            return false;
        current++;
        return true;
    }

    // Handle string literals.
    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n')
                line++;
            advance();
        }
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }
        // The closing ".
        advance();
        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    // Handle number literals.
    private void number() {
        while (isDigit(peek()))
            advance();
        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();
            while (isDigit(peek()))
                advance();
        }

        // Convert the string to a double.
        addToken(NUMBER,
                Double.parseDouble(source.substring(start, current)));
    }

    // Handle identifiers and reserved words.
    private void identifier() {
        while (isAlphaNumeric(peek()))
            advance();
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null)
            type = IDENTIFIER;
        addToken(type);
    }

}

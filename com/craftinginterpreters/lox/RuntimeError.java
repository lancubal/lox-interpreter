package com.craftinginterpreters.lox;

class RuntimeError extends RuntimeException {
    final Token token;

    // Tracks the token that identifies where in the user’s code the runtime error came from.
    RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }
}

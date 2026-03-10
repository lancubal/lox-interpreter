package com.craftinginterpreters.lox;

public class Return extends RuntimeException {
    final Object value;

    Return(Object value) {
        // We don't need the stack trace for this exception, since it's used for control flow, not error handling.
        super(null, null, false, false);
        
        this.value = value;
    }
}

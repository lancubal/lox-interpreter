package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Environment {
    static final Object UNINITIALIZED = new Object();
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();
    private final List<Object> valuesList = new ArrayList<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    // Bind a variable name to a value in the environment.
    void define(String name, Object value) {
        values.put(name, value);
        valuesList.add(value);
    }

    // Walk up the chain of enclosing environments to find the one at the given distance.
    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }
        return environment;
    }

    // Fast O(1) indexed variable access for resolved local variables.
    Object getAt(int distance, int index) {
        Object value = ancestor(distance).valuesList.get(index);
        if (value == UNINITIALIZED) {
            throw new RuntimeError(new Token(TokenType.IDENTIFIER, "", null, -1),
                    "Variable used before initialization.");
        }
        return value;
    }

    // Fast O(1) indexed variable assignment for resolved local variables.
    void assignAt(int distance, int index, Object value) {
        ancestor(distance).valuesList.set(index, value);
    }

    // Get a variable from the environment at the given distance (by name).
    Object getAt(int distance, String name) {
        Object value = ancestor(distance).values.get(name);
        if (value == UNINITIALIZED) {
            throw new RuntimeError(new Token(TokenType.IDENTIFIER, name, null, -1),
                    "Variable '" + name + "' used before initialization.");
        }
        return value;
    }

    Object getAt(int distance, Token name) {
        Object value = ancestor(distance).values.get(name.lexeme);
        if (value == UNINITIALIZED) {
            throw new RuntimeError(name,
                    "Variable '" + name.lexeme + "' used before initialization.");
        }
        return value;
    }

    // Set a variable's value in the environment at the given distance (by name).
    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            Object value = values.get(name.lexeme);
            if (value == UNINITIALIZED) {
                throw new RuntimeError(name,
                        "Variable '" + name.lexeme + "' used before initialization.");
            }
            return value;
        }

        if (enclosing != null)
            return enclosing.get(name);

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    // Assignment is not allowed to create new variables. It only updates existing
    // ones.
    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        // If the variable isn't in this environment, try the enclosing one.
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

}

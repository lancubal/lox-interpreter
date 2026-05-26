package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {

  private final Stmt.Function declaration;

  // The environment where the function was declared, which is used to resolve free variables in the
  // function body.
  // Holds on to the surrounding variables at the time of the function's declaration.
  private final Environment closure;

  private final boolean isInitializer;

  LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
    this.isInitializer = isInitializer;

    this.declaration = declaration;
    this.closure = closure;
  }

  LoxFunction bind(LoxInstance instance) {
    Environment environment = new Environment(closure);
    environment.define("this", instance);
    return new LoxFunction(declaration, environment, isInitializer);
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    // Each function call gets its own environment, which is enclosed in the environment where the
    // function was declared.
    Environment environment = new Environment(closure);

    // Bind the function's parameters to the arguments in the new environment.
    for (int i = 0; i < declaration.params.size(); i++) {
      environment.define(declaration.params.get(i).lexeme, arguments.get(i));
    }

    try {
      interpreter.executeBlock(declaration.body, environment);
    } catch (Return returnStatement) {
      if (isInitializer) return closure.getAt(0, "this");
      return returnStatement.value;
    }

    if (isInitializer) return closure.getAt(0, "this");

    // If the function doesn't explicitly return a value, return nil.
    return null;
  }

  @Override
  public int arity() {
    return declaration.params.size();
  }

  @Override
  public String toString() {
    return "<fn " + declaration.name.lexeme + ">";
  }
}

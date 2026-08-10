package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {

  private final Stmt.Function declaration;

  // The environment where the function was declared, which is used to resolve free variables in the
  // function body.
  // Holds on to the surrounding variables at the time of the function's declaration.
  private final Environment closure;

  private final boolean isInitializer;
  final boolean isGetter;

  LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer, boolean isGetter) {
    this.isInitializer = isInitializer;
    this.isGetter = isGetter;

    this.declaration = declaration;
    this.closure = closure;
  }

  LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
    this(declaration, closure, isInitializer, false);
  }

  LoxFunction bind(LoxInstance instance) {
    Environment environment = new Environment(closure);
    environment.define("this", instance);
    return new LoxFunction(declaration, environment, isInitializer, isGetter);
  }

  LoxFunction bind(LoxInstance instance, LoxClass currentClass, String methodName) {
    Environment environment = new Environment(closure);
    environment.define("this", instance);
    environment.define("currentClass", currentClass);
    environment.define("currentMethodName", methodName);
    return new LoxFunction(declaration, environment, isInitializer, isGetter);
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
    if (declaration.name == null) return "<fn>";
    return "<fn " + declaration.name.lexeme + ">";
  }
}

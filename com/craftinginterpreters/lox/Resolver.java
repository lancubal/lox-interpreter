package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

  private final Interpreter interpreter;

  private static class Variable {
    final Token name;
    boolean state;
    boolean isUsed;
    final int index;

    Variable(Token name, boolean state, int index) {
      this.name = name;
      this.state = state;
      this.isUsed = false;
      this.index = index;
    }
  }

  // Stack of scopes, where each scope is a map of variable names to a Variable state object.
  private final Stack<Map<String, Variable>> scopes = new Stack<>();
  private FunctionType currentFunction = FunctionType.NONE;

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  private enum FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD
  }

  private enum ClassType {
    NONE,
    CLASS,
    SUBCLASS
  }

  private ClassType currentClass = ClassType.NONE;

  // A block statement introduces a new scope for the statements inside it.
  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    declare(stmt.name);
    define(stmt.name);

    if (stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
      Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
    }

    if (stmt.superclass != null) {
      currentClass = ClassType.SUBCLASS;
      resolve(stmt.superclass);
    }

    if (stmt.superclass != null) {
      beginScope();
      scopes.peek().put("super", new Variable(new Token(TokenType.SUPER, "super", null, -1), true, 0));
    }

    beginScope();
    scopes.peek().put("this", new Variable(new Token(TokenType.THIS, "this", null, -1), true, 0));

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }
      resolveFunction(method, declaration);
    }

    endScope();

    if (stmt.superclass != null) endScope();

    currentClass = enclosingClass;

    return null;
  }

  // A function declaration introduces a new scope for its body and binds its
  // parameters in that scope.

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    // Define the function name in the current scope so that it can be recursive.
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(stmt, FunctionType.FUNCTION);
    return null;
  }

  @Override
  public Void visitFunctionExpr(Expr.Function expr) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = FunctionType.FUNCTION;

    beginScope();
    for (Token param : expr.params) {
      declare(param);
      define(param);
    }
    resolve(expr.body);
    endScope();

    currentFunction = enclosingFunction;
    return null;
  }

  // A variable declaration adds a new variable to the current scope.
  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);
    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }
    define(stmt.name);
    return null;
  }

  // Variable and assignment expressions need to have their variables resolved.

  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    // Check if the variable is being accessed in its own initializer.
    // Declared but not yet defined variables are marked as false in the current
    // scope.
    if (!scopes.isEmpty()) {
      Variable var = scopes.peek().get(expr.name.lexeme);
      if (var != null && var.state == false) {
        Lox.error(expr.name, "Can't read local variable in its own initializer.");
      }
    }

    resolveLocal(expr, expr.name);
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    // Resolve the expression for the assignment value first
    resolve(expr.value);
    // Then resolve the variable being assigned to, which may be a local variable.
    resolveLocal(expr, expr.name);
    return null;
  }

  // Other nodes don't introduce new scopes, but we still need to visit them to
  // resolve any variables they contain.

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  // When we resolve a if statement there is no control flow analysis, we resolve
  // all the branches.
  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null) resolve(stmt.elseBranch);
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {

    // We don't allow return statements outside of functions.
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Can't return from top-level code.");
    }

    if (stmt.value != null) {
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword, "Can't return a value from an initializer.");
      }
      resolve(stmt.value);
    }
    return null;
  }

  // Resolve its condition and body exactly once.
  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);
    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  @Override
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitSuperExpr(Expr.Super expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword, "Can't use 'super' outside of a class.");
    } else if (currentClass != ClassType.SUBCLASS) {
      Lox.error(expr.keyword, "Can't use 'super' in a class with no superclass.");
    }
    resolveLocal(expr, expr.keyword);
    return null;
  }

  @Override
  public Void visitThisExpr(Expr.This expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword, "Can't use 'this' outside of a class.");
      return null;
    }
    resolveLocal(expr, expr.keyword);
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
    return null;
  }

  // Helper methods for managing scopes and resolving variables.

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  private Void resolveFunction(Stmt.Function function, FunctionType type) {

    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    // New scope for the function body.
    beginScope();
    // Bind the function's parameters in the new scope.
    for (Token param : function.params) {
      declare(param);
      define(param);
    }
    // Resolve the function's body in the new scope.
    resolve(function.body);
    endScope();
    currentFunction = enclosingFunction;
    return null;
  }

  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }

  void resolve(Expr expr) {
    expr.accept(this);
  }

  private void beginScope() {
    scopes.push(new HashMap<String, Variable>());
  }

  private void endScope() {
    Map<String, Variable> scope = scopes.pop();
    for (Map.Entry<String, Variable> entry : scope.entrySet()) {
      Variable var = entry.getValue();
      if (!var.isUsed && var.name != null && !entry.getKey().equals("this") && !entry.getKey().equals("super")) {
        Lox.error(var.name, "Local variable '" + var.name.lexeme + "' is never used.");
      }
    }
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, Variable> scope = scopes.peek();

    // Collision check: if the variable name is already declared in the current
    // scope, report an error.
    if (scope.containsKey(name.lexeme)) {
      Lox.error(name, "Already a variable with this name in this scope.");
    }

    int index = scope.size();
    scope.put(name.lexeme, new Variable(name, false, index));
  }

  // We set the variable to "defined" to mark it fully initialized and ready for
  // use.
  private void define(Token name) {
    if (scopes.isEmpty()) return;
    Variable var = scopes.peek().get(name.lexeme);
    if (var != null) {
      var.state = true;
    } else {
      int index = scopes.peek().size();
      scopes.peek().put(name.lexeme, new Variable(name, true, index));
    }
  }

  // Find the variable in the nearest enclosing scope and tell the interpreter how
  // many scopes away it is and its slot index in that scope.
  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        Variable var = scopes.get(i).get(name.lexeme);
        int distance = scopes.size() - 1 - i;
        interpreter.resolve(expr, distance, var.index);
        var.isUsed = true;
        return;
      }
    }
  }
}

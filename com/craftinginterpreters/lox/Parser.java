package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }
    return statements;
  }

  Object parseRepl() {
    if (check(VAR) || check(CLASS) || check(IF) || check(WHILE) ||
        check(FOR) || check(PRINT) || check(RETURN) || check(LEFT_BRACE) ||
        (check(FUN) && checkNext(IDENTIFIER))) {
      return parse();
    }

    try {
      Expr expr = expression();
      if (match(SEMICOLON)) {
        // Optional trailing semicolon in REPL expression
      }
      if (isAtEnd()) {
        return expr;
      }
    } catch (ParseError error) {
      // Fall back to statements
    }

    return parse();
  }

  private boolean checkNext(TokenType type) {
    if (isAtEnd()) return false;
    if (tokens.get(current + 1).type == EOF) return false;
    return tokens.get(current + 1).type == type;
  }

  private Expr expression() {
    return comma();
  }

  private Expr comma() {
    Expr expr = assignment();

    while (match(COMMA)) {
      Token operator = previous();
      Expr right = assignment();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private int loopDepth = 0;

  private Stmt statement() {
    if (match(FOR)) return forStatement();
    if (match(IF)) return ifStatement();
    if (match(PRINT)) return printStatement();
    if (match(RETURN)) return returnStatement();
    if (match(WHILE)) return whileStatement();
    if (match(BREAK)) return breakStatement();
    if (match(LEFT_BRACE)) return new Stmt.Block(block());
    return expressionStatement();
  }

  private Stmt breakStatement() {
    Token keyword = previous();
    if (loopDepth == 0) {
      error(keyword, "Can't use 'break' outside of a loop.");
    }
    consume(SEMICOLON, "Expect ';' after 'break'.");
    return new Stmt.Break(keyword);
  }

  private Stmt forStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt initializer;
    if (match(SEMICOLON)) {
      // Ommited initializer.
      initializer = null;
    } else if (match(VAR)) {
      // Variable declaration.
      initializer = varDeclaration();
    } else {
      // Expression statement.
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");

    Stmt body;
    try {
      loopDepth++;
      body = statement();
    } finally {
      loopDepth--;
    }

    // Desugar the for loop into a while loop.
    if (increment != null) {
      body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
    }

    if (condition == null) {
      condition = new Expr.Literal(true);
    }
    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    return body;
  }

  private Stmt ifStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after if condition.");
    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(ELSE)) {
      elseBranch = statement();
    }
    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt declaration() {
    try {
      if (match(CLASS)) return classDeclaration();
      if (match(FUN)) return function("function");
      if (match(VAR)) return varDeclaration();
      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    Token name = consume(IDENTIFIER, "Expect class name.");
    Expr.Variable superclass = null;
    if (match(LESS)) {
      consume(IDENTIFIER, "Expect superclass name.");
      superclass = new Expr.Variable(previous());
    }
    consume(LEFT_BRACE, "Expect '{' before class body.");
    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(function("method"));
    }
    consume(RIGHT_BRACE, "Expect '}' after class body.");
    return new Stmt.Class(name, superclass, methods);
  }

  private Stmt varDeclaration() {
    Token name = consume(IDENTIFIER, "Expect variable name.");
    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }
    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private Stmt whileStatement() {
    consume(LEFT_PAREN, "Excpect '(' after 'while'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after condition.");
    Stmt body;
    try {
      loopDepth++;
      body = statement();
    } finally {
      loopDepth--;
    }

    return new Stmt.While(condition, body);
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt returnStatement() {
    Token keyword = previous();
    Expr value = null;

    // Optional return value.
    if (!check(SEMICOLON)) {
      value = expression();
    }

    consume(SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Stmt.Function function(String kind) {
    Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

    // Parameters.
    consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
    List<Token> parameters = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Can't have more than 255 parameters.");
        }
        parameters.add(consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after parameters.");

    consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Stmt.Function(name, parameters, body);
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }
    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  // We parse the left-hand side, which can be any expression of higher
  // precedence.
  // If we find an =, we parse the right-hand side and then wrap it all up in an
  // assignment expression tree node.
  private Expr assignment() {
    Expr expr = ternary();
    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();
      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable) expr).name;
        return new Expr.Assign(name, value);
      } else if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get) expr;
        return new Expr.Set(get.object, get.name, value);
      }
      error(equals, "Invalid assignment target.");
    }
    return expr;
  }

  private Expr ternary() {
    Expr expr = or();
    if (match(QUESTION)) {
      Expr thenBranch = expression();
      consume(COLON, "Expect ':' after then branch of conditional operator.");
      Expr elseBranch = ternary();
      expr = new Expr.Ternary(expr, thenBranch, elseBranch);
    }
    return expr;
  }

  private Expr or() {
    Expr expr = and();
    while (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }
    return expr;
  }

  private Expr and() {
    Expr expr = equality();
    while (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }
    return expr;
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

    if (match(PLUS, SLASH, STAR, BANG_EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, COMMA)) {
      Token operator = previous();
      error(operator, "Binary operator missing left-hand operand.");

      switch (operator.type) {
        case COMMA:
          comma();
          break;
        case BANG_EQUAL:
        case EQUAL_EQUAL:
          equality();
          break;
        case GREATER:
        case GREATER_EQUAL:
        case LESS:
        case LESS_EQUAL:
          comparison();
          break;
        case PLUS:
          term();
          break;
        case SLASH:
        case STAR:
          factor();
          break;
        default:
          break;
      }

      throw new ParseError();
    }

    return call();
  }

  private Expr call() {
    Expr expr = primary();

    while (true) {
      if (match(LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(DOT)) {
        Token name = consume(IDENTIFIER, "Expect property name after '.'.");
        expr = new Expr.Get(expr, name);
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {

        if (arguments.size() >= 255) {
          error(peek(), "Can't have more than 255 arguments.");
        }

        arguments.add(assignment());

      } while (match(COMMA));
    }

    Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

    return new Expr.Call(callee, paren, arguments);
  }

  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);
    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }
    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    if (match(SUPER)) {
      Token keyword = previous();
      consume(DOT, "Expect '.' after 'super'.");
      Token method = consume(IDENTIFIER, "Expect superclass method name.");
      return new Expr.Super(keyword, method);
    }

    if (match(THIS)) return new Expr.This(previous());

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
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
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  // Consume the current token and return it.
  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  // Consume a token of the given type, or throw an error if it doesn't match.
  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();
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
      if (previous().type == SEMICOLON) return;
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

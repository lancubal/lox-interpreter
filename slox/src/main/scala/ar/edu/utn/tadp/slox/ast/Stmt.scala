package ar.edu.utn.tadp.slox.ast

import ar.edu.utn.tadp.slox.tokens.Token

sealed trait Stmt

object Stmt:
  case class Expression(expression: Expr) extends Stmt
  case class Print(expression: Expr) extends Stmt
  case class Var(name: Token, initializer: Option[Expr]) extends Stmt
  case class Block(statements: List[Stmt]) extends Stmt
  case class If(condition: Expr, thenBranch: Stmt, elseBranch: Option[Stmt]) extends Stmt
  case class While(condition: Expr, body: Stmt) extends Stmt
  case class Function(name: Token, params: List[Token], body: List[Stmt]) extends Stmt
  case class Return(keyword: Token, value: Option[Expr]) extends Stmt
  case class Class(name: Token, superclass: Option[Expr.Variable], methods: List[Stmt.Function]) extends Stmt

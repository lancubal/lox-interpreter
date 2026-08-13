package ar.edu.utn.tadp.slox.parser

import ar.edu.utn.tadp.slox.ast.*
import ar.edu.utn.tadp.slox.tokens.{Token, TokenType}
import ar.edu.utn.tadp.slox.tokens.TokenType.*
import ar.edu.utn.tadp.slox.values.Value

case class ParseError(token: Token, message: String)

case class Parser(tokens: List[Token]) {

  def parse(): Either[List[String], List[Stmt]] = {
    parseLoop(tokens, List.empty, List.empty)
  }

  private def parseLoop(
      currentTokens: List[Token],
      stmts: List[Stmt],
      errors: List[String]
  ): Either[List[String], List[Stmt]] = {
    if (isAtEnd(currentTokens)) {
      if (errors.nonEmpty) Left(errors.reverse)
      else Right(stmts.reverse)
    } else {
      declaration(currentTokens) match {
        case Right((stmt, nextTokens)) =>
          parseLoop(nextTokens, stmt :: stmts, errors)
        case Left((err, synchronizedTokens)) =>
          parseLoop(synchronizedTokens, stmts, s"[line ${err.token.line}] Error at '${err.token.lexeme}': ${err.message}" :: errors)
      }
    }
  }

  private def declaration(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    matchToken(toks, CLASS) match {
      case Some(nextToks) => classDeclaration(nextToks)
      case None =>
        matchToken(toks, FUN) match {
          case Some(nextToks) => function("function", nextToks)
          case None =>
            matchToken(toks, VAR) match {
              case Some(nextToks) => varDeclaration(nextToks)
              case None           => statement(toks)
            }
        }
    }
  }

  private def classDeclaration(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    consume(toks, IDENTIFIER, "Expect class name.").flatMap { case (name, t1) =>
      val superRes = if (check(t1, LESS)) {
        consume(t1, LESS, "Expect '<'.").flatMap { case (_, tSuperLess) =>
          consume(tSuperLess, IDENTIFIER, "Expect superclass name.").map { case (superName, tSuperName) =>
            (Some(Expr.Variable(superName)), tSuperName)
          }
        }
      } else Right((None, t1))

      superRes.flatMap { case (superclass, t2) =>
        consume(t2, LEFT_BRACE, "Expect '{' before class body.").flatMap { case (_, t3) =>
          classMethodsLoop(t3, List.empty).flatMap { case (methods, t4) =>
            consume(t4, RIGHT_BRACE, "Expect '}' after class body.").map { case (_, t5) =>
              (Stmt.Class(name, superclass, methods), t5)
            }
          }
        }
      }
    }
  }

  private def classMethodsLoop(
      toks: List[Token],
      acc: List[Stmt.Function]
  ): Either[(ParseError, List[Token]), (List[Stmt.Function], List[Token])] = {
    if (check(toks, RIGHT_BRACE) || isAtEnd(toks)) Right((acc.reverse, toks))
    else {
      function("method", toks).flatMap { case (fnStmt, nextToks) =>
        classMethodsLoop(nextToks, fnStmt :: acc)
      }
    }
  }

  private def function(kind: String, toks: List[Token]): Either[(ParseError, List[Token]), (Stmt.Function, List[Token])] = {
    consume(toks, IDENTIFIER, s"Expect $kind name.").flatMap { case (name, t1) =>
      consume(t1, LEFT_PAREN, s"Expect '(' after $kind name.").flatMap { case (_, t2) =>
        parametersLoop(t2).flatMap { case (params, t3) =>
          consume(t3, LEFT_BRACE, s"Expect '{' before $kind body.").flatMap { case (_, t4) =>
            blockStatements(t4).map { case (body, t5) =>
              (Stmt.Function(name, params, body), t5)
            }
          }
        }
      }
    }
  }

  private def parametersLoop(toks: List[Token]): Either[(ParseError, List[Token]), (List[Token], List[Token])] = {
    if (check(toks, RIGHT_PAREN)) {
      consume(toks, RIGHT_PAREN, "Expect ')' after parameters.").map { case (_, tNext) => (List.empty, tNext) }
    } else {
      def loop(t: List[Token], acc: List[Token]): Either[(ParseError, List[Token]), (List[Token], List[Token])] = {
        consume(t, IDENTIFIER, "Expect parameter name.").flatMap { case (param, tNext) =>
          matchToken(tNext, COMMA) match {
            case Some(tAfterComma) => loop(tAfterComma, param :: acc)
            case None              => Right((param :: acc, tNext))
          }
        }
      }

      loop(toks, List.empty).flatMap { case (paramsRev, tEnd) =>
        consume(tEnd, RIGHT_PAREN, "Expect ')' after parameters.").map { case (_, tAfterParen) =>
          (paramsRev.reverse, tAfterParen)
        }
      }
    }
  }

  private def varDeclaration(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    consume(toks, IDENTIFIER, "Expect variable name.").flatMap { case (name, t1) =>
      val initRes = matchToken(t1, EQUAL) match {
        case Some(nextToks) => expression(nextToks).map { case (e, tRes) => (Some(e), tRes) }
        case None           => Right((None, t1))
      }

      initRes.flatMap { case (initializer, t2) =>
        consume(t2, SEMICOLON, "Expect ';' after variable declaration.").map { case (_, t3) =>
          (Stmt.Var(name, initializer), t3)
        }
      }
    }
  }

  private def statement(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    matchToken(toks, FOR) match {
      case Some(nextToks) => forStatement(nextToks)
      case None =>
        matchToken(toks, IF) match {
          case Some(nextToks) => ifStatement(nextToks)
          case None =>
            matchToken(toks, PRINT) match {
              case Some(nextToks) => printStatement(nextToks)
              case None =>
                matchToken(toks, RETURN) match {
                  case Some(nextToks) => returnStatement(toks.head, nextToks)
                  case None =>
                    matchToken(toks, WHILE) match {
                      case Some(nextToks) => whileStatement(nextToks)
                      case None =>
                        matchToken(toks, LEFT_BRACE) match {
                          case Some(nextToks) =>
                            blockStatements(nextToks).map { case (stmts, tRes) => (Stmt.Block(stmts), tRes) }
                          case None => expressionStatement(toks)
                        }
                    }
                }
            }
        }
    }
  }

  private def forStatement(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    consume(toks, LEFT_PAREN, "Expect '(' after 'for'.").flatMap { case (_, t1) =>
      val initRes = matchToken(t1, SEMICOLON) match {
        case Some(nextToks) => Right((None, nextToks))
        case None =>
          matchToken(t1, VAR) match {
            case Some(nextToks) => varDeclaration(nextToks).map { case (s, t) => (Some(s), t) }
            case None           => expressionStatement(t1).map { case (s, t) => (Some(s), t) }
          }
      }

      initRes.flatMap { case (initializer, t2) =>
        val condRes = if (!check(t2, SEMICOLON)) expression(t2).map { case (e, t) => (Some(e), t) } else Right((None, t2))
        condRes.flatMap { case (condition, t3) =>
          consume(t3, SEMICOLON, "Expect ';' after loop condition.").flatMap { case (_, t4) =>
            val incRes = if (!check(t4, RIGHT_PAREN)) expression(t4).map { case (e, t) => (Some(e), t) } else Right((None, t4))
            incRes.flatMap { case (increment, t5) =>
              consume(t5, RIGHT_PAREN, "Expect ')' after for clauses.").flatMap { case (_, t6) =>
                statement(t6).map { case (body, t7) =>
                  val bodyWithInc = increment match {
                    case Some(inc) => Stmt.Block(List(body, Stmt.Expression(inc)))
                    case None      => body
                  }
                  val condExpr = condition.getOrElse(Expr.Literal(Value.BoolVal(true)))
                  val whileLoop = Stmt.While(condExpr, bodyWithInc)
                  val forStmt = initializer match {
                    case Some(init) => Stmt.Block(List(init, whileLoop))
                    case None       => whileLoop
                  }
                  (forStmt, t7)
                }
              }
            }
          }
        }
      }
    }
  }

  private def ifStatement(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    consume(toks, LEFT_PAREN, "Expect '(' after 'if'.").flatMap { case (_, t1) =>
      expression(t1).flatMap { case (cond, t2) =>
        consume(t2, RIGHT_PAREN, "Expect ')' after if condition.").flatMap { case (_, t3) =>
          statement(t3).flatMap { case (thenB, t4) =>
            val elseRes = matchToken(t4, ELSE) match {
              case Some(nextToks) => statement(nextToks).map { case (s, t) => (Some(s), t) }
              case None           => Right((None, t4))
            }
            elseRes.map { case (elseB, t5) =>
              (Stmt.If(cond, thenB, elseB), t5)
            }
          }
        }
      }
    }
  }

  private def printStatement(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    expression(toks).flatMap { case (expr, t1) =>
      consume(t1, SEMICOLON, "Expect ';' after value.").map { case (_, t2) =>
        (Stmt.Print(expr), t2)
      }
    }
  }

  private def returnStatement(keyword: Token, toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    val (value, t1) = if (!check(toks, SEMICOLON)) {
      expression(toks) match {
        case Right((e, t)) => (Some(e), t)
        case Left(err)     => return Left(err)
      }
    } else (None, toks)

    consume(t1, SEMICOLON, "Expect ';' after return value.").map { case (_, t2) =>
      (Stmt.Return(keyword, value), t2)
    }
  }

  private def whileStatement(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    consume(toks, LEFT_PAREN, "Expect '(' after 'while'.").flatMap { case (_, t1) =>
      expression(t1).flatMap { case (cond, t2) =>
        consume(t2, RIGHT_PAREN, "Expect ')' after condition.").flatMap { case (_, t3) =>
          statement(t3).map { case (body, t4) =>
            (Stmt.While(cond, body), t4)
          }
        }
      }
    }
  }

  private def blockStatements(toks: List[Token]): Either[(ParseError, List[Token]), (List[Stmt], List[Token])] = {
    def loop(t: List[Token], acc: List[Stmt]): Either[(ParseError, List[Token]), (List[Stmt], List[Token])] = {
      if (check(t, RIGHT_BRACE) || isAtEnd(t)) {
        Right((acc.reverse, t))
      } else {
        declaration(t) match {
          case Right((stmt, tNext)) => loop(tNext, stmt :: acc)
          case Left(err)            => Left(err)
        }
      }
    }

    loop(toks, List.empty).flatMap { case (stmts, t1) =>
      consume(t1, RIGHT_BRACE, "Expect '}' after block.").map { case (_, t2) =>
        (stmts, t2)
      }
    }
  }

  private def expressionStatement(toks: List[Token]): Either[(ParseError, List[Token]), (Stmt, List[Token])] = {
    expression(toks).flatMap { case (expr, t1) =>
      consume(t1, SEMICOLON, "Expect ';' after expression.").map { case (_, t2) =>
        (Stmt.Expression(expr), t2)
      }
    }
  }

  private def expression(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = assignment(toks)

  private def assignment(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    orExpr(toks).flatMap { case (expr, t1) =>
      matchToken(t1, EQUAL) match {
        case Some(nextToks) =>
          val equalsToken = t1.head
          assignment(nextToks).flatMap { case (value, t2) =>
            expr match {
              case Expr.Variable(name) => Right((Expr.Assign(name, value), t2))
              case Expr.Get(obj, name) => Right((Expr.Set(obj, name, value), t2))
              case _                   => Left((ParseError(equalsToken, "Invalid assignment target."), t2))
            }
          }
        case None => Right((expr, t1))
      }
    }
  }

  private def orExpr(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    def loop(left: Expr, t: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
      matchToken(t, OR) match {
        case Some(nextToks) =>
          val operator = t.head
          andExpr(nextToks).flatMap { case (right, tNext) =>
            loop(Expr.Logical(left, operator, right), tNext)
          }
        case None => Right((left, t))
      }
    }
    andExpr(toks).flatMap { case (left, t1) => loop(left, t1) }
  }

  private def andExpr(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    def loop(left: Expr, t: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
      matchToken(t, AND) match {
        case Some(nextToks) =>
          val operator = t.head
          equality(nextToks).flatMap { case (right, tNext) =>
            loop(Expr.Logical(left, operator, right), tNext)
          }
        case None => Right((left, t))
      }
    }
    equality(toks).flatMap { case (left, t1) => loop(left, t1) }
  }

  private def equality(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    def loop(left: Expr, t: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
      if (check(t, BANG_EQUAL) || check(t, EQUAL_EQUAL)) {
        val operator = t.head
        comparison(t.tail).flatMap { case (right, tNext) =>
          loop(Expr.Binary(left, operator, right), tNext)
        }
      } else Right((left, t))
    }
    comparison(toks).flatMap { case (left, t1) => loop(left, t1) }
  }

  private def comparison(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    def loop(left: Expr, t: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
      if (check(t, GREATER) || check(t, GREATER_EQUAL) || check(t, LESS) || check(t, LESS_EQUAL)) {
        val operator = t.head
        term(t.tail).flatMap { case (right, tNext) =>
          loop(Expr.Binary(left, operator, right), tNext)
        }
      } else Right((left, t))
    }
    term(toks).flatMap { case (left, t1) => loop(left, t1) }
  }

  private def term(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    def loop(left: Expr, t: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
      if (check(t, MINUS) || check(t, PLUS)) {
        val operator = t.head
        factor(t.tail).flatMap { case (right, tNext) =>
          loop(Expr.Binary(left, operator, right), tNext)
        }
      } else Right((left, t))
    }
    factor(toks).flatMap { case (left, t1) => loop(left, t1) }
  }

  private def factor(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    def loop(left: Expr, t: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
      if (check(t, SLASH) || check(t, STAR)) {
        val operator = t.head
        unary(t.tail).flatMap { case (right, tNext) =>
          loop(Expr.Binary(left, operator, right), tNext)
        }
      } else Right((left, t))
    }
    unary(toks).flatMap { case (left, t1) => loop(left, t1) }
  }

  private def unary(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    if (check(toks, BANG) || check(toks, MINUS)) {
      val operator = toks.head
      unary(toks.tail).map { case (right, tNext) =>
        (Expr.Unary(operator, right), tNext)
      }
    } else call(toks)
  }

  private def call(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    primary(toks).flatMap { case (expr, t1) =>
      def loop(currentExpr: Expr, t: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
        matchToken(t, LEFT_PAREN) match {
          case Some(nextToks) =>
            finishCall(currentExpr, nextToks).flatMap { case (callExpr, tNext) =>
              loop(callExpr, tNext)
            }
          case None =>
            matchToken(t, DOT) match {
              case Some(nextToks) =>
                consume(nextToks, IDENTIFIER, "Expect property name after '.'.").flatMap { case (name, tNext) =>
                  loop(Expr.Get(currentExpr, name), tNext)
                }
              case None => Right((currentExpr, t))
            }
        }
      }
      loop(expr, t1)
    }
  }

  private def finishCall(callee: Expr, toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    if (check(toks, RIGHT_PAREN)) {
      consume(toks, RIGHT_PAREN, "Expect ')' after arguments.").map { case (paren, tNext) =>
        (Expr.Call(callee, paren, List.empty), tNext)
      }
    } else {
      def argumentsLoop(t: List[Token], acc: List[Expr]): Either[(ParseError, List[Token]), (List[Expr], List[Token])] = {
        expression(t).flatMap { case (arg, tNext) =>
          matchToken(tNext, COMMA) match {
            case Some(tAfterComma) => argumentsLoop(tAfterComma, arg :: acc)
            case None              => Right(((arg :: acc).reverse, tNext))
          }
        }
      }

      argumentsLoop(toks, List.empty).flatMap { case (args, t1) =>
        consume(t1, RIGHT_PAREN, "Expect ')' after arguments.").map { case (paren, t2) =>
          (Expr.Call(callee, paren, args), t2)
        }
      }
    }
  }

  private def primary(toks: List[Token]): Either[(ParseError, List[Token]), (Expr, List[Token])] = {
    if (isAtEnd(toks)) Left((ParseError(peek(toks), "Expect expression."), toks))
    else {
      val token = toks.head
      val next = toks.tail
      token.tokenType match {
        case FALSE => Right((Expr.Literal(Value.BoolVal(false)), next))
        case TRUE  => Right((Expr.Literal(Value.BoolVal(true)), next))
        case NIL   => Right((Expr.Literal(Value.NilVal), next))
        case NUMBER =>
          val numVal = token.literal.get.asInstanceOf[Double]
          Right((Expr.Literal(Value.NumVal(numVal)), next))
        case STRING =>
          val strVal = token.literal.get.asInstanceOf[String]
          Right((Expr.Literal(Value.StrVal(strVal)), next))
        case SUPER =>
          consume(next, DOT, "Expect '.' after 'super'.").flatMap { case (_, t1) =>
            consume(t1, IDENTIFIER, "Expect superclass method name.").map { case (method, t2) =>
              (Expr.Super(token, method), t2)
            }
          }
        case THIS => Right((Expr.This(token), next))
        case IDENTIFIER => Right((Expr.Variable(token), next))
        case LEFT_PAREN =>
          expression(next).flatMap { case (expr, t1) =>
            consume(t1, RIGHT_PAREN, "Expect ')' after expression.").map { case (_, t2) =>
              (Expr.Grouping(expr), t2)
            }
          }
        case _ => Left((ParseError(token, s"Expect expression. Got '${token.lexeme}'"), next))
      }
    }
  }

  private def matchToken(toks: List[Token], expected: TokenType): Option[List[Token]] = {
    if (check(toks, expected)) Some(toks.tail)
    else None
  }

  private def check(toks: List[Token], expected: TokenType): Boolean = {
    if (isAtEnd(toks)) false
    else toks.head.tokenType == expected
  }

  private def consume(toks: List[Token], expected: TokenType, message: String): Either[(ParseError, List[Token]), (Token, List[Token])] = {
    if (check(toks, expected)) Right((toks.head, toks.tail))
    else Left((ParseError(peek(toks), message), synchronize(toks)))
  }

  private def peek(toks: List[Token]): Token = {
    if (toks.nonEmpty) toks.head
    else Token(EOF, "", None, 0)
  }

  private def isAtEnd(toks: List[Token]): Boolean = {
    toks.isEmpty || toks.head.tokenType == EOF
  }

  private def synchronize(toks: List[Token]): List[Token] = {
    var t = if (toks.nonEmpty) toks.tail else toks
    while (t.nonEmpty && t.head.tokenType != EOF) {
      if (t.head.tokenType == SEMICOLON) return t.tail
      t.head.tokenType match {
        case CLASS | FUN | VAR | FOR | IF | WHILE | PRINT | RETURN => return t
        case _ => t = t.tail
      }
    }
    t
  }
}

package ar.edu.utn.tadp.slox.interpreter

import ar.edu.utn.tadp.slox.ast.*
import ar.edu.utn.tadp.slox.environment.Environment
import ar.edu.utn.tadp.slox.result.EvaluationResult
import ar.edu.utn.tadp.slox.tokens.Token
import ar.edu.utn.tadp.slox.tokens.TokenType.*
import ar.edu.utn.tadp.slox.values.Value
import ar.edu.utn.tadp.slox.values.Value.*

case class Interpreter() {

  def interpret(statements: List[Stmt], initialEnv: Environment): EvaluationResult[Environment] = {
    executeBlock(statements, initialEnv)
  }

  def executeBlock(statements: List[Stmt], env: Environment): EvaluationResult[Environment] = {
    statements.foldLeft[EvaluationResult[Environment]](EvaluationResult.Success(env)) {
      case (EvaluationResult.Success(currentEnv), stmt) => execute(stmt, currentEnv)
      case (nonSuccess, _)                              => nonSuccess
    }
  }

  def execute(stmt: Stmt, env: Environment): EvaluationResult[Environment] = {
    stmt match {
      case Stmt.Expression(expr) =>
        evaluate(expr, env).map { case (_, updatedEnv) => updatedEnv }

      case Stmt.Print(expr) =>
        evaluate(expr, env).map { case (value, updatedEnv) =>
          println(value.toString)
          updatedEnv
        }

      case Stmt.Var(name, initializer) =>
        initializer match {
          case Some(initExpr) =>
            evaluate(initExpr, env).map { case (value, updatedEnv) =>
              updatedEnv.define(name.lexeme, value)
            }
          case None =>
            EvaluationResult.Success(env.define(name.lexeme, NilVal))
        }

      case Stmt.Block(statements) =>
        val blockEnv = Environment(enclosing = Some(env))
        executeBlock(statements, blockEnv).map { updatedBlockEnv =>
          updatedBlockEnv.enclosing.getOrElse(env)
        }

      case Stmt.If(condition, thenBranch, elseBranch) =>
        evaluate(condition, env).flatMap { case (condValue, env1) =>
          if (condValue.isTruthy) {
            execute(thenBranch, env1)
          } else {
            elseBranch match {
              case Some(elseStmt) => execute(elseStmt, env1)
              case None           => EvaluationResult.Success(env1)
            }
          }
        }

      case Stmt.While(condition, body) =>
        def loop(currentEnv: Environment): EvaluationResult[Environment] = {
          evaluate(condition, currentEnv).flatMap { case (condValue, envAfterCond) =>
            if (condValue.isTruthy) {
              execute(body, envAfterCond).flatMap { envAfterBody =>
                loop(envAfterBody)
              }
            } else {
              EvaluationResult.Success(envAfterCond)
            }
          }
        }
        loop(env)

      case Stmt.Function(name, params, body) =>
        val fnVal = FnVal(Stmt.Function(name, params, body), env)
        EvaluationResult.Success(env.define(name.lexeme, fnVal))

      case Stmt.Return(keyword, valueExpr) =>
        valueExpr match {
          case Some(expr) =>
            evaluate(expr, env).flatMap { case (valRes, _) =>
              EvaluationResult.ReturnSignal(valRes)
            }
          case None =>
            EvaluationResult.ReturnSignal(NilVal)
        }

      case Stmt.Class(name, superclassExpr, methods) =>
        val (superclassVal, env1) = superclassExpr match {
          case Some(superVar) =>
            evaluate(superVar, env) match {
              case EvaluationResult.Success((sc: ClassVal, e1)) => (Some(sc), e1)
              case EvaluationResult.Success((_, _)) =>
                return EvaluationResult.RuntimeError(superVar.name, "Superclass must be a class.")
              case err: EvaluationResult.RuntimeError => return err
              case sig: EvaluationResult.ReturnSignal => return sig
            }
          case None => (None, env)
        }

        val envWithSuper = superclassVal match {
          case Some(sc) => Environment(enclosing = Some(env1)).define("super", sc)
          case None     => env1
        }

        val methodsMap = methods.map { methodStmt =>
          val isInit = methodStmt.name.lexeme == "init"
          val fnVal = FnVal(methodStmt, envWithSuper, isInitializer = isInit)
          (methodStmt.name.lexeme, fnVal)
        }.toMap

        val classVal = ClassVal(name.lexeme, superclassVal, methodsMap)
        EvaluationResult.Success(env1.define(name.lexeme, classVal))
    }
  }

  def evaluate(expr: Expr, env: Environment): EvaluationResult[(Value, Environment)] = {
    expr match {
      case Expr.Literal(value) =>
        EvaluationResult.Success((value, env))

      case Expr.Grouping(expression) =>
        evaluate(expression, env)

      case Expr.Unary(operator, rightExpr) =>
        evaluate(rightExpr, env).flatMap { case (rightVal, env1) =>
          operator.tokenType match {
            case MINUS =>
              rightVal match {
                case NumVal(n) => EvaluationResult.Success((NumVal(-n), env1))
                case _ => EvaluationResult.RuntimeError(operator, "Operand must be a number.")
              }
            case BANG =>
              EvaluationResult.Success((BoolVal(!rightVal.isTruthy), env1))
            case _ =>
              EvaluationResult.RuntimeError(operator, "Unknown unary operator.")
          }
        }

      case Expr.Binary(leftExpr, operator, rightExpr) =>
        evaluate(leftExpr, env).flatMap { case (leftVal, env1) =>
          evaluate(rightExpr, env1).flatMap { case (rightVal, env2) =>
            operator.tokenType match {
              case MINUS =>
                checkNumberOperands(operator, leftVal, rightVal) { (a, b) => NumVal(a - b) }.map((_, env2))
              case STAR =>
                checkNumberOperands(operator, leftVal, rightVal) { (a, b) => NumVal(a * b) }.map((_, env2))
              case SLASH =>
                checkNumberOperands(operator, leftVal, rightVal) { (a, b) => NumVal(a / b) }.map((_, env2))
              case PLUS =>
                (leftVal, rightVal) match {
                  case (NumVal(a), NumVal(b)) => EvaluationResult.Success((NumVal(a + b), env2))
                  case (StrVal(a), StrVal(b)) => EvaluationResult.Success((StrVal(a + b), env2))
                  case _ => EvaluationResult.RuntimeError(operator, "Operands must be two numbers or two strings.")
                }
              case GREATER =>
                checkNumberOperands(operator, leftVal, rightVal) { (a, b) => BoolVal(a > b) }.map((_, env2))
              case GREATER_EQUAL =>
                checkNumberOperands(operator, leftVal, rightVal) { (a, b) => BoolVal(a >= b) }.map((_, env2))
              case LESS =>
                checkNumberOperands(operator, leftVal, rightVal) { (a, b) => BoolVal(a < b) }.map((_, env2))
              case LESS_EQUAL =>
                checkNumberOperands(operator, leftVal, rightVal) { (a, b) => BoolVal(a <= b) }.map((_, env2))
              case BANG_EQUAL =>
                EvaluationResult.Success((BoolVal(leftVal != rightVal), env2))
              case EQUAL_EQUAL =>
                EvaluationResult.Success((BoolVal(leftVal == rightVal), env2))
              case _ =>
                EvaluationResult.RuntimeError(operator, "Unknown binary operator.")
            }
          }
        }

      case Expr.Variable(name) =>
        env.get(name).map(v => (v, env))

      case Expr.Assign(name, valueExpr) =>
        evaluate(valueExpr, env).flatMap { case (value, env1) =>
          env1.assign(name, value)
        }

      case Expr.Logical(leftExpr, operator, rightExpr) =>
        evaluate(leftExpr, env).flatMap { case (leftVal, env1) =>
          operator.tokenType match {
            case OR =>
              if (leftVal.isTruthy) EvaluationResult.Success((leftVal, env1))
              else evaluate(rightExpr, env1)
            case AND =>
              if (!leftVal.isTruthy) EvaluationResult.Success((leftVal, env1))
              else evaluate(rightExpr, env1)
            case _ =>
              EvaluationResult.RuntimeError(operator, "Unknown logical operator.")
          }
        }

      case Expr.Call(calleeExpr, paren, argumentExprs) =>
        evaluate(calleeExpr, env).flatMap { case (calleeVal, env1) =>
          evaluateArguments(argumentExprs, env1, List.empty).flatMap { case (args, env2) =>
            calleeVal match {
              case fn: FnVal =>
                if (args.length != fn.declaration.params.length) {
                  EvaluationResult.RuntimeError(paren, s"Expected ${fn.declaration.params.length} arguments but got ${args.length}.")
                } else {
                  val fnEnv = fn.declaration.params.zip(args).foldLeft(Environment(enclosing = Some(fn.closure))) {
                    case (currentFnEnv, (paramToken, argVal)) =>
                      currentFnEnv.define(paramToken.lexeme, argVal)
                  }
                  executeBlock(fn.declaration.body, fnEnv) match {
                    case EvaluationResult.Success(_) =>
                      if (fn.isInitializer) fn.closure.get(Token(THIS, "this", None, paren.line)).map(v => (v, env2))
                      else EvaluationResult.Success((NilVal, env2))
                    case EvaluationResult.ReturnSignal(retVal) =>
                      if (fn.isInitializer) fn.closure.get(Token(THIS, "this", None, paren.line)).map(v => (v, env2))
                      else EvaluationResult.Success((retVal, env2))
                    case err: EvaluationResult.RuntimeError => err
                  }
                }

              case nativeFn: NativeFnVal =>
                if (args.length != nativeFn.arity) {
                  EvaluationResult.RuntimeError(paren, s"Expected ${nativeFn.arity} arguments but got ${args.length}.")
                } else {
                  nativeFn.implementation(args).map(v => (v, env2))
                }

              case klass: ClassVal =>
                val instance = InstanceVal(klass)
                klass.methods.get("init") match {
                  case Some(initializer) =>
                    if (args.length != initializer.declaration.params.length) {
                      EvaluationResult.RuntimeError(paren, s"Expected ${initializer.declaration.params.length} arguments but got ${args.length}.")
                    } else {
                      val initClosure = Environment(enclosing = Some(initializer.closure)).define("this", instance)
                      val initEnv = initializer.declaration.params.zip(args).foldLeft(initClosure) {
                        case (cEnv, (paramToken, argVal)) => cEnv.define(paramToken.lexeme, argVal)
                      }
                      executeBlock(initializer.declaration.body, initEnv) match {
                        case EvaluationResult.Success(_) =>
                          initEnv.get(Token(THIS, "this", None, paren.line)).map(v => (v, env2))
                        case EvaluationResult.ReturnSignal(_) =>
                          initEnv.get(Token(THIS, "this", None, paren.line)).map(v => (v, env2))
                        case err: EvaluationResult.RuntimeError => err
                      }
                    }
                  case None =>
                    if (args.nonEmpty) EvaluationResult.RuntimeError(paren, s"Expected 0 arguments but got ${args.length}.")
                    else EvaluationResult.Success((instance, env2))
                }

              case _ =>
                EvaluationResult.RuntimeError(paren, "Can only call functions and classes.")
            }
          }
        }

      case Expr.Get(objectExpr, name) =>
        evaluate(objectExpr, env).flatMap { case (objVal, env1) =>
          objVal match {
            case inst: InstanceVal =>
              inst.fields.get(name.lexeme) match {
                case Some(valField) => EvaluationResult.Success((valField, env1))
                case None =>
                  findMethod(inst.klass, name.lexeme) match {
                    case Some(method) =>
                      val boundMethod = method.copy(closure = Environment(enclosing = Some(method.closure)).define("this", inst))
                      EvaluationResult.Success((boundMethod, env1))
                    case None =>
                      EvaluationResult.RuntimeError(name, s"Undefined property '${name.lexeme}'.")
                  }
              }
            case _ =>
              EvaluationResult.RuntimeError(name, "Only instances have properties.")
          }
        }

      case Expr.Set(objectExpr, name, valueExpr) =>
        evaluate(objectExpr, env).flatMap { case (objVal, env1) =>
          evaluate(valueExpr, env1).flatMap { case (valRes, env2) =>
            objVal match {
              case inst: InstanceVal =>
                inst.fields(name.lexeme) = valRes
                EvaluationResult.Success((valRes, env2))
              case _ =>
                EvaluationResult.RuntimeError(name, "Only instances have fields.")
            }
          }
        }

      case Expr.This(keyword) =>
        env.get(keyword).map(v => (v, env))

      case Expr.Super(keyword, methodToken) =>
        env.get(keyword) match {
          case EvaluationResult.Success(sc: ClassVal) =>
            env.get(Token(THIS, "this", None, keyword.line)) match {
              case EvaluationResult.Success(inst: InstanceVal) =>
                findMethod(sc, methodToken.lexeme) match {
                  case Some(method) =>
                    val boundMethod = method.copy(closure = Environment(enclosing = Some(method.closure)).define("this", inst))
                    EvaluationResult.Success((boundMethod, env))
                  case None =>
                    EvaluationResult.RuntimeError(methodToken, s"Undefined property '${methodToken.lexeme}'.")
                }
              case _ => EvaluationResult.RuntimeError(keyword, "Super call requires valid 'this' instance.")
            }
          case _ => EvaluationResult.RuntimeError(keyword, "Super call requires valid superclass.")
        }
    }
  }

  private def findMethod(klass: ClassVal, name: String): Option[FnVal] = {
    klass.methods.get(name) match {
      case Some(m) => Some(m)
      case None    => klass.superclass.flatMap(sc => findMethod(sc, name))
    }
  }

  private def evaluateArguments(
      exprs: List[Expr],
      currentEnv: Environment,
      acc: List[Value]
  ): EvaluationResult[(List[Value], Environment)] = {
    exprs.foldLeft[EvaluationResult[(List[Value], Environment)]](EvaluationResult.Success((List.empty, currentEnv))) {
      case (EvaluationResult.Success((argsSoFar, envSoFar)), expr) =>
        evaluate(expr, envSoFar).map { case (argVal, nextEnv) =>
          (argsSoFar :+ argVal, nextEnv)
        }
      case (nonSuccess, _) => nonSuccess
    }
  }

  private def checkNumberOperands(operator: Token, left: Value, right: Value)(
      op: (Double, Double) => Value
  ): EvaluationResult[Value] = {
    (left, right) match {
      case (NumVal(a), NumVal(b)) => EvaluationResult.Success(op(a, b))
      case _                      => EvaluationResult.RuntimeError(operator, "Operands must be numbers.")
    }
  }
}

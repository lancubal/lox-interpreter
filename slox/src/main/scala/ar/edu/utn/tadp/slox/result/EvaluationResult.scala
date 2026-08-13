package ar.edu.utn.tadp.slox.result

import ar.edu.utn.tadp.slox.tokens.Token
import ar.edu.utn.tadp.slox.values.Value

sealed trait EvaluationResult[+A]:
  def map[B](f: A => B): EvaluationResult[B] = this match
    case EvaluationResult.Success(v) => EvaluationResult.Success(f(v))
    case signal: EvaluationResult.ReturnSignal => signal
    case error: EvaluationResult.RuntimeError => error

  def flatMap[B](f: A => EvaluationResult[B]): EvaluationResult[B] = this match
    case EvaluationResult.Success(v) => f(v)
    case signal: EvaluationResult.ReturnSignal => signal
    case error: EvaluationResult.RuntimeError => error

object EvaluationResult:
  case class Success[+A](value: A) extends EvaluationResult[A]
  case class ReturnSignal(value: Value) extends EvaluationResult[Nothing]
  case class RuntimeError(token: Token, message: String) extends EvaluationResult[Nothing]

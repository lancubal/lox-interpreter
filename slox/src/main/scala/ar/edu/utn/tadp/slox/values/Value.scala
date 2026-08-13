package ar.edu.utn.tadp.slox.values

import ar.edu.utn.tadp.slox.ast.Stmt
import ar.edu.utn.tadp.slox.environment.Environment
import ar.edu.utn.tadp.slox.result.EvaluationResult
import scala.collection.mutable

sealed trait Value:
  def isTruthy: Boolean = this match
    case Value.NilVal => false
    case Value.BoolVal(b) => b
    case _ => true

  override def toString: String = this match
    case Value.NilVal => "nil"
    case Value.BoolVal(b) => b.toString
    case Value.NumVal(n) =>
      val text = n.toString
      if text.endsWith(".0") then text.substring(0, text.length - 2)
      else text
    case Value.StrVal(s) => s
    case Value.FnVal(decl, _, _) => s"<fn ${decl.name.lexeme}>"
    case Value.NativeFnVal(name, _, _) => s"<native fn $name>"
    case Value.ClassVal(name, _, _) => name
    case Value.InstanceVal(klass, _) => s"${klass.name} instance"

object Value:
  case object NilVal extends Value
  case class BoolVal(value: Boolean) extends Value
  case class NumVal(value: Double) extends Value
  case class StrVal(value: String) extends Value
  case class FnVal(
      declaration: Stmt.Function,
      closure: Environment,
      isInitializer: Boolean = false
  ) extends Value
  case class NativeFnVal(
      name: String,
      arity: Int,
      implementation: List[Value] => EvaluationResult[Value]
  ) extends Value
  case class ClassVal(
      name: String,
      superclass: Option[ClassVal],
      methods: Map[String, FnVal]
  ) extends Value
  case class InstanceVal(
      klass: ClassVal,
      fields: mutable.Map[String, Value] = mutable.Map.empty
  ) extends Value

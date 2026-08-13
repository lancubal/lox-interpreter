package ar.edu.utn.tadp.slox.environment

import ar.edu.utn.tadp.slox.result.EvaluationResult
import ar.edu.utn.tadp.slox.tokens.Token
import ar.edu.utn.tadp.slox.values.Value

case class Environment(
    values: Map[String, Value] = Map.empty,
    enclosing: Option[Environment] = None
) {
  def define(name: String, value: Value): Environment =
    copy(values = values + (name -> value))

  def get(name: Token): EvaluationResult[Value] =
    values.get(name.lexeme) match
      case Some(value) => EvaluationResult.Success(value)
      case None =>
        enclosing match
          case Some(env) => env.get(name)
          case None =>
            EvaluationResult.RuntimeError(
              name,
              s"Undefined variable '${name.lexeme}'."
            )

  def getAt(distance: Int, name: String): Value =
    ancestor(distance).values(name)

  def assignAt(distance: Int, name: Token, value: Value): Environment =
    if distance == 0 then copy(values = values + (name.lexeme -> value))
    else
      val updatedEnclosing = enclosing.get.assignAt(distance - 1, name, value)
      copy(enclosing = Some(updatedEnclosing))

  def ancestor(distance: Int): Environment =
    if distance == 0 then this
    else enclosing.get.ancestor(distance - 1)

  def assign(name: Token, value: Value): EvaluationResult[(Value, Environment)] =
    if values.contains(name.lexeme) then
      EvaluationResult.Success(
        (value, copy(values = values + (name.lexeme -> value)))
      )
    else
      enclosing match
        case Some(env) =>
          env.assign(name, value).map { case (v, updatedEnclosing) =>
            (v, copy(enclosing = Some(updatedEnclosing)))
          }
        case None =>
          EvaluationResult.RuntimeError(
            name,
            s"Undefined variable '${name.lexeme}'."
          )
}

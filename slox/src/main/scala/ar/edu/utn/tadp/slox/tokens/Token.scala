package ar.edu.utn.tadp.slox.tokens

case class Token(
    tokenType: TokenType,
    lexeme: String,
    literal: Option[Any],
    line: Int
) {
  override def toString: String = s"$tokenType $lexeme ${literal.getOrElse("")}"
}

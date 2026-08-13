package ar.edu.utn.tadp.slox.scanner

import ar.edu.utn.tadp.slox.tokens.{Token, TokenType}
import ar.edu.utn.tadp.slox.tokens.TokenType.*
import scala.annotation.tailrec

case class Scanner(source: String) {

  private val keywords: Map[String, TokenType] = Map(
    "and" -> AND,
    "class" -> CLASS,
    "else" -> ELSE,
    "false" -> FALSE,
    "for" -> FOR,
    "fun" -> FUN,
    "if" -> IF,
    "nil" -> NIL,
    "or" -> OR,
    "print" -> PRINT,
    "return" -> RETURN,
    "super" -> SUPER,
    "this" -> THIS,
    "true" -> TRUE,
    "var" -> VAR,
    "while" -> WHILE
  )

  def scanTokens(): Either[List[String], List[Token]] = {
    scanLoop(start = 0, current = 0, line = 1, tokens = List.empty, errors = List.empty)
  }

  @tailrec
  private def scanLoop(
      start: Int,
      current: Int,
      line: Int,
      tokens: List[Token],
      errors: List[String]
  ): Either[List[String], List[Token]] = {
    if (current >= source.length) {
      val eofToken = Token(EOF, "", None, line)
      if (errors.nonEmpty) Left(errors.reverse)
      else Right((eofToken :: tokens).reverse)
    } else {
      val c = source.charAt(current)
      val nextCurrent = current + 1

      c match {
        case '(' => addToken(LEFT_PAREN, "(", None, current, nextCurrent, line, tokens, errors)
        case ')' => addToken(RIGHT_PAREN, ")", None, current, nextCurrent, line, tokens, errors)
        case '{' => addToken(LEFT_BRACE, "{", None, current, nextCurrent, line, tokens, errors)
        case '}' => addToken(RIGHT_BRACE, "}", None, current, nextCurrent, line, tokens, errors)
        case ',' => addToken(COMMA, ",", None, current, nextCurrent, line, tokens, errors)
        case '.' => addToken(DOT, ".", None, current, nextCurrent, line, tokens, errors)
        case '-' => addToken(MINUS, "-", None, current, nextCurrent, line, tokens, errors)
        case '+' => addToken(PLUS, "+", None, current, nextCurrent, line, tokens, errors)
        case ';' => addToken(SEMICOLON, ";", None, current, nextCurrent, line, tokens, errors)
        case '*' => addToken(STAR, "*", None, current, nextCurrent, line, tokens, errors)

        case '!' =>
          if (matchChar('=', nextCurrent)) addToken(BANG_EQUAL, "!=", None, current, nextCurrent + 1, line, tokens, errors)
          else addToken(BANG, "!", None, current, nextCurrent, line, tokens, errors)

        case '=' =>
          if (matchChar('=', nextCurrent)) addToken(EQUAL_EQUAL, "==", None, current, nextCurrent + 1, line, tokens, errors)
          else addToken(EQUAL, "=", None, current, nextCurrent, line, tokens, errors)

        case '<' =>
          if (matchChar('=', nextCurrent)) addToken(LESS_EQUAL, "<=", None, current, nextCurrent + 1, line, tokens, errors)
          else addToken(LESS, "<", None, current, nextCurrent, line, tokens, errors)

        case '>' =>
          if (matchChar('=', nextCurrent)) addToken(GREATER_EQUAL, ">=", None, current, nextCurrent + 1, line, tokens, errors)
          else addToken(GREATER, ">", None, current, nextCurrent, line, tokens, errors)

        case '/' =>
          if (matchChar('/', nextCurrent)) {
            val endLinePos = skipComment(nextCurrent + 1)
            scanLoop(endLinePos, endLinePos, line, tokens, errors)
          } else {
            addToken(SLASH, "/", None, current, nextCurrent, line, tokens, errors)
          }

        case ' ' | '\r' | '\t' =>
          scanLoop(nextCurrent, nextCurrent, line, tokens, errors)

        case '\n' =>
          scanLoop(nextCurrent, nextCurrent, line + 1, tokens, errors)

        case '"' =>
          scanString(nextCurrent, line, tokens, errors)

        case digit if digit.isDigit =>
          scanNumber(current, line, tokens, errors)

        case alpha if alpha.isLetter || alpha == '_' =>
          scanIdentifier(current, line, tokens, errors)

        case _ =>
          val err = s"[line $line] Error: Unexpected character '$c'."
          scanLoop(nextCurrent, nextCurrent, line, tokens, err :: errors)
      }
    }
  }

  private def matchChar(expected: Char, current: Int): Boolean = {
    if (current >= source.length) false
    else source.charAt(current) == expected
  }

  private def skipComment(current: Int): Int = {
    var p = current
    while (p < source.length && source.charAt(p) != '\n') {
      p += 1
    }
    p
  }

  private def addToken(
      tType: TokenType,
      lexeme: String,
      literal: Option[Any],
      start: Int,
      nextCurrent: Int,
      line: Int,
      tokens: List[Token],
      errors: List[String]
  ): Either[List[String], List[Token]] = {
    val token = Token(tType, lexeme, literal, line)
    scanLoop(nextCurrent, nextCurrent, line, token :: tokens, errors)
  }

  private def scanString(
      current: Int,
      line: Int,
      tokens: List[Token],
      errors: List[String]
  ): Either[List[String], List[Token]] = {
    var p = current
    var currentLine = line
    while (p < source.length && source.charAt(p) != '"') {
      if (source.charAt(p) == '\n') currentLine += 1
      p += 1
    }

    if (p >= source.length) {
      val err = s"[line $currentLine] Error: Unterminated string."
      scanLoop(p, p, currentLine, tokens, err :: errors)
    } else {
      val valStr = source.substring(current, p)
      val fullLexeme = source.substring(current - 1, p + 1)
      val token = Token(STRING, fullLexeme, Some(valStr), currentLine)
      val nextPos = p + 1
      scanLoop(nextPos, nextPos, currentLine, token :: tokens, errors)
    }
  }

  private def scanNumber(
      start: Int,
      line: Int,
      tokens: List[Token],
      errors: List[String]
  ): Either[List[String], List[Token]] = {
    var p = start
    while (p < source.length && source.charAt(p).isDigit) {
      p += 1
    }

    if (p < source.length && source.charAt(p) == '.' && p + 1 < source.length && source.charAt(p + 1).isDigit) {
      p += 1 // consume '.'
      while (p < source.length && source.charAt(p).isDigit) {
        p += 1
      }
    }

    val lexeme = source.substring(start, p)
    val numVal = lexeme.toDouble
    val token = Token(NUMBER, lexeme, Some(numVal), line)
    scanLoop(p, p, line, token :: tokens, errors)
  }

  private def scanIdentifier(
      start: Int,
      line: Int,
      tokens: List[Token],
      errors: List[String]
  ): Either[List[String], List[Token]] = {
    var p = start
    while (p < source.length && (source.charAt(p).isLetterOrDigit || source.charAt(p) == '_')) {
      p += 1
    }

    val text = source.substring(start, p)
    val tType = keywords.getOrElse(text, IDENTIFIER)
    val token = Token(tType, text, None, line)
    scanLoop(p, p, line, token :: tokens, errors)
  }
}

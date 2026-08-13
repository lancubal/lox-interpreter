package ar.edu.utn.tadp.slox

import ar.edu.utn.tadp.slox.environment.Environment
import ar.edu.utn.tadp.slox.interpreter.Interpreter
import ar.edu.utn.tadp.slox.parser.Parser
import ar.edu.utn.tadp.slox.result.EvaluationResult
import ar.edu.utn.tadp.slox.scanner.Scanner
import ar.edu.utn.tadp.slox.values.Value
import ar.edu.utn.tadp.slox.values.Value.*
import scala.io.Source
import scala.io.StdIn

object Main {

  val initialGlobalEnvironment: Environment = {
    val startTime = System.currentTimeMillis()
    val clockFn = NativeFnVal(
      name = "clock",
      arity = 0,
      implementation = _ => EvaluationResult.Success(NumVal((System.currentTimeMillis() - startTime) / 1000.0))
    )
    Environment().define("clock", clockFn)
  }

  def main(args: Array[String]): Unit = {
    if (args.length > 1) {
      println("Usage: slox [script]")
      System.exit(64)
    } else if (args.length == 1) {
      runFile(args(0))
    } else {
      runPrompt()
    }
  }

  private def runFile(path: String): Unit = {
    val source = Source.fromFile(path).mkString
    run(source, initialGlobalEnvironment) match {
      case Left(_) => System.exit(65)
      case Right(res) =>
        res match {
          case EvaluationResult.RuntimeError(_, _) => System.exit(70)
          case _                                  => ()
        }
    }
  }

  private def runPrompt(): Unit = {
    var env = initialGlobalEnvironment
    print("> ")
    var line = StdIn.readLine()
    while (line != null) {
      run(line, env) match {
        case Right(EvaluationResult.Success(nextEnv)) => env = nextEnv
        case _                                        => ()
      }
      print("> ")
      line = StdIn.readLine()
    }
  }

  def run(source: String, env: Environment): Either[List[String], EvaluationResult[Environment]] = {
    val scanner = Scanner(source)
    scanner.scanTokens() match {
      case Left(errors) =>
        errors.foreach(System.err.println)
        Left(errors)
      case Right(tokens) =>
        val parser = Parser(tokens)
        parser.parse() match {
          case Left(errors) =>
            errors.foreach(System.err.println)
            Left(errors)
          case Right(statements) =>
            val interpreter = Interpreter()
            val result = interpreter.interpret(statements, env)
            result match {
              case EvaluationResult.RuntimeError(token, msg) =>
                System.err.println(s"$msg\n[line ${token.line}]")
              case _ => ()
            }
            Right(result)
        }
    }
  }
}

# Design & Pattern Architecture: `slox` (Lox in Scala 3)

`slox` is an implementation of the **Lox** language interpreter written in **Scala 3**, adhering strictly to the **Object-Functional (Functional-Immutable)** programming paradigm as taught in *Técnicas de Arquitectura y Diseño de Programación (TADP - UTN FRBA)*.

---

## 1. Core Architectural Pillars

### 1.1 Pure Immutability
- **No Shared Mutable State**: Variables (`var`), mutable maps, or mutating arrays are strictly prohibited.
- **Copy-on-Write State Transitions**: All state changes (e.g., updating a variable binding, pushing a new local scope frame, returning from a function call) return a new copy of the immutable container using Scala 3 `case class` `.copy(...)` methods or immutable collection operations (`Map`, `List`, `Vector`).

### 1.2 Algebraic Data Types (ADTs) & Sealed Hierarchies
- Every domain concept (Tokens, AST Expressions, AST Statements, Runtime Values, and Execution Results) is modeled using `sealed trait` hierarchies extended by `case class` and `case object`.
- This ensures compile-time exhaustiveness checking for `match` expressions.

### 1.3 Pure Functional Evaluation Pipeline
- **AST Expressions (`Expr`)**: Evaluated via pure functions returning `EvaluationResult[Value]`.
- **AST Statements (`Stmt`)**: Executed via pure functions taking an `Environment` and returning a new `ExecutionResult[Environment]`.
- **Higher-Order Functions & Folding**: Sequence processing (statement lists, parameter binding, argument evaluation) uses `foldLeft`, `flatMap`, `map`, and tail recursion.

---

## 2. Domain & Type System Modeling

### 2.1 Tokens & Scanner (`ar.edu.utn.tadp.slox.tokens`)
```scala
sealed trait TokenType
case object LEFT_PAREN extends TokenType
case object RIGHT_PAREN extends TokenType
case object PLUS extends TokenType
case object VAR extends TokenType
// ... other token types

case class Token(tokenType: TokenType, lexeme: String, literal: Option[Any], line: Int)
```

### 2.2 AST Expressions (`ar.edu.utn.tadp.slox.ast.Expr`)
```scala
sealed trait Expr

object Expr {
  case class Literal(value: Value) extends Expr
  case class Grouping(expression: Expr) extends Expr
  case class Unary(operator: Token, right: Expr) extends Expr
  case class Binary(left: Expr, operator: Token, right: Expr) extends Expr
  case class Variable(name: Token) extends Expr
  case class Assign(name: Token, value: Expr) extends Expr
  case class Logical(left: Expr, operator: Token, right: Expr) extends Expr
  case class Call(callee: Expr, paren: Token, arguments: List[Expr]) extends Expr
  case class Get(objectExpr: Expr, name: Token) extends Expr
  case class Set(objectExpr: Expr, name: Token, value: Expr) extends Expr
  case class This(keyword: Token) extends Expr
  case class Super(keyword: Token, method: Token) extends Expr
}
```

### 2.3 AST Statements (`ar.edu.utn.tadp.slox.ast.Stmt`)
```scala
sealed trait Stmt

object Stmt {
  case class Expression(expression: Expr) extends Stmt
  case class Print(expression: Expr) extends Stmt
  case class Var(name: Token, initializer: Option[Expr]) extends Stmt
  case class Block(statements: List[Stmt]) extends Stmt
  case class If(condition: Expr, thenBranch: Stmt, elseBranch: Option[Stmt]) extends Stmt
  case class While(condition: Expr, body: Stmt) extends Stmt
  case class Function(name: Token, params: List[Token], body: List[Stmt]) extends Stmt
  case class Return(keyword: Token, value: Option[Expr]) extends Stmt
  case class Class(name: Token, superclass: Option[Expr.Variable], methods: List[Stmt.Function]) extends Stmt
}
```

### 2.4 Runtime Values (`ar.edu.utn.tadp.slox.values.Value`)
```scala
sealed trait Value

object Value {
  case object NilVal extends Value
  case class BoolVal(value: Boolean) extends Value
  case class NumVal(value: Double) extends Value
  case class StrVal(value: String) extends Value
  case class FnVal(declaration: Stmt.Function, closure: Environment) extends Value
  case class NativeFnVal(arity: Int, implementation: List[Value] => EvaluationResult[Value]) extends Value
  case class ClassVal(name: String, superclass: Option[ClassVal], methods: Map[String, FnVal]) extends Value
  case class InstanceVal(klass: ClassVal, fields: Map[String, Value] = Map.empty) extends Value
}
```

---

## 3. Immutable Environment & State Machine

### 3.1 Immutable `Environment`
Instead of mutating a hash map, `Environment` is an immutable class wrapping a `Map[String, Value]` and an optional `enclosing: Option[Environment]`.

```scala
case class Environment(values: Map[String, Value] = Map.empty, enclosing: Option[Environment] = None) {
  def define(name: String, value: Value): Environment =
    copy(values = values + (name -> value))

  def get(name: Token): EvaluationResult[Value] =
    values.get(name.lexeme) match {
      case Some(value) => EvaluationResult.Success(value)
      case None => enclosing match {
        case Some(env) => env.get(name)
        case None => EvaluationResult.RuntimeError(name, s"Undefined variable '${name.lexeme}'.")
      }
    }

  def assign(name: Token, value: Value): EvaluationResult[(Value, Environment)] =
    if (values.contains(name.lexeme)) {
      EvaluationResult.Success((value, copy(values = values + (name.lexeme -> value))))
    } else {
      enclosing match {
        case Some(env) =>
          env.assign(name, value).map { case (v, updatedEnclosing) =>
            (v, copy(enclosing = Some(updatedEnclosing)))
          }
        case None =>
          EvaluationResult.RuntimeError(name, s"Undefined variable '${name.lexeme}'.")
      }
    }
}
```

---

## 4. Execution Monad & Result Handling (`ar.edu.utn.tadp.slox.result`)

To model side-effects, control flow (`return`, `break`), and runtime errors without throwing Java exceptions:

```scala
sealed trait EvaluationResult[+A] {
  def map[B](f: A => B): EvaluationResult[B]
  def flatMap[B](f: A => EvaluationResult[B]): EvaluationResult[B]
}

object EvaluationResult {
  case class Success[A](value: A) extends EvaluationResult[A]
  case class ReturnSignal(value: Value) extends EvaluationResult[Nothing]
  case class RuntimeError(token: Token, message: String) extends EvaluationResult[Nothing]
}
```

---

## 5. Execution Pipeline via `foldLeft`

Executing a block of statements transforms the initial environment purely:

```scala
def executeBlock(statements: List[Stmt], env: Environment): EvaluationResult[Environment] = {
  statements.foldLeft[EvaluationResult[Environment]](EvaluationResult.Success(env)) {
    case (EvaluationResult.Success(currentEnv), stmt) => execute(stmt, currentEnv)
    case (nonSuccess, _) => nonSuccess // Short-circuit on ReturnSignal or RuntimeError
  }
}
```

---

## 6. Project Structure Overview

```
slox/
├── build.sbt
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── ar/
│   │           └── edu/
│   │               └── utn/
│   │                   └── tadp/
│   │                       └── slox/
│   │                           ├── Main.scala
│   │                           ├── Scanner.scala
│   │                           ├── Parser.scala
│   │                           ├── Interpreter.scala
│   │                           ├── ast/
│   │                           │   ├── Expr.scala
│   │                           │   └── Stmt.scala
│   │                           ├── tokens/
│   │                           │   ├── Token.scala
│   │                           │   └── TokenType.scala
│   │                           ├── values/
│   │                           │   └── Value.scala
│   │                           ├── environment/
│   │                           │   └── Environment.scala
│   │                           └── result/
│   │                               └── EvaluationResult.scala
│   └── test/
│       └── scala/
│           └── ar/
│               └── edu/
│                   └── utn/
│                       └── tadp/
│                           └── slox/
│                               └── InterpreterSpec.scala
```

---

## 7. Summary of Advantages

1. **Deterministic State Reasoning**: Thread-safe and state-predictable without synchronization or locking.
2. **Pattern Matching Clarity**: Pattern matching directly maps grammar productions to evaluation cases.
3. **Pure Control Flow**: Control flow (like `return`) is modeled as data signals (`ReturnSignal`), making interpreter evaluation fully referentially transparent.

# Design & Pattern Architecture: `slox` (Lox in Scala 3)

`slox` is an implementation of the **Lox** language interpreter written in **Scala 3**, adhering strictly to the **Object-Functional (Functional-Immutable)** programming paradigm as taught in *Técnicas de Arquitectura y Diseño de Programación (TADP - UTN FRBA)*.

---

## 1. Core Architectural Pillars

### 1.1 Pure Immutability
- **No Shared Mutable State**: Variables (`var`), mutable global states, or mutating arrays are strictly avoided in AST evaluation and interpreter execution.
- **Copy-on-Write State Transitions**: All state changes (e.g., updating a variable binding, pushing a new local scope frame, returning from a function call) return a new copy of the immutable container using Scala 3 `case class` `.copy(...)` methods or immutable collection operations (`Map`, `List`, `Vector`).

### 1.2 Algebraic Data Types (ADTs) & Sealed Hierarchies
- Every domain concept (Tokens, AST Expressions, AST Statements, Runtime Values, and Execution Results) is modeled using `sealed trait` hierarchies extended by `case class` and `case object`.
- This ensures compile-time exhaustiveness checking for `match` expressions.

### 1.3 Pure Functional Evaluation Pipeline
- **AST Expressions (`Expr`)**: Evaluated via pure functions returning `EvaluationResult[(Value, Environment)]`.
- **AST Statements (`Stmt`)**: Executed via pure functions taking an `Environment` and returning a new `EvaluationResult[Environment]`.
- **Higher-Order Functions & Folding**: Sequence processing (statement lists, parameter binding, argument evaluation) uses `foldLeft`, `flatMap`, `map`, and tail recursion.

---

## 2. Object-Oriented & Functional Paradigm Synergies (TADP Analysis)

### 2.1 Object-Oriented Paradigm Contributions (POO)

#### A. Domain Encapsulation via Traits and Classes
Every domain concept (Runtime Values, AST Expressions, Statements) is encapsulated into an object abstraction with its own behavior:

```scala
// ar/edu/utn/tadp/slox/values/Value.scala
sealed trait Value:
  def isTruthy: Boolean = this match
    case Value.NilVal => false
    case Value.BoolVal(b) => b
    case _ => true

  override def toString: String = this match
    case Value.NilVal => "nil"
    case Value.NumVal(n) => n.toString
    case Value.StrVal(s) => s
    case Value.FnVal(decl, _, _) => s"<fn ${decl.name.lexeme}>"
    case Value.ClassVal(name, _, _) => name
    case Value.InstanceVal(klass, _) => s"${klass.name} instance"
```
- **POO Advantage**: Encapsulates behavior (`isTruthy`, `toString`) directly inside the `Value` abstraction. Any system component interacts with `Value` instances uniformly.

#### B. Object Composition & Delegation (`Environment`)
The variable lookup scope chain is modeled as a recursive object composition:

```scala
// ar/edu/utn/tadp/slox/environment/Environment.scala
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
          case Some(env) => env.get(name) // Delegation up the scope chain
          case None => EvaluationResult.RuntimeError(name, s"Undefined variable '${name.lexeme}'.")
}
```
- **POO Advantage**: `Environment` composes an optional enclosing `Environment` (`enclosing`). Variable resolution is delegated cleanly up the scope chain without leaking scope internals.

---

### 2.2 Functional Paradigm Contributions (PF)

#### A. Algebraic Data Types (ADTs) & Pattern Matching
Instead of using verbose Visitor patterns, expressions and statements are ADTs evaluated via pattern matching:

```scala
// ar/edu/utn/tadp/slox/ast/Expr.scala
sealed trait Expr

object Expr:
  case class Literal(value: Value) extends Expr
  case class Unary(operator: Token, right: Expr) extends Expr
  case class Binary(left: Expr, operator: Token, right: Expr) extends Expr
  case class Variable(name: Token) extends Expr
  case class Assign(name: Token, value: Expr) extends Expr
  case class Logical(left: Expr, operator: Token, right: Expr) extends Expr
  case class Call(callee: Expr, paren: Token, arguments: List[Expr]) extends Expr
  case class Get(objectExpr: Expr, name: Token) extends Expr
  case class Set(objectExpr: Expr, name: Token, value: Expr) extends Expr
  case class Super(keyword: Token, method: Token) extends Expr
```
- **PF Advantage**: Concise, pattern-matching evaluation in `Interpreter.scala` with compiler-enforced exhaustiveness checking.

#### B. Referentially Transparent Monadic Result Container (`EvaluationResult[+A]`)
Instead of throwing side-effecting Java exceptions (`throw`), control flow signals (`return`) and runtime errors are modeled as monadic data structures:

```scala
// ar/edu/utn/tadp/slox/result/EvaluationResult.scala
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
```
- **PF Advantage**: Enables composition with `.flatMap` and `.map`. If a `return` signal or `RuntimeError` occurs, pure functional short-circuiting takes place without throwing exceptions.

#### C. Pure Sequence Folding (`foldLeft`)
Executing statement blocks is modeled as pure state transformations over statement lists:

```scala
def executeBlock(statements: List[Stmt], env: Environment): EvaluationResult[Environment] = {
  statements.foldLeft[EvaluationResult[Environment]](EvaluationResult.Success(env)) {
    case (EvaluationResult.Success(currentEnv), stmt) => execute(stmt, currentEnv)
    case (nonSuccess, _)                              => nonSuccess // Short-circuit
  }
}
```
- **PF Advantage**: Guarantees pure immutability. Block execution transforms input environment `env` into a new output environment deterministically.

#### D. Tail-Recursive Lexical Scanning (`@tailrec`)
The lexical scanner (`Scanner.scala`) avoids imperative loops with mutable pointers, opting for pure tail recursion:

```scala
@tailrec
private def scanLoop(
    start: Int,
    current: Int,
    line: Int,
    tokens: List[Token],
    errors: List[String]
): Either[List[String], List[Token]] = {
  if (current >= source.length) Right((Token(EOF, "", None, line) :: tokens).reverse)
  else {
    // Character-by-character functional tokenization
  }
}
```

---

## 3. Paradigm Synergy Matrix

| Aspect | Object-Oriented Contribution (POO) | Functional Contribution (PF) |
| :--- | :--- | :--- |
| **Domain & Entities** | Models domain classes (`Value`, `Expr`, `Stmt`, `Token`, `Environment`) encapsulating state and operations. | Entities are modeled as immutable ADTs (`sealed trait` / `case class`). |
| **State Management** | `Environment` encapsulates scope nesting and delegation. | State transitions are copy-on-write (`copy(...)`), returning new instances. |
| **Control Flow** | Polymorphic instance methods (`isTruthy`, `toString`). | Referential transparency via monadic `EvaluationResult` with `flatMap` & `foldLeft`. |

---

## 4. Project Directory Structure

```
slox/
├── build.sbt
├── src/
│   ├── main/scala/ar/edu/utn/tadp/slox/
│   │   ├── Main.scala              # REPL & CLI entry point
│   │   ├── ast/                    # AST ADTs (Expr, Stmt)
│   │   ├── tokens/                 # Token & TokenType
│   │   ├── values/                 # Runtime Value ADT
│   │   ├── environment/            # Immutable Environment
│   │   ├── result/                 # Monadic EvaluationResult
│   │   ├── scanner/                # Tail-recursive Scanner
│   │   ├── parser/                 # Monadic Parser
│   │   └── interpreter/            # Pure Pattern-Matching Interpreter
│   └── test/scala/ar/edu/utn/tadp/slox/
│       └── InterpreterSpec.scala   # Integration Test Suite
```

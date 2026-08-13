# slox — Scala 3 Object-Functional Lox Interpreter

`slox` is an interpreter for the **Lox** programming language implemented in **Scala 3**, following the **Object-Functional / Functional-Immutable** programming paradigm taught in *Técnicas de Arquitectura y Diseño de Programación (TADP - UTN FRBA)*.

---

## Key Features & Paradigm Architecture

### 1. Pure Immutability
- No shared mutable state or global variables.
- All state transitions (variable bindings, scope frame nesting, function calls) return a new copy of the immutable container using Scala 3 `case class` `.copy(...)` methods.

### 2. Algebraic Data Types (ADTs) & Pattern Matching
- Domain entities (Tokens, AST Expressions `Expr`, Statements `Stmt`, Values `Value`, and Results `EvaluationResult`) are modeled using `sealed trait` hierarchies with compile-time exhaustiveness checking.

### 3. Monadic Result Handling (`EvaluationResult[+A]`)
- Control flow (`return`, `break`) and runtime errors are managed as monadic data signals (`ReturnSignal`, `RuntimeError`, `Success`) without throwing Java exceptions (`throw`).

### 4. Pure Sequence Folding (`foldLeft`)
- Executing statement lists and blocks transforms input environments into new output environments deterministically using `foldLeft`.

### 5. Tail-Recursive Scanner (`@tailrec`)
- Lexical tokenization is written as a pure tail-recursive function.

---

## Project Structure

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

---

## Build & Run

### 1. Compile
```bash
sbt compile
```

### 2. Run Integration Tests
```bash
sbt test
```

### 3. Run a Script
```bash
sbt "run <path-to-script.lox>"
```

### 4. Interactive REPL
```bash
sbt run
```

---

## Documentation References

- **Architecture & Design**: [`../slox-design.md`](../slox-design.md)
- **Lox Language Specification**: [`../lox-spec.md`](../lox-spec.md)
- **Repository Root**: [`../README.md`](../README.md)

# Lox Interpreter

This repository contains three complete implementations of the **Lox** language interpreter, following the book [*Crafting Interpreters*](https://craftinginterpreters.com/) by Robert Nystrom and the **Object-Functional / Functional-Immutable** paradigm from *TADP (UTN FRBA)*:

1. **jlox**: A tree-walk interpreter implemented in Java. Detailed answers and code walkthroughs available in [`jlox-challenges.md`](jlox-challenges.md).
2. **clox**: A high-performance bytecode virtual machine interpreter implemented in C. Detailed answers, benchmarks, and architectural designs available in [`clox-challenges.md`](clox-challenges.md).
3. **slox**: An object-functional, purely immutable interpreter implemented in Scala 3 following TADP principles. Detailed architectural design available in [`slox-design.md`](slox-design.md) and language spec in [`lox-spec.md`](lox-spec.md).

---

## About Lox

Lox is a dynamically typed programming language designed specifically for the book. It supports:

- Data types: numbers, strings, booleans, and `nil`
- Arithmetic, comparison, and logical operators
- Variables and assignment
- Control flow: `if`, `while`, `for`
- Functions and closures
- Classes, instances, methods, initializers, and inheritance

---

## Java Implementation (jlox)

A tree-walk interpreter written in Java.

### Project Structure (jlox)

```
com/craftinginterpreters/lox/
├── Lox.java        # Entry point, REPL and file execution
├── Scanner.java    # Lexical analysis (tokenization)
├── Token.java      # Token representation
└── TokenType.java  # Enum with all token types
```

### Build & Run (jlox)

#### Compile
```bash
javac com/craftinginterpreters/lox/*.java
```

#### Run a script
```bash
java com.craftinginterpreters.lox.Lox <file.lox>
```

#### Interactive mode (REPL)
```bash
java com.craftinginterpreters.lox.Lox
```

---

## C Implementation (clox)

A high-performance bytecode virtual machine (VM) written in C.

### Key Features (clox)
- **Single-Pass Compiler**: Directly compiles Lox source code to bytecode without generating an intermediate AST.
- **Stack-Based VM**: Executes bytecode instructions using a stack-based virtual machine.
- **Garbage Collector**: A mark-sweep garbage collector manages dynamic objects and memory allocation.
- **Hash Table**: A custom hash table implementation is used for string interning, global/local variables, and method lookup.
- **Closures**: Supports lexically scoped closures using upvalues.

### Project Structure (clox)

```
clox/
├── main.c          # Entry point
├── chunk.c/h       # Bytecode representation (chunks of instructions)
├── compiler.c/h    # Pratt parser / single-pass compiler to bytecode
├── debug.c/h       # Disassembler for inspecting bytecode
├── memory.c/h      # Custom memory management and garbage collector
├── object.c/h      # Runtime objects (strings, functions, classes)
├── scanner.c/h     # Lexical scanner
├── table.c/h       # Custom hash table implementation
├── value.c/h       # Dynamic value representation (Value array)
├── vm.c/h          # Stack-based Virtual Machine execution engine
└── Makefile        # Build configuration
```

### Build & Run (clox)

#### Compile
Navigate to the `clox` directory and compile the executable using `make`:
```bash
cd clox
make
```

#### Run a script
```bash
./clox/clox <file.lox>
```

#### Interactive mode (REPL)
```bash
./clox/clox
```

#### Clean build artifacts
```bash
cd clox
make clean
```

---

## Scala 3 Implementation (slox)

An object-functional, purely immutable Lox interpreter written in Scala 3 following the **TADP (UTN FRBA)** paradigm.

### Key Features (slox)
- **Pure Immutability**: No shared mutable variables (`var` prohibited in AST/Interpreter state); environment scoping and state transitions use copy-on-write state chains.
- **Algebraic Data Types (ADTs)**: Domain entities (Tokens, AST Expressions `Expr`, Statements `Stmt`, Values `Value`, and Results `EvaluationResult`) modeled with Scala 3 `sealed trait` and `case class` / `enum`.
- **Monadic Result Handling**: Control flow (`return`, `break`) and runtime error signals modeled via monadic `EvaluationResult[+A]` without throwing Java exceptions.
- **Pure Sequence Folding**: Block and statement list execution modeled via pure `foldLeft` sequence state transitions.

### Project Structure (slox)

```
slox/
├── build.sbt
├── src/
│   ├── main/scala/ar/edu/utn/tadp/slox/
│   │   ├── Main.scala              # REPL & file entry point
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

### Build & Run (slox)

#### Compile
```bash
cd slox
sbt compile
```

#### Run tests
```bash
cd slox
sbt test
```

#### Run a script
```bash
cd slox
sbt "run <script.lox>"
```

#### Interactive mode (REPL)
```bash
cd slox
sbt run
```

---

## Completed Challenges & Extensions Summary

All challenges from every chapter in the book have been fully implemented, benchmarked, and documented in markdown reports.

### 1. `jlox` Challenges ([`jlox-challenges.md`](jlox-challenges.md))

- **Chapter 4 (Scanning)**: Non-regular lexical grammars (Python indentation & Haskell nested comments); significant whitespace in CoffeeScript/Ruby/C preprocessor; nested block comments `/* ... */`.
- **Chapter 5 (Representing Code)**: RPN Visitor converter; ternary conditional operator `? :`.
- **Chapter 6 (Parsing Expressions)**: Comma operator `,`; error recovery for binary operators without left operand.
- **Chapter 7 (Evaluating Expressions)**: String concatenation type coercion; division by zero safety; `NaN` evaluation semantics.
- **Chapter 8 (Statements and State)**: REPL auto-printing; uninitialized variable runtime error enforcement.
- **Chapter 9 (Control Flow)**: `break` statement implementation for `while` and `for`.
- **Chapter 10 (Functions)**: Anonymous functions / Lambdas (`fun(a, b) { ... }`); local function declarations.
- **Chapter 11 (Resolving and Binding)**: Static analysis pass for unused local variable detection.
- **Chapter 12 (Classes)**: Static class methods; getter properties.
- **Chapter 13 (Inheritance)**: BETA-style top-down method overriding semantics (`inner()`).

### 2. `clox` Challenges ([`clox-challenges.md`](clox-challenges.md))

- **Chapter 14 (Chunks of Bytecode)**: RLE line compression; `OP_CONSTANT_LONG` opcode ($>256$ constants).
- **Chapter 15 (A Virtual Machine)**: Stack allocation and dynamic growth optimization.
- **Chapter 16 (Scanning on Demand)**: Token stream lookahead rules.
- **Chapter 17 (Compiling Expressions)**: `switch`/`case` compilation.
- **Chapter 18 (Types of Values)**: Tagged union vs NaN-boxed representations.
- **Chapter 19 (Strings)**: String interning with FNV-1a hash tables.
- **Chapter 20 (Hash Tables)**: Open addressing, linear probing, tombstone recycling benchmarks.
- **Chapter 21 (Global Variables)**: `OP_DEFINE_GLOBAL` and late-binding lookup.
- **Chapter 22 (Local Variables)**: `const` immutable variable enforcement; `continue` loop jump patching.
- **Chapter 23 (Jumping Back and Forth)**: `switch`/`case` fall-through and jump table patching.
- **Chapter 24 (Calls and Functions)**: Native functions registry (`clock()`, `hasField()`, `getField()`, `setField()`, `deleteField()`).
- **Chapter 25 (Closures)**: Upvalue stack-to-heap migration; loop variable closure capture.
- **Chapter 26 (Garbage Collection)**: Mark-sweep garbage collector with GC stress testing.
- **Chapter 27 (Classes and Instances)**: Instance field reflection native methods (`hasField`, `getField`, `setField`, `deleteField`).
- **Chapter 28 (Methods and Initializers)**: Cached `init()` method resolution (~22.5% speedup); Inline Caches & shape flags analysis.
- **Chapter 29 (Superclasses & Inheritance)**: Class Name Mangling for private fields (`this._id`); subclass tree invalidation; BETA-style top-down inheritance & `inner()` instruction dispatch.
- **Chapter 30 (Optimization & Epilogue)**: `gprof` profiling & inlining `push()` / `pop()` (**~25.8% - 47.2% overall performance boost**); Small String Optimization (SSO) in Tagged Union `Value` (0 heap allocations for short strings).

### 3. `slox` Implementation ([`slox-design.md`](slox-design.md))

- Complete Scala 3 Object-Functional (Functional-Immutable) Lox interpreter implementation following TADP principles.

---

## References

- Book: [Crafting Interpreters](https://craftinginterpreters.com/) — Robert Nystrom
- Author's reference code: [munificent/craftinginterpreters](https://github.com/munificent/craftinginterpreters)
- UTN FRBA TADP: [tadp-clases](https://github.com/tadp-utn-frba/tadp-clases.git)

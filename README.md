# Lox Interpreter

This repository contains two complete implementations of the **Lox** language interpreter, following the book [*Crafting Interpreters*](https://craftinginterpreters.com/) by Robert Nystrom, including all extension challenges implemented, profiled, and documented:

1. **jlox**: A tree-walk interpreter implemented in Java. Detailed answers and code walkthroughs available in [`jlox-challenges.md`](jlox-challenges.md).
2. **clox**: A high-performance bytecode virtual machine interpreter implemented in C. Detailed answers, benchmarks, and architectural designs available in [`clox-challenges.md`](clox-challenges.md).

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

## Completed Challenges Summary

All challenges from every chapter in the book have been fully implemented, benchmarked, and documented in markdown reports.

### 1. `jlox` Challenges ([`jlox-challenges.md`](jlox-challenges.md))

- **Chapter 4 (Scanning)**:
  - Non-regular lexical grammar analysis (Python indentation stack & Haskell nested comments `{- ... -}`).
  - Significant whitespace analysis in CoffeeScript, Ruby, and C preprocessor (`#define FOO(x)` vs `#define FOO (x)`).
  - C-style block comment (`/* ... */`) support with nesting in scanner.
- **Chapter 5 (Representing Code)**:
  - Reverse Polish Notation (RPN) Visitor AST converter.
  - Ternary conditional operator (`condition ? thenBranch : elseBranch`) syntax and evaluation.
- **Chapter 6 (Parsing Expressions)**:
  - Comma operator `,` left-associative sequence expression parsing.
  - Error recovery for binary operators missing left operands (`+ 5`, `* 10`).
- **Chapter 7 (Evaluating Expressions)**:
  - String concatenation with automatic type coercion (`"foo" + 123`).
  - Division by zero runtime safety handling.
  - Comparison operator type rules and `NaN` evaluation semantics.
- **Chapter 8 (Statements and State)**:
  - REPL expression auto-printing (evaluating expressions without trailing semicolons).
  - Runtime error enforcement for accessing uninitialized variables.
- **Chapter 9 (Control Flow)**:
  - `break` statement implementation for `while` and `for` loops.
- **Chapter 10 (Functions)**:
  - Anonymous functions / Lambda expressions (`fun(a, b) { return a + b; }`).
  - Local function declarations and lexical scope resolution.
- **Chapter 11 (Resolving and Binding)**:
  - Static analysis pass for detecting unused local variables.
- **Chapter 12 (Classes)**:
  - Class static methods (`class Math { static square(n) { return n * n; } }`).
  - Getter properties (`class Circle { area { return 3.14 * this.radius * this.radius; } }`).
- **Chapter 13 (Inheritance)**:
  - BETA-style top-down method overriding concept analysis (`inner()` keyword semantics).

### 2. `clox` Challenges ([`clox-challenges.md`](clox-challenges.md))

- **Chapter 14 (Chunks of Bytecode)**:
  - Run-length encoding (RLE) line number compression in bytecode chunks.
  - `OP_CONSTANT_LONG` opcode supporting $>256$ constants per chunk.
  - Dynamic memory allocator profiling (`reallocate`).
- **Chapter 15 (A Virtual Machine)**:
  - Stack allocation and dynamic growth optimization.
  - Binary operator instruction set optimizations.
- **Chapter 16 (Scanning on Demand)**:
  - Flexible token stream generation and lookahead scanner rules.
- **Chapter 17 (Compiling Expressions)**:
  - `switch`/`case` statement compilation and expression parsing.
- **Chapter 18 (Types of Values)**:
  - Tagged union vs. NaN-boxed value representations.
- **Chapter 19 (Strings)**:
  - String interning with FNV-1a hash tables.
- **Chapter 20 (Hash Tables)**:
  - Open addressing hash table benchmarks, linear probing, and tombstone recycling.
- **Chapter 21 (Global Variables)**:
  - Global variable declaration, late-binding lookup, and `OP_DEFINE_GLOBAL`.
- **Chapter 22 (Local Variables)**:
  - `const` immutable variable declaration compile-time & runtime enforcement.
  - `continue` loop statement bytecode jump patching.
- **Chapter 23 (Jumping Back and Forth)**:
  - `switch`/`case` statement compilation with fall-through and jump patching.
- **Chapter 24 (Calls and Functions)**:
  - Native functions registry (`clock()`, `hasField()`, `getField()`, `setField()`, `deleteField()`).
- **Chapter 25 (Closures)**:
  - Flat upvalue array allocation & closed upvalue stack-to-heap migration.
  - Loop variable closure capture optimization (`for` loop scope snapshotting).
- **Chapter 26 (Garbage Collection)**:
  - Tri-color mark-sweep garbage collector with GC stress testing and re-entrancy protection.
- **Chapter 27 (Classes and Instances)**:
  - Dynamic instance field reflection native methods (`hasField`, `getField`, `setField`, `deleteField`).
  - Dynamic property access optimization analysis.
- **Chapter 28 (Methods and Initializers)**:
  - Cached `init()` method initializer resolution (~22.5% faster instantiations).
  - Monomorphic Inline Caches (ICs) & polymorphic devirtualization analysis.
  - `OP_INVOKE` field shadowing trade-offs & shape flags analysis.
- **Chapter 29 (Superclasses & Inheritance)**:
  - Class Name Mangling for private fields (`this._id` -> `"ClassName._id"`).
  - Subclass tree invalidation & epoch counters for late class modification in Ruby/Smalltalk.
  - BETA-style top-down inheritance & `inner()` keyword opcode dispatch implementation in C.
- **Chapter 30 (Optimization & Epilogue)**:
  - `gprof` profiling & inlining `push()` and `pop()` for **~25.8% - 47.2% overall performance boost**.
  - Small String Optimization (SSO) in Tagged Union `Value` (storing strings up to 6 bytes inline with 0 heap allocations).
  - Comprehensive architectural reflection on interpreter and compiler design.

---

## References

- Book: [Crafting Interpreters](https://craftinginterpreters.com/) — Robert Nystrom
- Author's reference code: [munificent/craftinginterpreters](https://github.com/munificent/craftinginterpreters)

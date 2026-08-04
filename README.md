# Lox Interpreter

This repository contains two implementations of the **Lox** language interpreter, following the book [*Crafting Interpreters*](https://craftinginterpreters.com/) by Robert Nystrom:

1. **jlox**: A tree-walk interpreter implemented in Java.
2. **clox**: A bytecode virtual machine interpreter implemented in C.

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

## References

- Book: [Crafting Interpreters](https://craftinginterpreters.com/) — Robert Nystrom
- Author's reference code: [munificent/craftinginterpreters](https://github.com/munificent/craftinginterpreters)

# Lox Interpreter

A Java implementation of the **Lox** language interpreter, following the book [*Crafting Interpreters*](https://craftinginterpreters.com/) by Robert Nystrom.

## About Lox

Lox is a dynamically typed programming language designed specifically for the book. It supports:

- Data types: numbers, strings, booleans, and `nil`
- Arithmetic, comparison, and logical operators
- Variables and assignment
- Control flow: `if`, `while`, `for`
- Functions and closures
- Classes and inheritance

## Project Structure

```
com/craftinginterpreters/lox/
├── Lox.java        # Entry point, REPL and file execution
├── Scanner.java    # Lexical analysis (tokenization)
├── Token.java      # Token representation
└── TokenType.java  # Enum with all token types
```

## Build & Run

### Compile

```bash
javac com/craftinginterpreters/lox/*.java
```

### Run a script

```bash
java com.craftinginterpreters.lox.Lox <file.lox>
```

### Interactive mode (REPL)

```bash
java com.craftinginterpreters.lox.Lox
```

## References

- Book: [Crafting Interpreters](https://craftinginterpreters.com/) — Robert Nystrom
- Author's reference code: [munificent/craftinginterpreters](https://github.com/munificent/craftinginterpreters)

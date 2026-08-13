# Lox Language Specification & Design Notes

This document records the exact language design specification and behavior for **Lox**, used as the authoritative reference for our Scala 3 object-functional implementation (`slox`).

---

## 1. Type System & Memory Management

### 1.1 Dynamic Typing
- Lox is **dynamically typed**.
- Variables can store values of any type, and a single variable can hold values of different types at different points during program execution.

### 1.2 Runtime Error Detection
- Type safety and operation compatibility are checked at **runtime**.
- If an operation is performed on incompatible types (for example, attempting to divide a number by a string, or performing arithmetic on `nil`), the error is detected immediately during evaluation and reported with a clean runtime error message.

### 1.3 Automatic Memory Management
- Allocation and deallocation of memory for strings, objects, instances, and functions are managed **automatically** (garbage collected / managed runtime).

---

## 2. Primitive Data Types

### 2.1 Booleans
- Literals: `true` and `false`.
- Used in conditional statements (`if`, `while`, `for`) and logical operations (`and`, `or`, `!`).

### 2.2 Numbers
- Lox has only **one single number type**: double-precision floating point numbers (`Double` in Scala 64-bit IEEE 754).
- Integer literals (e.g., `42`) and floating-point literals (e.g., `3.14`) are both represented under the hood as `Double`.

### 2.3 Strings
- Text literals enclosed in double quotes (e.g., `"hello world"`).
- Support escape sequences and multi-line content.

### 2.4 Nil
- Keyword: `nil`.
- Represents the absence of a value ("no value", analogous to `null` or `None`).

---

## 3. Expressions & Operators

### 3.1 Arithmetic Infix/Binary Operators
- Operands sit on both sides of the operator (infix notation).
- `+` (addition): Adds two numbers. Also overloaded for **string concatenation** if both operands are strings.
- `-` (subtraction): Subtracts right number operand from left number operand.
- `*` (multiplication): Multiplies two number operands.
- `/` (division): Divides left number operand by right number operand.
- **Type Constraints**: `-`, `*`, `/` require both operands to be numbers. Passing any other type triggers a runtime error. `+` requires either two numbers or two strings.

### 3.2 Unary / Prefix Operators
- Appears before its single operand (prefix notation).
- `-` (negation): Negates a number operand (e.g. `-x`). Requires a number operand; passing any other type produces a runtime error.

### 3.3 Comparison Operators (Strictly for Numbers)
- Infix operators:
  - `<` (less than)
  - `<=` (less than or equal to)
  - `>` (greater than)
  - `>=` (greater than or equal to)
- **Type Constraints**: Both operands must be numbers. Comparing non-number types triggers a runtime error.

### 3.4 Equality Operators (Universal)
- Infix operators:
  - `==` (equal to)
  - `!=` (not equal to)
- **Universal Evaluation**: Can compare any two values of any type without throwing a runtime error.
- Operands of different types are considered **not equal** (e.g., `314 == "pi"` evaluates to `false`).
- `nil` is only equal to `nil`.

### 3.5 Logical Operators & Truthiness
- **Truthiness Rules**: `false` and `nil` are **falsy**. All other values (including `0`, `""`, and objects) are **truthy**.
- **Unary Logical NOT (`!`)**:
  - Prefix operator `!`.
  - Returns `false` if operand is truthy, and `true` if operand is falsy (`!true` -> `false`, `!false` -> `true`).
- **Short-Circuiting Logical Operators (`and`, `or`)**:
  - `and`: Evaluates left operand. If left operand is falsy, returns left operand immediately (**short-circuits**, right operand is not evaluated). Otherwise, evaluates and returns right operand.
  - `or`: Evaluates left operand. If left operand is truthy, returns left operand immediately (**short-circuits**, right operand is not evaluated). Otherwise, evaluates and returns right operand.

### 3.6 Operator Precedence & Associativity
Operators follow standard C precedence rules (from highest to lowest precedence):

| Precedence Group | Operators | Associativity |
| :--- | :--- | :---: |
| **Grouping & Primary** | `()`, literals, variables | N/A |
| **Unary** | `!`, `-` | Right-to-left |
| **Multiplicative** | `*`, `/` | Left-to-right |
| **Additive** | `+`, `-` | Left-to-right |
| **Comparison** | `<`, `<=`, `>`, `>=` | Left-to-right |
| **Equality** | `==`, `!=` | Left-to-right |
| **Logical AND** | `and` | Left-to-right |
| **Logical OR** | `or` | Left-to-right |
| **Assignment** | `=` | Right-to-left |

### 3.7 Intentionally Omitted Operators
Standard Lox explicitly **excludes** the following operator categories:
- No bitwise operators (`&`, `|`, `^`, `~`).
- No shift operators (`<<`, `>>`, `>>>`).
- No modulo operator (`%`).
- No conditional ternary operator (`?:`).

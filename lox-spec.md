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

---

## 4. Statements & Block Scoping

### 4.1 Statement vs. Expression Distinction
- **Expression**: Evaluates to produce a **value** (e.g. `1 + 2`, `a == b`).
- **Statement**: Executes to produce a **side-effect** (e.g. printing, declaring variables, altering control flow, defining blocks). Statements do not yield a value to surrounding code.

### 4.2 Print Statement (`print`)
- Syntax: `print expression;`
- Evaluates the single expression and outputs its string representation to standard output followed by a newline.

### 4.3 Expression Statement
- Syntax: `expression;`
- An expression followed by a semicolon `;` promotes the expression to a statement.
- The expression is evaluated for its side-effect (such as a function call or variable assignment) and the resulting value is discarded.

### 4.4 Block Statement & Lexical Scoping
- Syntax: `{ statement1; statement2; ... }`
- Groups zero or more statements inside curly braces `{ ... }` into a single compound statement wherever a statement is expected.
- **Scope Effect**: A block creates a new **nested local lexical scope frame**. Variables declared inside the block exist only within that block and are discarded when execution leaves the block. Inner scopes can access and shadow variables from enclosing outer scopes.

---

## 5. Variables & Binding

### 5.1 Variable Declarations (`var`)
- Syntax: `var identifier = initializer;` or `var identifier;`
- **Default Initial Value**: If the initializer expression is omitted, the variable is automatically assigned the value `nil`.

### 5.2 Access & Assignment
- **Access**: Reference variable by identifier (`var breakfast = "bagels"; print breakfast;`). Referring to an undeclared variable produces a runtime error.
- **Assignment**: Update variable binding (`breakfast = "beignets";`). Assigning to an undeclared variable produces a runtime error.

---

## 6. Control Flow

### 6.1 Conditional Execution (`if` / `else`)
- Syntax: `if (condition) thenBranch else elseBranch`
- Evaluates the `condition` expression. If truthy, executes `thenBranch`. If falsy and an optional `elseBranch` exists, executes `elseBranch`.

### 6.2 Loops (`while`)
- Syntax: `while (condition) body`
- Repeatedly evaluates `condition`. As long as `condition` is truthy, executes `body`.

### 6.3 Loops (`for`)
- Syntax: `for (initializer; condition; increment) body`
- Syntactic sugar for a `while` loop wrapped in an enclosing block scope:
  - `initializer`: Executed once before the loop (can be a `var` declaration, expression statement, or empty `;`).
  - `condition`: Evaluated before each iteration (defaults to `true` if omitted).
  - `increment`: Executed at the end of each iteration step after `body`.

---

## 7. Functions & Closures

### 7.1 Function Declarations (`fun`)
- Syntax: `fun name(param1, param2, ...) { body }`
- Declares a function named `name` with zero or more comma-separated parameter identifiers.
- **Mandatory Block Body**: The body of a function is **always a block** enclosed in `{ ... }`.

### 7.2 Function Calls & Invocation
- Syntax: `callee(arg1, arg2, ...)` or `callee()` (for zero arguments).
- **Mandatory Parentheses**: Parentheses `()` are required to invoke a function. Omitting parentheses (e.g. `makeBreakfast`) does not execute the function—it simply evaluates to the function object value itself.
- Arity Check: Invoking a function with fewer or more arguments than its declared parameter count produces a runtime error.

### 7.3 Return Statements (`return`)
- Syntax: `return expression;` or `return;`
- Immediately terminates function execution and passes the evaluated `expression` value back to the caller. `return;` without an expression returns `nil`.
- **Implicit Return**: If execution reaches the end of the function body block without encountering an explicit `return` statement, the function implicitly returns `nil`.

### 7.4 First-Class Functions & Lexical Closures
- **First-Class Values**: Functions in Lox are first-class objects—they can be bound to variables, stored in data structures, passed as parameters to other functions, and returned from function calls.
- **Lexical Closures**: Local functions close over variables in their surrounding enclosing scope chains. A function retains access to outer variables even after the enclosing outer function has finished executing and returned (`returnFunction()`).

---

## 8. Classes, Instances & Inheritance

### 8.1 Class Declarations & First-Class Classes
- Syntax:
  ```lox
  class Breakfast {
    cook() {
      print "Eggs a-fryin'!";
    }
    serve(who) {
      print "Enjoy your breakfast, " + who + ".";
    }
  }
  ```
- **Method Declarations**: The body of a class contains its methods, structured like function declarations but without the `fun` keyword.
- **First-Class Values**: Executing a class declaration creates a runtime **Class Object** and binds it to a variable named after the class. Classes are first-class values (can be stored in variables, passed to functions, etc.).

### 8.2 Instance Construction (Classes as Factories)
- **No `new` Keyword**: Lox does not use a `new` keyword. Instead, the class object itself acts as a **factory function**.
- Calling a class like a function (`var breakfast = Breakfast();`) constructs and returns a new instance of that class.

### 8.3 Dynamic Fields & Properties
- Properties can be dynamically defined, assigned, and read on instances at runtime using dot syntax:
  - **Set Property**: `instance.propertyName = value;`
  - **Get Property**: `instance.propertyName`
- Reading a non-existent property on an instance produces a runtime error unless a method with that name exists on the class.

### 8.4 `this` Keyword
- Inside a method body, the `this` keyword refers to the specific instance upon which the method was invoked.
- `this` can be used to access and mutate instance fields (`this.meat = meat;`).
- Attempting to use `this` outside of a class method produces a compile/runtime error.

### 8.5 Constructors & Initializers (`init()`)
- Defining a method named `init()` inside a class marks it as the class constructor/initializer:
  ```lox
  class Breakfast {
    init(meat, bread) {
      this.meat = meat;
      this.bread = bread;
    }
  }
  ```
- When a class is called as a factory function (`Breakfast("bacon", "toast")`), any arguments passed are automatically forwarded to `init(...)`.
- `init()` automatically returns `this` when invoked during object construction.

### 8.6 Single Inheritance (`<`)
- Syntax: `class Subclass < Superclass { ... }`
- **Subclassing**: `Subclass` inherits all methods defined in `Superclass`. Lox supports **single inheritance** only.
- Overriding: A subclass can define a method with the same name as a superclass method to override its behavior.

### 8.7 `super` Keyword
- Syntax: `super.methodName(args...)`
- Used inside a subclass method to invoke an inherited method implementation on the `Superclass`, bypassing any overriding method defined in the subclass itself.
- Common use case: Calling `super.init(...)` inside a subclass constructor to initialize superclass state.

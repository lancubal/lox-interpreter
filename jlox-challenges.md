# JLox Challenges

## Scanning

### 1. 
The lexical grammars of Python and Haskell are not regular. What
does that mean, and why aren’t they?

#### Answer:
A lexical grammar is **regular** if it can be recognized by a Finite State Automaton (FSA) or described by a formal regular expression (without features like backreferences). Regular languages have finite memory and cannot perform tasks that require matching arbitrary pairs of delimiters or tracking unbounded counts.

Python and Haskell are not regular at the lexical level for the following reasons:

1. **Indentation and Layout (The Off-side Rule)**:
   - Both languages use indentation to define block structures (e.g., Python's `INDENT` and `DEDENT` tokens).
   - To generate these tokens, the scanner must keep a stack of current indentation levels to compare the current line's indentation with the previous ones.
   - An FSA has only a finite number of states and cannot maintain an arbitrarily deep stack of indentation levels, which makes this feature context-free (requiring a Pushdown Automaton).

2. **Nested Comments (Haskell)**:
   - Haskell supports nested block comments of the form `{- ... {- ... -} ... -}`.
   - Parsing nested delimiters requires matching opening and closing tokens at arbitrary depths. Because this requires keeping track of the nesting depth (which can be arbitrarily large), it cannot be recognized by a finite state automaton (similar to the classic non-regular language $L = \{a^n b^n\}$).

---

### 2. 
Aside from separating tokens —distinguishing "print foo" from
"printfoo"— spaces aren’t used for much in most languages. However,
in a couple of dark corners, a space does affect how code is parsed in
CoffeeScript, Ruby, and the C preprocessor. Where and what effect
does it have in each of those languages?

#### Answer:

1. **Ruby**:
   - **Method Calls without Parentheses vs. Expression Parentheses**:
     In Ruby, parentheses are optional for method calls. If you write `foo(bar, baz)`, it calls `foo` with two arguments. However, if you write `foo (bar, baz)`, the space tells the parser that `(bar, baz)` is a single parenthesized expression being passed as the first argument, which results in a syntax error because the comma is invalid in that context.
   - **Ambiguous Operators (Unary vs. Binary)**:
     A space can change whether a symbol is parsed as a binary operator or a unary sign. For instance:
     - `foo -bar` is interpreted as calling `foo` with the argument `-bar` (unary minus).
     - `foo - bar` is interpreted as the binary subtraction `foo` minus `bar`.

2. **CoffeeScript**:
   - **Implicit Function Calls**:
     Similar to Ruby, CoffeeScript allows calling functions without parentheses. Spacing changes how expressions are grouped:
     - `a - b` is the binary subtraction `a - b`.
     - `a -b` is a function call `a(-b)` where the argument is negative `b`.
   - **Regular Expression Literals vs. Division**:
     A space can disambiguate division from a regex literal:
     - `a / b / c` is parsed as division (`a` divided by `b` divided by `c`).
     - `a /b/ c` can be interpreted as a regex literal `/b/` appearing between `a` and `c`.

3. **C Preprocessor**:
   - **Macro Definitions (`#define`)**:
     A space immediately after the macro name in a `#define` directive changes the macro's type:
     - **Function-like macro** (no space): `#define FOO(x) (x + 1)` defines a macro that takes an argument `x`.
     - **Object-like macro** (with space): `#define FOO (x) (x + 1)` defines a macro `FOO` that expands literally to the text `(x) (x + 1)`.

---

### 3. 
Our scanner here, like most, discards comments and whitespace since
those aren’t needed by the parser. Why might you want to write a
scanner that does not discard those? What would it be useful for?

#### Answer:
Scanners that retain comments and whitespace (often called **lossless scanners** or scanners producing a **concrete syntax tree** / **parse tree**) are useful for tools other than standard compilers:

1. **Source Code Formatters (e.g., `gofmt`, `Prettier`, `clang-format`)**:
   Formatters need to read the code, construct an AST, and output the formatted code while retaining comment placements relative to code constructs.
   
2. **Linters and Style Checkers (e.g., `ESLint`, `Checkstyle`)**:
   Linters inspect whitespace style rules (like indentation, missing spaces, trailing spaces) and verify comment conventions (e.g., ensuring every public method has a Javadoc comment).

3. **Documentation Generators (e.g., `Javadoc`, `Doxygen`, `Sphinx`)**:
   These tools extract documentation comments directly from the source code. A scanner must recognize and capture these comments, linking them to the classes or methods they precede.

4. **IDE Features (Syntax Highlighting, Refactoring, Code Navigation)**:
   IDEs need to know about every character in the source file to highlight comments correctly and to perform refactoring tasks (like renaming a variable) without losing existing formatting or comments surrounding the modified code.

5. **Transpilers (Source-to-Source Compilers)**:
   When translating code from one programming language to another (e.g., TypeScript to JavaScript), keeping the original comments helps make the output code human-readable and maintainable.

---

### 4. 
Add support to Lox’s scanner for C-style /* ... */ block comments.
Make sure to handle newlines in them. Consider allowing them to nest.
Is adding support for nesting more work than you expected? Why?

#### Answer:
Yes, supporting nested comments is slightly more complex than non-nested comments, though it is straightforward when using a simple tracking variable.

- **Non-nested block comments** can be parsed by scanning until the closing `*/` sequence is encountered. This can be expressed by a relatively simple state machine or regex.
- **Nested block comments** are not a regular language. They require tracking the nesting level to know when the comment has truly closed (e.g., matching the number of `/*` with `*/`). 
- In our scanner, we solved this by introducing a `depth` counter. We increment `depth` when we encounter `/*` and decrement it when we see `*/`. Only when `depth` reaches `0` do we stop scanning.
- We also had to ensure we correctly advance two characters for both `/*` and `*/` inside the loop, to avoid false positives (e.g. an isolated `*` or `/` matching incorrectly, or overlapping delimiters like `/*/`).

#### Implementation Details:
In `com/craftinginterpreters/lox/Scanner.java`, we modified the `case '/'` block in `scanToken()` to handle `/*` block comments:
1. Checked if the next character matches `*` using `match('*')`.
2. Initialized `depth = 1`.
3. Used a `while` loop that runs as long as `depth > 0` and the file has not ended.
4. Inside the loop, if we peek at `/*` (`peek() == '/' && peekNext() == '*'`), we consume both characters and increment `depth`.
5. If we peek at `*/` (`peek() == '*' && peekNext() == '/'`), we consume both characters and decrement `depth`.
6. Tracked newlines by checking `peek() == '\n'` and incrementing the `line` counter.
7. Reported an error if the loop finishes and `depth > 0` (unterminated block comment).

## Representing Code

### 1.
Earlier, I said that the ```|```, ```*```, and ```+``` forms we added to our grammar
metasyntax were just syntactic sugar. Take this grammar:
```
expr → expr ( "(" ( expr ( "," expr )* )? ")" | "."
IDENTIFIER )+
| IDENTIFIER
| NUMBER
```
Produce a grammar that matches the same language but does not use
any of that notational sugar.
Bonus: What kind of expression does this bit of grammar encode?

#### Answer:

To remove the syntactic sugar, we expand the operators `|` (alternation), `*` (zero-or-more), `+` (one-or-more), `?` (optional), and parenthesis groupings into recursive production rules.

Here is the equivalent context-free grammar:

```
expr      → IDENTIFIER
expr      → NUMBER
expr      → expr tail

tail      → operation
tail      → tail operation

operation → "(" args ")"
operation → "." IDENTIFIER

args      → 
args      → arg_list

arg_list  → expr
arg_list  → arg_list "," expr
```
*(Note: `args → ` has an empty right-hand side, representing the epsilon $\epsilon$ production for empty arguments).*

#### Bonus Answer:
This grammar encodes **function/method calls** and **property/member accesses** (getters) chained together, starting with an identifier or number. For example, it matches expressions like `foo.bar(baz, 42).qux()`.

### 2.
The Visitor pattern lets you emulate the functional style in an object-
oriented language. Devise a complementary pattern for a functional
language. It should let you bundle all of the operations on one type
together and let you define new types easily.
(SML or Haskell would be ideal for this exercise, but Scheme or
another Lisp works as well.)

#### Answer:

The complementary problem is known as the **Expression Problem**. 
- In **Object-Oriented (OO)** programming, adding new types (subclasses) is easy, but adding new operations (methods) is hard because you have to touch every class. The Visitor pattern emulates the functional style in OO, making it easy to add operations but hard to add new types.
- In **Functional Programming (FP)**, adding new operations (functions) is easy, but adding new types (variants in an Algebraic Data Type) is hard because you have to update every function that pattern-matches over the type.

To emulate the OO style in a functional language (making it easy to add new types and bundle their operations together), we can use the **Object Pattern** (also known as the **Church encoding** or **records of functions**).

##### 1. Scheme / Lisp Approach (Message-Passing Closures)
In Lisp/Scheme, we can represent a "type" (or object) as a closure that captures the state and takes a "message" (symbol) indicating the operation to perform:

```scheme
;; Define a "type" constructor for a Binary Expression
(define (make-binary-expr left op right)
  (lambda (msg)
    (cond ((eq? msg 'evaluate) (perform-op op (left 'evaluate) (right 'evaluate)))
          ((eq? msg 'to-string) (string-append "(" (left 'to-string) " " op " " (right 'to-string) ")"))
          (else (error "Unknown operation")))))
```
To define a new type, we just write a new constructor function (like `make-literal-expr`) that returns a lambda handling the same messages. The operations for each type are bundled together inside the constructor.

##### 2. Haskell Approach (Record of Functions)
In Haskell, we can represent the suite of operations on a type as a record containing function fields. Each "type" is an instance of this record:

```haskell
-- Define the bundle of operations
data Expr = Expr {
    evaluate :: IO Double,
    toString :: String
}

-- Adding a new type is as simple as defining a new value of Expr:
literalExpr :: Double -> Expr
literalExpr val = Expr {
    evaluate = return val,
    toString = show val
}

binaryExpr :: String -> Expr -> Expr -> Expr
binaryExpr op left right = Expr {
    evaluate = do
        lVal <- evaluate left
        rVal <- evaluate right
        return (applyOp op lVal rVal),
    toString = "(" ++ toString left ++ " " ++ op ++ " " ++ toString right ++ ")"
}
```
With this pattern, adding a new type (like a new unary expression) does not require changing any existing functions; we just define a new function that returns a record of type `Expr`. All operations for that type are grouped together in one place.

### 3.
In reverse Polish notation (RPN), the operands to an arithmetic
operator are both placed before the operator, so 1 + 2 becomes 1 2 +.
Evaluation proceeds from left to right. Numbers are pushed onto an
implicit stack. An arithmetic operator pops the top two numbers,
performs the operation, and pushes the result. Thus, this:
```(1 + 2) * (4 - 3)```
in RPN becomes:
```1 2 + 4 3 - *```
Define a visitor class for our syntax tree classes that takes an
expression, converts it to RPN, and returns the resulting string.

#### Answer:

We defined a new visitor class `RpnPrinter` that implements `Expr.Visitor<String>` to format expressions into Reverse Polish Notation.

##### Implementation Details:
The new visitor class was created in `com/craftinginterpreters/lox/RpnPrinter.java`. Its core rules for translating expressions to RPN are:
1. **Literals**: Returns the string representation of the literal directly (`1` becomes `"1"`).
2. **Groupings**: Since RPN has no parenthesis groupings (operations are unambiguous due to their order), we simply discard the grouping structure and return the inner expression formatted (`(expr)` becomes `expr`).
3. **Unary Operations**: Places the unary operator after its operand (`-123` becomes `"123 -"`).
4. **Binary Operations**: Formats the left operand, then the right operand, and places the binary operator at the end (`1 + 2` becomes `"1 2 +"`).

##### Code implementation:
```java
package com.craftinginterpreters.lox;

class RpnPrinter implements Expr.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return expr.left.accept(this) + " " + expr.right.accept(this) + " " + expr.operator.lexeme;
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return expr.expression.accept(this);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return expr.right.accept(this) + " " + expr.operator.lexeme;
    }

    // (Other Expr.Visitor methods such as visitAssignExpr, visitCallExpr, etc. are also implemented)
}
```

## Parsing Expressions

### 1.
In C, a block is a statement form that allows you to pack a series of
statements where a single one is expected. The comma operator is an
analogous syntax for expressions. A comma-separated series of
expressions can be given where a single expression is expected (except
inside a function call’s argument list). At runtime, the comma operator
evaluates the left operand and discards the result. Then it evaluates and
returns the right operand.
Add support for comma expressions. Give them the same precedence
and associativity as in C. Write the grammar, and then implement the
necessary parsing code.

#### Answer:

##### 1. Grammar:
To give the comma operator the lowest precedence (lower than assignment) and left-associativity, we update the expression grammar hierarchy:

```bnf
expression → comma ;
comma      → assignment ( "," assignment )* ;
```

##### 2. Precedence & Associativity:
- **Precedence**: Lowest of all expression operators (below assignment `=`).
- **Associativity**: Left-associative (`1, 2, 3` is parsed as `(1, 2), 3`).

##### 3. Implementation Details:
- **Parser (`com/craftinginterpreters/lox/Parser.java`)**:
  - `expression()` now calls `comma()`.
  - `comma()` parses a series of `assignment()` expressions separated by `COMMA` tokens into `Expr.Binary` nodes.
  - In `finishCall()`, function arguments are parsed using `assignment()` so that commas act as argument delimiters rather than comma operators (unless wrapped in parentheses).
- **Interpreter (`com/craftinginterpreters/lox/Interpreter.java`)**:
  - In `visitBinaryExpr()`, added `case COMMA:` which evaluates the left operand (for side-effects), evaluates the right operand, and returns the right operand's result.

### 2.
Likewise, add support for the C-style conditional or “ternary” operator
?:. What precedence level is allowed between the ? and :? Is the
whole operator left-associative or right-associative?

#### Answer:

##### 1. Precedence & Associativity Answers:
- **Precedence level between `?` and `:`**: Any expression (full `expression` level syntax, including comma and assignment) is allowed between `?` and `:`. Because the colon `:` acts as an explicit matching delimiter for `?`, the then-branch is unambiguous.
- **Associativity**: The ternary operator is **right-associative**. An expression like `a ? b : c ? d : e` is evaluated as `a ? b : (c ? d : e)`.

##### 2. Grammar:
```bnf
assignment → ( call "." )? IDENTIFIER "=" assignment
           | ternary ;
ternary    → logic_or ( "?" expression ":" ternary )? ;
```

##### 3. Implementation Details:
- **Tokens (`com/craftinginterpreters/lox/TokenType.java`, `Scanner.java`)**: Added `QUESTION` (`?`) and `COLON` (`:`) token types and scanner rules.
- **AST (`com/craftinginterpreters/lox/Expr.java`)**: Added `Expr.Ternary` node containing `Expr condition`, `Expr thenBranch`, and `Expr elseBranch`, plus `visitTernaryExpr` in `Visitor<R>`.
- **Parser (`com/craftinginterpreters/lox/Parser.java`)**: Added `ternary()` method called from `assignment()`. If `?` matches, it parses `thenBranch` via `expression()`, consumes `:`, and recursively parses `elseBranch` via `ternary()` to enforce right-associativity.
- **Interpreter (`com/craftinginterpreters/lox/Interpreter.java`)**: Implemented `visitTernaryExpr` with short-circuit evaluation (only the executed branch is evaluated).

### 3.
Add error productions to handle each binary operator appearing
without a left-hand operand. In other words, detect a binary operator
appearing at the beginning of an expression. Report that as an error,
but also parse and discard a right-hand operand with the appropriate
precedence

#### Answer:

##### Explanation & Error Production Strategy:
In a top-down recursive descent parser, every expression parsing method descends to `unary()` / `primary()` to parse the initial left-hand operand. If an expression mistakenly starts with a binary operator (such as `+`, `*`, `/`, `==`, `!=`, `<`, `<=`, `>`, `>=`, `,`), it reaches `unary()` where no left operand has been supplied.

By matching these binary operators at the start of `unary()`, we can:
1. Log a syntax error reporting the missing left-hand operand.
2. Parse and discard the right-hand operand at the operator's corresponding precedence level so the rest of the expression is consumed correctly before throwing a `ParseError` to synchronize.

##### Implementation Details:
In `com/craftinginterpreters/lox/Parser.java`, inside `unary()`:
- Checked for leading binary operators (`+`, `*`, `/`, `==`, `!=`, `<`, `<=`, `>`, `>=`, `,`).
- Reported the error: `error(operator, "Binary operator missing left-hand operand.")`.
- Discarded the right-hand operand using appropriate precedence:
  - `*`, `/`: calls `factor()`
  - `+`: calls `term()`
  - `<`, `<=`, `>`, `>=`: calls `comparison()`
  - `==`, `!=`: calls `equality()`
  - `,`: calls `comma()`
- Threw `ParseError` to allow clean statement synchronization.

## Evaluating Expressions

### 1.
Allowing comparisons on types other than numbers could be useful.
The operators might have a reasonable interpretation for strings. Even
comparisons among mixed types, like 3 < "pancake" could be handy
to enable things like ordered collections of heterogeneous types. Or it
could simply lead to bugs and confusion.
Would you extend Lox to support comparing other types? If so, which
pairs of types do you allow and how do you define their ordering?
Justify your choices and compare them to other languages.

#### Answer:

##### 1. Decision & Allowed Pairs:
I would extend Lox to allow **string-to-string comparisons**, but **disallow comparisons between mixed/different types**.

- **Allowed Pairs**:
  - `(Number, Number)`: Numerical comparison (standard mathematical ordering).
  - `(String, String)`: Lexicographical comparison based on Unicode code point values (dictionary order).
- **Disallowed Pairs**:
  - Any pair of mixed types (e.g., `3 < "pancake"`, `true < 5`, `nil < "foo"`). These raise a runtime error: `"Operands must be two numbers or two strings."`.

##### 2. Justification & Comparison with Other Languages:

- **String Comparison (`String` vs `String`)**:
  - *Why allow it?* Lexicographical ordering for strings (e.g. `"apple" < "banana"`) is intuitive, universally understood, and essential for basic tasks like sorting a list of names.

- **Heterogeneous Comparison (`Mixed Types`)**:
  - *JavaScript*: Performs implicit type coercion. For example, `"2" < 3` converts `"2"` to a number (`true`), but `3 < "pancake"` converts `"pancake"` to `NaN`, making `3 < NaN` evaluate to `false` and `"pancake" < 3` also `false`. This breaks total ordering assumptions and causes silent, hard-to-find bugs.
  - *Python 2 vs Python 3*: Python 2 allowed comparing arbitrary types by ordering their type names alphabetically (`3 < "pancake"` returned `True` because `'int' < 'str'`). Python 3 deliberately removed this feature and now raises a `TypeError` when comparing incompatible types. Python's core developers realized that allowing cross-type comparisons hid logic bugs (such as forgetting to convert string user input into an integer before comparing).
  - *Lox Choice*: In a dynamic language, catching type mismatches early via runtime errors is safer than returning arbitrary boolean results. Comparing a number to a string is almost always a bug, so raising a runtime error provides clearer feedback to the programmer.

### 2.
Many languages define + such that if either operand is a string, the
other is converted to a string and the results are then concatenated. For
example, "scone" + 4 would yield scone4. Extend the code in
visitBinaryExpr() to support that.

#### Answer:

##### Implementation Details:
In `com/craftinginterpreters/lox/Interpreter.java`, we updated `case PLUS:` inside `visitBinaryExpr()`:
- If both operands are numbers (`Double`), we perform numerical addition.
- If **either** operand is a `String` (`left instanceof String || right instanceof String`), we convert both operands to strings using `stringify()` and return their concatenation.

```java
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double) left + (double) right;
        }
        if (left instanceof String || right instanceof String) {
          return stringify(left) + stringify(right);
        }
        throw new RuntimeError(expr.operator, "Operands must be two numbers or at least one string.");
```

### 3.
What happens right now if you divide a number by zero? What do you
think should happen? Justify your choice. How do other languages you
know handle division by zero, and why do they make the choices they
do?
Change the implementation in visitBinaryExpr() to detect and report
a runtime error for this case

#### Answer:

##### 1. Current Behavior in Lox:
Lox represents numbers as Java `double` values (IEEE 754 floating-point). In Java, dividing a `double` by `0.0` yields `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`, or `Double.NaN` (for `0.0 / 0.0`). It does not throw an exception, so Lox currently evaluates `5 / 0` to `Infinity` silently.

##### 2. What Should Happen & Justification:
Division by zero is mathematically undefined. Allowing `Infinity` or `NaN` to silently propagate into further arithmetic produces confusing downstream results and masks bugs. Throwing an explicit runtime error immediately when dividing by `0` catches programmer errors at the point of origin.

##### 3. How Other Languages Handle Division by Zero:
- **Java / C / C++**:
  - *Integer Division*: Throws an exception (`ArithmeticException` in Java) or causes undefined behavior/signal (`SIGFPE` in C) because integers cannot represent infinity.
  - *Floating-Point Division*: Adheres to IEEE 754, returning `Infinity` or `NaN`.
- **Python**: Throws a `ZeroDivisionError` for **both** integer (`1 / 0`) and floating-point (`1.0 / 0.0`) division, prioritizing safety and explicit error reporting over IEEE 754 silent propagation.
- **JavaScript**: Returns `Infinity`, `-Infinity`, or `NaN` for all divisions by zero because numbers are IEEE 754 double-precision floats.

##### 4. Implementation Details:
In `com/craftinginterpreters/lox/Interpreter.java`, we updated `case SLASH:` in `visitBinaryExpr()` to check if the right operand equals `0` and throw a `RuntimeError`:

```java
      case SLASH:
        checkNumberOperands(expr.operator, left, right);
        if ((double) right == 0) {
          throw new RuntimeError(expr.operator, "Division by zero.");
        }
        return (double) left / (double) right;
```

## Statements and State

### 1.
The REPL no longer supports entering a single expression and
automatically printing its result value. That’s a drag. Add support to
the REPL to let users type in both statements and expressions. If they
enter a statement, execute it. If they enter an expression, evaluate it
and display the result value.

#### Answer:

##### Implementation Details:
- **Parser (`com/craftinginterpreters/lox/Parser.java`)**:
  Added `parseRepl()`. If the input starts with statement keywords (`var`, `class`, `if`, `while`, `for`, `print`, `return`, `{`, or `fun <identifier>`), it parses and returns `List<Stmt>`. Otherwise, it attempts to parse the line as an `Expr` (allowing an optional trailing semicolon).
- **Interpreter (`com/craftinginterpreters/lox/Interpreter.java`)**:
  Added `interpret(Expr expr)` which evaluates the expression and prints `stringify(value)` to `System.out`.
- **Resolver (`com/craftinginterpreters/lox/Resolver.java`)**:
  Exposed `resolve(Expr expr)` so individual REPL expressions can be resolved before evaluation.
- **Main REPL (`com/craftinginterpreters/lox/Lox.java`)**:
  Updated `runPrompt()` to invoke `runRepl()`. If the parser returns an `Expr`, it resolves and evaluates it with auto-printing. If it returns a `List<Stmt>`, it resolves and executes the statements normally.

### 2.
Maybe you want Lox to be a little more explicit about variable
initialization. Instead of implicitly initializing variables to nil, make it
a runtime error to access a variable that has not been initialized or
assigned to, as in:
```
// No initializers.
var a;
var b;
a = "assigned";
print a; // OK, was assigned first.
print b; // Error!
```

#### Answer:

##### Implementation Details:
1. **Sentinel Object (`com/craftinginterpreters/lox/Environment.java`)**:
   Created a unique sentinel object: `static final Object UNINITIALIZED = new Object();`.
2. **Variable Declaration (`com/craftinginterpreters/lox/Interpreter.java`)**:
   Updated `visitVarStmt()`. If a `var` declaration lacks an initializer (`var a;`), its value is set to `Environment.UNINITIALIZED` rather than `null` (`nil`).
3. **Variable Access (`com/craftinginterpreters/lox/Environment.java`)**:
   Updated `get()` and `getAt()`. If the looked-up value matches `UNINITIALIZED`, a `RuntimeError` is thrown: `"Variable '<name>' used before initialization."`.
4. **Variable Assignment**:
   When a variable is assigned a value (`a = "assigned";`), the sentinel is overwritten in `Environment` with the assigned value, allowing subsequent reads.

### 3.
What does the following program do?
```
var a = 1;
{
var a = a + 2;
print a;
}
```
What did you expect it to do? Is it what you think it should do? What
does analogous code in other languages you are familiar with do?
What do you think users will expect this to do?

#### Answer:

##### 1. What the Program Does in Lox (at Chapter 8):
The program prints **`3`**.

- **Execution step-by-step**:
  1. `var a = 1;` defines `a` in global scope with value `1`.
  2. Inside the block, `var a = a + 2;` evaluates the initializer expression `a + 2` *before* `a` is defined in the inner environment scope.
  3. When `a + 2` is evaluated, the lookup for `a` resolves to the outer global variable `a` (which is `1`), so `1 + 2` evaluates to `3`.
  4. Finally, the inner environment binds the new local variable `a` to `3`.
  5. `print a;` prints `3`.

##### 2. Is it what it should do?
While this behavior is a logical side-effect of evaluating initializers before binding the variable name in a simple dynamic environment, it is questionable language design. Referencing a variable in its own initializer (`var a = a + 2`) is usually a programmer bug (either a typo or a misunderstanding of shadowing). Later in the book (Chapter 11: Resolving and Binding), Lox introduces static scope resolution to catch this at compile-time and throw an error: *"Can't read local variable in its own initializer."*

##### 3. How Analogous Code Behaves in Other Languages:
- **JavaScript (`let` / `const`)**:
  Throws `ReferenceError: Cannot access 'a' before initialization`. JavaScript hoists `let a` into the block's *Temporal Dead Zone (TDZ)*, so the inner `a` shadows the outer `a` for the entire block. Reading `a` in `let a = a + 2` accesses the uninitialized inner variable.
- **Python**:
  Throws `UnboundLocalError: local variable 'a' referenced before assignment`. Assigning to `a` anywhere in a function scope marks `a` as local for the entire function, masking the global `a`.
- **C / C++**:
  Compiles, but results in **undefined behavior / garbage value**. The name `a` is in scope immediately after its declarator, so `a + 2` reads uninitialized stack memory.
- **Java / C#**:
  Triggers a **compile-time error** (`Variable 'a' is already defined in the scope`). Java prohibits local variable shadowing within nested blocks in the same method.
- **Scheme / Lisp**:
  - `(let ((a (+ a 2))) ...)` evaluates the initializer `(+ a 2)` in the *outer* environment, producing `3`.
  - `(letrec ...)` evaluates initializers with the new bindings in scope.

##### 4. What Users Will Expect:
- Programmers familiar with Scheme/Lisp or basic macro expansion might expect outer `a` to be used, producing `3`.
- The vast majority of modern developers (accustomed to JS, Python, C++, Java) will expect an error (TDZ, shadowing/redeclaration error, or uninitialized access error), as reading a variable inside its own declaration is almost always an error.

## Control Flow

### 1.
A few chapters from now, when Lox supports first-class functions and
dynamic dispatch, we technically won’t need branching statements
built into the language. Show how conditional execution can be
implemented in terms of those. Name a language that uses this
technique for its control flow.

#### Answer:

##### 1. Implementing Conditional Execution via Dynamic Dispatch & Functions:
By representing booleans as objects and passing branches as zero-argument functions (closures), dynamic dispatch automatically invokes the appropriate branch without requiring an `if` statement:

```lox
class Boolean {
  ifThenElse(thenBranch, elseBranch) {}
}

class TrueClass < Boolean {
  ifThenElse(thenBranch, elseBranch) {
    return thenBranch();
  }
}

class FalseClass < Boolean {
  ifThenElse(thenBranch, elseBranch) {
    return elseBranch();
  }
}

var True = TrueClass();
var False = FalseClass();

// Usage example:
var condition = True;
condition.ifThenElse(
  fun () { print "Condition is true!"; },
  fun () { print "Condition is false!"; }
);
```

##### 2. Programming Language that uses this technique:
**Smalltalk** (and derivative languages like **Self**, **Pharo**, and **Squeak**).
In Smalltalk, `ifTrue:ifFalse:` is not a language keyword or built-in statement; it is a regular message sent to boolean objects (`true` and `false`), where the branches are passed as block closures `[ ... ]`.

### 2.
Likewise, looping can be implemented using those same tools,
provided our interpreter supports an important optimization. What is it,
and why is it necessary? Name a language that uses this technique for
iteration.

#### Answer:

##### 1. Optimization Name:
**Tail Call Optimization (TCO)** (also known as **Tail Recursion Elimination**).

##### 2. Why it is Necessary:
When loops are expressed recursively using function calls, every function call normally allocates a new frame on the call stack. Without TCO, a loop executing thousands or millions of times will consume stack memory proportional to the number of iterations ($O(N)$ stack space), quickly exhausting the call stack and causing a **StackOverflowError**.

With **Tail Call Optimization**, when a function call occurs in the *tail position* (the very last action performed before returning), the compiler or interpreter reuses the current stack frame instead of allocating a new one. This reduces the stack space consumption to $O(1)$ constant space, allowing recursive loops to run indefinitely without overflowing the stack.

##### 3. Programming Language that uses this technique:
**Scheme** (Lisp dialect).
The Scheme language standards (R5RS, R6RS, R7RS) explicitly mandate that implementations must be properly tail-recursive. Scheme does not provide built-in primitive looping keywords like `while` or `for`; all iteration is performed using tail-recursive function calls.

### 3.
Unlike Lox, most other C-style languages also support break and
continue statements inside loops. Add support for break statements.
The syntax is a break keyword followed by a semicolon. It should be a
syntax error to have a break statement appear outside of any enclosing
loop. At runtime, a break statement causes execution to jump to the
end of the nearest enclosing loop and proceeds from there. Note that
the break may be nested inside other blocks and if statements that
also need to be exited.

#### Answer:

##### Implementation Details:
1. **Tokens & AST (`com/craftinginterpreters/lox/TokenType.java`, `Scanner.java`, `Stmt.java`)**:
   Added `BREAK` token type, registered `"break"` in `Scanner`, and defined `Stmt.Break` AST node.
2. **Static Validation in Parser (`com/craftinginterpreters/lox/Parser.java`)**:
   - Maintained a `loopDepth` counter.
   - Incremented `loopDepth` when parsing `whileStatement()` and `forStatement()` bodies (decrementing in a `finally` block).
   - In `breakStatement()`, verified that `loopDepth > 0`. If `loopDepth == 0`, reported a syntax error: `"Can't use 'break' outside of a loop."`.
3. **Resolver (`com/craftinginterpreters/lox/Resolver.java`)**:
   Added `visitBreakStmt()`.
4. **Runtime Control Flow in Interpreter (`com/craftinginterpreters/lox/Interpreter.java`)**:
   - Created a lightweight `BreakException extends RuntimeException`.
   - In `visitBreakStmt()`, threw `BreakException`.
   - In `visitWhileStmt()`, wrapped the loop execution in `try { while (...) { execute(stmt.body); } } catch (BreakException b) {}`. Throwing `BreakException` instantly unwinds through any deeply nested blocks or `if` statements inside the loop body, terminating the loop execution.

## Functions

### 1.
Our interpreter carefully checks that the number of arguments passed
to a function matches the number of parameters it expects. Since this
check is done at runtime on every call, it has a performance cost.
Smalltalk implementations don’t have that problem. Why not?

#### Answer:

In Smalltalk, method names (**selectors**) are defined using **keyword syntax**, where each argument is preceded by a keyword ending with a colon (`:`).

For example:
- `collection at: index` sends the message `at:` (1 argument).
- `collection at: index put: value` sends the message `at:put:` (2 arguments).

##### Why Smalltalk doesn't need an explicit arity check:
1. **The method name encodes the parameter count**: The selector name includes every keyword and colon. `at:` and `at:put:` are distinct method names in the class's method dictionary.
2. **Dispatch enforces arity**: Method resolution in Smalltalk looks up the method by its full keyword selector name. Since a method definition's selector name explicitly specifies the exact number of colons (and thus parameters), finding a matching method during message dispatch inherently guarantees that the number of arguments matches the method's parameters.
3. **No method mismatch**: If a caller sends a message with a different number of keywords/arguments (e.g. sending `at:put:` to an object that only defines `at:`), the message lookup fails to find a matching selector and raises a `doesNotUnderstand:` (Message Not Understood) exception, rather than an arity error.

Because method lookup already incorporates the exact number of arguments into the selector key, Smalltalk implementations do not require a separate runtime check to verify arity during method invocation.

### 2.
Lox’s function declaration syntax performs two independent
operations. It creates a function and also binds it to a name. This
improves usability for the common case where you do want to
associate a name with the function. But in functional-styled code, you
often want to create a function to immediately pass it to some other
function or return it. In that case, it doesn’t need a name.
Languages that encourage a functional style usually support
anonymous functions or lambdas—an expression syntax that creates
a function without binding it to a name. Add anonymous function
syntax to Lox so that this works:
```
fun thrice(fn) {
for (var i = 1; i <= 3; i = i + 1) {
fn(i);
}
}
thrice(fun (a) {
print a;
});
// "1".
// "2".
// "3".
```
How do you handle the tricky case of an anonymous function
expression occurring in an expression statement:
```fun () {};```

#### Answer:

##### Implementation Details:
1. **AST Representation (`com/craftinginterpreters/lox/Expr.java`)**:
   Added `Expr.Function(List<Token> params, List<Stmt> body)`.
2. **Parser Expression Rule (`com/craftinginterpreters/lox/Parser.java`)**:
   Updated `primary()`: when `match(FUN)` is encountered, it consumes `(`, parses parameter tokens, consumes `)`, parses the `{ ... }` block, and returns an `Expr.Function`.
3. **Handling `fun () {};` in Expression Statements**:
   The tricky case arises in `declaration()` where a line starting with `fun` could be either a named function declaration statement (`fun foo() {}`) or an anonymous function expression statement (`fun () {};`).
   To disambiguate, in `declaration()`:
   ```java
   if (check(FUN) && checkNext(IDENTIFIER)) {
     advance();
     return function("function");
   }
   ```
   If `FUN` is followed by an `IDENTIFIER`, it is parsed as a named function declaration statement (`Stmt.Function`). If `FUN` is **not** followed by an identifier (e.g. `fun (`), `declaration()` leaves `FUN` unconsumed and falls through to `statement()` -> `expressionStatement()`. `expressionStatement()` parses `expression()` -> `primary()` consumes `FUN`, creates `Expr.Function`, consumes `;`, and returns `Stmt.Expression(Expr.Function)`.
4. **Interpreter Evaluation (`com/craftinginterpreters/lox/Interpreter.java` & `LoxFunction.java`)**:
   - `visitFunctionExpr` constructs a synthetic `Stmt.Function(null, params, body)` and wraps it in a `LoxFunction`.
   - Updated `LoxFunction.toString()` to handle `declaration.name == null`, returning `"<fn>"`.
5. **Static Resolution (`com/craftinginterpreters/lox/Resolver.java`)**:
   Implemented `visitFunctionExpr()` to push a new scope, bind function parameters, resolve body statements, and restore `currentFunction` state.

### 3.
Is this program valid?
```
fun scope(a) {
var a = "local";
}
```
In other words, are a function’s parameters in the same scope as its
local variables, or in an outer scope? What does Lox do? What about
other languages you are familiar with? What do you think a language
should do?

#### Answer:

##### 1. What Lox Does:
- **At Chapter 10 (Dynamic Environments)**:
  Lox creates an environment for the function parameters and a separate nested child environment for the body block `{ ... }`. Evaluating `var a = "local";` defines a new variable `a` in the inner block environment that silently shadows the parameter `a`. Thus, the program runs without errors.
- **At Chapter 11 (Static Resolver)**:
  When the static resolver is added, function parameters and the top-level statements of the function body share the same scope. `Resolver.java` reports a compile-time error: *"Already a variable with this name in this scope."*

##### 2. How Other Languages Handle This:
- **Java / C / C++ / C#**:
  Triggers a **compile-time error** (`redefinition of 'a'` / `Variable 'a' is already defined in the scope`). Parameters and top-level body variables occupy the same method scope.
- **JavaScript (`let` / `const`)**:
  Triggers a **SyntaxError** (`Identifier 'a' has already been declared`). `let` and `const` disallow shadowing parameters at the top level of a function body.
- **Python**:
  Parameters and function-local variables share the same `locals()` scope dictionary. Reassigning `a = "local"` mutates the parameter variable `a` in place; it does not throw an error or create a shadow binding.

##### 3. What a Language Should Do:
A language **should treat parameters and function body variables as part of the same scope and raise a compile-time error upon redeclaration**.
Declaring a local variable with the same name as a parameter makes the argument passed into the function immediately inaccessible without ever being read. This is almost universally a programmer mistake (a typo or misunderstanding of variable names). Catching it statically at compile time prevents subtle bugs.

## Resolving and Binding

### 1.
Why is it safe to eagerly define the variable bound to a function’s
name when other variables must wait until after they are initialized
before they can be used?

### 2.
How do other languages you know handle local variables that refer to
the same name in their initializer, like:
```
var a = "outer";
{
var a = a;
}
Is it a runtime error? Compile error? Allowed? Do they treat global
variables differently? Do you agree with their choices? Justify your
answer.
```

### 3.
Extend the resolver to report an error if a local variable is never used.

### 4.
Our resolver calculates which environment the variable is found in, but
it’s still looked up by name in that map. A more efficient environment
representation would store local variables in an array and look them up
by index.
Extend the resolver to associate a unique index for each local variable
declared in a scope. When resolving a variable access, look up both the
scope the variable is in and its index and store that. In the interpreter,
use that to quickly access a variable by its index instead of using a
map.

## Classes

### 1.
We have methods on instances, but there is no way to define “static”
methods that can be called directly on the class object itself. Add
support for them. Use a class keyword preceding the method to
indicate a static method that hangs off the class object.
```
class Math {
class square(n) {
return n * n;
}
}
print Math.square(3); // Prints "9".
```
You can solve this however you like, but the “metaclasses” used by
Smalltalk and Ruby are a particularly elegant approach. Hint: Make
LoxClass extend LoxInstance and go from there.

### 2. 
Most modern languages support “getters” and “setters”—members on
a class that look like field reads and writes but that actually execute
user-defined code. Extend Lox to support getter methods. These are
declared without a parameter list. The body of the getter is executed
when a property with that name is accessed.
```
class Circle {
init(radius) {
this.radius = radius;
}
area {
return 3.141592653 * this.radius * this.radius;
}
}
var circle = Circle(4);
print circle.area; // Prints roughly "50.2655".
```

### 3.
Python and JavaScript allow you to freely access an object’s fields
from outside of its own methods. Ruby and Smalltalk encapsulate
instance state. Only methods on the class can access the raw fields, and
it is up to the class to decide which state is exposed. Most statically
typed languages offer modifiers like private and public to control
which parts of a class are externally accessible on a per-member basis.
What are the trade-offs between these approaches and why might a
language prefer one or the other?

## Inheritance

### 1.
Lox supports only single inheritance—a class may have a single
superclass and that’s the only way to reuse methods across classes.
Other languages have explored a variety of ways to more freely reuse
and share capabilities across classes: mixins, traits, multiple
inheritance, virtual inheritance, extension methods, etc.
If you were to add some feature along these lines to Lox, which would
you pick and why? If you’re feeling courageous (and you should be at
this point), go ahead and add it.

### 2.
In Lox, as in most other object-oriented languages, when looking up a
method, we start at the bottom of the class hierarchy and work our way
up—a subclass’s method is preferred over a superclass’s. In order to
get to the superclass method from within an overriding method, you
use super.
The language BETA takes the opposite approach. When you call a
method, it starts at the top of the class hierarchy and works down. A
superclass method wins over a subclass method. In order to get to the
subclass method, the superclass method can call inner, which is sort
of like the inverse of super. It chains to the next method down the
hierarchy.
The superclass method controls when and where the subclass is
allowed to refine its behavior. If the superclass method doesn’t call
inner at all, then the subclass has no way of overriding or modifying
the superclass’s behavior.
Take out Lox’s current overriding and super behavior and replace it
with BETA’s semantics. In short:
When calling a method on a class, prefer the method highest on
the class’s inheritance chain.
Inside the body of a method, a call to inner looks for a method
with the same name in the nearest subclass along the inheritance
chain between the class containing the inner and the class of
this. If there is no matching method, the inner call does nothing.
For example:
```
class Doughnut {
cook() {
print "Fry until golden brown.";
inner();
print "Place in a nice box.";
}
}
class BostonCream < Doughnut {
cook() {
print "Pipe full of custard and coat with chocolate.";
}
}
BostonCream().cook();
```

This should print:
```
Fry until golden brown.
Pipe full of custard and coat with chocolate.
Place in a nice box.
```

### 3.
In the chapter where I introduced Lox, I challenged you to come up
with a couple of features you think the language is missing. Now that
you know how to build an interpreter, implement one of those features.

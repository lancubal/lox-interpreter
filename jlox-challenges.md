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

### 3.
What happens right now if you divide a number by zero? What do you
think should happen? Justify your choice. How do other languages you
know handle division by zero, and why do they make the choices they
do?
Change the implementation in visitBinaryExpr() to detect and report
a runtime error for this case

## Statements and State

### 1.
The REPL no longer supports entering a single expression and
automatically printing its result value. That’s a drag. Add support to
the REPL to let users type in both statements and expressions. If they
enter a statement, execute it. If they enter an expression, evaluate it
and display the result value.

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

## Control Flow

### 1.
A few chapters from now, when Lox supports first-class functions and
dynamic dispatch, we technically won’t need branching statements
built into the language. Show how conditional execution can be
implemented in terms of those. Name a language that uses this
technique for its control flow.

### 2.
Likewise, looping can be implemented using those same tools,
provided our interpreter supports an important optimization. What is it,
and why is it necessary? Name a language that uses this technique for
iteration.

### 3.
Unlike Lox, most other C-style languages also support break and
continue statements inside loops. Add support for break statements.
The syntax is a break keyword followed by a semicolon. It should be a
syntax error to have a break statement appear outside of any enclosing
loop. At runtime, a break statement causes execution to jump to the
end of the nearest enclosing loop and proceeds from there. Note that
the break may be nested inside other blocks and if statements that
also need to be exited.

## Functions

### 1.
Our interpreter carefully checks that the number of arguments passed
to a function matches the number of parameters it expects. Since this
check is done at runtime on every call, it has a performance cost.
Smalltalk implementations don’t have that problem. Why not?

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

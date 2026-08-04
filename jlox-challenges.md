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
Aside from separating tokens—distinguishing print foo from
printfoo—spaces aren’t used for much in most languages. However,
in a couple of dark corners, a space does affect how code is parsed in
CoffeeScript, Ruby, and the C preprocessor. Where and what effect
does it have in each of those languages?

### 3. 
Our scanner here, like most, discards comments and whitespace since
those aren’t needed by the parser. Why might you want to write a
scanner that does not discard those? What would it be useful for?

### 4. 
Add support to Lox’s scanner for C-style /* ... */ block comments.
Make sure to handle newlines in them. Consider allowing them to nest.
Is adding support for nesting more work than you expected? Why?

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

### 2.
The Visitor pattern lets you emulate the functional style in an object-
oriented language. Devise a complementary pattern for a functional
language. It should let you bundle all of the operations on one type
together and let you define new types easily.
(SML or Haskell would be ideal for this exercise, but Scheme or
another Lisp works as well.)

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

### 2.
Likewise, add support for the C-style conditional or “ternary” operator
?:. What precedence level is allowed between the ? and :? Is the
whole operator left-associative or right-associative?

### 3.
Add error productions to handle each binary operator appearing
without a left-hand operand. In other words, detect a binary operator
appearing at the beginning of an expression. Report that as an error,
but also parse and discard a right-hand operand with the appropriate
precedence

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

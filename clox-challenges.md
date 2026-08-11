# CLox Challenges

## Chunks of Bytecode

### 1.
Our encoding of line information is hilariously wasteful of memory.
Given that a series of instructions often correspond to the same source
line, a natural solution is something akin to run-length encoding of the
line numbers.
Devise an encoding that compresses the line information for a series of
instructions on the same line. Change `writeChunk()` to write this
compressed form, and implement a `getLine()` function that, given the
index of an instruction, determines the line where the instruction
occurs.
Hint: It’s not necessary for `getLine()` to be particularly efficient.
Since it is called only when a runtime error occurs, it is well off the
critical path where performance matters.

#### Answer:

##### Implementation Details:
1. **Run-Length Encoding (RLE) Data Structure (`clox/chunk.h`)**:
   Defined a `LineStart` struct and updated `Chunk` to store a dynamic array of `LineStart` entries instead of a flat parallel `int* lines` array:
   ```c
   typedef struct {
     int line;
     int count;
   } LineStart;

   typedef struct {
     int count;
     int capacity;
     uint8_t *code;
     int lineCount;
     int lineCapacity;
     LineStart *lines;
     ValueArray constants;
   } Chunk;
   ```
2. **Writing RLE Lines (`clox/chunk.c`)**:
   In `writeChunk()`, when a bytecode byte is appended, we check if the new byte belongs to the same line as the previous byte (`chunk->lines[chunk->lineCount - 1].line == line`). If so, we simply increment the `count` of the last `LineStart` entry. Otherwise, we allocate and append a new `LineStart` entry.
3. **Instruction Line Lookup (`clox/chunk.c`)**:
   Implemented `getLine(Chunk* chunk, int instructionIndex)`:
   ```c
   int getLine(Chunk *chunk, int instructionIndex) {
     int accumulated = 0;
     for (int i = 0; i < chunk->lineCount; i++) {
       accumulated += chunk->lines[i].count;
       if (instructionIndex < accumulated) {
         return chunk->lines[i].line;
       }
     }
     return -1;
   }
   ```
4. **Disassembler & VM Updates (`clox/debug.c` & `clox/vm.c`)**:
   Updated `disassembleInstruction()` and VM error reporting to retrieve line numbers via `getLine(chunk, offset)`.

---

### 2.
Because `OP_CONSTANT` uses only a single byte for its operand, a chunk
may only contain up to 256 different constants. That’s small enough
that people writing real-world code will hit that limit. We could use
two or more bytes to store the operand, but that makes every constant
instruction take up more space. Most chunks won’t need that many
unique constants, so that wastes space and sacrifices some locality in
the common case to support the rare case.
To balance those two competing aims, many instruction sets feature
multiple instructions that perform the same operation but with
operands of different sizes. Leave our existing one-byte `OP_CONSTANT`
instruction alone, and define a second `OP_CONSTANT_LONG` instruction.
It stores the operand as a 24-bit number, which should be plenty.
Implement this function:
```c
void writeConstant(Chunk* chunk, Value value, int line) {
  // Implement me...
}
```
It adds value to chunk’s constant array and then writes an appropriate
instruction to load the constant. Also add support to the disassembler
for `OP_CONSTANT_LONG` instructions.
Defining two instructions seems to be the best of both worlds. What
sacrifices, if any, does it force on us?

#### Answer:

##### Implementation Details:
1. **Opcode & Function Signature (`clox/chunk.h` & `clox/chunk.c`)**:
   Added `OP_CONSTANT_LONG` to `OpCode` enum and implemented `writeConstant()`:
   ```c
   void writeConstant(Chunk *chunk, Value value, int line) {
     int index = addConstant(chunk, value);
     if (index < 256) {
       writeChunk(chunk, OP_CONSTANT, line);
       writeChunk(chunk, (uint8_t)index, line);
     } else {
       writeChunk(chunk, OP_CONSTANT_LONG, line);
       writeChunk(chunk, (uint8_t)(index & 0xff), line);
       writeChunk(chunk, (uint8_t)((index >> 8) & 0xff), line);
       writeChunk(chunk, (uint8_t)((index >> 16) & 0xff), line);
     }
   }
   ```
2. **Disassembler (`clox/debug.c`)**:
   Implemented `longConstantInstruction()`, which reconstructs the 24-bit operand from 3 little-endian bytes:
   ```c
   static int longConstantInstruction(const char *name, Chunk *chunk, int offset) {
     uint32_t constant = chunk->code[offset + 1] |
                         (chunk->code[offset + 2] << 8) |
                         (chunk->code[offset + 3] << 16);
     printf("%-16s %4d '", name, constant);
     printValue(chunk->constants.values[constant]);
     printf("'\n");
     return offset + 4;
   }
   ```
3. **VM Execution (`clox/vm.c`)**:
   Added `READ_24BIT()` macro and handled `case OP_CONSTANT_LONG:` in the VM `run()` loop.

##### Sacrifices and Trade-offs:
1. **Opcode Slot Consumption**: Virtual machines use 1-byte opcodes (providing up to 256 unique instructions). Adding `OP_CONSTANT_LONG` uses an opcode slot that could otherwise be dedicated to another instruction.
2. **Increased Compiler and VM Complexity**: Code paths throughout the compiler, disassembler, and interpreter loop must handle two variations of constant loading logic instead of one unified path.
3. **Instruction Cache & Branch Prediction Overhead**: Enlarging the `switch (instruction)` statement in the core VM loop increases CPU instruction cache pressure and expands the branch target buffer/table size.

---

### 3.
Our `reallocate()` function relies on the C standard library for
dynamic memory allocation and freeing. `malloc()` and `free()` aren’t
magic. Find a couple of open source implementations of them and
explain how they work. How do they keep track of which bytes are
allocated and which are free? What is required to allocate a block of
memory? Free it? How do they make that efficient? What do they do
about fragmentation?
Hardcore mode: Implement `reallocate()` without calling `realloc()`,
`malloc()`, or `free()`. You are allowed to call `malloc()` once, at the
beginning of the interpreter’s execution, to allocate a single big block
of memory, which your `reallocate()` function has access to. It
parcels out blobs of memory from that single region, your own
personal heap. It’s your job to define how it does that.

#### Answer:

##### Part 1: How Open Source `malloc()` / `free()` Implementations Work (`dlmalloc`, `ptmalloc`, `jemalloc`, `tcmalloc`)

1. **Tracking Allocated vs Free Memory**:
   - **Boundary Tags and Headers**: Allocators like `dlmalloc` and `ptmalloc` prepend a hidden header (e.g. 8–16 bytes) before each allocated chunk. The header stores the total chunk size and flags (e.g., `INUSE_BIT`). Adjacent physical chunks use boundary tags so a freed chunk can inspect its left and right neighbors instantly.
   - **Size-Segregated Arenas & Slab Pages (`jemalloc` / `tcmalloc`)**: Instead of placing headers on small individual objects, memory is carved into fixed-size pages (slabs) dedicated to uniform object sizes (e.g., 16B, 32B, 64B slabs). Allocations use bitmasks on the slab page header, eliminating per-object header overhead.

2. **Allocating Memory (`malloc`)**:
   - **Fast Path**: Look up the requested size in a segregated array of size bins or thread-local caches (`tcache` in `jemalloc`/`tcmalloc`). If a free block exists in that bin, pop it from the free list in $O(1)$ constant time without lock contention.
   - **Slow Path**: If the bin is empty, request a new memory arena/page from the operating system via `mmap()` or `sbrk()`, split off the requested size, and insert the remainder into the appropriate free bin.

3. **Freeing Memory (`free`)**:
   - Given a pointer `ptr`, compute the header location via pointer arithmetic (`((Header*)ptr) - 1`).
   - Clear the in-use flag (or clear the bit in the slab bitmap).
   - **Coalescing**: Inspect adjacent physical neighbors. If a neighbor is also free, merge them into a single larger free block to prevent fragmentation.
   - Return the block to the corresponding size bin or thread-local cache.

4. **Efficiency Optimizations**:
   - **Thread-Local Caching (`tcmalloc`/`jemalloc`)**: Thread-private allocation pools avoid multi-threading mutex locks on hot allocation paths.
   - **Binning / Segregated Free Lists**: Speeds up free block discovery to $O(1)$ array lookup rather than searching long linked lists.

5. **Handling Fragmentation**:
   - **Internal Fragmentation**: Wasted padding inside blocks is minimized by using closely spaced size classes.
   - **External Fragmentation**: Solved via immediate/deferred **coalescing** of adjacent free blocks and returning unused pages back to the OS via `madvise(MADV_DONTNEED)` or `munmap()`.

---

##### Part 2: Hardcore Mode Implementation (`clox/memory.c`)

We implemented **Hardcore Mode** by building a custom memory pool allocator in `clox/memory.c`:

1. **Heap Initialization (`initCustomHeap()`)**:
   Calls `malloc()` **only once** during `initVM()` to reserve a single contiguous 16 MB heap buffer (`CUSTOM_HEAP_SIZE = 16 * 1024 * 1024`).
2. **Block Header Structure**:
   ```c
   typedef struct BlockHeader {
     size_t size;
     bool isFree;
     struct BlockHeader *next;
   } BlockHeader;
   ```
3. **Allocation & Splitting (`customAlloc()`)**:
   Searches `heapStart` for a free block using First-Fit. If `block->size >= requested_size + sizeof(BlockHeader) + 16`, it splits the block, creating a new remainder free block.
4. **Freeing & Coalescing (`customFree()`)**:
   Marks `block->isFree = true` and traverses adjacent blocks to merge (`coalesceHeap()`) neighboring free blocks into larger contiguous regions.
5. **Runtime Independence**:
   `clox` executes with zero runtime calls to standard C `malloc()`, `realloc()`, or `free()`. All dynamic memory management is performed internally by `reallocate()` within our pre-allocated 16 MB heap.

---

## A Virtual Machine

### 1.
What bytecode instruction sequences would you generate for the
following expressions:
```lox
1 * 2 + 3
1 + 2 * 3
3 - 2 - 1
1 + 2 * 3 - 4 / -5
```
(Remember that Lox does not have a syntax for negative number
literals, so the `-5` is negating the number `5`.)

#### Answer:

1. **`1 * 2 + 3`**:
   ```
   OP_CONSTANT 0  (1)
   OP_CONSTANT 1  (2)
   OP_MULTIPLY
   OP_CONSTANT 2  (3)
   OP_ADD
   ```

2. **`1 + 2 * 3`**:
   ```
   OP_CONSTANT 0  (1)
   OP_CONSTANT 1  (2)
   OP_CONSTANT 2  (3)
   OP_MULTIPLY
   OP_ADD
   ```

3. **`3 - 2 - 1`**:
   ```
   OP_CONSTANT 0  (3)
   OP_CONSTANT 1  (2)
   OP_SUBTRACT
   OP_CONSTANT 2  (1)
   OP_SUBTRACT
   ```

4. **`1 + 2 * 3 - 4 / -5`**:
   ```
   OP_CONSTANT 0  (1)
   OP_CONSTANT 1  (2)
   OP_CONSTANT 2  (3)
   OP_MULTIPLY
   OP_ADD
   OP_CONSTANT 3  (4)
   OP_CONSTANT 4  (5)
   OP_NEGATE
   OP_DIVIDE
   OP_SUBTRACT
   ```

---

### 2.
If we really wanted a minimal instruction set, we could eliminate either
`OP_NEGATE` or `OP_SUBTRACT`. Show the bytecode instruction sequence
you would generate for:
```lox
4 - 3 * -2
```
First, without using `OP_NEGATE`. Then, without using `OP_SUBTRACT`.
Given the above, do you think it makes sense to have both instructions?
Why or why not? Are there any other redundant instructions you would
consider including?

#### Answer:

##### 1. Bytecode without `OP_NEGATE`:
Negating a value `x` without `OP_NEGATE` is performed as `0 - x`:
```
OP_CONSTANT 0  (4)
OP_CONSTANT 1  (3)
OP_CONSTANT 2  (0)
OP_CONSTANT 3  (2)
OP_SUBTRACT     (0 - 2 = -2)
OP_MULTIPLY     (3 * -2 = -6)
OP_SUBTRACT     (4 - -6 = 10)
```

##### 2. Bytecode without `OP_SUBTRACT`:
Subtraction `a - b` without `OP_SUBTRACT` is performed as `a + (-b)`:
```
OP_CONSTANT 0  (4)
OP_CONSTANT 1  (3)
OP_CONSTANT 2  (2)
OP_NEGATE       (-2)
OP_MULTIPLY     (3 * -2 = -6)
OP_NEGATE       (-(-6) = 6)
OP_ADD          (4 + 6 = 10)
```

##### 3. Does it make sense to have both instructions?
**Yes, absolutely.**
- **Code Size and Locality**: Omitting `OP_NEGATE` forces the compiler to push a constant `0` and emit `OP_SUBTRACT` (3 instructions / bytes instead of 1).
- **Execution Performance**: Subtraction and negation are among the most frequent arithmetic operations. Emulating one with the other introduces extra stack push/pop operations and VM instruction dispatch overhead.

##### 4. Other Useful "Redundant" Instructions:
- **`OP_INC` / `OP_DEC`**: Incrementing or decrementing a variable by 1 (e.g. `i = i + 1` in loops). A dedicated opcode avoids pushing constant `1` and executing `OP_ADD`.
- **`OP_ZERO` / `OP_ONE`**: Direct loading of common integer constants `0` and `1` without consuming space in the constant table array.
- **`OP_NOT_EQUAL` (`!=`)**: Replaces `OP_EQUAL` + `OP_NOT`.
- **`OP_GREATER_EQUAL` (`>=`) / `OP_LESS_EQUAL` (`<=`)**: Replaces `OP_LESS` + `OP_NOT` or `OP_GREATER` + `OP_NOT`, accelerating loop condition evaluations.

---

### 3.
Our VM’s stack has a fixed size, and we don’t check if pushing a value
overflows it. This means the wrong series of instructions could cause
our interpreter to crash or go into undefined behavior. Avoid that by
dynamically growing the stack as needed.
What are the costs and benefits of doing so?

#### Answer:

##### Implementation Details:
1. **Dynamic Stack Declaration (`clox/vm.h`)**:
   Replaced fixed array `Value stack[STACK_MAX]` with a dynamic pointer `Value *stack` and `int stackCapacity`.
2. **Growing and Pointer Re-basing (`clox/vm.c`)**:
   In `push()`, before pushing a new value, we verify if `stackTop - stack >= stackCapacity`. If full, we grow `stackCapacity` using `GROW_ARRAY()`.
   **Critical Pointer Maintenance**: Because `realloc()` may relocate `vm.stack` in memory, we record relative offset distances (`stackTopOffset` and `vm.frames[i].slots` offsets) before reallocation and restore them afterwards to prevent dangling pointers:
   ```c
   void push(Value value) {
     int currentCount = (int)(vm.stackTop - vm.stack);
     if (currentCount >= vm.stackCapacity) {
       int oldCapacity = vm.stackCapacity;
       vm.stackCapacity = GROW_CAPACITY(oldCapacity);

       int stackTopOffset = (int)(vm.stackTop - vm.stack);
       int frameSlotOffsets[FRAMES_MAX];
       for (int i = 0; i < vm.frameCount; i++) {
         frameSlotOffsets[i] = (int)(vm.frames[i].slots - vm.stack);
       }

       vm.stack = GROW_ARRAY(Value, vm.stack, oldCapacity, vm.stackCapacity);

       vm.stackTop = vm.stack + stackTopOffset;
       for (int i = 0; i < vm.frameCount; i++) {
         vm.frames[i].slots = vm.stack + frameSlotOffsets[i];
       }
     }

     *vm.stackTop = value;
     vm.stackTop++;
   }
   ```

##### Benefits:
- **Memory Safety & Robustness**: Eliminates stack overflow crashes, undefined behavior, and segmentation faults when running heavily nested operations or recursive functions.
- **Resource Efficiency**: Programs start with a minimal memory footprint and scale dynamically as needed.

##### Costs:
- **`push()` Check Overhead**: Every `push()` invocation incurs a capacity comparison check.
- **Reallocation Complexity**: Reallocating the stack array requires re-basing all pointers (`vm.stackTop` and `CallFrame.slots`) that point into the stack buffer.

---

### 4.
To interpret `OP_NEGATE`, we pop the operand, negate the value, and then
push the result. That’s a simple implementation, but it increments and
decrements `stackTop` unnecessarily, since the stack ends up the same
height in the end. It might be faster to simply negate the value in place
on the stack and leave `stackTop` alone. Try that and see if you can
measure a performance difference.
Are there other instructions where you can do a similar optimization?

#### Answer:

##### 1. In-Place Negation Implementation (`clox/vm.c`):
Replaced the `pop()` + `push()` cycle with direct top-of-stack mutation:
```c
case OP_NEGATE:
  if (!IS_NUMBER(peek(0))) {
    runtimeError("Operand must be a number.");
    return INTERPRET_RUNTIME_ERROR;
  }
  vm.stackTop[-1] = NUMBER_VAL(-AS_NUMBER(vm.stackTop[-1]));
  break;
```

##### 2. Performance Impact Analysis:
- **Avoids Stack Pointer Updates**: Eliminates `stackTop--` followed immediately by `stackTop++`.
- **Bypasses Dynamic Push Checks**: Skips capacity bound comparisons and conditional branches inside `push()`.
- **Improves CPU Cache Locality**: Mutates `vm.stackTop[-1]` directly in L1 data cache without intermediate register spill and push operations.

##### 3. Other Instructions Suited for In-Place Optimizations:
1. **`OP_NOT` (`!x`)**:
   Replaces `push(BOOL_VAL(isFalsey(pop())))` with:
   ```c
   case OP_NOT:
     vm.stackTop[-1] = BOOL_VAL(isFalsey(vm.stackTop[-1]));
     break;
   ```
2. **Binary Arithmetic Operators (`OP_ADD`, `OP_SUBTRACT`, `OP_MULTIPLY`, `OP_DIVIDE`)**:
   Instead of `pop()`, `pop()`, `push()` (3 stack pointer writes and a capacity check), we compute `a op b` directly into `vm.stackTop[-2]` and decrement `vm.stackTop--` **once**:
   ```c
   #define BINARY_OP(valueType, op) \
     do { \
       if (!IS_NUMBER(peek(0)) || !IS_NUMBER(peek(1))) { \
         runtimeError("Operands must be numbers."); \
         return INTERPRET_RUNTIME_ERROR; \
       } \
       double b = AS_NUMBER(vm.stackTop[-1]); \
       double a = AS_NUMBER(vm.stackTop[-2]); \
       vm.stackTop[-2] = valueType(a op b); \
       vm.stackTop--; \
     } while (false)
   ```
3. **Comparison Operators (`OP_EQUAL`, `OP_GREATER`, `OP_LESS`)**:
   Evaluates comparison between `vm.stackTop[-2]` and `vm.stackTop[-1]`, writes the `bool` result directly into `vm.stackTop[-2]`, and decrements `vm.stackTop--` once.

---

## Scanning on Demand

### 1.
Many newer languages support string interpolation. Inside a string
literal, you have some sort of special delimiters—most commonly `${`
at the beginning and `}` at the end. Between those delimiters, any
expression can appear. When the string literal is executed, the inner
expression is evaluated, converted to a string, and then merged with
the surrounding string literal.

For example, if Lox supported string interpolation, then this:
```lox
var drink = "Tea";
var steep = 4;
var cool = 2;
print "${drink} will be ready in ${steep + cool} minutes.";
```
would print: `Tea will be ready in 6 minutes.`

What token types would you define to implement a scanner for string
interpolation? What sequence of tokens would you emit for the above
string literal?

What tokens would you emit for:
`"Nested ${"interpolation?! Are you ${"mad?!"}"}"`

Consider looking at other language implementations that support
interpolation to see how they handle it.

#### Answer:

##### 1. Token Types Required:
To support string interpolation, a scanner divides interpolated strings into three segmented token types (alongside standard `TOKEN_STRING` for non-interpolated strings):
- **`TOKEN_STRING_HEAD`**: String segment from opening `"` up to the first `${`.
- **`TOKEN_STRING_MIDDLE`**: String segment from a closing `}` of an interpolated expression up to the next `${`.
- **`TOKEN_STRING_TAIL`**: String segment from the closing `}` of the final interpolated expression up to the closing `"`.

##### 2. Token Sequence for `"${drink} will be ready in ${steep + cool} minutes."`:
```
TOKEN_STRING_HEAD    ""                          (Initial empty head before ${drink})
TOKEN_IDENTIFIER     "drink"
TOKEN_STRING_MIDDLE  " will be ready in "        (Text between first } and second ${)
TOKEN_IDENTIFIER     "steep"
TOKEN_PLUS           "+"
TOKEN_IDENTIFIER     "cool"
TOKEN_STRING_TAIL    " minutes."                 (Final tail from second } to ending ")
```

##### 3. Token Sequence for `"Nested ${"interpolation?! Are you ${"mad?!"}"}"`:
Tracing nested levels via a scanner brace stack:
```
TOKEN_STRING_HEAD    "Nested "                   (Outer string head)
TOKEN_STRING_HEAD    "interpolation?! Are you "  (Level 1 nested string head)
TOKEN_STRING         "mad?!"                     (Level 2 inner plain string literal)
TOKEN_STRING_TAIL    ""                          (Level 1 nested empty string tail)
TOKEN_STRING_TAIL    ""                          (Outer empty string tail)
```

##### 4. Scanner Implementation & Nesting Stack:
Real-world languages (e.g. Swift, ES6 template literals, Kotlin, Ruby) manage interpolation by maintaining a **Scanner State Stack**:
- When encountering `${` inside a string, push `STATE_INTERPOLATION` onto the scanner state stack and switch the scanner to normal expression lexing.
- Track `{` and `}` in normal code to match nested blocks.
- When popping a `}` that corresponds to `STATE_INTERPOLATION`, switch the scanner back to string mode to scan `TOKEN_STRING_MIDDLE` or `TOKEN_STRING_TAIL`.

---

### 2.
Several languages use angle brackets for generics and also have a `>>`
right shift operator. This led to a classic problem in early versions of
C++:
```cpp
vector<vector<string>> nestedVectors;
```
This would produce a compile error because the `>>` was lexed to a
single right shift token, not two `>` tokens. Users were forced to avoid
this by putting a space between the closing angle brackets.

Later versions of C++ are smarter and can handle the above code. Java
and C# never had the problem. How do those languages specify and
implement this?

#### Answer:

##### 1. Cause of the Problem (Maximal Munch Principle):
Scanners follow the **Maximal Munch Rule** (Longest Match Rule). When reading `>>`, a standard independent lexer greedily matches the 2-character right-shift token (`TOKEN_RIGHT_SHIFT`) rather than two 1-character tokens (`>`). In C++03, lexing was strictly decoupled from parsing, resulting in a syntax error when the parser received a right-shift token inside template closing brackets `vector<vector<string>>`.

##### 2. Java and C# Specification and Implementation:
Java (JLS §3.5) and C# never suffered from this issue because their language specifications explicitly allow generic type parsing to drive token splitting:
- **Parser-Driven Token Splitting**: In Java and C#, the lexer emits `>>` or `>>>` as usual. However, when the parser is inside a generic type parameter context and encounters a `>>` token:
  1. The parser consumes the first `>` character to close the inner generic type parameter.
  2. The parser splits the `>>` token, leaving the remaining `>` character in the token stream to close the outer generic type parameter.

##### 3. Modern C++ (C++11 DR 175) Solution:
C++11 revised the language grammar rule (CWG 175):
- In template argument lists, any `>` character that would otherwise form a `>>` right-shift operator is treated as two distinct `>` tokens, **unless** the expression is explicitly enclosed in parentheses `vector<int<(1 >> 2)>>`.
- **Implementation Mechanism**: The front-end tracks `template_nesting_depth`. When `template_nesting_depth > 0` and no parentheses enclose the expression, the lexer/parser automatically decomposes `>>` into two `>` tokens.

---

### 3.
Many languages, especially later in their evolution, define “contextual
keywords”. These are identifiers that act like reserved words in some
contexts but can be normal user-defined identifiers in others.

For example, `await` is a keyword inside an `async` method in C#, but in
other methods, you can use `await` as your own identifier.

Name a few contextual keywords from other languages, and the
context where they are meaningful. What are the pros and cons of
having contextual keywords? How would you implement them in your
language’s front end if you needed to?

#### Answer:

##### 1. Examples of Contextual Keywords in Modern Languages:
- **C#**:
  - `await`: Meaningful only inside methods declared with `async`.
  - `get` / `set` / `value`: Meaningful only inside property declarations (`int Age { get; set; }`).
  - `where` / `select` / `from`: Meaningful only inside LINQ query expressions.
  - `var`: Contextual keyword for local variable type inference.
- **Python (Soft Keywords)**:
  - `match` / `case`: Introduced in Python 3.10 for pattern matching. Outside `match` blocks, `match` and `case` remain valid variable names.
  - `type`: Introduced in Python 3.12 for type alias statements (`type Point = tuple[float, float]`).
- **Java**:
  - `var`: Introduced in Java 10 (`var x = 10;`). `var var = 5;` is legal Java code.
  - `record` / `sealed` / `non-sealed` / `permits` / `yield`: Introduced in Java 14–17 for records, sealed classes, and switch expressions.
- **Kotlin**:
  - `it`: Implicit parameter in single-argument lambdas.
  - `field`: Accesses property backing fields inside getters/setters.
- **Swift**:
  - `mutating` / `override` / `convenience` / `willSet` / `didSet`: Meaningful only in declaration modifiers and property observers.

##### 2. Pros and Cons of Contextual Keywords:

###### Pros:
1. **Backward Compatibility**: Allows language designers to introduce major new features (like `async`/`await` or pattern matching) into mature languages without breaking existing codebase repositories that already use those words as identifiers.
2. **Uncluttered Reserved Keyword List**: Preserves common English nouns/verbs for programmers to use as variable names.

###### Cons:
1. **Increased Front-End Complexity**: Tokenization and parsing become context-dependent rather than context-free.
2. **Readability & Tooling Ambiguity**: IDE syntax highlighters and human programmers can find constructs like `var var = var;` or `await(await)` confusing to read.
3. **Degraded Error Recovery**: When syntax errors occur near contextual keywords, the parser struggles to determine whether the user intended an identifier or a keyword.

##### 3. Implementation Strategies in the Front-End:

- **Strategy A: Scanner Emits `TOKEN_IDENTIFIER`, Parser Inspects Lexemes (Recommended)**:
  The scanner treats all contextual keywords as plain `TOKEN_IDENTIFIER` tokens. When the parser enters a specific grammar production (e.g. `parseAsyncMethod()`), it inspects whether the current token is an identifier matching the string `"await"`:
  ```c
  if (check(TOKEN_IDENTIFIER) && matchLexeme("await")) {
    // Parse as await expression
  }
  ```
  *Benefit*: Keeps the lexer 100% context-free and decoupled from parsing logic.

- **Strategy B: Parser-Driven Lexer State**:
  The parser toggles a boolean flag in the lexer when entering specific contexts (`lexer.inAsyncContext = true`). When enabled, the lexer checks its keyword hash table for `"await"` and emits `TOKEN_AWAIT`; when disabled, `"await"` scans as `TOKEN_IDENTIFIER`.

---

## Compiling Expressions

### 1.
To really understand the parser, you need to see how execution threads
through the interesting parsing functions—`parsePrecedence()` and
the parser functions stored in the table. Take this (strange) expression:
```lox
(-1 + 2) * 3 - -4
```
Write a trace of how those functions are called. Show the order they
are called, which calls which, and the arguments passed to them.

#### Answer:

##### Precedence Reference Levels:
- `PREC_ASSIGNMENT` (1)
- `PREC_TERM` (6) (`+`, `-`)
- `PREC_FACTOR` (7) (`*`, `/`)
- `PREC_UNARY` (8) (`!`, `-`)

##### Function Call Trace for `(-1 + 2) * 3 - -4`:

```
expression()
└── parsePrecedence(PREC_ASSIGNMENT = 1)
    ├── [sees TOKEN_LEFT_PAREN] -> calls prefix rule: grouping(canAssign = true)
    │   └── expression()
    │       └── parsePrecedence(PREC_ASSIGNMENT = 1)
    │           ├── [sees TOKEN_MINUS] -> calls prefix rule: unary(canAssign = true)
    │           │   └── parsePrecedence(PREC_UNARY = 8)
    │           │       ├── [sees TOKEN_NUMBER "1"] -> calls prefix rule: number(canAssign = false)
    │           │       │   └── emits OP_CONSTANT 1
    │           │       └── [returns to unary(), emits OP_NEGATE]
    │           ├── [sees TOKEN_PLUS] -> calls infix rule: binary(canAssign = true)
    │           │   └── parsePrecedence(PREC_TERM + 1 = PREC_FACTOR = 7)
    │           │       ├── [sees TOKEN_NUMBER "2"] -> calls prefix rule: number(canAssign = false)
    │           │       │   └── emits OP_CONSTANT 2
    │           │       └── [returns to binary(), emits OP_ADD]
    │           └── [sees TOKEN_RIGHT_PAREN, loop terminates]
    │   └── grouping() consumes TOKEN_RIGHT_PAREN
    ├── [sees TOKEN_STAR] -> calls infix rule: binary(canAssign = true)
    │   └── parsePrecedence(PREC_FACTOR + 1 = PREC_UNARY = 8)
    │       ├── [sees TOKEN_NUMBER "3"] -> calls prefix rule: number(canAssign = false)
    │       │   └── emits OP_CONSTANT 3
    │       └── [returns to binary(), emits OP_MULTIPLY]
    ├── [sees TOKEN_MINUS] -> calls infix rule: binary(canAssign = true)
    │   └── parsePrecedence(PREC_TERM + 1 = PREC_FACTOR = 7)
    │       ├── [sees TOKEN_MINUS] -> calls prefix rule: unary(canAssign = true)
    │       │   └── parsePrecedence(PREC_UNARY = 8)
    │       │       ├── [sees TOKEN_NUMBER "4"] -> calls prefix rule: number(canAssign = false)
    │       │       │   └── emits OP_CONSTANT 4
    │       │       └── [returns to unary(), emits OP_NEGATE]
    │       └── [returns to binary(), emits OP_SUBTRACT]
    └── [sees TOKEN_EOF, top-level parsePrecedence terminates]
```

##### Bytecode Emitted Sequence:
```
OP_CONSTANT 1  (1)
OP_NEGATE      (-1)
OP_CONSTANT 2  (2)
OP_ADD         (-1 + 2 = 1)
OP_CONSTANT 3  (3)
OP_MULTIPLY    (1 * 3 = 3)
OP_CONSTANT 4  (4)
OP_NEGATE      (-4)
OP_SUBTRACT    (3 - -4 = 7)
```

---

### 2.
The `ParseRule` row for `TOKEN_MINUS` has both prefix and infix function
pointers. That’s because `-` is both a prefix operator (unary negation)
and an infix one (subtraction).

In the full Lox language, what other tokens can be used in both prefix
and infix positions? What about in C or in another language of your
choice?

#### Answer:

##### 1. Dual Prefix/Infix Tokens in Full Lox:
- **`TOKEN_MINUS` (`-`)**:
  - **Prefix**: Unary negation (`-x`).
  - **Infix**: Subtraction (`a - b`).
- **`TOKEN_LEFT_PAREN` (`(`)**:
  - **Prefix**: Parenthesized grouping expression `(a + b)` via `grouping()`.
  - **Infix**: Function call operator `fn(arg1, arg2)` via `call()`.

##### 2. Dual Prefix/Infix Tokens in C and C++:
- **`*` (Asterisk)**:
  - **Prefix**: Pointer dereference (`*ptr`).
  - **Infix**: Binary multiplication (`a * b`).
- **`&` (Ampersand)**:
  - **Prefix**: Address-of operator (`&variable`).
  - **Infix**: Bitwise AND (`a & b`).
- **`+` (Plus)**:
  - **Prefix**: Unary positive identity (`+x`).
  - **Infix**: Binary addition (`a + b`).
- **`-` (Minus)**:
  - **Prefix**: Unary negation (`-x`).
  - **Infix**: Binary subtraction (`a - b`).
- **`(` (Left Parenthesis)**:
  - **Prefix**: Expression grouping `(a + b)` or Type casting `(int)x`.
  - **Infix**: Function call invocation `foo(x)`.
- **`[` (Left Bracket)** (C++11):
  - **Prefix**: Lambda capture specification `[x]() { ... }`.
  - **Infix**: Array subscripting `array[index]`.

##### 3. Dual Tokens in Go:
- **`<-` (Channel Operator)**:
  - **Prefix**: Channel receive operation (`val := <-ch`).
  - **Infix**: Channel send operation (`ch <- val`).

---

### 3.
You might be wondering about complex “mixfix” expressions that
have more than two operands separated by tokens. C’s conditional or
“ternary” operator, `?:`, is a widely known one.

Add support for that operator to the compiler. You don’t have to
generate any bytecode, just show how you would hook it up to the
parser and handle the operands.

#### Answer:

##### 1. New Token Types and Precedence Level:
Add `TOKEN_QUESTION` (`?`) and `TOKEN_COLON` (`:`) to `TokenType` in `scanner.h`.
Add `PREC_CONDITIONAL` precedence right above `PREC_ASSIGNMENT`:
```c
typedef enum {
  PREC_NONE,
  PREC_ASSIGNMENT,  // =
  PREC_CONDITIONAL, // ?:
  PREC_OR,          // or
  ...
} Precedence;
```

##### 2. Pratt Parse Table Registration (`compiler.c`):
Register `conditional()` as the infix parser function for `TOKEN_QUESTION`:
```c
[TOKEN_QUESTION] = {NULL, conditional, PREC_CONDITIONAL},
[TOKEN_COLON]    = {NULL, NULL,        PREC_NONE},
```

##### 3. Parsing Function `conditional()` (`compiler.c`):
When `?` is encountered, the left-hand condition expression has already been parsed and compiled. `conditional()` handles parsing the middle (`then`) and right (`else`) operand expressions:
```c
static void conditional(bool canAssign) {
  // 1. Parse the middle "then" expression:
  parsePrecedence(PREC_CONDITIONAL);

  // 2. Consume the required ':' delimiter token:
  consume(TOKEN_COLON, "Expect ':' after '?' in conditional expression.");

  // 3. Parse the right "else" expression:
  // (Using PREC_CONDITIONAL - 1 allows right-associativity for chained ternary operators)
  parsePrecedence(PREC_CONDITIONAL - 1);
}
```

##### 4. Control Flow Overview (If Bytecode Emitted):
- After condition: emit `OP_JUMP_IF_FALSE` to jump to the `else` branch if condition is falsey.
- After `then` expression: emit `OP_JUMP` to skip past the `else` branch.
- Patch jump offsets after both branches are compiled.

---

## Types of Values

### 1.
We could reduce our binary operators even further than we did here.
Which other instructions can you eliminate, and how would the compiler
cope with their absence?

#### Answer:

In Chapter 18, we already eliminated `!=`, `<=`, and `>=` by compiling them as:
- `a != b` → `a == b` followed by `OP_NOT`
- `a <= b` → `a > b` followed by `OP_NOT`
- `a >= b` → `a < b` followed by `OP_NOT`

We could reduce our binary instructions even further:

##### 1. Eliminate `OP_LESS` (`<`):
- **Equivalence**: `a < b` is identical to `b > a`.
- **Compiler Strategy**: When compiling `a < b`, the compiler parses `b` first, parses `a` second, and emits `OP_GREATER`.
- **Result**: We eliminate the `OP_LESS` instruction entirely from the VM instruction set.

##### 2. Eliminate `OP_SUBTRACT` (`-`):
- **Equivalence**: `a - b` is identical to `a + (-b)`.
- **Compiler Strategy**: When compiling `a - b`, the compiler parses `a`, parses `b`, emits `OP_NEGATE` for `b`, and then emits `OP_ADD`.
- **Result**: Removes `OP_SUBTRACT` from the VM.

##### 3. Summary of Reduced Instruction Transpilations:

| Expression | Equivalent Expression | Emitted Bytecode Sequence |
| :--- | :--- | :--- |
| `a < b` | `b > a` | Compile `b`, compile `a`, `OP_GREATER` |
| `a <= b` | `!(a > b)` | Compile `a`, compile `b`, `OP_GREATER`, `OP_NOT` |
| `a >= b` | `!(b > a)` | Compile `b`, compile `a`, `OP_GREATER`, `OP_NOT` |
| `a != b` | `!(a == b)` | Compile `a`, compile `b`, `OP_EQUAL`, `OP_NOT` |
| `a - b` | `a + (-b)` | Compile `a`, compile `b`, `OP_NEGATE`, `OP_ADD` |

##### Trade-offs:
While eliminating instructions shrinks the VM opcode table and binary footprint, it increases bytecode size, adds extra stack manipulation, and slows down VM execution for common arithmetic operations.

---

### 2.
Conversely, we can improve the speed of our bytecode VM by adding
more specific instructions that correspond to higher-level operations.
What instructions would you define to speed up the kind of user code we
added support for in this chapter?

#### Answer:

To maximize the execution speed of our bytecode VM, we can introduce specialized, higher-level opcodes that eliminate instruction dispatch cycles, avoid temporary stack pushes, and skip runtime type checks:

##### 1. Fused Comparison Operators:
- **`OP_NOT_EQUAL` (`!=`)**: Replaces `OP_EQUAL` + `OP_NOT`.
- **`OP_GREATER_EQUAL` (`>=`)**: Replaces `OP_LESS` + `OP_NOT`.
- **`OP_LESS_EQUAL` (`<=`)**: Replaces `OP_GREATER` + `OP_NOT`.
- **Performance Impact**: Reduces bytecode size by 50% for these comparisons and saves one complete VM instruction fetch/decode cycle per evaluation.

##### 2. Immediate / Constant-Inlined Operations:
- **`OP_ADD_IMMEDIATE` (e.g. `x + 1`)**: Takes an immediate integer operand (e.g. 1 byte) and adds it directly to the top of the stack without pushing a constant or accessing the constant table.
- **`OP_INCREMENT` / `OP_DECREMENT` (`++` / `--`)**: Directly mutates local variable slots in-place, dramatically accelerating loop counter updates (`for (var i = 0; i < N; i = i + 1)`).

##### 3. Fused Comparison Jump Instructions (Branch Fusing):
- **`OP_JUMP_IF_NOT_EQUAL`**, **`OP_JUMP_IF_LESS`**, **`OP_JUMP_IF_GREATER`**:
  In conditional statements and loops (`if (a > b)` or `while (i < 100)`), the standard VM emits:
  `OP_GREATER` → `OP_JUMP_IF_FALSE` (offset).
  Fusing these into a single `OP_JUMP_IF_NOT_GREATER` opcode:
  1. Compares the two operands on the stack.
  2. Jumps immediately if false.
  3. Avoids pushing and popping an intermediate `BoolValue` object onto the stack.

##### 4. Type-Specialized Arithmetic and String Operations:
- **`OP_ADD_NUM` vs `OP_CONCAT_STRING`**: Currently `OP_ADD` checks at runtime whether operands are numbers (`double`) or strings (`ObjString*`). Adding type-specialized opcodes allows the compiler (when operand types are known) to emit `OP_ADD_NUM` (pure double addition without type branches) or `OP_CONCAT_STRING` (pure string concatenation).
- **`OP_EQUAL_NUM` / `OP_EQUAL_STRING` / `OP_EQUAL_BOOL`**: Fast-path equality comparisons that skip generic `Value` tag checking.

---

## Strings

### 1.
Each string requires two separate dynamic allocations—one for the
`ObjString` and a second for the character array. Accessing the characters
from a value requires two pointer indirections, which can be bad for
performance. A more efficient solution relies on a technique called
flexible array members. Use that to store the `ObjString` and its
character array in a single contiguous allocation.

#### Answer:

##### Implementation Details:

1. **Flexible Array Member Definition (`clox/object.h`)**:
   Updated `struct ObjString` so `chars` is defined as a C99 Flexible Array Member at the end of the struct instead of a separate `char*` pointer:
   ```c
   struct ObjString {
     Obj obj;
     int length;
     uint32_t hash;
     char chars[]; // Flexible array member
   };
   ```
2. **Single Contiguous Allocation (`clox/object.c`)**:
   Updated `allocateString()` to request `sizeof(ObjString) + length + 1` bytes in a single allocation. The string characters are copied directly into `string->chars`:
   ```c
   static ObjString *allocateString(const char *chars, int length, uint32_t hash) {
     ObjString *string =
         (ObjString *)allocateObject(sizeof(ObjString) + length + 1, OBJ_STRING);
     string->length = length;
     string->hash = hash;
     memcpy(string->chars, chars, length);
     string->chars[length] = '\0';

     push(OBJ_VAL(string));
     tableSet(&vm.strings, string, NIL_VAL);
     pop();

     return string;
   }
   ```
3. **Single Deallocation (`clox/memory.c`)**:
   Updated `freeObject()` so freeing an `ObjString` requires only a single `reallocate()` call:
   ```c
   case OBJ_STRING: {
     ObjString *string = (ObjString *)object;
     reallocate(object, sizeof(ObjString) + string->length + 1, 0);
     break;
   }
   ```

##### Performance Benefits:
1. **50% Reduction in Heap Allocations**: Allocating an `ObjString` takes 1 call to the memory pool allocator instead of 2.
2. **Zero Pointer Indirection Overhead**: Accessing `string->chars` directly computes the memory offset adjacent to `ObjString` fields, eliminating double pointer dereferencing.
3. **Improved L1 Cache Locality**: String header metadata (`length`, `hash`) and character bytes reside together in the same cache line.

---

### 2.
When we create the `ObjString` for each string literal, we copy the
characters onto the heap. That way, when the string is later freed, we
know it is safe to free the characters too.
This is a simpler approach but wastes some memory, which might be a
problem on very constrained devices. Instead, we could keep track of
which `ObjStrings` own their character array and which are “constant
strings” that just point back to the original source string or some other
non-freeable location. Add support for this.

#### Answer:

##### Implementation Details:

1. **Ownership Flag in `struct ObjString` (`clox/object.h`)**:
   Added a boolean `isOwned` flag and a `const char *chars` pointer to `ObjString`:
   ```c
   struct ObjString {
     Obj obj;
     int length;
     uint32_t hash;
     bool isOwned;
     const char *chars;
   };
   ```
2. **Constant vs Owned Allocation (`clox/object.c`)**:
   - `copyString()` (used for string literals in source code) sets `isOwned = false`. The `chars` pointer points directly to the existing source code memory buffer without making a heap copy.
   - `takeString()` (used for dynamically concatenated strings) sets `isOwned = true` and takes ownership of the dynamically allocated heap buffer.
   ```c
   static ObjString *allocateString(const char *chars, int length, uint32_t hash, bool isOwned) {
     ObjString *string = ALLOCATE_OBJ(ObjString, OBJ_STRING);
     string->length = length;
     string->hash = hash;
     string->isOwned = isOwned;
     string->chars = chars;

     push(OBJ_VAL(string));
     tableSet(&vm.strings, string, NIL_VAL);
     pop();

     return string;
   }
   ```
3. **Selective Freeing in Garbage Collector (`clox/memory.c`)**:
   In `freeObject()`, when an `ObjString` is swept by the GC, we only free `string->chars` if `isOwned == true`:
   ```c
   case OBJ_STRING: {
     ObjString *string = (ObjString *)object;
     if (string->isOwned) {
       FREE_ARRAY(char, (char *)string->chars, string->length + 1);
     }
     FREE(ObjString, object);
     break;
   }
   ```

##### Memory & Performance Benefits:
- **Zero Memory Duplication for Literals**: String literals in source code consume zero additional heap memory for their characters.
- **Lower GC Pressure**: Decreases the total bytes allocated on the heap, reducing the frequency of GC cycles.

---

### 3.
If Lox was your language, what would you have it do when a user tries
to use `+` with one string operand and the other some other type? Justify
your choice. What do other languages do?

#### Answer:

##### 1. How Other Languages Handle `string + non_string`:

###### A. Implicit Coercion / Auto-Stringification (`"age: " + 25` → `"age: 25"`):
- **Languages**: JavaScript, Java, C#.
- **Behavior**: If either operand is a string, the non-string operand is automatically converted to its string representation (`toString()`) and concatenated.
- **Pros**: Convenient for quick logging and formatting.
- **Cons**: Leads to surprising dynamic typing bugs and associativity anomalies:
  - In JavaScript: `"5" + 3` yields `"53"`, but `"5" - 3` yields `2`.
  - Associativity issue: `1 + 2 + "3"` yields `"33"`, whereas `"1" + 2 + 3` yields `"123"`.

###### B. Strict Type Error / Explicit Conversion Required:
- **Languages**: Python, Ruby, Rust, Go, Haskell.
- **Behavior**: Raises a runtime `TypeError` or compile error when `+` receives mixed types (`"age: " + 25`).
- **Required Syntax**: Explicit conversion required (`"age: " + str(25)` in Python, `"age: " + 25.to_s` in Ruby, or `fmt.Sprintf` in Go).
- **Pros**: High predictability, prevents subtle type coercion bugs, preserves clean operator semantics.

###### C. Dedicated String Concatenation Operator:
- **Languages**: PHP (`.`), Lua (`..`), Perl (`.`).
- **Behavior**: Distinguishes numeric addition (`+`) from string concatenation (`.` or `..`).

---

##### 2. Recommended Choice for Lox: **Strict Type Error with Explicit Conversion**

##### Justification:
1. **Consistency with Lox's Type Philosophy**:
   Lox is dynamic but strictly typed at runtime. Operations like `true + false` or `"a" - 2` already raise runtime errors. Allowing `+` to silently coerce numbers or booleans into strings would create an inconsistent exception where `+` behaves coercively while all other arithmetic operators enforce type boundaries.
2. **Prevents Silent Bugs**:
   Implicit string coercion causes bugs in calculation-heavy programs where a string unexpectedly contaminates a numeric pipeline.
3. **String Interpolation is the Superior Solution**:
   With string interpolation (e.g. `"${drink} in ${time} min"`) or explicit string functions (`String(val)`), developers can construct formatted strings cleanly and intentionally without overloading the binary `+` operator.

---

## Hash Tables

### 1.
In clox, we happen to only need keys that are strings, so the hash table
we built is hardcoded for that key type. If we exposed hash tables to
Lox users as a first-class collection, it would be useful to support
different kinds of keys.
Add support for keys of the other primitive types: numbers, Booleans,
and nil. Later, clox will support user-defined classes. If we want to
support keys that are instances of those classes, what kind of
complexity does that add?

#### Answer:

##### 1. Generic Primitive Keys Implementation Details:

- **Updated `Entry` Struct (`clox/table.h`)**:
  Replaced hardcoded `ObjString *key` with generic `Value key`:
  ```c
  typedef struct {
    Value key;
    Value value;
  } Entry;
  ```
- **Sentinel Values for Empty and Tombstone Slots (`clox/value.h`)**:
  To support `nil` as a valid primitive key, we defined explicit sentinel representations `EMPTY_VAL` and `TOMBSTONE_VAL` for NaN-boxed and tagged union values:
  ```c
  #define TAG_EMPTY 4
  #define TAG_TOMBSTONE 5
  #define EMPTY_VAL ((Value)(uint64_t)(QNAN | TAG_EMPTY))
  #define TOMBSTONE_VAL ((Value)(uint64_t)(QNAN | TAG_TOMBSTONE))
  ```
- **Value Hashing Function `hashValue()` (`clox/table.c`)**:
  ```c
  uint32_t hashValue(Value key) {
  #ifdef NAN_BOXING
    if (IS_OBJ(key)) {
      Obj *obj = AS_OBJ(key);
      if (obj->type == OBJ_STRING) return ((ObjString *)obj)->hash;
      uint64_t bits = (uint64_t)(uintptr_t)obj;
      bits = ((bits >> 32) ^ bits) * 0x45d9f3b;
      return (uint32_t)bits;
    }
    uint64_t bits = key;
    bits = ((bits >> 32) ^ bits) * 0x45d9f3b;
    bits = ((bits >> 32) ^ bits) * 0x45d9f3b;
    bits = (bits >> 32) ^ bits;
    return (uint32_t)bits;
  #endif
  }
  ```
- **Equality Comparison**:
  `findEntry()` uses `valuesEqual(entry->key, key)` to compare primitive keys (`bool`, `nil`, `number`, `ObjString`).

##### 2. Complexity of Supporting User-Defined Class Instances as Keys:

If users can use custom class instances (`class Point { ... }`) as keys:

- **Identity-based Hashing (Pointer Equality)**:
  - Hashes the raw memory pointer (`(uintptr_t)instance`). Two distinct instances with identical field values are treated as different keys.
  - *Complexity*: Low ($O(1)$ lookup speed).

- **Value-based Hashing & Custom Methods (`hash()` / `==` Overriding)**:
  - **VM Re-entrancy & Execution Overhead**: Every hash table lookup (`tableGet`/`tableSet`) must invoke user Lox bytecode methods (`key.hash()` and `key == other`). Hash lookups are no longer fast C operations.
  - **Garbage Collection Side Effects**: Invoking Lox bytecode inside an internal hash table lookup can trigger a GC cycle or allocate memory, potentially mutating or invalidating the table while searching.
  - **Mutation Hazard**: If a user modifies an object's field after inserting it into a hash table, its hash code changes. The object becomes permanently lost/unreachable in the hash table bucket.

---

### 2.
Hash tables have a lot of knobs you can tweak that affect their
performance. You decide whether to use separate chaining or open
addressing. Depending on which fork in that road you take, you can
tune how many entries are stored in each node, or the probing strategy
you use. You control the hash function, load factor, and growth rate.
All of this variety wasn’t created just to give CS doctoral candidates
something to publish theses on: each has its uses in the many varied
domains and hardware scenarios where hashing comes into play. Look
up a few hash table implementations in different open source systems,
research the choices they made, and try to figure out why they did
things that way.

#### Answer:

A comparative analysis of hash table implementations across major open-source systems highlights how differing design constraints (CPU cache architecture, memory footprints, security against HashDoS, and pointer stability) drive architectural choices:

##### 1. Abseil Swiss Tables (`absl::flat_hash_map` / Google C++) & Rust `std::collections::HashMap`
- **Addressing Mode**: Open Addressing with **Group Scanning (SIMD Probing)**.
- **Key Innovation**: Separate 1-byte Control Array metadata + SIMD Parallel Search.
  - Keeps a compact parallel metadata array where each byte stores a 7-bit hash prefix or control bit (empty, deleted, sentinel).
  - Uses 16-byte SIMD vector operations (`_mm_cmpeq_epi8` in SSE2/NEON) to inspect **16 buckets simultaneously in a single CPU instruction**.
- **Load Factor**: **87.5%** ($7/8$).
- **Growth Rate**: $2\times$.
- **Rationale**: Modern CPUs spend hundreds of clock cycles waiting for main RAM accesses on cache misses. By checking 16 bucket metadata bytes in 1 CPU cycle inside L1 cache, Swiss Tables eliminate unnecessary pointer dereferences and key equality checks, achieving high load factors without speed degradation.

##### 2. CPython `dict` (Compact Dict / Python 3.6+)
- **Addressing Mode**: Open Addressing with **Sparse Index Array + Dense Entry Array**.
- **Key Innovation**: Split indexing array (`indices`) and contiguous entry storage (`entries`).
  - `indices`: Array of small integer types (`int8_t`, `int16_t`, `int32_t`) acting as hash bucket slots.
  - `entries`: Compact, dense array of `{hash, key, value}` structures stored in insertion order.
- **Load Factor**: **66.6%** ($2/3$).
- **Growth Rate**: $2\times$ or $4\times$.
- **Rationale**:
  1. **Memory Reduction**: Storing sparse index arrays with 1-byte (`int8_t`) integers instead of 24-byte `{hash, key, value}` structs reduces memory usage by 60–70%.
  2. **Preserves Insertion Order**: Traversing the dense `entries` array naturally yields key-value pairs in insertion order without requiring extra linked list pointers.

##### 3. Java `java.util.HashMap` (JDK 8+)
- **Addressing Mode**: **Separate Chaining** with Hybrid Linked List / Red-Black Tree Buckets.
- **Key Innovation**: Bucket Treeification (`TREEIFY_THRESHOLD = 8`).
  - Buckets start as singly-linked lists.
  - If collision depth in a single bucket exceeds 8 entries, the bucket automatically transforms into a **Red-Black Tree** ($O(\log N)$ worst-case lookup).
- **Load Factor**: **75%** ($0.75$).
- **Growth Rate**: $2\times$.
- **Rationale**:
  1. **HashDoS Security**: Defends against Algorithmic Complexity Attacks (where malicious input generates identical hashes). Guaranteed $O(\log N)$ bound prevents Denial-of-Service.
  2. **Reference Stability**: Key and value objects remain in fixed heap memory locations during table resizing.

##### 4. Linux Kernel `hashtable.h`
- **Addressing Mode**: **Separate Chaining** via Intrusive Nodes (`hlist_head` / `hlist_node`).
- **Key Innovation**: Embedded intrusive node pointers inside data structures (e.g. `struct inode`, `struct task_struct`).
- **Rationale**: Zero dynamic heap allocations when inserting kernel objects into a hash table. Eliminates memory allocation failures under critical kernel memory pressure.

##### 5. Architectural Comparison Matrix:

| System | Addressing Strategy | Probing / Bucket Type | Load Factor | Primary Optimization Goal |
| :--- | :--- | :--- | :--- | :--- |
| **Google Swiss Tables** | Open Addressing | 16-byte SIMD Control Vector | 87.5% | L1/L2 Cache Locality & SIMD Parallelism |
| **CPython `dict`** | Open Addressing | Sparse Index Array + Dense Entries | 66.6% | Low RAM Footprint & Preserved Insertion Order |
| **Java `HashMap`** | Separate Chaining | Linked List $\rightarrow$ Red-Black Tree ($N \ge 8$) | 75.0% | HashDoS Attack Defense & Pointer Stability |
| **Linux Kernel** | Separate Chaining | Intrusive Doubly-Linked Head/Node | N/A | Zero Heap Allocation & Memory Safety |
| **`clox` Table** | Open Addressing | Linear Probing with Tombstones | 75.0% | Implementation Simplicity & Cache Alignment |

---

### 3.
Benchmarking a hash table is notoriously difficult. A hash table
implementation may perform well with some keysets and poorly with
others. It may work well at small sizes but degrade as it grows, or vice
versa. It may choke when deletions are common, but fly when they
aren’t. Creating benchmarks that accurately represent how your users
will use the hash table is a challenge.
Write a handful of different benchmark programs to validate our hash
table implementation. How does the performance vary between them?
Why did you choose the specific test cases you chose?

#### Answer:

To validate the performance characteristics of our `clox` hash table implementation across diverse runtime conditions, we implemented a dedicated C benchmark program ([`clox/benchmark_table.c`](file:///home/luna/repos/lox-interpreter/clox/benchmark_table.c)) covering 4 distinct operational scenarios.

##### 1. Designed Benchmark Scenarios & Rationale:

1. **Benchmark 1: Sequential Insertion & Growth (`benchSequentialInserts`)**:
   - Inserts 10,000 unique numeric keys into an initially empty table, then queries all 10,000 keys.
   - *Rationale*: Measures array reallocation overhead (`adjustCapacity`), capacity growth (8 → 16 → ... → 16,384), and average probe length during table scaling.
2. **Benchmark 2: Read-Heavy Workload (`benchReadHeavyWorkload`)**:
   - Performs 100,000 lookup operations on a table populated with 5,000 entries (50% hit rate).
   - *Rationale*: Models standard VM runtime access patterns (such as global variable resolution and class property lookups), where reads outnumber writes by orders of magnitude.
3. **Benchmark 3: Tombstone Churn (`benchTombstoneChurn`)**:
   - Repeatedly deletes 50% of the table entries (`tableDelete`) and inserts new entries for 50 cycles.
   - *Rationale*: Evaluates open addressing performance degradation caused by **tombstone accumulation** (`TOMBSTONE_VAL`). Tombstones pollute probe sequences and increase average search lengths.
4. **Benchmark 4: Interned String Keys (`benchStringKeys`)**:
   - Inserts and looks up 2,000 interned `ObjString*` keys.
   - *Rationale*: Validates the fast path for string keys where pre-computed FNV-1a hashes and pointer equality (`a == b`) avoid character-by-character string comparisons.

##### 2. Empirical Benchmark Execution Results:

```
=== CLOX HASH TABLE BENCHMARK SUITE ===
  [Bench 1 - Sequential Number Keys] Insert 10000 entries: 1.20 ms | Lookup: 0.12 ms (Found: 10000/10000)
  [Bench 2 - Read-Heavy Workload] 100000 Lookups (Size 5000): 2.62 ms (Hits: 50068)
  [Bench 3 - Tombstone Churn] 50 delete/reinsert cycles (Size 2000): 3.43 ms
  [Bench 4 - String Keys] Insert 2000 strings: 0.19 ms | Lookup: 0.02 ms (Found: 2000/2000)
=======================================
```

##### 3. Performance Analysis & Variations:

- **Read-Heavy Speed**: Lookups in Benchmark 2 executed at **38.1 million operations per second** (2.62 ms for 100,000 lookups). Contiguous memory layout in open addressing provides high CPU L1 cache hit rates.
- **Tombstone Overhead**: Benchmark 3 took 3.43 ms for 2,000 entries under tombstone churn—substantially slower per entry than pure insertions. In linear probing, tombstones are not reclaimed until table resize (`adjustCapacity`), forcing `tableGet` to probe longer chains past deleted slots.
- **String Key Fast Path**: String lookups (Benchmark 4) executed in 0.02 ms for 2,000 keys (0.01 µs per lookup) because string interning reduces equality checks to simple pointer comparisons.

---

## Global Variables

### 1.
The compiler adds a global variable’s name to the constant table as a
string every time an identifier is encountered. It creates a new constant
each time, even if that variable name is already in a previous slot in the
constant table. That’s wasteful in cases where the same variable is
referenced multiple times by the same function. That, in turn, increases
the odds of filling up the constant table and running out of slots since
we allow only 256 constants in a single chunk.
Optimize this. How does your optimization affect the performance of
the compiler compared to the runtime? Is this the right trade-off?

#### Answer:

##### 1. Implementation Details (`clox/compiler.c`):
Updated `identifierConstant()` to search the current chunk's constant table for an existing string value before allocating a new constant slot:

```c
static uint8_t identifierConstant(Token *name) {
  ObjString *identifier = copyString(name->start, name->length);
  Value identifierVal = OBJ_VAL(identifier);

  Chunk *chunk = currentChunk();
  for (int i = 0; i < chunk->constants.count; i++) {
    if (valuesEqual(chunk->constants.values[i], identifierVal)) {
      return (uint8_t)i;
    }
  }

  return makeConstant(identifierVal);
}
```

##### 2. Trade-off & Performance Analysis:

- **Compiler Performance Impact**:
  - Adds an $O(N)$ linear search over the chunk's constant array for each identifier.
  - Because single chunks cap constants at $N \le 256$, searching a 256-element array of contiguous `uint64_t` values in CPU L1 cache takes less than 100 nanoseconds—a completely negligible overhead during compilation.
- **Runtime Performance & Memory Impact**:
  - Eliminates redundant string constant slots in `chunk->constants`.
  - Prevents premature "Too many constants in one chunk" compiler errors when a single function references global variables or properties repeatedly.
  - Improves L1 data cache efficiency during VM bytecode execution by shrinking the size of constant arrays.
- **Is this the right trade-off?**:
  - **YES**. Compilation happens once per function/script, whereas VM execution runs repeatedly in loops. Paying a microsecond during compilation to conserve constant slots and improve runtime memory density is the ideal compiler design trade-off.

---

### 2.
Looking up a global variable by name in a hash table each time it is
used is pretty slow, even with a good hash table. Can you come up
with a more efficient way to store and access global variables without
changing the semantics?

#### Answer:

To eliminate the runtime performance overhead of performing hash table lookups (`tableGet` / `tableSet`) on every global variable access, we can implement two alternative architectural techniques while fully preserving Lox's dynamic semantics (such as late binding and runtime declaration):

##### Solution 1: Global Symbol Array (Index-Based Direct Access)

- **Architecture**:
  1. **Global Symbol Table**: Instead of an unordered hash table, `vm.globals` is stored as a flat, dynamically-resized `ValueArray globals` indexed by integer slots (`0, 1, 2, ...`).
  2. **Compiler Symbol Map**: The compiler maintains a global mapping of variable name strings to integer slot indices. The first time a global variable `x` is declared or referenced anywhere in the program, it is assigned a unique index (e.g. `index = 0`).
  3. **Bytecode Opcodes**:
     Replace `OP_GET_GLOBAL <name_string_index>` with `OP_GET_GLOBAL_INDEX <slot_index>`.
  4. **Runtime Execution (`vm.c`)**:
     Reading `x` becomes `Value val = vm.globals.values[slot_index];`.
     - *Undefined Check*: Unallocated slots contain an `UNINITIALIZED_VAL` sentinel. If `vm.globals.values[slot_index] == UNINITIALIZED_VAL`, the VM raises a runtime error: `"Undefined variable 'x'"`.
- **Performance**: Access time drops from an $O(1)$ hash table probe to a single $O(1)$ direct C array indexing operation!

##### Solution 2: Inline Caching (IC)

- **Architecture**:
  1. **Cached Instruction Bytecode**: Modify `OP_GET_GLOBAL` to accept two operands: `<name_constant_index> <cached_slot_index>`.
  2. **First-Execution Fast-Path Patching**:
     - On first execution, the VM performs the normal hash table lookup `tableGet(&vm.globals, name, &value)`.
     - It caches the entry pointer or slot index directly into the instruction's `<cached_slot_index>` operand.
  3. **Subsequent Executions**: On subsequent iterations (e.g. inside tight loops), the VM reads directly from `<cached_slot_index>` without touching the hash table.
- **Performance**: Achieves near-zero overhead inside loops while retaining a hash table backing store for dynamic REPL environments.

##### Comparison Matrix:

| Strategy | Lookup Overhead | Memory Footprint | REPL & Late-Binding Support |
| :--- | :--- | :--- | :--- |
| **Standard `clox` (Hash Table)** | Hash Probe & Key Check | Moderate (Table Buckets) | 100% |
| **Global Symbol Array** | Single Array Index ($O(1)$) | Very Low (Flat Array) | 100% |
| **Inline Caching (IC)** | Direct Pointer Dereference | Low + Small IC Payload | 100% (De-optimizes on redefinition) |

---

### 3.
When running in the REPL, a user might write a function that
references an unknown global variable. Then, in the next line, they
declare the variable. Lox should handle this gracefully by not reporting
an “unknown variable” compile error when the function is first
defined.
But when a user runs a Lox script, the compiler has access to the full
text of the entire program before any code is run. Consider this
program:
```lox
fun useVar() {
  print oops;
}
var ooops = "too many o's!";
```
Here, we can tell statically that `oops` will not be defined because there
is no declaration of that global anywhere in the program. Note that
`useVar()` is never called either, so even though the variable isn’t
defined, no runtime error will occur because it’s never used either.
We could report mistakes like this as compile errors, at least when
running from a script. Do you think we should? Justify your answer.
What do other scripting languages you know do?

#### Answer:

##### 1. How Other Scripting Languages Handle Unknown Globals:

- **JavaScript**:
  - Compiling an uncalled function referencing an undeclared global (`function foo() { console.log(oops); }`) succeeds with **zero compile errors**.
  - A `ReferenceError: oops is not defined` is thrown only at **runtime when `foo()` is executed**.
  - Static analysis tools (ESLint `no-undef`, TypeScript) catch undeclared variables at build time.
- **Python**:
  - Compiles functions to bytecode without checking if global names exist at compile time.
  - Raises `NameError: name 'oops' is not defined` only at **runtime if that code path is executed**.
  - Static linters (`mypy`, `flake8`, `pylint`) catch typos statically.
- **Lua**:
  - Referencing an undeclared global variable evaluates to `nil` at runtime. No compile or runtime error occurs!
- **Ruby**:
  - Accessing an uninitialized global variable (`$oops`) evaluates to `nil` at runtime without raising an exception.

##### 2. Trade-off Analysis for Static Global Scope Checking in Lox:

###### Arguments FOR Static Compile Errors in Script Mode:
1. **Early Error Detection**: Catches typos (e.g., `oops` vs `ooops`) immediately before execution instead of waiting for a rare runtime code path to fail.
2. **Full AST Availability**: When compiling a script file, the parser inspects the entire file before running code.

###### Arguments AGAINST Static Compile Errors in Script Mode:
1. **Semantic Inconsistency**: Creates a confusing semantic split between REPL mode (where late binding is required) and Script mode. Code that works in the REPL might fail to compile in a script.
2. **Host Environment & Native Bindings**: Languages like Lox often have native functions or host variables injected into the global table at runtime (e.g. `clock()`, foreign function interfaces, or host embeds). A static compiler check would falsely report these as compile errors.
3. **Multi-File Scripts & Modules**: In programs split across multiple files, File A might reference a global defined in File B loaded at runtime.

##### 3. Conclusion & Recommendation:

**No, the core Lox interpreter compiler should NOT report compile errors for undeclared globals in script mode**.

- The **language core** should remain dynamic with consistent late-binding semantics across both REPL and Script execution modes.
- Catching typos and undeclared identifiers should be left to external **Linters, Static Analyzers, or IDE Language Servers**, providing safety tools without constraining the language runtime's dynamic flexibility.

---

## Local Variables

### 1.
Our simple local array makes it easy to calculate the stack slot of each
local variable. But it means that when the compiler resolves a
reference to a variable, we have to do a linear scan through the array.
Come up with something more efficient. Do you think the additional
complexity is worth it?

#### Answer:

##### 1. Proposed $O(1)$ Alternative: Scoped Hash Map with Stacked Slot Lists

Instead of performing a linear array scan ($O(N)$) backwards through `compiler->locals`, we can maintain a **Scoped Identifier Hash Table** mapping variable names to a stack of active stack slot indices:

- **Structure**: `Table scopeTable` mapping identifier strings (`ObjString*`) to a linked list or dynamic stack of integer slot numbers `[slot_depth_0, slot_depth_1, ...]`.
- **Operations**:
  1. **Variable Declaration (`addLocal`)**: When local `x` is declared at stack slot $S$, we look up `"x"` in `scopeTable` and push slot $S$ to the top of `"x"`'s stack.
  2. **Variable Resolution (`resolveLocal`)**: To resolve `x`, we query `scopeTable.get("x")`. The top value of the stack gives the innermost stack slot index in **$O(1)$ average time**!
  3. **Scope Exit (`endScope`)**: When leaving a block, as locals are popped, we pop their slot indices from `scopeTable`. If a variable's stack becomes empty, we remove the key from `scopeTable`.

##### 2. Trade-off Analysis: Is the Additional Complexity Worth It?

**NO, for an interpreter compiler like `clox`, the extra complexity is NOT worth it.**

- **Small Variable Count in Human Code**: Well-written functions rarely exceed 10 to 30 local variables. Scanning a contiguous array of 20 `Local` structs in CPU L1 cache takes less than 15 nanoseconds—far faster than hashing a string and probing a hash table!
- **Zero Overhead on Scope Exit**: With a simple array, `endScope()` cleans up all out-of-scope locals in 1 CPU instruction (`compiler->localCount -= count;`). With a hash table, `endScope()` requires removing entries or popping stack nodes for every out-of-scope variable.
- **Cache Line Locality & Simplicity**: The `Local locals[256]` array is a dense contiguous memory block stored directly inside the `Compiler` struct. Pointer chasing in heap-allocated hash tables introduces cache misses and allocation overhead.

---

### 2.
How do other languages handle code like this:
```lox
var a = a;
```
What would you do if it was your language? Why?

#### Answer:

##### 1. How Other Languages Handle `var a = a;`:

- **C / C++**:
  - **Allowed**. The variable `a` enters the symbol table immediately at its name token, *before* its initializer expression is evaluated.
  - **Behavior**: `int a = a;` initializes `a` with its own uninitialized garbage stack memory.
- **JavaScript**:
  - **`var a = a;`**: Allowed. `var` declarations are hoisted to the top of the function with value `undefined`. Assigns `undefined` to `a`.
  - **`let a = a;` / `const a = a;`**: **Runtime `ReferenceError` (Temporal Dead Zone - TDZ)**. The variable enters scope at block start but stays in the TDZ until initialized. Reading `a` on the right side throws `Cannot access 'a' before initialization`.
- **C# / Java**:
  - **Compile Error**. In C#, `CS0841: Cannot use local variable 'a' before it is declared`. Java reports `variable a might not have been initialized`.
- **Rust**:
  - **Variable Shadowing**. `let a = a;` is legal if an outer variable `a` exists! The right-hand `a` resolves to the **outer scope** `a`, initializing the new inner `a` with the outer `a`'s value. If no outer `a` exists, Rust reports a compile error (`cannot find value 'a' in this scope`).
- **Lox (`clox`)**:
  - **Compile Error**. In `clox`, `declareVariable()` sets `local.depth = -1` (uninitialized mark). When `a` on the right side is resolved by `resolveLocal()`, seeing `local.depth == -1` triggers a compile error: `"Can't read local variable in its own initializer."`

##### 2. Recommended Choice for Lox & Justification:

If Lox was my language, I would choose **Compile Error (Lox's approach) or Outer-Scope Shadowing (Rust's approach)**.

##### Justification:
1. **Prevents Silent Bugs**: Reading uninitialized stack garbage (C) or `nil`/`undefined` (JS `var`) in 99% of cases represents a typo or logic bug. Raising a compile error catches the bug at build time.
2. **Clear Temporal Boundaries**: Enforces a clean, unambiguous three-step sequence:
   1. Evaluate right-hand side expression (resolving identifiers in the current outer scope).
   2. Allocate stack slot for the new variable.
   3. Mark the new variable as initialized and ready for scope lookups.
3. **Supports Elegant Shadowing**: If `var a = a + 1;` is written inside a block, evaluating the right-hand `a` against the *outer scope* variable `a` allows clean, functional-style variable shadowing without ambiguity.

---

### 3.
Many languages make a distinction between variables that can be
reassigned and those that can’t. In Java, the `final` modifier prevents
you from assigning to a variable. In JavaScript, a variable declared
with `let` can be assigned, but one declared using `const` can’t. Swift
treats `let` as single-assignment and uses `var` for assignable variables.
Scala and Kotlin use `val` and `var`.

Pick a keyword for a single-assignment variable form to add to Lox.
Justify your choice, then implement it. An attempt to assign to a
variable declared using your new keyword should cause a compile
error.

---

### 4.
Extend clox to allow more than 256 local variables to be in scope at a
time.

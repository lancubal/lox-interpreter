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

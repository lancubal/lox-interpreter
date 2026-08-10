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

---

### 3.
Our VM’s stack has a fixed size, and we don’t check if pushing a value
overflows it. This means the wrong series of instructions could cause
our interpreter to crash or go into undefined behavior. Avoid that by
dynamically growing the stack as needed.
What are the costs and benefits of doing so?

---

### 4.
To interpret `OP_NEGATE`, we pop the operand, negate the value, and then
push the result. That’s a simple implementation, but it increments and
decrements `stackTop` unnecessarily, since the stack ends up the same
height in the end. It might be faster to simply negate the value in place
on the stack and leave `stackTop` alone. Try that and see if you can
measure a performance difference.
Are there other instructions where you can do a similar optimization?

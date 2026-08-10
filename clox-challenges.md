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

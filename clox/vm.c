#include <math.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <time.h>

#include "chunk.h"
#include "common.h"
#include "compiler.h"
#include "debug.h"
#include "memory.h"
#include "object.h"
#include "table.h"
#include "value.h"
#include "vm.h"

VM vm;

static void runtimeError(const char *format, ...);

static bool clockNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  (void)args;
  *result = NUMBER_VAL((double)clock() / CLOCKS_PER_SEC);
  return true;
}

static bool sqrtNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  if (!IS_NUMBER(args[0])) {
    runtimeError("Argument to sqrt() must be a number.");
    return false;
  }
  double num = AS_NUMBER(args[0]);
  if (num < 0) {
    runtimeError("Cannot calculate square root of negative number.");
    return false;
  }
  *result = NUMBER_VAL(sqrt(num));
  return true;
}

static bool absNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  if (!IS_NUMBER(args[0])) {
    runtimeError("Argument to abs() must be a number.");
    return false;
  }
  *result = NUMBER_VAL(fabs(AS_NUMBER(args[0])));
  return true;
}

static bool floorNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  if (!IS_NUMBER(args[0])) {
    runtimeError("Argument to floor() must be a number.");
    return false;
  }
  *result = NUMBER_VAL(floor(AS_NUMBER(args[0])));
  return true;
}

static bool ceilNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  if (!IS_NUMBER(args[0])) {
    runtimeError("Argument to ceil() must be a number.");
    return false;
  }
  *result = NUMBER_VAL(ceil(AS_NUMBER(args[0])));
  return true;
}

static bool strNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  Value val = args[0];
  if (IS_STRING(val)) {
    *result = val;
    return true;
  }

  char buffer[64];
  if (IS_NIL(val)) {
    snprintf(buffer, sizeof(buffer), "nil");
  } else if (IS_BOOL(val)) {
    snprintf(buffer, sizeof(buffer), AS_BOOL(val) ? "true" : "false");
  } else if (IS_NUMBER(val)) {
    snprintf(buffer, sizeof(buffer), "%g", AS_NUMBER(val));
  } else {
    snprintf(buffer, sizeof(buffer), "<object>");
  }

  int len = (int)strlen(buffer);
  char *heapChars = ALLOCATE(char, len + 1);
  memcpy(heapChars, buffer, len);
  heapChars[len] = '\0';

  *result = OBJ_VAL(takeString(heapChars, len));
  return true;
}

static bool typeNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  Value val = args[0];
  const char *typeName = "unknown";

  if (IS_NIL(val))
    typeName = "nil";
  else if (IS_BOOL(val))
    typeName = "boolean";
  else if (IS_NUMBER(val))
    typeName = "number";
  else if (IS_STRING(val))
    typeName = "string";
  else if (IS_FUNCTION(val) || IS_CLOSURE(val) || IS_NATIVE(val))
    typeName = "function";
  else if (IS_CLASS(val))
    typeName = "class";
  else if (IS_INSTANCE(val))
    typeName = "instance";
  else if (IS_BOUND_METHOD(val))
    typeName = "method";

  *result = OBJ_VAL(copyString(typeName, (int)strlen(typeName)));
  return true;
}

static bool hasFieldNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  if (!IS_INSTANCE(args[0])) {
    runtimeError("First argument to hasField() must be an instance.");
    return false;
  }
  if (!IS_STRING(args[1])) {
    runtimeError("Second argument to hasField() must be a string field name.");
    return false;
  }

  ObjInstance *instance = AS_INSTANCE(args[0]);
  ObjString *name = AS_STRING(args[1]);

  Value dummy;
  bool exists = tableGet(&instance->fields, OBJ_VAL(name), &dummy);
  *result = BOOL_VAL(exists);
  return true;
}

static bool getFieldNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  if (!IS_INSTANCE(args[0])) {
    runtimeError("First argument to getField() must be an instance.");
    return false;
  }
  if (!IS_STRING(args[1])) {
    runtimeError("Second argument to getField() must be a string field name.");
    return false;
  }

  ObjInstance *instance = AS_INSTANCE(args[0]);
  ObjString *name = AS_STRING(args[1]);

  Value val;
  if (tableGet(&instance->fields, OBJ_VAL(name), &val)) {
    *result = val;
  } else {
    *result = NIL_VAL;
  }
  return true;
}

static bool setFieldNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  if (!IS_INSTANCE(args[0])) {
    runtimeError("First argument to setField() must be an instance.");
    return false;
  }
  if (!IS_STRING(args[1])) {
    runtimeError("Second argument to setField() must be a string field name.");
    return false;
  }

  ObjInstance *instance = AS_INSTANCE(args[0]);
  ObjString *name = AS_STRING(args[1]);
  Value value = args[2];

  tableSet(&instance->fields, OBJ_VAL(name), value);
  *result = value;
  return true;
}

static bool deleteFieldNative(int argCount, Value *args, Value *result) {
  (void)argCount;
  if (!IS_INSTANCE(args[0])) {
    runtimeError("First argument to deleteField() must be an instance.");
    return false;
  }
  if (!IS_STRING(args[1])) {
    runtimeError("Second argument to deleteField() must be a string field name.");
    return false;
  }

  ObjInstance *instance = AS_INSTANCE(args[0]);
  ObjString *name = AS_STRING(args[1]);

  bool deleted = tableDelete(&instance->fields, OBJ_VAL(name));
  *result = BOOL_VAL(deleted);
  return true;
}

static void resetStack() {
  vm.stackTop = vm.stack;
  vm.frameCount = 0;
  vm.openUpvalues = NULL;
}

static void runtimeError(const char *format, ...) {
  va_list args;
  va_start(args, format);
  vfprintf(stderr, format, args);
  va_end(args);
  fputs("\n", stderr);

  for (int i = vm.frameCount - 1; i >= 0; i--) {
    CallFrame *frame = &vm.frames[i];
    ObjFunction *function = frame->function;
    size_t instruction = frame->ip - function->chunk.code - 1;
    fprintf(stderr, "[line %d] in ", getLine(&function->chunk, (int)instruction));
    if (function->name == NULL) {
      fprintf(stderr, "script\n");
    } else {
      fprintf(stderr, "%s()\n", function->name->chars);
    }
  }
  resetStack();
}

static void defineNative(const char *name, NativeFn function, int arity) {
  push(OBJ_VAL(copyString(name, (int)strlen(name))));
  push(OBJ_VAL(newNative(function, arity)));
  tableSet(&vm.globals, vm.stack[0], vm.stack[1]);
  pop();
  pop();
}

void initVM() {
  initCustomHeap();
  vm.stack = NULL;
  vm.stackCapacity = 0;
  resetStack();
  vm.objects = NULL;
  vm.bytesAllocated = 0;
  vm.nextGC = 1024 * 1024;

  vm.grayCount = 0;
  vm.grayCapacity = 0;
  vm.grayStack = NULL;

  initTable(&vm.globals);
  initTable(&vm.strings);

  vm.initString = NULL;
  vm.initString = copyString("init", 4);

  defineNative("clock", clockNative, 0);
  defineNative("sqrt", sqrtNative, 1);
  defineNative("abs", absNative, 1);
  defineNative("floor", floorNative, 1);
  defineNative("ceil", ceilNative, 1);
  defineNative("str", strNative, 1);
  defineNative("type", typeNative, 1);
  defineNative("hasField", hasFieldNative, 2);
  defineNative("getField", getFieldNative, 2);
  defineNative("setField", setFieldNative, 3);
  defineNative("deleteField", deleteFieldNative, 2);
}

void freeVM() {
  freeTable(&vm.globals);
  freeTable(&vm.strings);
  vm.initString = NULL;
  freeObjects();
  FREE_ARRAY(Value, vm.stack, vm.stackCapacity);
  freeCustomHeap();
}

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

Value pop() {
  vm.stackTop--;
  return *vm.stackTop;
}

static Value peek(int distance) { return vm.stackTop[-1 - distance]; }

static bool callFunction(ObjFunction *function, int argCount) {
  if (argCount != function->arity) {
    runtimeError("Expected %d arguments but got %d.", function->arity, argCount);
    return false;
  }

  if (vm.frameCount == FRAMES_MAX) {
    runtimeError("Stack overflow.");
    return false;
  }
  CallFrame *frame = &vm.frames[vm.frameCount++];
  frame->function = function;
  frame->closure = NULL;
  frame->ip = function->chunk.code;
  frame->slots = vm.stackTop - argCount - 1;
  return true;
}

static bool callClosure(ObjClosure *closure, int argCount) {
  if (argCount != closure->function->arity) {
    runtimeError("Expected %d arguments but got %d.", closure->function->arity,
                 argCount);
    return false;
  }

  if (vm.frameCount == FRAMES_MAX) {
    runtimeError("Stack overflow.");
    return false;
  }
  CallFrame *frame = &vm.frames[vm.frameCount++];
  frame->function = closure->function;
  frame->closure = closure;
  frame->ip = closure->function->chunk.code;
  frame->slots = vm.stackTop - argCount - 1;
  return true;
}

static bool callValue(Value callee, int argCount) {
  if (IS_OBJ(callee)) {
    switch (OBJ_TYPE(callee)) {
    case OBJ_BOUND_METHOD: {
      ObjBoundMethod *bound = AS_BOUND_METHOD(callee);
      vm.stackTop[-argCount - 1] = bound->receiver;
      return callValue(OBJ_VAL(bound->method), argCount);
    }
    case OBJ_CLASS: {
      ObjClass *klass = AS_CLASS(callee);
      vm.stackTop[-argCount - 1] = OBJ_VAL(newInstance(klass));
      Value initializer;
      if (tableGet(&klass->methods, OBJ_VAL(vm.initString), &initializer)) {
        return callValue(initializer, argCount);
      } else if (argCount != 0) {
        runtimeError("Expected 0 arguments but got %d", argCount);
        return false;
      }
      return true;
    }
    case OBJ_FUNCTION: {
      return callFunction(AS_FUNCTION(callee), argCount);
    }
    case OBJ_CLOSURE: {
      return callClosure(AS_CLOSURE(callee), argCount);
    }
    case OBJ_NATIVE: {
      ObjNative *native = AS_NATIVE_OBJ(callee);
      if (argCount != native->arity) {
        runtimeError("Expected %d arguments but got %d.", native->arity, argCount);
        return false;
      }
      Value result;
      if (!native->function(argCount, vm.stackTop - argCount, &result)) {
        return false;
      }
      vm.stackTop -= argCount + 1;
      push(result);
      return true;
    }
    default:
      break; // Non-callable
    }
  }
  runtimeError("Can only call function and classes.");
  return false;
}

static bool invokeFromClass(ObjClass *klass, ObjString *name, int argCount) {
  Value method;
  if (!tableGet(&klass->methods, OBJ_VAL(name), &method)) {
    runtimeError("Undefined property '%s'.", name->chars);
    return false;
  }

  return callValue(method, argCount);
}

static bool invoke(ObjString *name, int argCount) {
  Value receiver = peek(argCount);

  if (!IS_INSTANCE(receiver)) {
    runtimeError("Only instances have methods.");
    return false;
  }

  ObjInstance *instance = AS_INSTANCE(receiver);

  Value value;
  if (!tableGet(&instance->fields, OBJ_VAL(name), &value)) {
    vm.stackTop[-argCount - 1] = value;
    return callValue(value, argCount);
  }
  return invokeFromClass(instance->klass, name, argCount);
}

static bool bindMethod(ObjClass *klass, ObjString *name) {
  Value method;
  if (!tableGet(&klass->methods, OBJ_VAL(name), &method)) {
    runtimeError("Undefined property '%s'.", name->chars);
    return false;
  }

  ObjBoundMethod *bound = newBoundMethod(peek(0), AS_CLOSURE(method));
  pop();
  push(OBJ_VAL(bound));
  return true;
}

static ObjUpvalue *captureUpvalue(Value *local) {
  ObjUpvalue *prevUpvalue = NULL;
  ObjUpvalue *upvalue = vm.openUpvalues;
  while (upvalue != NULL && upvalue->location > local) {
    prevUpvalue = upvalue;
    upvalue = upvalue->next;
  }

  if (upvalue != NULL && upvalue->location == local) {
    return upvalue;
  }

  ObjUpvalue *createdupvalue = newUpvalue(local);
  createdupvalue->next = upvalue;

  if (prevUpvalue == NULL) {
    vm.openUpvalues = createdupvalue;
  } else {
    prevUpvalue->next = createdupvalue;
  }
  return createdupvalue;
}

static void closeUpvalues(Value *last) {
  while (vm.openUpvalues != NULL && vm.openUpvalues->location >= last) {
    ObjUpvalue *upvalue = vm.openUpvalues;
    upvalue->closed = *upvalue->location;
    upvalue->location = &upvalue->closed;
    vm.openUpvalues = upvalue->next;
  }
}

static void defineMethod(ObjString *name) {
  Value method = peek(0);
  ObjClass *klass = AS_CLASS(peek(1));
  tableSet(&klass->methods, OBJ_VAL(name), method);
  pop();
}

static bool isFalsey(Value value) {
  return IS_NIL(value) || (IS_BOOL(value) && !AS_BOOL(value));
}

static void concatenate() {
  ObjString *b = AS_STRING(peek(0));
  ObjString *a = AS_STRING(peek(1));

  int length = a->length + b->length;
  char *chars = ALLOCATE(char, length + 1);
  memcpy(chars, a->chars, a->length);
  memcpy(chars + a->length, b->chars, b->length);
  chars[length] = '\0';

  ObjString *result = takeString(chars, length);
  pop();
  pop();
  push(OBJ_VAL(result));
}

static InterpretResult run() {
  CallFrame *frame = &vm.frames[vm.frameCount - 1];
  register uint8_t *ip = frame->ip;

#define READ_BYTE() (*ip++)
#define READ_CONSTANT()                                                        \
  (frame->function->chunk.constants.values[READ_BYTE()])
#define READ_SHORT()                                                           \
  (ip += 2, (uint16_t)((ip[-2] << 8) | ip[-1]))
#define READ_24BIT()                                                           \
  (ip += 3, (uint32_t)(ip[-3] | (ip[-2] << 8) | (ip[-1] << 16)))
#define READ_STRING() AS_STRING(READ_CONSTANT())
#define BINARY_OP(valueType, op)                                               \
  do {                                                                         \
    if (!IS_NUMBER(peek(0)) || !IS_NUMBER(peek(1))) {                          \
      frame->ip = ip;                                                          \
      runtimeError("Operands must be numbers.");                               \
      return INTERPRET_RUNTIME_ERROR;                                          \
    }                                                                          \
    double b = AS_NUMBER(vm.stackTop[-1]);                                     \
    double a = AS_NUMBER(vm.stackTop[-2]);                                     \
    vm.stackTop[-2] = valueType(a op b);                                       \
    vm.stackTop--;                                                             \
  } while (false)

  for (;;) {
#ifdef DEBUG_TRACE_EXECUTION
    printf(" ");
    for (Value *slot = vm.stack; slot < vm.stackTop; slot++) {
      printf("[ ");
      printValue(*slot);
      printf(" ]");
    }
    printf("\n");
    disassembleInstruction(
        &frame->function->chunk,
        (int)(ip - frame->function->chunk.code));
#endif
    uint8_t instruction = READ_BYTE();
    // printf("op %d\n", instruction); fflush(stdout);
    switch (instruction) {
    case OP_CONSTANT: {
      Value constant = READ_CONSTANT();
      push(constant);
      break;
    }
    case OP_CONSTANT_LONG: {
      uint32_t index = READ_24BIT();
      Value constant =
          frame->function->chunk.constants.values[index];
      push(constant);
      break;
    }
    case OP_NIL:
      push(NIL_VAL);
      break;
    case OP_TRUE:
      push(BOOL_VAL(true));
      break;
    case OP_FALSE:
      push(BOOL_VAL(false));
      break;
    case OP_POP: {
      pop();
      break;
    }
    case OP_DUP: {
      push(peek(0));
      break;
    }
    case OP_GET_LOCAL: {
      uint8_t slot = READ_BYTE();
      push(frame->slots[slot]);
      break;
    }
    case OP_GET_LOCAL_LONG: {
      uint32_t slot = READ_24BIT();
      push(frame->slots[slot]);
      break;
    }
    case OP_SET_LOCAL: {
      uint8_t slot = READ_BYTE();
      frame->slots[slot] = peek(0);
      break;
    }
    case OP_SET_LOCAL_LONG: {
      uint32_t slot = READ_24BIT();
      frame->slots[slot] = peek(0);
      break;
    }
    case OP_GET_GLOBAL: {
      ObjString *name = READ_STRING();
      Value value;
      if (!tableGet(&vm.globals, OBJ_VAL(name), &value)) {
        frame->ip = ip;
        runtimeError("Undefined variable '%s'", name->chars);
        return INTERPRET_RUNTIME_ERROR;
      }
      push(value);
      break;
    }
    case OP_SET_GLOBAL: {
      ObjString *name = READ_STRING();
      if (tableSet(&vm.globals, OBJ_VAL(name), peek(0))) {
        tableDelete(&vm.globals, OBJ_VAL(name));
        frame->ip = ip;
        runtimeError("Undefined variable '%s'.", name->chars);
        return INTERPRET_RUNTIME_ERROR;
      }
      break;
    }
    case OP_DEFINE_GLOBAL: {
      ObjString *name = READ_STRING();
      tableSet(&vm.globals, OBJ_VAL(name), peek(0));
      pop();
      break;
    }
    case OP_GET_UPVALUE: {
      uint8_t slot = READ_BYTE();
      push(*frame->closure->upvalues[slot]->location);
      break;
    }
    case OP_SET_UPVALUE: {
      uint8_t slot = READ_BYTE();
      *frame->closure->upvalues[slot]->location = peek(0);
      break;
    }
    case OP_GET_PROPERTY: {
      if (!IS_INSTANCE(peek(0))) {
        frame->ip = ip;
        runtimeError("Only instances have properties.");
        return INTERPRET_RUNTIME_ERROR;
      }
      ObjInstance *instance = AS_INSTANCE(peek(0));
      ObjString *name = READ_STRING();

      Value value;
      if (tableGet(&instance->fields, OBJ_VAL(name), &value)) {
        pop();
        push(value);
        break;
      }

      frame->ip = ip;
      if (!bindMethod(instance->klass, name)) {
        return INTERPRET_RUNTIME_ERROR;
      }
      frame = &vm.frames[vm.frameCount - 1];
      ip = frame->ip;
      break;
    }
    case OP_SET_PROPERTY: {
      if (!IS_INSTANCE(peek(1))) {
        frame->ip = ip;
        runtimeError("Only instances have fields.");
        return INTERPRET_RUNTIME_ERROR;
      }
      ObjInstance *instance = AS_INSTANCE(peek(1));
      tableSet(&instance->fields, OBJ_VAL(READ_STRING()), peek(0));
      Value value = pop();
      pop();
      push(value);
      break;
    }
    case OP_GET_SUPER: {
      ObjString *name = READ_STRING();
      ObjClass *superclass = AS_CLASS(pop());

      frame->ip = ip;
      if (!bindMethod(superclass, name)) {
        return INTERPRET_RUNTIME_ERROR;
      }
      frame = &vm.frames[vm.frameCount - 1];
      ip = frame->ip;
      break;
    }
    case OP_EQUAL: {
      Value b = pop();
      Value a = pop();
      push(BOOL_VAL(valuesEqual(a, b)));
      break;
    }
    case OP_GREATER:
      BINARY_OP(BOOL_VAL, >);
      break;
    case OP_LESS:
      BINARY_OP(BOOL_VAL, <);
      break;
    case OP_ADD:
      if (IS_STRING(peek(0)) && IS_STRING(peek(1))) {
        concatenate();
      } else if (IS_NUMBER(peek(0)) && IS_NUMBER(peek(1))) {
        double b = AS_NUMBER(pop());
        double a = AS_NUMBER(pop());

        push(NUMBER_VAL(a + b));
      } else {
        frame->ip = ip;
        runtimeError("Operands must be two numbers or two strings.");
        return INTERPRET_RUNTIME_ERROR;
      }
      break;
    case OP_SUBTRACT:
      BINARY_OP(NUMBER_VAL, -);
      break;
    case OP_MULTIPLY:
      BINARY_OP(NUMBER_VAL, *);
      break;
    case OP_DIVIDE:
      BINARY_OP(NUMBER_VAL, /);
      break;
    case OP_NOT:
      vm.stackTop[-1] = BOOL_VAL(isFalsey(vm.stackTop[-1]));
      break;
    case OP_NEGATE:
      if (!IS_NUMBER(peek(0))) {
        frame->ip = ip;
        runtimeError("Operand must be a number.");
        return INTERPRET_RUNTIME_ERROR;
      }
      vm.stackTop[-1] = NUMBER_VAL(-AS_NUMBER(vm.stackTop[-1]));
      break;
    case OP_PRINT: {
      printValue(pop());
      printf("\n");
      fflush(stdout);
      break;
    }
    case OP_JUMP: {
      uint16_t offset = READ_SHORT();
      ip += offset;
      break;
    }
    case OP_JUMP_IF_FALSE: {
      uint16_t offset = READ_SHORT();
      if (isFalsey(peek(0)))
        ip += offset;
      break;
    }
    case OP_LOOP: {
      uint16_t offset = READ_SHORT();
      ip -= offset;
      break;
    }
    case OP_CALL: {
      int argCount = READ_BYTE();
      frame->ip = ip;
      if (!callValue(peek(argCount), argCount)) {
        return INTERPRET_RUNTIME_ERROR;
      }
      frame = &vm.frames[vm.frameCount - 1];
      ip = frame->ip;
      break;
    }
    case OP_INVOKE: {
      ObjString *method = READ_STRING();
      int argCount = READ_BYTE();
      frame->ip = ip;
      if (!invoke(method, argCount)) {
        return INTERPRET_RUNTIME_ERROR;
      }
      frame = &vm.frames[vm.frameCount - 1];
      ip = frame->ip;
      break;
    }
    case OP_SUPER_INVOKE: {
      ObjString *method = READ_STRING();
      int argCount = READ_BYTE();
      ObjClass *superclass = AS_CLASS(pop());
      frame->ip = ip;
      if (!invokeFromClass(superclass, method, argCount)) {
        return INTERPRET_RUNTIME_ERROR;
      }
      frame = &vm.frames[vm.frameCount - 1];
      ip = frame->ip;
      break;
    }
    case OP_CLOSURE: {
      ObjFunction *function = AS_FUNCTION(READ_CONSTANT());
      ObjClosure *closure = newClosure(function);
      push(OBJ_VAL(closure));
      for (int i = 0; i < closure->upvalueCount; i++) {
        uint8_t isLocal = READ_BYTE();
        uint8_t index = READ_BYTE();
        if (isLocal) {
          closure->upvalues[i] = captureUpvalue(frame->slots + index);
        } else {
          closure->upvalues[i] = frame->closure->upvalues[index];
        }
      }
      break;
    }
    case OP_CLOSE_UPVALUE: {
      closeUpvalues(vm.stackTop - 1);
      pop();
      break;
    }
    case OP_CLASS:
      push(OBJ_VAL(newClass(READ_STRING())));
      break;
    case OP_INHERIT: {
      Value superclass = peek(1);
      ObjClass *subclass = AS_CLASS(peek(0));

      if (!IS_CLASS(superclass)) {
        frame->ip = ip;
        runtimeError("Superclass must be a class.");
        return INTERPRET_RUNTIME_ERROR;
      }

      tableAddAll(&AS_CLASS(superclass)->methods, &subclass->methods);
      pop(); // Subclass
      break;
    }
    case OP_METHOD:
      defineMethod(READ_STRING());
      break;
    case OP_RETURN: {
      Value result = pop();
      closeUpvalues(frame->slots);
      vm.frameCount--;
      if (vm.frameCount == 0) {
        pop();
        return INTERPRET_OK;
      }

      vm.stackTop = frame->slots;
      push(result);
      frame = &vm.frames[vm.frameCount - 1];
      ip = frame->ip;
      break;
    }
    }
  }
#undef READ_BYTE
#undef READ_SHORT
#undef READ_STRING
#undef READ_CONSTANT
#undef BINARY_OP
}

InterpretResult interpret(const char *source) {
  ObjFunction *function = compile(source);
  if (function == NULL)
    return INTERPRET_COMPILE_ERROR;

  push(OBJ_VAL(function));
  callFunction(function, 0);

  return run();
}

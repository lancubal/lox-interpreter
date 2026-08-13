#ifndef clox_object_h
#define clox_object_h

#include "chunk.h"
#include "common.h"
#include "table.h"
#include "value.h"

#define OBJ_TYPE(value) (AS_OBJ(value)->type)

#define IS_BOUND_METHOD(value) isObjType(value, OBJ_BOUND_METHOD)
#define IS_CLASS(value) isObjType(value, OBJ_CLASS)
#define IS_INSTANCE(value) isObjType(value, OBJ_INSTANCE)
#define IS_FUNCTION(value) isObjType(value, OBJ_FUNCTION)
#define IS_NATIVE(value) isObjType(value, OBJ_NATIVE)
#ifndef NAN_BOXING
#define IS_STRING(value) (IS_SMALL_STRING(value) || isObjType(value, OBJ_STRING))
#else
#define IS_STRING(value) isObjType(value, OBJ_STRING)
#endif
#define IS_CLOSURE(value) isObjType(value, OBJ_CLOSURE)

#define AS_BOUND_METHOD(value) ((ObjBoundMethod *)AS_OBJ(value))
#define AS_CLASS(value) ((ObjClass *)AS_OBJ(value))
#define AS_INSTANCE(value) ((ObjInstance *)AS_OBJ(value))
#define AS_CLOSURE(value) ((ObjClosure *)AS_OBJ(value))
#define AS_FUNCTION(value) ((ObjFunction *)AS_OBJ(value))
#define AS_NATIVE_OBJ(value) ((ObjNative *)AS_OBJ(value))
#define AS_NATIVE(value) (((ObjNative *)AS_OBJ(value))->function)
#define AS_STRING(value) ((ObjString *)AS_OBJ(value))
#define AS_CSTRING(value) (((ObjString *)AS_OBJ(value))->chars)

typedef enum {
  OBJ_CLOSURE,
  OBJ_CLASS,
  OBJ_BOUND_METHOD,
  OBJ_INSTANCE,
  OBJ_STRING,
  OBJ_FUNCTION,
  OBJ_NATIVE,
  OBJ_UPVALUE,
} ObjType;

struct Obj {
  ObjType type;
  bool isMarked;
  struct Obj *next;
};

typedef struct ObjClass ObjClass;
typedef struct ObjFunction ObjFunction;

struct ObjFunction {
  Obj obj;
  int arity;
  int upvalueCount;
  Chunk chunk;
  ObjString *name;
  ObjClass *enclosingClass;
};

typedef bool (*NativeFn)(int argCount, Value *args, Value *result);

typedef struct {
  Obj obj;
  NativeFn function;
  int arity;
} ObjNative;

struct ObjString {
  Obj obj;
  int length;
  uint32_t hash;
  bool isOwned;
  const char *chars;
};

typedef struct ObjUpvalue {
  Obj obj;
  Value *location;
  Value closed;
  struct ObjUpvalue *next;
} ObjUpvalue;

typedef struct {
  Obj obj;
  ObjFunction *function;
  ObjUpvalue **upvalues;
  int upvalueCount;
} ObjClosure;

struct ObjClass {
  Obj obj;
  ObjString *name;
  ObjClass *superclass;
  Table methods;
  Value initializer;
};

typedef struct {
  Obj obj;
  ObjClass *klass;
  Table fields;
} ObjInstance;

typedef struct {
  Obj obj;
  Value receiver;
  ObjClosure *method;
} ObjBoundMethod;

ObjBoundMethod *newBoundMethod(Value receiver, ObjClosure *method);
ObjClass *newClass(ObjString *name);
ObjInstance *newInstance(ObjClass *klass);
ObjClosure *newClosure(ObjFunction *function);
ObjFunction *newFunction();
ObjNative *newNative(NativeFn function, int arity);
ObjString *takeString(char *chars, int length);
ObjString *copyString(const char *chars, int length);
ObjUpvalue *newUpvalue(Value *slot);
void printObject(Value value);

static inline bool isObjType(Value value, ObjType type) {
  return IS_OBJ(value) && AS_OBJ(value)->type == type;
}

static inline const char *valueAsCString(const Value *val, int *outLen) {
#ifndef NAN_BOXING
  if (IS_SMALL_STRING(*val)) {
    if (outLen != NULL) *outLen = (int)val->as.smallString.length;
    return val->as.smallString.chars;
  }
#endif
  if (isObjType(*val, OBJ_STRING)) {
    ObjString *str = (ObjString *)AS_OBJ(*val);
    if (outLen != NULL) *outLen = str->length;
    return str->chars;
  }
  if (outLen != NULL) *outLen = 0;
  return "";
}

#endif

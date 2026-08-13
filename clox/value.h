#ifndef clox_value_h
#define clox_value_h

#include "common.h"
#include <string.h>

typedef struct Obj Obj;
typedef struct ObjString ObjString;

#ifdef NAN_BOXING

#define SIGN_BIT ((uint64_t)0x8000000000000000)
#define QNAN ((uint64_t)0x7ffc000000000000)

#define TAG_NIL 1       // 01
#define TAG_FALSE 2     // 10
#define TAG_TRUE 3      // 11
#define TAG_EMPTY 4     // 100
#define TAG_TOMBSTONE 5 // 101

typedef uint64_t Value;

#define IS_BOOL(value) (((value) | 1) == TRUE_VAL)
#define IS_NIL(value) ((value) == NIL_VAL)
#define IS_EMPTY(value) ((value) == EMPTY_VAL)
#define IS_TOMBSTONE(value) ((value) == TOMBSTONE_VAL)
#define IS_NUMBER(value) (((value) & QNAN) != QNAN)
#define IS_OBJ(value) (((value) & (QNAN | SIGN_BIT)) == (QNAN | SIGN_BIT))

#define AS_BOOL(value) ((value) == TRUE_VAL)
#define AS_NUMBER(value) valueToNum(value)
#define AS_OBJ(value) ((Obj *)(uintptr_t)((value) & ~(SIGN_BIT | QNAN)))

static inline double valueToNum(Value value) {
  double num;
  memcpy(&num, &value, sizeof(Value));
  return num;
}

#define BOOL_VAL(b) ((b) ? TRUE_VAL : FALSE_VAL)
#define FALSE_VAL ((Value)(uint64_t)(QNAN | TAG_FALSE))
#define TRUE_VAL ((Value)(uint64_t)(QNAN | TAG_TRUE))
#define NIL_VAL ((Value)(uint64_t)(QNAN | TAG_NIL))
#define EMPTY_VAL ((Value)(uint64_t)(QNAN | TAG_EMPTY))
#define TOMBSTONE_VAL ((Value)(uint64_t)(QNAN | TAG_TOMBSTONE))
#define NUMBER_VAL(num) numToValue(num)
#define OBJ_VAL(obj) (Value)(SIGN_BIT | QNAN | (uint64_t)(uintptr_t)(obj))

static inline Value numToValue(double num) {
  Value value;
  memcpy(&value, &num, sizeof(double));
  return value;
}

#else

#define SMALL_STRING_MAX 6

typedef enum {
  VAL_BOOL,
  VAL_NIL,
  VAL_NUMBER,
  VAL_OBJ,
  VAL_EMPTY,
  VAL_TOMBSTONE,
  VAL_SMALL_STRING
} ValueType;

typedef struct {
  ValueType type;
  union {
    bool boolean;
    double number;
    Obj *obj;
    struct {
      uint8_t length;
      char chars[7];
    } smallString;
  } as;
} Value;

#define IS_SMALL_STRING(value) ((value).type == VAL_SMALL_STRING)
#define IS_BOOL(value) ((value).type == VAL_BOOL)
#define IS_NIL(value) ((value).type == VAL_NIL)
#define IS_EMPTY(value) ((value).type == VAL_EMPTY)
#define IS_TOMBSTONE(value) ((value).type == VAL_TOMBSTONE)
#define IS_NUMBER(value) ((value).type == VAL_NUMBER)
#define IS_OBJ(value) ((value).type == VAL_OBJ)

#define AS_BOOL(value) ((value).as.boolean)
#define AS_NUMBER(value) ((value).as.number)
#define AS_OBJ(value) ((value).as.obj)

#define BOOL_VAL(value) ((Value){VAL_BOOL, {.boolean = value}})
#define NIL_VAL ((Value){VAL_NIL, {.number = 0}})
#define EMPTY_VAL ((Value){VAL_EMPTY, {.number = 0}})
#define TOMBSTONE_VAL ((Value){VAL_TOMBSTONE, {.number = 0}})
#define NUMBER_VAL(value) ((Value){VAL_NUMBER, {.number = value}})
#define OBJ_VAL(object) ((Value){VAL_OBJ, {.obj = (Obj *)object}})

static inline Value SMALL_STRING_VAL(const char *chars, int length) {
  Value val;
  val.type = VAL_SMALL_STRING;
  val.as.smallString.length = (uint8_t)length;
  memcpy(val.as.smallString.chars, chars, length);
  val.as.smallString.chars[length] = '\0';
  return val;
}

#endif

typedef struct {
  int capacity;
  int count;
  Value *values;
} ValueArray;

bool valuesEqual(Value a, Value b);
void initValueArray(ValueArray *array);
void writeValueArray(ValueArray *array, Value value);
void freeValueArray(ValueArray *array);
void printValue(Value value);

#endif

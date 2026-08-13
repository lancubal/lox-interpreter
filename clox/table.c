#include <stdint.h>
#include <string.h>
#include <sys/types.h>

#include "memory.h"
#include "object.h"
#include "table.h"
#include "value.h"

#define TABLE_MAX_LOAD 0.75

uint32_t hashValue(Value key) {
#ifdef NAN_BOXING
  if (IS_OBJ(key)) {
    Obj *obj = AS_OBJ(key);
    if (obj->type == OBJ_STRING) {
      return ((ObjString *)obj)->hash;
    }
    uint64_t bits = (uint64_t)(uintptr_t)obj;
    bits = ((bits >> 32) ^ bits) * 0x45d9f3b;
    return (uint32_t)bits;
  }
  uint64_t bits = key;
  bits = ((bits >> 32) ^ bits) * 0x45d9f3b;
  bits = ((bits >> 32) ^ bits) * 0x45d9f3b;
  bits = (bits >> 32) ^ bits;
  return (uint32_t)bits;
#else
  switch (key.type) {
  case VAL_BOOL:
    return AS_BOOL(key) ? 2 : 3;
  case VAL_NIL:
    return 0;
  case VAL_NUMBER: {
    double num = AS_NUMBER(key);
    uint64_t bits;
    memcpy(&bits, &num, sizeof(double));
    bits = ((bits >> 32) ^ bits) * 0x45d9f3b;
    return (uint32_t)bits;
  }
  case VAL_SMALL_STRING: {
    uint32_t hash = 2166136261u;
    for (int i = 0; i < key.as.smallString.length; i++) {
      hash ^= (uint32_t)key.as.smallString.chars[i];
      hash *= 16777619;
    }
    return hash;
  }
  case VAL_OBJ: {
    Obj *obj = AS_OBJ(key);
    if (obj->type == OBJ_STRING) {
      return ((ObjString *)obj)->hash;
    }
    uint64_t bits = (uint64_t)(uintptr_t)obj;
    bits = ((bits >> 32) ^ bits) * 0x45d9f3b;
    return (uint32_t)bits;
  }
  default:
    return 0;
  }
#endif
}

void initTable(Table *table) {
  table->count = 0;
  table->capacity = 0;
  table->entries = NULL;
}

void freeTable(Table *table) {
  FREE_ARRAY(Entry, table->entries, table->capacity);
  initTable(table);
}

static Entry *findEntry(Entry *entries, int capacity, Value key) {
  uint32_t index = hashValue(key) & (capacity - 1);
  Entry *tombstone = NULL;

  for (;;) {
    Entry *entry = &entries[index];

    if (IS_EMPTY(entry->key)) {
      return tombstone != NULL ? tombstone : entry;
    } else if (IS_TOMBSTONE(entry->key)) {
      if (tombstone == NULL)
        tombstone = entry;
    } else if (valuesEqual(entry->key, key)) {
      return entry;
    }

    index = (index + 1) & (capacity - 1);
  }
}

bool tableGet(Table *table, Value key, Value *value) {
  if (table->count == 0)
    return false;

  Entry *entry = findEntry(table->entries, table->capacity, key);
  if (IS_EMPTY(entry->key) || IS_TOMBSTONE(entry->key))
    return false;

  *value = entry->value;
  return true;
}

static void adjustCapacity(Table *table, int capacity) {
  Entry *entries = ALLOCATE(Entry, capacity);
  for (int i = 0; i < capacity; i++) {
    entries[i].key = EMPTY_VAL;
    entries[i].value = NIL_VAL;
  }

  int count = 0;
  for (int i = 0; i < table->capacity; i++) {
    Entry *entry = &table->entries[i];
    if (IS_EMPTY(entry->key) || IS_TOMBSTONE(entry->key))
      continue;

    Entry *dest = findEntry(entries, capacity, entry->key);
    dest->key = entry->key;
    dest->value = entry->value;
    count++;
  }

  FREE_ARRAY(Entry, table->entries, table->capacity);

  table->entries = entries;
  table->capacity = capacity;
  table->count = count;
}

bool tableSet(Table *table, Value key, Value value) {
  if (table->count + 1 > table->capacity * TABLE_MAX_LOAD) {
    int capacity = GROW_CAPACITY(table->capacity);
    adjustCapacity(table, capacity);
  }
  Entry *entry = findEntry(table->entries, table->capacity, key);
  bool isNewKey = IS_EMPTY(entry->key) || IS_TOMBSTONE(entry->key);
  if (isNewKey && IS_EMPTY(entry->key))
    table->count++;

  entry->key = key;
  entry->value = value;
  return isNewKey;
}

bool tableDelete(Table *table, Value key) {
  if (table->count == 0)
    return false;

  Entry *entry = findEntry(table->entries, table->capacity, key);
  if (IS_EMPTY(entry->key) || IS_TOMBSTONE(entry->key))
    return false;

  entry->key = TOMBSTONE_VAL;
  entry->value = NIL_VAL;
  return true;
}

void tableAddAll(Table *from, Table *to) {
  for (int i = 0; i < from->capacity; i++) {
    Entry *entry = &from->entries[i];
    if (!IS_EMPTY(entry->key) && !IS_TOMBSTONE(entry->key)) {
      tableSet(to, entry->key, entry->value);
    }
  }
}

ObjString *tableFindString(Table *table, const char *chars, int length,
                           uint32_t hash) {
  if (table->count == 0)
    return NULL;

  uint32_t index = hash & (table->capacity - 1);

  for (int i = 0; i < table->capacity; i++) {
    Entry *entry = &table->entries[(index + i) & (table->capacity - 1)];
    if (IS_EMPTY(entry->key)) {
      return NULL;
    } else if (!IS_TOMBSTONE(entry->key) && IS_OBJ(entry->key)) {
      Obj *obj = AS_OBJ(entry->key);
      if (obj->type == OBJ_STRING) {
        ObjString *string = (ObjString *)obj;
        if (string->length == length && string->hash == hash &&
            memcmp(string->chars, chars, length) == 0) {
          return string;
        }
      }
    }
  }
  return NULL;
}

void tableRemoveWhite(Table *table) {
  for (int i = 0; i < table->capacity; i++) {
    Entry *entry = &table->entries[i];
    if (!IS_EMPTY(entry->key) && !IS_TOMBSTONE(entry->key) &&
        IS_OBJ(entry->key) && !AS_OBJ(entry->key)->isMarked) {
      tableDelete(table, entry->key);
    }
  }
}

void markTable(Table *table) {
  for (int i = 0; i < table->capacity; i++) {
    Entry *entry = &table->entries[i];
    if (!IS_EMPTY(entry->key) && !IS_TOMBSTONE(entry->key)) {
      markValue(entry->key);
      markValue(entry->value);
    }
  }
}

#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include "common.h"
#include "object.h"
#include "table.h"
#include "value.h"
#include "vm.h"

static double getElapsedMs(clock_t start, clock_t end) {
  return ((double)(end - start)) / CLOCKS_PER_SEC * 1000.0;
}

// Benchmark 1: Sequential Numeric & String Key Insert & Lookup
static void benchSequentialInserts(int numEntries) {
  Table table;
  initTable(&table);

  clock_t start = clock();
  for (int i = 0; i < numEntries; i++) {
    tableSet(&table, NUMBER_VAL(i), NUMBER_VAL(i * 2));
  }
  clock_t insertEnd = clock();

  int foundCount = 0;
  for (int i = 0; i < numEntries; i++) {
    Value val;
    if (tableGet(&table, NUMBER_VAL(i), &val)) {
      foundCount++;
    }
  }
  clock_t lookupEnd = clock();

  printf("  [Bench 1 - Sequential Number Keys] Insert %d entries: %.2f ms | Lookup: %.2f ms (Found: %d/%d)\n",
         numEntries, getElapsedMs(start, insertEnd), getElapsedMs(insertEnd, lookupEnd), foundCount, numEntries);

  freeTable(&table);
}

// Benchmark 2: Read-Heavy Workload (95% Read / 5% Write)
static void benchReadHeavyWorkload(int tableSize, int totalOps) {
  Table table;
  initTable(&table);

  for (int i = 0; i < tableSize; i++) {
    tableSet(&table, NUMBER_VAL(i), NUMBER_VAL(i * 10));
  }

  clock_t start = clock();
  int foundCount = 0;
  for (int i = 0; i < totalOps; i++) {
    int keyIdx = rand() % (tableSize * 2); // 50% hit rate
    Value val;
    if (tableGet(&table, NUMBER_VAL(keyIdx), &val)) {
      foundCount++;
    }
  }
  clock_t end = clock();

  printf("  [Bench 2 - Read-Heavy Workload] %d Lookups (Size %d): %.2f ms (Hits: %d)\n",
         totalOps, tableSize, getElapsedMs(start, end), foundCount);

  freeTable(&table);
}

// Benchmark 3: Deletion & Tombstone Churn Benchmark
static void benchTombstoneChurn(int initialSize, int churnCycles) {
  Table table;
  initTable(&table);

  for (int i = 0; i < initialSize; i++) {
    tableSet(&table, NUMBER_VAL(i), NUMBER_VAL(i));
  }

  clock_t start = clock();
  for (int cycle = 0; cycle < churnCycles; cycle++) {
    // Delete half entries (leaves tombstones)
    for (int i = 0; i < initialSize / 2; i++) {
      tableDelete(&table, NUMBER_VAL(i));
    }
    // Re-insert new entries
    for (int i = 0; i < initialSize / 2; i++) {
      tableSet(&table, NUMBER_VAL(initialSize + cycle * 1000 + i), NUMBER_VAL(i));
    }
  }
  clock_t end = clock();

  printf("  [Bench 3 - Tombstone Churn] %d delete/reinsert cycles (Size %d): %.2f ms\n",
         churnCycles, initialSize, getElapsedMs(start, end));

  freeTable(&table);
}

// Benchmark 4: String Key Workload
static void benchStringKeys(int numStrings) {
  Table table;
  initTable(&table);

  ObjString **strings = malloc(sizeof(ObjString*) * numStrings);
  for (int i = 0; i < numStrings; i++) {
    char buf[32];
    int len = sprintf(buf, "var_name_%d", i);
    strings[i] = copyString(buf, len);
  }

  clock_t start = clock();
  for (int i = 0; i < numStrings; i++) {
    tableSet(&table, OBJ_VAL(strings[i]), NUMBER_VAL(i));
  }
  clock_t insertEnd = clock();

  int foundCount = 0;
  for (int i = 0; i < numStrings; i++) {
    Value val;
    if (tableGet(&table, OBJ_VAL(strings[i]), &val)) {
      foundCount++;
    }
  }
  clock_t lookupEnd = clock();

  printf("  [Bench 4 - String Keys] Insert %d strings: %.2f ms | Lookup: %.2f ms (Found: %d/%d)\n",
         numStrings, getElapsedMs(start, insertEnd), getElapsedMs(insertEnd, lookupEnd), foundCount, numStrings);

  free(strings);
  freeTable(&table);
}

int main() {
  initVM();
  printf("=== CLOX HASH TABLE BENCHMARK SUITE ===\n");
  benchSequentialInserts(10000);
  benchReadHeavyWorkload(5000, 100000);
  benchTombstoneChurn(2000, 50);
  benchStringKeys(2000);
  printf("=======================================\n");
  freeVM();
  return 0;
}

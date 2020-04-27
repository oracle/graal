/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include "harness.h"

#define NUM_JOINS (5)
#define FREELIST_ENTRY_COUNT (120000)
#define LARGER_DATA_SIZE (360000)
#define SMALLER_DATA_SIZE (90000)
#define TABLE_SIZE ((int) (SMALLER_DATA_SIZE * 2))

int64_t smaller_data[2 * SMALLER_DATA_SIZE];
int64_t larger_data[2 * LARGER_DATA_SIZE];

int32_t collatz(int32_t n0) {
  int32_t steps = 0;
  int32_t n = n0;
  while (n > 1) {
    if (n % 2 == 0) {
      n = n / 2;
    } else {
      n = 3 * n + 1;
    }
    steps++;
  }
  return steps;
}

int32_t hash(int64_t x0) {
  int64_t x = x0;
  x = ((x >> 16) ^ x) * 0x45d9f3b;
  x = ((x >> 16) ^ x) * 0x45d9f3b;
  x = (x >> 16) ^ x;
  return (int32_t) x;
}

typedef struct _entry {
  int64_t key;
  int64_t val;
  struct _entry* next;
} entry;

void entry_ctor(entry* e, int64_t key, int64_t val) {
  e->key = key;
  e->val = val;
  e->next = NULL;
}

typedef struct {
  int32_t size;
  int32_t capacity;
  entry* entries[];
} htable;

typedef struct _chunk_self {
  struct _chunk_self* next;
  char data[];
} chunk;

typedef struct {
  size_t total_chunk_size;
  size_t chunk_count;
  chunk* head;
  char region[];
} freelist;

int8_t _entry_freelist_memory[
  sizeof(freelist) + FREELIST_ENTRY_COUNT * (sizeof(chunk) + sizeof(entry))
];
freelist* entry_freelist = (freelist*) _entry_freelist_memory;

void initialize_freelist(freelist* f, size_t total_chunk_size, size_t chunk_count) {
  f->total_chunk_size = total_chunk_size;
  f->chunk_count = chunk_count;
  for (size_t i = 0; i < chunk_count; i++) {
    chunk* p = (chunk*) ((f->region) + i * total_chunk_size);
    if (i == chunk_count - 1) {
      p->next = NULL;
    } else {
      p->next = (chunk*) ((f->region) + (i + 1) * total_chunk_size);
    }
  }
  f->head = (chunk*) f->region;
}

void* allocate(freelist* f) {
  chunk* curr = f->head;
  if (curr == NULL) {
    return NULL;
  }
  f->head = curr->next;
  memset(curr->data, 0, f->total_chunk_size - sizeof(struct _chunk_self*));
  return (void*) curr->data;
}

void deallocate(freelist* f, void* segment) {
  chunk* c = (chunk*) (((char*) segment) - sizeof(struct _chunk_self*));
  c->next = f->head;
  f->head = c;
}

int8_t _ht_memory[
  sizeof(htable) + TABLE_SIZE * sizeof(entry*)
];
htable* join_table = (htable*) _ht_memory;

void ht_initialize(htable* ht) {
  ht->size = 0;
  ht->capacity = TABLE_SIZE;
  for (entry** curr = ht->entries; curr < ht->entries + TABLE_SIZE; curr++) {
    *curr = NULL;
  }
}

int ht_put(htable* ht, int64_t key, int64_t val) {
  // Find the location for the entry.
  int32_t h = hash(key);
  if (h < 0) {
    h = -h;
  }
  int32_t index = h % TABLE_SIZE;
  entry* pe = ht->entries[index];

  // Overwrite an existing key if necessary.
  while (pe != NULL) {
    if (key == pe->key) {
      pe->val = val;
      return 1;
    }
    pe = pe->next;
  }

  entry* e = (entry*) allocate(entry_freelist);
  if (e == NULL) {
    return 0;
  }
  entry_ctor(e, key, val);

  e->next = ht->entries[index];
  ht->entries[index] = e;

  return 1;
}

int ht_get(htable* ht, int64_t key, int64_t* val) {
  int32_t h = hash(key);
  if (h < 0) {
    h = -h;
  }
  int32_t index = h % TABLE_SIZE;

  entry* e = ht->entries[index];
  while (e != NULL) {
    if (key == e->key) {
      *val = e->val;
      return 1;
    }
    e = e->next;
  }

  return 0;
}

int ht_free(htable* ht) {
  int entryCount = 0;

  for (entry** curr = ht->entries; curr < ht->entries + TABLE_SIZE; curr++) {
    entry* e = *curr;
    while (e != NULL) {
      entry* junk = e;
      e = e->next;
      deallocate(entry_freelist, junk);
      entryCount++;
    }

    *curr = NULL;
  }

  return entryCount;
}

int hash_join() {
  int32_t checksum = 0;

  // Initialize the memory pool.
  initialize_freelist(entry_freelist, sizeof(chunk) + sizeof(entry), FREELIST_ENTRY_COUNT);

  for (int k = 0; k < NUM_JOINS; k++) {
    // Build a hash table.
    ht_initialize(join_table);
    for (int i = 0; i < SMALLER_DATA_SIZE; i++) {
      int64_t key = smaller_data[2 * i + 0];
      int64_t val = smaller_data[2 * i + 1];
      if (ht_put(join_table, key, val) == 0) {
        return -1;
      }
    }

    // Perform the join.
    for (int i = 0; i < LARGER_DATA_SIZE; i++) {
      int64_t key = larger_data[2 * i + 0];
      int64_t smaller_value = 0;
      if (ht_get(join_table, key, &smaller_value) != 0) {
        int64_t value = larger_data[2 * i + 1];
        checksum += value + smaller_value;
      }
    }

    // Deallocate the hash table.
    checksum += ht_free(join_table);
  }

  return (int32_t) checksum;
}

int benchmarkIterationsCount() {
  return 40;
}

void benchmarkSetupOnce() {
  for (int32_t i = 0; i < SMALLER_DATA_SIZE; i++) {
    int64_t key = 9 * i;
    int64_t val = collatz(i);
    smaller_data[2 * i + 0] = key;
    smaller_data[2 * i + 1] = val;
  }
  for (int32_t i = 0; i < LARGER_DATA_SIZE; i++) {
    int64_t key = i;
    int64_t val = 3 * i * i + i + 1;
    larger_data[2 * i + 0] = key;
    larger_data[2 * i + 1] = val;
  }
}

void benchmarkSetupEach() {
}

void benchmarkTeardownEach(char* outputFile) {
}

int benchmarkRun() {
  return hash_join();
}

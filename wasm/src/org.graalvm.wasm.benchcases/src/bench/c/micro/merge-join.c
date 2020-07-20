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
#include "harness.h"

#define FREELIST_SIZE (1000000)
#define ELEMENT_COUNT (400000)

int table1[ELEMENT_COUNT];
int table2[ELEMENT_COUNT];

void initializeData(int* table, int size, int seed) {
  for (int i = 0; i < size; i++) {
    table[i] = (seed * seed) % size + (i * seed) % size;
  }
}

typedef struct _node_self {
  int element;
  struct _node_self* next;
} node;

int padding[1024];
node freelist[FREELIST_SIZE];
node* freelistHead;

void initializeMemory() {
  freelistHead = &freelist[0];
  for (int i = 0; i < FREELIST_SIZE; i++) {
    freelist[i].element = 0;
    freelist[i].next = i == FREELIST_SIZE - 1 ? NULL : &freelist[i + 1];
  }
}

node* allocate() {
  node* n = freelistHead;
  if (n != NULL) {
    freelistHead = n->next;
  }
  return n;
}

void deallocate(node* n) {
  n->next = freelistHead;
  freelistHead = n;
}

void constructor(node* n) {
  n->element = 0;
  n->next = NULL;
}

node* load(int* table, int size) {
  node* head = NULL;
  node* current = NULL;
  for (int i = 0; i < size; i++) {
    node* next = allocate();
    constructor(next);
    if (current != NULL) {
      current->next = next;
    } else {
      head = next;
    }
    next->element = table[i];
    current = next;
  }
  return head;
}

int computeLength(node* list) {
  int length = 0;
  for (node* current = list; current != NULL; current = current->next) {
    length++;
  }
  return length;
}

node* cut(node* list, int count) {
  // The count must be greater than zero.
  node* firstLast = NULL;
  node* secondHead = list;
  int left = count;
  for (; left > 0; left--) {
    firstLast = secondHead;
    secondHead = secondHead->next;
  }
  firstLast->next = NULL;
  return secondHead;
}

node* merge(node* list1, node* list2) {
  node head;
  constructor(&head);
  node* current = &head;
  while (list1 != NULL && list2 != NULL) {
    if (list1->element < list2->element) {
      current->next = list1;
      list1 = list1->next;
      current = current->next;
      current->next = NULL;
    } else {
      current->next = list2;
      list2 = list2->next;
      current = current->next;
      current->next = NULL;
    }
  }
  if (list1 != NULL) {
    current->next = list1;
  } else if (list2 != NULL) {
    current->next = list2;
  }
  node* result = head.next;
  return result;
}

node* mergeSort(node* list, int length) {
  if (length < 2) {
    return list;
  }

  int firstLength = length / 2;
  int secondLength = length - firstLength;
  node* first = list;
  node* second = cut(first, firstLength);
  first = mergeSort(first, firstLength);
  second = mergeSort(second, secondLength);
  node* result = merge(first, second);

  return result;
}

node* sort(node* list) {
  int length = computeLength(list);
  return mergeSort(list, length);
}

node* join(node* list1, node* list2) {
  node head;
  constructor(&head);
  node* current = &head;
  while (list1 != NULL && list2 != NULL) {
    if (list1->element < list2->element) {
      node* junk = list1;
      list1 = list1->next;
      deallocate(junk);
    } else if (list1->element > list2->element) {
      node* junk = list2;
      list2 = list2->next;
      deallocate(junk);
    } else {
      current->next = allocate();
      if (current->next == NULL) {
        abort();
      }
      current = current->next;
      constructor(current);
      current->element = list1->element;
      node* junk1 = list1;
      node* junk2 = list2;
      list1 = list1->next;
      list2 = list2->next;
      deallocate(junk1);
      deallocate(junk2);
    }
  }
  node* result = head.next;
  return result;
}

int checksum(node* list) {
  int hash = computeLength(list);
  for (node* current = list; current != NULL; current = current->next) {
    hash ^= current->element;
  }
  return hash;
}

int joinLength() {
  // Load the data into two lists.
  node* list1 = load(table1, ELEMENT_COUNT);
  node* list2 = load(table2, ELEMENT_COUNT);

  // Sort each list.
  list1 = sort(list1);
  list2 = sort(list2);

  // Compute intermediate checksums.
  int cs1 = checksum(list1);
  int cs2 = checksum(list2);

  // Pairwise traverse the lists, and merge them into the final list.
  node* joined = join(list1, list2);

  // Return the checksum of the resulting list.
  return cs1 + cs2 + checksum(joined);
}

int benchmarkIterationsCount() {
  return 50;
}

void benchmarkSetupOnce() {
  int seed1 = 21;
  int seed2 = 10;
  initializeData(table1, ELEMENT_COUNT, seed1);
  initializeData(table2, ELEMENT_COUNT, seed2);
}

void benchmarkSetupEach() {
  initializeMemory();
}

void benchmarkTeardownEach(char* outputFile) {
}

int benchmarkRun() {
  return joinLength();
}

/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "harness.h"

#ifndef DATA_COUNT
#define DATA_COUNT (1000000)
#endif

int64_t data[DATA_COUNT];

int compare_ints(const void* a, const void* b) {
  const int64_t* da = (const int64_t*) a;
  const int64_t* db = (const int64_t*) b;
  return (*da > *db) - (*db > *da);
}

int benchmarkIterationsCount() {
  return 10;
}

void benchmarkSetupOnce() {
}

void benchmarkSetupEach() {
  for (int64_t i = 0; i < DATA_COUNT; i++) {
    int64_t key = (i + i / 10000 * i) % (5 * DATA_COUNT);
    data[i] = key;
  }
}

void benchmarkTeardownEach(char* outputFile) {
}

int benchmarkRun() {
  qsort(data, DATA_COUNT, sizeof(int64_t), compare_ints);

  int64_t density = 0;
  for (int i = 0; i < DATA_COUNT; i++) {
    int64_t key = i;
    if (bsearch(&key, data, DATA_COUNT, sizeof(int64_t), compare_ints) != NULL) {
      density++;
    }
  }

  return density;
}

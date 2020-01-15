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
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

// ------------------------
// The following methods
// can be called inside
// the benchmarks.
// ------------------------

#ifdef DISABLE_TEST_API
void __testutil_save_binary_file(char* filename, unsigned char* data, int size) {
}
#else
extern void __testutil_save_binary_file(char* filename, unsigned char* data, int size);
#endif

// ------------------------
// The following methods
// must be implemented
// by the benchmarks.
// ------------------------

int benchmarkWarmupCount();

void benchmarkSetupOnce();

void benchmarkSetupEach();

void benchmarkTeardownEach();

int benchmarkRun();

// -------------------------
// Function pointer list.
//
// This is done to prevent the deoptimization of these functions.
// They need to be called by the harness programmatically,
// which means that it must be possible to look them up.
// -------------------------

int (*functionWarmupCount)() = benchmarkWarmupCount;

void (*functionSetupOnce)() = benchmarkSetupOnce;

void (*functionSetupEach)() = benchmarkSetupEach;

void (*functionTeardownEach)() = benchmarkTeardownEach;

int (*functionRun)() = benchmarkRun;

// -------------------------
// End of the function pointer list.
// -------------------------

void runIteration(char* label, int i) {
  functionSetupEach();
  struct timeval start, end;
  gettimeofday(&start, NULL);
  int result = functionRun();
  gettimeofday(&end, NULL);
  long start_t = start.tv_sec * 1000000 + start.tv_usec;
  long end_t = end.tv_sec * 1000000 + end.tv_usec;
  double time = (end_t - start_t) / 1000000.0;
  printf("%s: %d, result = %d, sec = %.3f, ops / sec = %.3f\n",
    label, i, result, time, 1.0 / time);
  functionTeardownEach();
}

int main() {
  functionSetupOnce();

  // Execute warmup.
  for (int i = 0; i != functionWarmupCount(); ++i) {
    runIteration("warmup", i);
  }

  // Execute the benchmark itself.
  runIteration("final run", 0);

  return 0;
}


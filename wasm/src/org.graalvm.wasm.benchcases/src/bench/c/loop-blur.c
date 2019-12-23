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
#include "harness.h"

#define X 1024
#define Y 1024
#define IMAGE_SIZE (X * Y)
#define N 8
#define ITERATIONS (IMAGE_SIZE * N)

uint32_t image[X][Y];

int benchmarkWarmupCount() {
  return 10;
}

void benchmarkSetupOnce() {
  for (uint32_t i = 0; i != X; ++i) {
    for (uint32_t j = 0; j != Y; ++j) {
      uint32_t value = 0;
      value |= (((i + 1) * 8) % 128) << 24;
      value |= (((i + 1) * 16) % 128) << 16;
      value |= (((i + 1) * 24) % 128) << 8;
      image[i][j] = value;
    }
  }
}

void benchmarkSetupEach() {
}

void benchmarkTeardownEach() {
}

int benchmarkRun() {
  uint32_t res = 0;
  for (uint32_t a = 0; a != X * N; ++a) {
    for (uint32_t b = 0; b != Y * N; ++b) {
      uint32_t r_sum = 0;
      uint32_t g_sum = 0;
      uint32_t b_sum = 0;

      uint32_t i = a % N;
      uint32_t j = b % N;

      uint32_t x_min = i > 0 ? i - 1 : i;
      uint32_t x_max = i < X - 1 ? i + 1 : i;
      uint32_t y_min = j > 0 ? j - 1 : j;
      uint32_t y_max = j < Y - 1 ? j + 1 : j;

      for (uint32_t ii = x_min; ii <= x_max; ++ii) {
        for (uint32_t jj = y_min; jj <= y_max; ++jj) {
          r_sum += (image[ii][jj] & 0xFF000000) >> 24;
          g_sum += (image[ii][jj] & 0x00FF0000) >> 16;
          b_sum += (image[ii][jj] & 0x0000FF00) >> 8;
        }
      }

      uint32_t num_pixels = (x_max - x_min + 1) * (y_max - y_min + 1);
      res = 0;
      res |= (r_sum / num_pixels) << 24;
      res |= (g_sum / num_pixels) << 16;
      res |= (b_sum / num_pixels) << 8;
      image[i][j] = res;
    }
  }
  return res / N;
}

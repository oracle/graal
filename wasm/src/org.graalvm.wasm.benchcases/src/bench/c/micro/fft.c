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
#include <complex.h>
#include <math.h>
#include <stdlib.h>
#include <stdint.h>
#include "harness.h"

#define PI 3.14159265358979323846

#define PERIOD (1 << 18)

double input_signal[PERIOD];
double complex output_spectre[PERIOD];

void fft_stage(double* signal, double complex* spectre, uint32_t N, uint32_t step) {
  if (N == 1) {
    spectre[0] = signal[0];
    return;
  }

  fft_stage(signal, spectre, N / 2, 2 * step);
  fft_stage(signal + step, spectre + N / 2, N / 2, 2 * step);

  for (uint32_t k = 0; k < N / 2; k++) {
    double complex a = spectre[k];
    double complex b = spectre[k + N / 2];
    double theta = -2 * PI * k / N;
    double costheta = cos(theta);
    double sintheta = sin(theta);
    double br = creal(b);
    double bi = cimag(b);
    double mr = costheta * br - sintheta * bi;
    double mi = costheta * bi + sintheta * br;
    spectre[k] = a + mr + mi * I;
    spectre[k + N / 2] = a - mr - mi * I;
  }
}

void fft(double* signal, double complex* spectre, uint32_t N) {
  fft_stage(signal, spectre, N, 1);
}

int run_ffts() {
  fft(input_signal, output_spectre, PERIOD);
  double checksum = 0.0;
  for (uint32_t k = 0; k < PERIOD; k++) {
    checksum += creal(output_spectre[k]);
  }
  // fprintf(stderr, "checksum = %f\n", checksum);
  int c64 = (int64_t) checksum;
  return (int) c64;
}

int benchmarkIterationsCount() {
  return 10;
}

void benchmarkSetupOnce() {
  for (uint32_t i = 0; i < PERIOD; i++) {
    input_signal[i] = (i * i % 27 + i % 64 - 51) % PERIOD;
  }
}

void benchmarkSetupEach() {
  for (uint32_t i = 0; i < PERIOD; i++) {
    output_spectre[i] = 0.0;
  }
}

void benchmarkTeardownEach(char* outputFile) {
}

int benchmarkRun() {
  return run_ffts();
}

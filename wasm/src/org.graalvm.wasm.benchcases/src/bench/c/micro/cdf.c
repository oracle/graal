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
#include <math.h>
#include <stdlib.h>
#include <stdint.h>
#include "harness.h"

#define DIFFERENTIAL_SIZE (0.0002)
#define PI 3.14159265358979323846

double inverseGaussian(double x, double mu, double lambda) {
  double exponent = -lambda * (x - mu) * (x - mu) / (2 * mu * mu * x);
  double factor = sqrt(lambda / (2 * PI * x * x * x));
  return factor * exp(exponent);
}

double logCauchy(double x, double mu, double sigma) {
  double diff = log(x) - mu;
  return 1 / (x * PI) * sigma / (diff * diff + sigma * sigma);
}

double pareto(double x, double alpha, double xm) {
  if (x < xm) {
    return 0.0;
  } else {
    return alpha * pow(xm, alpha) / pow(x, alpha + 1);
  }
}

int32_t integrate() {
  double start = 1.0;
  double end = 1000.0;

  double p0 = 0.0;
  for (double x = start; x < end; x += DIFFERENTIAL_SIZE) {
    p0 += DIFFERENTIAL_SIZE * inverseGaussian(x, 4.5, 2.1);
  }

  double p1 = 0.0;
  for (double x = start; x < end; x += DIFFERENTIAL_SIZE) {
    p1 += DIFFERENTIAL_SIZE * logCauchy(x, 2.1, 1.6);
  }

  double p2 = 0.0;
  for (double x = start; x < end; x += DIFFERENTIAL_SIZE) {
    p2 += DIFFERENTIAL_SIZE * pareto(x, 1.0, 0.5);
  }

  double checksum = p0 + p1 + p2;
  // fprintf(stderr, "Checksum: %f\n", checksum);

  return (int) (checksum * 100);
}

int benchmarkIterationsCount() {
  return 16;
}

void benchmarkSetupOnce() {
}

void benchmarkSetupEach() {
}

void benchmarkTeardownEach(char* outputFile) {
}

int benchmarkRun() {
  return (int) integrate();
}

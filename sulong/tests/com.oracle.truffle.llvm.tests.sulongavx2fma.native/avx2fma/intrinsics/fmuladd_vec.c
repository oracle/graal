/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <assert.h>

/*
 * With -mavx2 -mfma the loop vectorizer turns these loops into llvm.fmuladd.v8f32 /
 * llvm.fmuladd.v4f64 at -O2/-O3 (and scalar llvm.fmuladd at -O1). All values are small
 * integers, so fused and unfused evaluation agree exactly and the assertion is
 * optimization-level independent.
 */

#define N 64

int main() {
    float af[N], bf[N], cf[N];
    double ad[N], bd[N], cd[N];

    for (int i = 0; i < N; i++) {
        af[i] = (float) (i - 3);
        bf[i] = (float) (2 * i + 1);
        cf[i] = (float) (7 - i);
        ad[i] = i - 3;
        bd[i] = 2 * i + 1;
        cd[i] = 7 - i;
    }

#pragma clang loop vectorize_width(8)
    for (int i = 0; i < N; i++) {
        cf[i] += af[i] * bf[i];
    }

#pragma clang loop vectorize_width(4)
    for (int i = 0; i < N; i++) {
        cd[i] += ad[i] * bd[i];
    }

    for (int i = 0; i < N; i++) {
        int expected = (i - 3) * (2 * i + 1) + (7 - i);
        assert(cf[i] == (float) expected);
        assert(cd[i] == (double) expected);
    }

    return 0;
}

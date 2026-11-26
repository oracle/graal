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
 * Generic rounding intrinsics at 256-bit width. The __builtin_elementwise_* forms lower to
 * llvm.{ceil,floor,trunc,rint,nearbyint,round,roundeven}.v8f32 / .v4f64 at every -O level.
 *
 * The halfway inputs (x.5) pin the distinct rounding rules against each other:
 *   - round     : half away from zero  (round(2.5) == 3, round(-2.5) == -3)
 *   - roundeven : half to even         (roundeven(2.5) == 2, roundeven(-8.5) == -8)
 *   - rint/nearbyint : honor rounding mode; default is round-to-nearest-even
 * This locks the llvm.round correctness fix (Math.round was round-half-up, giving -2 for -2.5).
 */

typedef float f32x8 __attribute__((vector_size(32)));
typedef double f64x4 __attribute__((vector_size(32)));

static volatile f32x8 in_f = {2.5f, -2.5f, 0.5f, -0.5f, 2.3f, -2.7f, 7.5f, -8.5f};
static volatile f64x4 in_d = {2.5, -2.5, 3.5, -0.5};

int main() {
    f32x8 f = in_f;
    f64x4 d = in_d;

    f32x8 rf;
    f64x4 rd;

    rf = __builtin_elementwise_ceil(f);
    assert(rf[0] == 3.0f && rf[1] == -2.0f && rf[2] == 1.0f && rf[3] == 0.0f);
    assert(rf[4] == 3.0f && rf[5] == -2.0f && rf[6] == 8.0f && rf[7] == -8.0f);
    rd = __builtin_elementwise_ceil(d);
    assert(rd[0] == 3.0 && rd[1] == -2.0 && rd[2] == 4.0 && rd[3] == 0.0);

    rf = __builtin_elementwise_floor(f);
    assert(rf[0] == 2.0f && rf[1] == -3.0f && rf[2] == 0.0f && rf[3] == -1.0f);
    assert(rf[4] == 2.0f && rf[5] == -3.0f && rf[6] == 7.0f && rf[7] == -9.0f);
    rd = __builtin_elementwise_floor(d);
    assert(rd[0] == 2.0 && rd[1] == -3.0 && rd[2] == 3.0 && rd[3] == -1.0);

    rf = __builtin_elementwise_trunc(f);
    assert(rf[0] == 2.0f && rf[1] == -2.0f && rf[2] == 0.0f && rf[3] == 0.0f);
    assert(rf[4] == 2.0f && rf[5] == -2.0f && rf[6] == 7.0f && rf[7] == -8.0f);
    rd = __builtin_elementwise_trunc(d);
    assert(rd[0] == 2.0 && rd[1] == -2.0 && rd[2] == 3.0 && rd[3] == 0.0);

    rf = __builtin_elementwise_round(f);
    assert(rf[0] == 3.0f && rf[1] == -3.0f && rf[2] == 1.0f && rf[3] == -1.0f);
    assert(rf[4] == 2.0f && rf[5] == -3.0f && rf[6] == 8.0f && rf[7] == -9.0f);
    rd = __builtin_elementwise_round(d);
    assert(rd[0] == 3.0 && rd[1] == -3.0 && rd[2] == 4.0 && rd[3] == -1.0);

    rf = __builtin_elementwise_roundeven(f);
    assert(rf[0] == 2.0f && rf[1] == -2.0f && rf[2] == 0.0f && rf[3] == 0.0f);
    assert(rf[4] == 2.0f && rf[5] == -3.0f && rf[6] == 8.0f && rf[7] == -8.0f);
    rd = __builtin_elementwise_roundeven(d);
    assert(rd[0] == 2.0 && rd[1] == -2.0 && rd[2] == 4.0 && rd[3] == 0.0);

    rf = __builtin_elementwise_rint(f);
    assert(rf[0] == 2.0f && rf[1] == -2.0f && rf[2] == 0.0f && rf[3] == 0.0f);
    assert(rf[4] == 2.0f && rf[5] == -3.0f && rf[6] == 8.0f && rf[7] == -8.0f);
    rd = __builtin_elementwise_rint(d);
    assert(rd[0] == 2.0 && rd[1] == -2.0 && rd[2] == 4.0 && rd[3] == 0.0);

    rf = __builtin_elementwise_nearbyint(f);
    assert(rf[0] == 2.0f && rf[1] == -2.0f && rf[2] == 0.0f && rf[3] == 0.0f);
    assert(rf[4] == 2.0f && rf[5] == -3.0f && rf[6] == 8.0f && rf[7] == -8.0f);
    rd = __builtin_elementwise_nearbyint(d);
    assert(rd[0] == 2.0 && rd[1] == -2.0 && rd[2] == 4.0 && rd[3] == 0.0);

    return 0;
}

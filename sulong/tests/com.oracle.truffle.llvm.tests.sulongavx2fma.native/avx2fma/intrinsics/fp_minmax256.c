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
#include <math.h>

/*
 * Two families of 256-bit floating-point min/max, which have different NaN/zero contracts:
 *
 *   __builtin_elementwise_min/max  -> llvm.minnum/maxnum : return the non-NaN operand when one
 *       is NaN. Tested only on distinct finite lanes and NaN-vs-number; the ±0 and NaN-vs-NaN
 *       sign choices differ between Sulong's Java-based emulation and the x86 second-operand
 *       rule, so those cases are deliberately avoided.
 *
 *   __builtin_elementwise_minimum/maximum -> llvm.minimum/maximum : IEEE-754-2019, propagate NaN
 *       and order -0.0 < +0.0. Java Math.min/max match these exactly, so the NaN and signed-zero
 *       edges are safe to assert (via isnan/signbit, never ==).
 */

typedef float f32x8 __attribute__((vector_size(32)));
typedef double f64x4 __attribute__((vector_size(32)));

static volatile f32x8 af = {1.0f, -2.0f, 3.5f, -0.5f, 5.0f, -6.0f, 7.0f, -8.0f};
static volatile f32x8 bf = {0.5f, -1.0f, 4.0f, -0.75f, 4.0f, -7.0f, 8.0f, -9.0f};
static volatile f64x4 ad = {1.0, -2.0, 3.5, -0.5};
static volatile f64x4 bd = {0.5, -1.0, 4.0, -0.75};

/* NaN-vs-number lanes for the maxnum/minnum contract. */
static volatile f32x8 nan_a = {NAN, 2.0f, NAN, -3.0f, NAN, 6.0f, NAN, -8.0f};
static volatile f32x8 nan_b = {3.0f, NAN, -4.0f, NAN, 5.0f, NAN, -8.0f, NAN};

/* Signed-zero and NaN lanes for the IEEE minimum/maximum contract. */
static volatile f32x8 z_a = {-0.0f, 0.0f, NAN, 1.0f, -0.0f, 0.0f, 2.0f, NAN};
static volatile f32x8 z_b = {0.0f, -0.0f, 1.0f, NAN, 0.0f, -0.0f, NAN, 2.0f};

int main() {
    f32x8 f;
    f64x4 d;

    f = __builtin_elementwise_max(af, bf);
    assert(f[0] == 1.0f && f[1] == -1.0f && f[2] == 4.0f && f[3] == -0.5f);
    assert(f[4] == 5.0f && f[5] == -6.0f && f[6] == 8.0f && f[7] == -8.0f);
    f = __builtin_elementwise_min(af, bf);
    assert(f[0] == 0.5f && f[1] == -2.0f && f[2] == 3.5f && f[3] == -0.75f);
    assert(f[4] == 4.0f && f[5] == -7.0f && f[6] == 7.0f && f[7] == -9.0f);

    d = __builtin_elementwise_max(ad, bd);
    assert(d[0] == 1.0 && d[1] == -1.0 && d[2] == 4.0 && d[3] == -0.5);
    d = __builtin_elementwise_min(ad, bd);
    assert(d[0] == 0.5 && d[1] == -2.0 && d[2] == 3.5 && d[3] == -0.75);

    /* maxnum/minnum drop NaN and return the numeric operand. */
    f = __builtin_elementwise_max(nan_a, nan_b);
    assert(f[0] == 3.0f && f[1] == 2.0f && f[2] == -4.0f && f[3] == -3.0f);
    assert(f[4] == 5.0f && f[5] == 6.0f && f[6] == -8.0f && f[7] == -8.0f);
    f = __builtin_elementwise_min(nan_a, nan_b);
    assert(f[0] == 3.0f && f[1] == 2.0f && f[2] == -4.0f && f[3] == -3.0f);
    assert(f[4] == 5.0f && f[5] == 6.0f && f[6] == -8.0f && f[7] == -8.0f);

#if __has_builtin(__builtin_elementwise_maximum) && __has_builtin(__builtin_elementwise_minimum)
    /* IEEE maximum: NaN propagates; +0.0 > -0.0. */
    f = __builtin_elementwise_maximum(z_a, z_b);
    assert(!signbit(f[0]) && f[0] == 0.0f);   /* max(-0, +0) == +0 */
    assert(!signbit(f[1]) && f[1] == 0.0f);   /* max(+0, -0) == +0 */
    assert(isnan(f[2]) && isnan(f[3]));       /* NaN propagates */
    assert(!signbit(f[4]) && f[4] == 0.0f);
    assert(!signbit(f[5]) && f[5] == 0.0f);
    assert(isnan(f[6]));                       /* maximum(2.0, NaN) -> NaN (IEEE propagates) */
    assert(isnan(f[7]));

    /* IEEE minimum: NaN propagates; -0.0 < +0.0. */
    f = __builtin_elementwise_minimum(z_a, z_b);
    assert(signbit(f[0]) && f[0] == 0.0f);    /* min(-0, +0) == -0 */
    assert(signbit(f[1]) && f[1] == 0.0f);    /* min(+0, -0) == -0 */
    assert(isnan(f[2]) && isnan(f[3]));
    assert(signbit(f[4]) && f[4] == 0.0f);
    assert(signbit(f[5]) && f[5] == 0.0f);
    assert(isnan(f[6]));                       /* minimum(2.0, NaN) -> NaN (IEEE propagates) */
    assert(isnan(f[7]));
#endif

    return 0;
}

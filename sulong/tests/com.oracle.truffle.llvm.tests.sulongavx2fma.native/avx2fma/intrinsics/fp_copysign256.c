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
 * llvm.copysign.v8f32 / .v4f64 at 256-bit width via __builtin_elementwise_copysign. copysign is
 * a pure bit operation (magnitude of x, sign of y), so Math.copySign per lane is bit-exact vs
 * x86 for every case, including the sign of zero and the magnitude bits of a NaN. We check via
 * signbit (never ==) so the signed-zero and NaN lanes are meaningful.
 */

typedef float f32x8 __attribute__((vector_size(32)));
typedef double f64x4 __attribute__((vector_size(32)));

static volatile f32x8 mag_f = {3.0f, 3.0f, 0.0f, 0.0f, INFINITY, INFINITY, 5.5f, NAN};
static volatile f32x8 sgn_f = {1.0f, -1.0f, 2.0f, -2.0f, -1.0f, 1.0f, -0.0f, -1.0f};
static volatile f64x4 mag_d = {3.0, 3.0, 0.0, INFINITY};
static volatile f64x4 sgn_d = {1.0, -1.0, -2.0, -1.0};

int main() {
    f32x8 f = __builtin_elementwise_copysign(mag_f, sgn_f);
    assert(f[0] == 3.0f && !signbit(f[0]));   /* +3 */
    assert(f[1] == -3.0f && signbit(f[1]));   /* -3 */
    assert(f[2] == 0.0f && !signbit(f[2]));   /* +0 */
    assert(f[3] == 0.0f && signbit(f[3]));    /* -0 */
    assert(isinf(f[4]) && signbit(f[4]));     /* -inf */
    assert(isinf(f[5]) && !signbit(f[5]));    /* +inf */
    assert(f[6] == -5.5f && signbit(f[6]));   /* magnitude 5.5 with sign of -0 -> -5.5 */
    assert(isnan(f[7]) && signbit(f[7]));     /* NaN keeps magnitude, takes sign */

    f64x4 d = __builtin_elementwise_copysign(mag_d, sgn_d);
    assert(d[0] == 3.0 && !signbit(d[0]));
    assert(d[1] == -3.0 && signbit(d[1]));
    assert(d[2] == 0.0 && signbit(d[2]));
    assert(isinf(d[3]) && signbit(d[3]));

    return 0;
}

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
#include <immintrin.h>

/*
 * rsqrt is an approximation on real hardware (|rel err| <= 1.5 * 2^-12) and an exact
 * 1/sqrt in Sulong's emulation, so only the tolerance is asserted -- never exact bits and
 * never printed values, or the native reference run would diverge from Sulong.
 */
#define RSQRT_TOL 0x1.8p-12f

static void check(float actual, float expected) {
    float err = (actual - expected) / expected;
    assert(err <= RSQRT_TOL && err >= -RSQRT_TOL);
}

int main() {
    __m128 a = _mm_set_ps(16.0f, 4.0f, 1.0f, 0.25f);
    float out[4];
    _mm_storeu_ps(out, _mm_rsqrt_ps(a));
    check(out[0], 2.0f);
    check(out[1], 1.0f);
    check(out[2], 0.5f);
    check(out[3], 0.25f);

    __m256 b = _mm256_set_ps(64.0f, 16.0f, 9.0f, 4.0f, 2.25f, 1.0f, 0.25f, 0.0625f);
    float outw[8];
    _mm256_storeu_ps(outw, _mm256_rsqrt_ps(b));
    check(outw[0], 4.0f);
    check(outw[1], 2.0f);
    check(outw[2], 1.0f);
    check(outw[3], 1.0f / 1.5f);
    check(outw[4], 0.5f);
    check(outw[5], 1.0f / 3.0f);
    check(outw[6], 0.25f);
    check(outw[7], 0.125f);

    return 0;
}

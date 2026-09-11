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

/* NaN and +/-0 lanes are deliberately avoided: Sulong's emulation follows Java Math.max/min
 * semantics there, which differ from the x86 second-operand rule. */

int main() {
    __m256d a = _mm256_set_pd(1.0, -2.0, 3.5, -0.5);
    __m256d b = _mm256_set_pd(0.5, -1.0, 4.0, -0.75);

    double out[4];
    _mm256_storeu_pd(out, _mm256_max_pd(a, b));
    assert(out[0] == -0.5 && out[1] == 4.0 && out[2] == -1.0 && out[3] == 1.0);
    _mm256_storeu_pd(out, _mm256_min_pd(a, b));
    assert(out[0] == -0.75 && out[1] == 3.5 && out[2] == -2.0 && out[3] == 0.5);

    __m256 c = _mm256_set_ps(8.0f, -7.0f, 6.0f, -5.0f, 4.0f, -3.0f, 2.0f, -1.0f);
    __m256 d = _mm256_set_ps(7.5f, -6.5f, 6.5f, -5.5f, 3.5f, -3.5f, 2.5f, -1.5f);

    float outf[8];
    _mm256_storeu_ps(outf, _mm256_max_ps(c, d));
    assert(outf[0] == -1.0f && outf[1] == 2.5f && outf[2] == -3.0f && outf[3] == 4.0f);
    assert(outf[4] == -5.0f && outf[5] == 6.5f && outf[6] == -6.5f && outf[7] == 8.0f);
    _mm256_storeu_ps(outf, _mm256_min_ps(c, d));
    assert(outf[0] == -1.5f && outf[1] == 2.0f && outf[2] == -3.5f && outf[3] == 3.5f);
    assert(outf[4] == -5.5f && outf[5] == 6.0f && outf[6] == -7.0f && outf[7] == 7.5f);

    return 0;
}

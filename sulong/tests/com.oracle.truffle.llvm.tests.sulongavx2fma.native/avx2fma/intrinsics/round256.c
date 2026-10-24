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

/* exercises llvm.x86.avx.round.pd.256/ps.256 and llvm.x86.sse41.round.ps, including
 * nearest-even halfway behavior, which distinguishes rint from round */

int main() {
    __m256d a = _mm256_set_pd(-1.5, 2.5, -2.3, 2.3);
    double out[4];

    _mm256_storeu_pd(out, _mm256_round_pd(a, _MM_FROUND_TO_NEAREST_INT | _MM_FROUND_NO_EXC));
    assert(out[0] == 2.0 && out[1] == -2.0 && out[2] == 2.0 && out[3] == -2.0);

    _mm256_storeu_pd(out, _mm256_floor_pd(a));
    assert(out[0] == 2.0 && out[1] == -3.0 && out[2] == 2.0 && out[3] == -2.0);

    _mm256_storeu_pd(out, _mm256_ceil_pd(a));
    assert(out[0] == 3.0 && out[1] == -2.0 && out[2] == 3.0 && out[3] == -1.0);

    _mm256_storeu_pd(out, _mm256_round_pd(a, _MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC));
    assert(out[0] == 2.0 && out[1] == -2.0 && out[2] == 2.0 && out[3] == -1.0);

    __m256 b = _mm256_set_ps(-0.5f, 0.5f, -1.5f, 1.5f, -7.7f, 7.7f, -3.2f, 3.2f);
    float outf[8];

    _mm256_storeu_ps(outf, _mm256_round_ps(b, _MM_FROUND_TO_NEAREST_INT | _MM_FROUND_NO_EXC));
    assert(outf[0] == 3.0f && outf[1] == -3.0f && outf[2] == 8.0f && outf[3] == -8.0f);
    assert(outf[4] == 2.0f && outf[5] == -2.0f && outf[6] == 0.0f && outf[7] == -0.0f);

    __m128 c = _mm_set_ps(-2.5f, 2.5f, -1.2f, 1.2f);
    float outs[4];
    _mm_storeu_ps(outs, _mm_round_ps(c, _MM_FROUND_TO_NEAREST_INT | _MM_FROUND_NO_EXC));
    assert(outs[0] == 1.0f && outs[1] == -1.0f && outs[2] == 2.0f && outs[3] == -2.0f);
    _mm_storeu_ps(outs, _mm_floor_ps(c));
    assert(outs[0] == 1.0f && outs[1] == -2.0f && outs[2] == 2.0f && outs[3] == -3.0f);

    return 0;
}

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
 * llvm.fma requires the correctly-rounded fused result, so hardware FMA (native run) and any
 * correct emulation must agree bit-for-bit. The reference values below include cases where the
 * fused result differs from a double-rounded multiply+add.
 */

/* volatile stops clang from constant-folding the whole test away at compile time */
static volatile float vfa = 1.0f + 0x1p-23f;
static volatile float vfb = 1.0f - 0x1p-23f;
static volatile float vfc = -1.0f;
static volatile double vda = 1.0 + 0x1p-52;
static volatile double vdb = 1.0 - 0x1p-52;

int main() {
    /* scalar: float fma is exactly representable through double arithmetic */
    float fa = vfa;
    float fb = vfb;
    float fc = vfc;
    float fr = __builtin_fmaf(fa, fb, fc);
    assert(fr == -0x1p-46f); /* (1+u)(1-u)-1 = -u^2 exactly, u=2^-23 */

    double da = vda;
    double db = vdb;
    double dr = __builtin_fma(da, db, -1.0);
    assert(dr == -0x1p-104); /* representable, only via fused rounding */

    /* 128-bit float lanes: llvm.fma.v4f32 */
    __m128 x4 = _mm_set_ps(2.0f, 3.0f, fa, 1.5f);
    __m128 y4 = _mm_set_ps(4.0f, 5.0f, fb, 2.5f);
    __m128 z4 = _mm_set_ps(1.0f, -15.0f, fc, 0.25f);
    __m128 r4 = _mm_fmadd_ps(x4, y4, z4);
    float out4[4];
    _mm_storeu_ps(out4, r4);
    assert(out4[0] == 4.0f);      /* 1.5*2.5+0.25 */
    assert(out4[1] == -0x1p-46f); /* fused-only result */
    assert(out4[2] == 0.0f);      /* 3*5-15 */
    assert(out4[3] == 9.0f);      /* 2*4+1 */

    /* 256-bit double lanes: llvm.fma.v4f64 */
    __m256d x = _mm256_set_pd(da, 2.0, -3.0, 0.5);
    __m256d y = _mm256_set_pd(db, 0.5, 2.0, 8.0);
    __m256d z = _mm256_set_pd(-1.0, 1.0, 6.0, -4.0);
    __m256d r = _mm256_fmadd_pd(x, y, z);
    double out[4];
    _mm256_storeu_pd(out, r);
    assert(out[0] == 0.0);      /* 0.5*8-4 */
    assert(out[1] == 0.0);      /* -3*2+6 */
    assert(out[2] == 2.0);      /* 2*0.5+1 */
    assert(out[3] == -0x1p-104); /* fused-only result */

    /* 256-bit float lanes: llvm.fma.v8f32 */
    __m256 xf = _mm256_set1_ps(fa);
    __m256 yf = _mm256_set1_ps(fb);
    __m256 zf = _mm256_set1_ps(fc);
    __m256 rf = _mm256_fmadd_ps(xf, yf, zf);
    float outf[8];
    _mm256_storeu_ps(outf, rf);
    for (int i = 0; i < 8; i++) {
        assert(outf[i] == -0x1p-46f);
    }

    /* fmsub/fnmadd variants lower to llvm.fma with negated operands */
    __m256d rs = _mm256_fmsub_pd(x, y, z);
    double outs[4];
    _mm256_storeu_pd(outs, rs);
    assert(outs[0] == 8.0);  /* 0.5*8+4 */
    assert(outs[1] == -12.0); /* -3*2-6 */

    __m256d rn = _mm256_fnmadd_pd(x, y, z);
    double outn[4];
    _mm256_storeu_pd(outn, rn);
    assert(outn[0] == -8.0); /* -(0.5*8)-4 */
    assert(outn[1] == 12.0); /* -(-3*2)+6 */

    return 0;
}

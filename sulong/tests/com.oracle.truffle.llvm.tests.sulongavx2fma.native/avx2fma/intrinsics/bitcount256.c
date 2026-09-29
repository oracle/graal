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
#include <stdint.h>

/*
 * Vector bit-counting intrinsics at 256-bit width:
 *
 *   llvm.ctpop.v*  via __builtin_elementwise_popcount  -- always emitted at the exact width.
 *   llvm.ctlz.v* / llvm.cttz.v*  via scalar clz/ctz loops -- the loop vectorizer emits the
 *       vector intrinsic when profitable. These lower to scalar code on AVX2 hardware (there is
 *       no vector LZCNT/TZCNT below AVX-512), so the intrinsic may or may not survive in the IR;
 *       either way the differential result is identical. Inputs are strictly non-zero so the
 *       count-leading/trailing-zero results are well defined.
 */

typedef int8_t i8x32 __attribute__((vector_size(32)));
typedef int16_t i16x16 __attribute__((vector_size(32)));
typedef int32_t i32x8 __attribute__((vector_size(32)));
typedef int64_t i64x4 __attribute__((vector_size(32)));

static volatile i8x32 v8 = {0, 1, 2, 3, 7, 8, 15, 16, 31, 32, 63, 64, 127, -1, -2, -128,
                            0, 1, 2, 3, 7, 8, 15, 16, 31, 32, 63, 64, 127, -1, -2, -128};
static volatile i16x16 v16 = {0, 1, 3, 7, 0xFF, 0x100, 0x7FFF, -1,
                              0, 1, 3, 7, 0xFF, 0x100, 0x7FFF, -1};
static volatile i32x8 v32 = {0, 1, 3, 0xFFFF, 0x10000, 0x7FFFFFFF, -1, -256};
static volatile i64x4 v64 = {0, 1, 0x100000000LL, -1};

static volatile uint32_t clz_in[8] = {1u, 2u, 3u, 0xFFu, 0x8000u, 0x80000000u, 0x7FFFFFFFu, 0xABCDu};

int main() {
    i8x32 p8 = __builtin_elementwise_popcount(v8);
    assert(p8[0] == 0 && p8[1] == 1 && p8[2] == 1 && p8[3] == 2);
    assert(p8[4] == 3 && p8[6] == 4 && p8[12] == 7);
    assert(p8[13] == 8 && p8[15] == 1); /* -1 -> 0xFF -> 8; -128 -> 0x80 -> 1 */

    i16x16 p16 = __builtin_elementwise_popcount(v16);
    assert(p16[0] == 0 && p16[1] == 1 && p16[4] == 8 && p16[6] == 15 && p16[7] == 16);

    i32x8 p32 = __builtin_elementwise_popcount(v32);
    assert(p32[0] == 0 && p32[1] == 1 && p32[3] == 16 && p32[5] == 31 && p32[6] == 32);

    i64x4 p64 = __builtin_elementwise_popcount(v64);
    assert(p64[0] == 0 && p64[1] == 1 && p64[2] == 1 && p64[3] == 64);

    /* Count leading / trailing zeros over a non-zero array (may vectorize to llvm.ctlz/cttz). */
    uint32_t clz[8];
    uint32_t ctz[8];
    for (int i = 0; i < 8; i++) {
        clz[i] = __builtin_clz(clz_in[i]);
        ctz[i] = __builtin_ctz(clz_in[i]);
    }
    assert(clz[0] == 31 && ctz[0] == 0);   /* 1 */
    assert(clz[1] == 30 && ctz[1] == 1);   /* 2 */
    assert(clz[3] == 24 && ctz[3] == 0);   /* 0xFF */
    assert(clz[4] == 16 && ctz[4] == 15);  /* 0x8000 */
    assert(clz[5] == 0 && ctz[5] == 31);   /* 0x80000000 */
    assert(clz[6] == 1 && ctz[6] == 0);    /* 0x7FFFFFFF */

    return 0;
}

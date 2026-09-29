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
 * Saturating integer add/subtract at 256-bit width. __builtin_elementwise_add_sat and
 * __builtin_elementwise_sub_sat emit llvm.{s,u}add.sat.v* / llvm.{s,u}sub.sat.v*; the
 * signed/unsigned form is selected by the element type. All four intrinsics are exercised, and
 * across the four cases the element widths i8/i16/i32/i64 are all covered. The asserts pin the
 * clamp boundaries: signed clamps to [INTn_MIN, INTn_MAX], unsigned to [0, UINTn_MAX].
 */

typedef int8_t   i8x32  __attribute__((vector_size(32)));
typedef uint8_t  u8x32  __attribute__((vector_size(32)));
typedef int16_t  i16x16 __attribute__((vector_size(32)));
typedef uint16_t u16x16 __attribute__((vector_size(32)));
typedef int32_t  i32x8  __attribute__((vector_size(32)));
typedef uint32_t u32x8  __attribute__((vector_size(32)));
typedef int64_t  i64x4  __attribute__((vector_size(32)));
typedef uint64_t u64x4  __attribute__((vector_size(32)));

/* signed i8 add_sat */
static volatile i8x32 sa = {100, -100, 10, 127, -128, 5, -5, 0};
static volatile i8x32 sb = {50, -50, 20, 1, -1, -10, 10, 0};
/* signed i8 sub_sat */
static volatile i8x32 ssa = {100, -100, -128, 127, 0};
static volatile i8x32 ssb = {-50, 50, 1, -1, 0};
/* unsigned u8 add_sat */
static volatile u8x32 ua = {200, 10, 255, 0};
static volatile u8x32 ub = {100, 20, 1, 0};

/* signed i16 add_sat */
static volatile i16x16 a16 = {30000, -30000, 100};
static volatile i16x16 b16 = {5000, -5000, 200};
/* unsigned u16 sub_sat */
static volatile u16x16 ua16 = {10, 0, 65535};
static volatile u16x16 ub16 = {20, 5, 1};

/* signed i32 sub_sat */
static volatile i32x8 a32 = {2000000000, -2000000000, 5};
static volatile i32x8 b32 = {-2000000000, 2000000000, 3};
/* unsigned u32 add_sat */
static volatile u32x8 ua32 = {4000000000u, 10, 0};
static volatile u32x8 ub32 = {1000000000u, 20, 0};

/* signed i64 add_sat */
static volatile i64x4 a64 = {9000000000000000000LL, -9000000000000000000LL, 1};
static volatile i64x4 b64 = {2000000000000000000LL, -2000000000000000000LL, 2};
/* unsigned u64 sub_sat */
static volatile u64x4 ua64 = {10, 0, 18000000000000000000ULL};
static volatile u64x4 ub64 = {20, 5, 1};

int main() {
    i8x32 r = __builtin_elementwise_add_sat(sa, sb);
    assert(r[0] == 127 && r[1] == -128 && r[2] == 30 && r[3] == 127);
    assert(r[4] == -128 && r[5] == -5 && r[6] == 5 && r[7] == 0);

    r = __builtin_elementwise_sub_sat(ssa, ssb);
    assert(r[0] == 127 && r[1] == -128 && r[2] == -128 && r[3] == 127 && r[4] == 0);

    u8x32 ru = __builtin_elementwise_add_sat(ua, ub);
    assert(ru[0] == 255 && ru[1] == 30 && ru[2] == 255 && ru[3] == 0);

    i16x16 r16 = __builtin_elementwise_add_sat(a16, b16);
    assert(r16[0] == 32767 && r16[1] == -32768 && r16[2] == 300);

    u16x16 ru16 = __builtin_elementwise_sub_sat(ua16, ub16);
    assert(ru16[0] == 0 && ru16[1] == 0 && ru16[2] == 65534);

    i32x8 r32 = __builtin_elementwise_sub_sat(a32, b32);
    assert(r32[0] == 2147483647 && r32[1] == (-2147483647 - 1) && r32[2] == 2);

    u32x8 ru32 = __builtin_elementwise_add_sat(ua32, ub32);
    assert(ru32[0] == 4294967295u && ru32[1] == 30 && ru32[2] == 0);

    i64x4 r64 = __builtin_elementwise_add_sat(a64, b64);
    assert(r64[0] == INT64_MAX && r64[1] == INT64_MIN && r64[2] == 3);

    u64x4 ru64 = __builtin_elementwise_sub_sat(ua64, ub64);
    assert(ru64[0] == 0 && ru64[1] == 0 && ru64[2] == 17999999999999999999ULL);

    return 0;
}

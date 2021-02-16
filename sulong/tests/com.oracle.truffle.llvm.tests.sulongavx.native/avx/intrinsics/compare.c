/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
#include <emmintrin.h>
#include <assert.h>

int main() {
    __m128i val1_1 = { 0x00110011, 0x00110011 };
    __m128i val1_2 = { 0x11001100, 0x11001100 };
    __m128i res1 = { 0xffffffff00000000L, 0xffffffff00000000L };

    __m128i val2_1 = { 0, 0 };
    __m128i val2_2 = { -1, -1 };
    __m128i res2 = { 0, 0 };

    __m128i val3_1 = { 0, 0 };
    __m128i val3_2 = { 0, 0 };
    __m128i res3 = { 0xffffffffffffffffL, 0xffffffffffffffffL };

    assert(_mm_cmpeq_epi8(val1_1, val1_2)[0] == res1[0]);
    assert(_mm_cmpeq_epi8(val1_1, val1_2)[1] == res1[1]);

    assert(_mm_cmpeq_epi8(val2_1, val2_2)[0] == res2[0]);
    assert(_mm_cmpeq_epi8(val2_1, val2_2)[1] == res2[1]);

    assert(_mm_cmpeq_epi8(val3_1, val3_2)[0] == res3[0]);
    assert(_mm_cmpeq_epi8(val3_1, val3_2)[1] == res3[1]);
}

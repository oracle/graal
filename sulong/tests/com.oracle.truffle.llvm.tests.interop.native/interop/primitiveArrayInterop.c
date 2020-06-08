/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
#include <polyglot.h>
#include <stdlib.h>

void free_seq(void *seq) {
    free(seq);
}

#define DEF_TEST(type, name)                                                                                                                         \
    void *alloc_seq_##name(type start, type step, int len) {                                                                                         \
        type *array = calloc(sizeof(type), len);                                                                                                     \
        for (int i = 0; i < len; i++) {                                                                                                              \
            array[i] = start + i * step;                                                                                                             \
        }                                                                                                                                            \
        return polyglot_from_##name##_array(array, len);                                                                                             \
    }                                                                                                                                                \
                                                                                                                                                     \
    type sum_##name(void *arg) {                                                                                                                     \
        int len = polyglot_get_array_size(arg);                                                                                                      \
        type *array = polyglot_as_##name##_array(arg);                                                                                               \
        type sum = 0;                                                                                                                                \
        for (int i = 0; i < len; i++) {                                                                                                              \
            sum += array[i];                                                                                                                         \
        }                                                                                                                                            \
        return sum;                                                                                                                                  \
    }

DEF_TEST(int8_t, i8)
DEF_TEST(int16_t, i16)
DEF_TEST(int32_t, i32)
DEF_TEST(int64_t, i64)
DEF_TEST(float, float)
DEF_TEST(double, double)

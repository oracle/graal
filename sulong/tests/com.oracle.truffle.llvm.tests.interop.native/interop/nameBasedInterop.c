/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
#include <stdint.h>

typedef struct {
    int8_t valueB;
    int16_t valueS;
    int32_t valueI;
    int64_t valueL;
    float valueF;
    double valueD;
} CLASS;

#define DEF_ACCESSORS(type, name)                                                                                                                    \
    type getStruct##name(CLASS *c) {                                                                                                                 \
        return c->value##name;                                                                                                                       \
    }                                                                                                                                                \
                                                                                                                                                     \
    void setStruct##name(CLASS *c, type v) {                                                                                                         \
        c->value##name = v;                                                                                                                          \
    }                                                                                                                                                \
                                                                                                                                                     \
    type getArray##name(type *arr, int idx) {                                                                                                        \
        return arr[idx];                                                                                                                             \
    }                                                                                                                                                \
                                                                                                                                                     \
    void setArray##name(type *arr, int idx, type v) {                                                                                                \
        arr[idx] = v;                                                                                                                                \
    }

DEF_ACCESSORS(int8_t, B)
DEF_ACCESSORS(int16_t, S)
DEF_ACCESSORS(int32_t, I)
DEF_ACCESSORS(int64_t, L)
DEF_ACCESSORS(float, F)
DEF_ACCESSORS(double, D)

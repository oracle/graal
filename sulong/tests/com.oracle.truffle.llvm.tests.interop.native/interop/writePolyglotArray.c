/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
#include <graalvm/llvm/polyglot.h>

__POLYGLOT_DECLARE_GENERIC_ARRAY(void *, pointer)

void get_pointer_typeid(void (*ret)(polyglot_typeid typeid)) {
    ret(polyglot_array_typeid(polyglot_pointer_typeid(), 0));
}

#define GENERATE_WRITE(C_TYPE, LLVM_TYPE)                                                                                                            \
    void write_##LLVM_TYPE(void *polyglot_arr, int idx, C_TYPE value) {                                                                              \
        C_TYPE *c_arr = (C_TYPE *) polyglot_arr;                                                                                                     \
        c_arr[idx] = value;                                                                                                                          \
    }                                                                                                                                                \
                                                                                                                                                     \
    void write_##LLVM_TYPE##_to_i8_array(void *polyglot_arr, int idx, C_TYPE value) {                                                                \
        C_TYPE *c_arr = (C_TYPE *) polyglot_as_typed(polyglot_arr, polyglot_array_typeid(polyglot_i8_typeid(), 0));                                  \
        c_arr[idx] = value;                                                                                                                          \
    }                                                                                                                                                \
                                                                                                                                                     \
    void write_##LLVM_TYPE##_to_i16_array(void *polyglot_arr, int idx, C_TYPE value) {                                                               \
        C_TYPE *c_arr = (C_TYPE *) polyglot_as_typed(polyglot_arr, polyglot_array_typeid(polyglot_i16_typeid(), 0));                                 \
        c_arr[idx] = value;                                                                                                                          \
    }                                                                                                                                                \
                                                                                                                                                     \
    void write_##LLVM_TYPE##_to_i32_array(void *polyglot_arr, int idx, C_TYPE value) {                                                               \
        C_TYPE *c_arr = (C_TYPE *) polyglot_as_typed(polyglot_arr, polyglot_array_typeid(polyglot_i32_typeid(), 0));                                 \
        c_arr[idx] = value;                                                                                                                          \
    }                                                                                                                                                \
                                                                                                                                                     \
    void write_##LLVM_TYPE##_to_i64_array(void *polyglot_arr, int idx, C_TYPE value) {                                                               \
        C_TYPE *c_arr = (C_TYPE *) polyglot_as_typed(polyglot_arr, polyglot_array_typeid(polyglot_i64_typeid(), 0));                                 \
        c_arr[idx] = value;                                                                                                                          \
    }                                                                                                                                                \
                                                                                                                                                     \
    void write_##LLVM_TYPE##_to_float_array(void *polyglot_arr, int idx, C_TYPE value) {                                                             \
        C_TYPE *c_arr = (C_TYPE *) polyglot_as_typed(polyglot_arr, polyglot_array_typeid(polyglot_float_typeid(), 0));                               \
        c_arr[idx] = value;                                                                                                                          \
    }                                                                                                                                                \
                                                                                                                                                     \
    void write_##LLVM_TYPE##_to_double_array(void *polyglot_arr, int idx, C_TYPE value) {                                                            \
        C_TYPE *c_arr = (C_TYPE *) polyglot_as_typed(polyglot_arr, polyglot_array_typeid(polyglot_double_typeid(), 0));                              \
        c_arr[idx] = value;                                                                                                                          \
    }

GENERATE_WRITE(uint8_t, i8)
GENERATE_WRITE(uint16_t, i16)
GENERATE_WRITE(uint32_t, i32)
GENERATE_WRITE(uint64_t, i64)
GENERATE_WRITE(float, float)
GENERATE_WRITE(double, double)
GENERATE_WRITE(void *, pointer)

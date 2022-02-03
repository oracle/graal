/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
#include <stdio.h>
#include <stdlib.h>
#include <graalvm/llvm/polyglot.h>
#include <graalvm/llvm/polyglot-buffer.h>

polyglot_value allocBuffer(uint64_t length) {
    return polyglot_from_buffer(calloc(length, 1), length);
}

polyglot_value fromBuffer(polyglot_value buf, uint64_t length) {
    return polyglot_from_buffer(buf, length);
}

polyglot_value fromConstBuffer(const void *buf, uint64_t length) {
    return polyglot_from_const_buffer(buf, length);
}

void freeBuffer(polyglot_value buffer) {
    free(buffer);
}

polyglot_value aString() {
    return polyglot_from_string("test", "UTF8");
}

uint64_t getBufferSize(polyglot_value buffer) {
    return polyglot_get_buffer_size(buffer);
}

bool isBufferWritable(polyglot_value buffer) {
    return polyglot_is_buffer_writable(buffer);
}

bool hasBufferElements(polyglot_value buffer) {
    return polyglot_has_buffer_elements(buffer);
}

#define RW_BUFFER_TYPE(ctype, jtype)                                                                                                                 \
    ctype read_##jtype(void *buffer, uint64_t offset) {                                                                                              \
        char *cbuffer = (char *) buffer;                                                                                                             \
        return *((ctype *) (cbuffer + offset));                                                                                                      \
    }                                                                                                                                                \
    void write_##jtype(void *buffer, uint64_t offset, ctype value) {                                                                                 \
        char *cbuffer = (char *) buffer;                                                                                                             \
        *((ctype *) (cbuffer + offset)) = value;                                                                                                     \
    }

RW_BUFFER_TYPE(int8_t, i8)
RW_BUFFER_TYPE(int16_t, i16)
RW_BUFFER_TYPE(int32_t, i32)
RW_BUFFER_TYPE(int64_t, i64)
RW_BUFFER_TYPE(float, float)
RW_BUFFER_TYPE(double, double)

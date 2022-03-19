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
#include <polyglot.h>

#define ACCESS_TYPE(ctype, jtype, val)                                                                                                               \
    void export_##jtype(const char *name) {                                                                                                          \
        polyglot_export(name, polyglot_from_##jtype(val));                                                                                           \
    }                                                                                                                                                \
                                                                                                                                                     \
    void put_member_##jtype(polyglot_value obj) {                                                                                                    \
        polyglot_put_member(obj, "member", polyglot_from_##jtype(val));                                                                              \
    }                                                                                                                                                \
                                                                                                                                                     \
    polyglot_value get_member_##jtype(polyglot_value obj) {                                                                                          \
        return polyglot_get_member(obj, "member");                                                                                                   \
    }                                                                                                                                                \
                                                                                                                                                     \
    ctype import_##jtype(const char *name) {                                                                                                         \
        return polyglot_as_##jtype(polyglot_import(name));                                                                                           \
    }

ACCESS_TYPE(bool, boolean, true)
ACCESS_TYPE(int8_t, i8, 42)
ACCESS_TYPE(int16_t, i16, 43)
ACCESS_TYPE(int32_t, i32, 44)
ACCESS_TYPE(int64_t, i64, 45ll)
ACCESS_TYPE(float, float, 46.0f)
ACCESS_TYPE(double, double, 47.0)

polyglot_value int_or_long(bool b) {
    if (b) {
        return polyglot_from_i32(123);
    } else {
        return polyglot_from_double(246.5);
    }
}

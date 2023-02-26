/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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

#ifndef GRAALVM_LLVM_POLYGLOT_H
#error "Do not include this header directly! Include <graalvm/llvm/polyglot.h> instead."
#endif

/*
 * DO NOT INCLUDE OR USE THIS HEADER FILE DIRECTLY!
 *
 * Everything in this header file is implementation details, and might change without notice even
 * in minor releases.
 */

#define __POLYGLOT_DECLARE_GENERIC_ARRAY(typedecl, typename)                                                                                         \
    __attribute__((always_inline)) static inline polyglot_typeid polyglot_##typename##_typeid() {                                                    \
        static typedecl __polyglot_typeid_##typename[0];                                                                                             \
        return __polyglot_as_typeid(__polyglot_typeid_##typename);                                                                                   \
    }                                                                                                                                                \
                                                                                                                                                     \
    __attribute__((always_inline)) static inline typedecl *polyglot_as_##typename##_array(polyglot_value p) {                                        \
        void *ret = polyglot_as_typed(p, polyglot_array_typeid(polyglot_##typename##_typeid(), 0));                                                  \
        return (typedecl *) ret;                                                                                                                     \
    }                                                                                                                                                \
                                                                                                                                                     \
    __attribute__((always_inline)) static inline polyglot_value polyglot_from_##typename##_array(typedecl *arr, uint64_t len) {                      \
        return polyglot_from_typed(arr, polyglot_array_typeid(polyglot_##typename##_typeid(), len));                                                 \
    }

__POLYGLOT_DECLARE_GENERIC_ARRAY(bool, boolean)
__POLYGLOT_DECLARE_GENERIC_ARRAY(int8_t, i8)
__POLYGLOT_DECLARE_GENERIC_ARRAY(int16_t, i16)
__POLYGLOT_DECLARE_GENERIC_ARRAY(int32_t, i32)
__POLYGLOT_DECLARE_GENERIC_ARRAY(int64_t, i64)
__POLYGLOT_DECLARE_GENERIC_ARRAY(float, float)
__POLYGLOT_DECLARE_GENERIC_ARRAY(double, double)

#define __POLYGLOT_DECLARE_GENERIC_TYPE(typedecl, typename)                                                                                          \
    __POLYGLOT_DECLARE_GENERIC_ARRAY(typedecl, typename)                                                                                             \
                                                                                                                                                     \
    __attribute__((always_inline)) static inline typedecl *polyglot_as_##typename(polyglot_value p) {                                                \
        void *ret = polyglot_as_typed(p, polyglot_##typename##_typeid());                                                                            \
        return (typedecl *) ret;                                                                                                                     \
    }                                                                                                                                                \
                                                                                                                                                     \
    __attribute__((always_inline)) static inline polyglot_value polyglot_from_##typename(typedecl * s) {                                             \
        return polyglot_from_typed(s, polyglot_##typename##_typeid());                                                                               \
    }

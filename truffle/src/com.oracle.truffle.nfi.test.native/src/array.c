/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <stdint.h>
#include <stdlib.h>


#define GEN_ARRAY_TEST(name, type) \
    \
    type sum_##name(type *arr, uint32_t length) { \
        type ret = 0; \
        for (; length > 0; length--) { \
            ret += *(arr++); \
        } \
        return ret; \
    } \
    \
    void store_##name(type *arr, uint32_t idx, type value) { \
        arr[idx] = value; \
    } \
    \
    char *null_array_##name(type *arr) { \
        if (arr == NULL) { \
            return "null"; \
        } else { \
            return "non_null"; \
        } \
    }


GEN_ARRAY_TEST(SINT8, int8_t)
GEN_ARRAY_TEST(UINT8, uint8_t)
GEN_ARRAY_TEST(SINT16, int16_t)
GEN_ARRAY_TEST(UINT16, uint16_t)
GEN_ARRAY_TEST(SINT32, int32_t)
GEN_ARRAY_TEST(UINT32, uint32_t)
GEN_ARRAY_TEST(SINT64, int64_t)
GEN_ARRAY_TEST(UINT64, uint64_t)
GEN_ARRAY_TEST(FLOAT, float)
GEN_ARRAY_TEST(DOUBLE, double)

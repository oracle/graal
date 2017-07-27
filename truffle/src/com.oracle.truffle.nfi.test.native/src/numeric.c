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
#include <trufflenfi.h>


#define GEN_NUMERIC_TEST(name, type) \
    \
    type increment_##name(type arg) { \
        return arg + 1; \
    } \
    \
    type callback_##name(type (*fn)(type), type arg) { \
        return fn(arg + 1) * 2; \
    } \
    \
    typedef type (*fnptr_##name)(type); \
    \
    fnptr_##name callback_ret_##name() { \
        return increment_##name; \
    } \
    \
    type pingpong_##name(TruffleEnv *env, fnptr_##name (*wrapFn)(TruffleEnv *env, fnptr_##name), type arg) { \
        fnptr_##name wrapped = wrapFn(env, increment_##name); \
        int ret = wrapped(arg + 1) * 2; \
        (*env)->releaseClosureRef(env, wrapped); \
        return ret; \
    }


GEN_NUMERIC_TEST(SINT8, int8_t)
GEN_NUMERIC_TEST(UINT8, uint8_t)
GEN_NUMERIC_TEST(SINT16, int16_t)
GEN_NUMERIC_TEST(UINT16, uint16_t)
GEN_NUMERIC_TEST(SINT32, int32_t)
GEN_NUMERIC_TEST(UINT32, uint32_t)
GEN_NUMERIC_TEST(SINT64, int64_t)
GEN_NUMERIC_TEST(UINT64, uint64_t)
GEN_NUMERIC_TEST(FLOAT, float)
GEN_NUMERIC_TEST(DOUBLE, double)
GEN_NUMERIC_TEST(POINTER, intptr_t)

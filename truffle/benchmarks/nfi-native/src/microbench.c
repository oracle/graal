/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

void simple_downcall() {
}

int64_t prim_args_downcall(int32_t a, int64_t b) {
    return a + b;
}

int64_t many_args_downcall(int32_t a, int32_t b, int32_t c, int32_t d,
                           int32_t e, int32_t f, int32_t g, int32_t h,
                           int32_t i, int32_t j, int32_t k, int32_t l) {
    return a + b + c + d + e + f + g + h + i + j + k + l;
}

void million_upcalls(void (*upcall)()) {
    int i;
    for (i = 0; i < 1000000; i++) {
        upcall();
    }
}

int64_t million_upcalls_prim_args(int64_t (*upcall)(int32_t a, int64_t b)) {
    int64_t sum = 0;
    int i;
    for (int i = 0; i < 1000000; i++) {
        sum += upcall(i, 17);
    }
    return sum;
}

void million_upcalls_env(TruffleEnv *env, void (*upcall)(TruffleEnv *env)) {
    int i;
    for (i = 0; i < 1000000; i++) {
        upcall(env);
    }
}

int64_t million_upcalls_many_args(int64_t (*upcall)(int32_t a, int32_t b, int32_t c, int32_t d,
                                                 int32_t e, int32_t f, int32_t g, int32_t h,
                                                 int32_t i, int32_t j, int32_t k, int32_t l)) {
    int64_t sum;
    int i;
    for (i = 0; i < 1000000; i++) {
        sum += upcall(i, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
    }
    return sum;
}

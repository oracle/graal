/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SYSTEMJAVA_TEST_H_INCLUDED
#define SYSTEMJAVA_TEST_H_INCLUDED

struct simple_struct {
    int first;
    int second;
};

struct simple_struct2 {
    char alpha;
    long long beta;
};

struct composite_struct {
    char c1;
    struct simple_struct c2;
    int c3;
    struct simple_struct2 c4;
    short c5;
};

struct weird {
    short  f_short;
    unsigned int   f_uint;
    int    f_int;
    long   f_long;
    float  f_float;
    double f_double;
    int a_int[8];
    char a_char[12];
};

#include <dlfcn.h>

#endif // SYSTEMJAVA_TEST_H_INCLUDED

/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates.
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
#ifndef VAHANDLER_H
#define VAHANDLER_H

#include <stdarg.h>

struct Varargs {
    const struct VarargsInterface *functions;
};
struct VarargsInterface {
    int (*pop_int)(struct Varargs *);
};
struct VarargsV {
    struct Varargs base;
    va_list args;
};

typedef double (*vahandler)(int, va_list);
typedef double (*vahandler_ptr)(int, va_list *);

typedef double (*struct_varargs_handler)(int, struct Varargs *);

struct A {
    int x;
    double y;
};

struct Large {
    float f1;
    float f2;
    float f3;
    double d1;
    double d2;
    double d3;
    int i1;
    int i2;
    int i3;
    long l1;
    long l2;
    long l3;
};

double sumDoublesNative(int count, va_list args);

double sumDoublesNativeWithPtr(int count, va_list *args);

double testVariousTypesNative(int count, va_list args);

double testLargeStructNative(int count, va_list args);

#endif // VAHANDLER_H

/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
#include <stdio.h>

#include "vahandler.h"

double sumDoublesNative(int count, va_list args) {
    double sum = 0;
    int i = 0;
    for (; i < count; ++i) {
        double num = va_arg(args, double);
        printf("arg[%d]=%f\n", i, num);
        sum += num;
    }
    return sum;
}

double testVariousTypesNative(int count, va_list args) {
    double sum = 0;
    int i = 0;
    for (; i < count; ++i) {
        double num1 = va_arg(args, double);
        int num2 = va_arg(args, int);
        sum += num1 + num2;
    }
    char *msg = va_arg(args, char *);
    struct A a = va_arg(args, struct A);
    struct A b = va_arg(args, struct A);
    struct A *c = va_arg(args, struct A *);
    int overflow1 = va_arg(args, int);
    char *overflow2 = va_arg(args, char *);
    printf("%s, %d, %f, %d, %f, %d, %f, %d, %s\n", msg, a.x, a.y, b.x, b.y, c->x, c->y, overflow1, overflow2);
    return sum;
}

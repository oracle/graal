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
#include <stdlib.h>
#include <stdio.h>
#include <math.h>

#include "vahandler.h"

static va_list globalVAList;

double callVAHandler(vahandler vaHandler, int count, ...) {
    va_list args;
    va_start(args, count);
    double res = (*vaHandler)(count, args);
    va_end(args);
    return res;
}

double callVAHandlers(vahandler vaHandler1, vahandler vaHandler2, int count, ...) {
    va_list args;
    va_start(args, count);
    double res1 = (*vaHandler1)(count / 2, args);
    double res2 = (*vaHandler2)(count / 2, args);
    va_end(args);
    return res1 + res2;
}

double callVAHandlerWithGlobalVAList(vahandler vaHandler, int count, ...) {
    va_start(globalVAList, count);
    double res = (*vaHandler)(count, globalVAList);
    va_end(globalVAList);
    return res;
}

double callVAHandlerWithAllocatedVAList(vahandler vaHandler, int count, ...) {
    // multiply by 2 to ensure the capacity, as the real size may be greater
    va_list *args = malloc(2 * sizeof(va_list));
    va_start(*args, count);
    double res = (*vaHandler)(count, *args);
    va_end(*args);
    free(args);
    return res;
}

double sumIntsLLVM(int count, va_list args) {
    int sum = 0;
    for (int i = 0; i < count; ++i) {
        int num = va_arg(args, int);
        printf("arg[%d]=%d\n", i, num);
        sum += num;
    }
    return sum;
}

double sumDoublesLLVM(int count, va_list args) {
    double sum = 0;
    for (int i = 0; i < count; ++i) {
        double num = va_arg(args, double);
        printf("arg[%d]=%f\n", i, num);
        sum += num;
    }
    return sum;
}

double testVariousTypesLLVM(int count, va_list args) {
    //double testVariousTypesLLVM(int count, ...) {
    //    va_list args;
    //    va_start(args, count);
    double sum = 0;
    for (int i = 0; i < count; ++i) {
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
    //    va_end(args);
    return sum;
}

double testVACopy(vahandler vaHandler1, vahandler vaHandler2, int count, ...) {
    va_list args1;
    va_start(args1, count);
    va_list args2;
    va_copy(args2, args1);
    double res1 = (*vaHandler1)(count / 2, args1);
    double res2 = (*vaHandler2)(count / 2, args2);
    va_end(args1);
    va_end(args2);
    return res1 + res2;
}

double testDelayedVACopy(vahandler vaHandler1, vahandler vaHandler2, int count, ...) {
    va_list args1;
    va_start(args1, count);
    va_list args2;
    double res1 = (*vaHandler1)(count / 2, args1);
    va_copy(args2, args1);
    double res2 = (*vaHandler2)(count / 2, args2);
    va_end(args1);
    va_end(args2);
    return res1 + res2;
}

double testGlobalVACopy1(vahandler vaHandler1, vahandler vaHandler2, int count, ...) {
    va_start(globalVAList, count);
    va_list args2;
    va_copy(args2, globalVAList);
    double res1 = (*vaHandler1)(count / 2, globalVAList);
    double res2 = (*vaHandler2)(count / 2, args2);
    va_end(globalVAList);
    va_end(args2);
    return res1 + res2;
}

double testGlobalVACopy2(vahandler vaHandler1, vahandler vaHandler2, int count, ...) {
    va_list args1;
    va_start(args1, count);
    va_copy(globalVAList, args1);
    double res1 = (*vaHandler1)(count / 2, args1);
    double res2 = (*vaHandler2)(count / 2, globalVAList);
    va_end(args1);
    va_end(globalVAList);
    return res1 + res2;
}

double testGlobalVACopy3(vahandler vaHandler1, vahandler vaHandler2, int count, ...) {
    va_start(globalVAList, count);
    // multiply by 2 to ensure the capacity, as the real size may be greater
    va_list *args2 = malloc(2 * sizeof(va_list));
    va_copy(*args2, globalVAList);
    double res1 = (*vaHandler1)(count / 2, globalVAList);
    double res2 = (*vaHandler2)(count / 2, *args2);
    va_end(*args2);
    free(args2);
    va_end(globalVAList);
    return res1 + res2;
}

int main(void) {
    printf("Sum of doubles (LLVM) (Global VAList)   : %f\n",
           callVAHandlerWithGlobalVAList(sumDoublesLLVM, 8, 1., 2, 3., 4, 5., 6, 7., 8, 9., 10, 11., 12, 13., 14, 15., 16));
    printf("Sum of doubles (LLVM) (Allocated VAList): %f\n",
           callVAHandlerWithGlobalVAList(sumDoublesLLVM, 8, 1., 2, 3., 4, 5., 6, 7., 8, 9., 10, 11., 12, 13., 14, 15., 16));

    printf("Sum of doubles (LLVM)           : %f\n", callVAHandler(sumDoublesLLVM, 8, 1., 2, 3., 4, 5., 6, 7., 8, 9., 10, 11., 12, 13., 14, 15., 16));
    printf("Sum of ints (LLVM)              : %f\n", callVAHandler(sumIntsLLVM, 8, 1., 2, 3., 4, 5., 6, 7., 8, 9., 10, 11., 12, 13., 14, 15., 16));

#ifndef NO_NATIVE_TESTS
    printf("Sum of doubles (native)         : %f\n",
           callVAHandler(sumDoublesNative, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("Sum of doubles (LLVM, native)   : %f\n",
           callVAHandlers(sumDoublesLLVM, sumDoublesNative, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("Sum of doubles (native, LLVM)   : %f\n",
           callVAHandlers(sumDoublesNative, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("Sum of doubles (native, native) : %f\n",
           callVAHandlers(sumDoublesNative, sumDoublesNative, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
#endif
    printf("Sum of doubles (LLVM, LLVM)     : %f\n",
           callVAHandlers(sumDoublesLLVM, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));

    printf("VACopy test (LLVM, LLVM) (Global VAList 1)  : %f\n",
           testGlobalVACopy1(sumDoublesLLVM, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("VACopy test (LLVM, LLVM) (Global VAList 2)  : %f\n",
           testGlobalVACopy2(sumDoublesLLVM, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("VACopy test (LLVM, LLVM) (Global VAList 3)  : %f\n",
           testGlobalVACopy3(sumDoublesLLVM, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("VACopy test (LLVM, LLVM)     : %f\n",
           testVACopy(sumDoublesLLVM, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
#ifndef NO_NATIVE_TESTS
    printf("VACopy test (native, LLVM)   : %f\n",
           testVACopy(sumDoublesNative, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("VACopy test (LLVM, native)   : %f\n",
           testVACopy(sumDoublesLLVM, sumDoublesNative, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("VACopy test (native, native) : %f\n",
           testVACopy(sumDoublesNative, sumDoublesNative, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
#endif
    printf("Delayed VACopy test (LLVM, LLVM)     : %f\n",
           testDelayedVACopy(sumDoublesLLVM, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
#ifndef NO_NATIVE_TESTS
    printf("Delayed VACopy test (native, LLVM)   : %f\n",
           testDelayedVACopy(sumDoublesNative, sumDoublesLLVM, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("Delayed VACopy test (LLVM, native)   : %f\n",
           testDelayedVACopy(sumDoublesLLVM, sumDoublesNative, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
    printf("Delayed VACopy test (native, native) : %f\n",
           testDelayedVACopy(sumDoublesNative, sumDoublesNative, 16, 1., 2., 3., 4., 5., 6., 7., 8., 9., 10., 11., 12., 13., 14., 15., 16.));
#endif
    struct A a;
    a.x = 10;
    a.y = 3.25;
    struct A b;
    b.x = 11;
    b.y = 4.25;
    struct A *c = malloc(sizeof(struct A));
    c->x = 12;
    c->y = 5.25;
    printf("Test various types (LLVM):\n");
    printf("res=%f\n", callVAHandler(testVariousTypesLLVM, 4, 25.0, 1, 27.25, 2, 26.75, 3, 25.5, 4, "Hello!", a, b, c, 1000, "Hello2!"));
#ifndef NO_NATIVE_TESTS
    printf("Test various types (native):\n");
    printf("res=%f\n", callVAHandler(testVariousTypesNative, 4, 25.0, 1, 27.25, 2, 26.75, 3, 25.5, 4, "Hello!", a, b, c, 1000, "Hello2!"));
#endif
}

/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include "longdouble.h"

__attribute__((noinline)) void toLong(long double n) {

    long m = (long) n;
    printf("to long %ld\n", m);
    printBits(sizeof(m), &m);
    printfplong("to long fp", &m);
}

__attribute__((noinline)) void toDouble(long double n) {

    double m = (double) n;
    printf("to double %lf\n", m);
    printBits(sizeof(m), &m);
    printfpdouble("to double fp", &m);
}

__attribute__((noinline)) void toInt(long double n) {

    int m = (int) n;
    printf("to int %d\n", m);
    printBits(sizeof(m), &m);
    printfpint("to int fp", &m);
}

__attribute__((noinline)) void toFloat(long double n) {

    float m = (float) n;
    printf("to float %f\n", m);
    printBits(sizeof(m), &m);
    printfpfloat("to float fp", &m);
}

int main(void) {

    long double m = 5.0;
    toLong(m);
    toDouble(m);
    toInt(m);
    toFloat(m);
    return 0;
}
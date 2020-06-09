/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
#include <polyglot.h>
#include <stdlib.h>

typedef struct complex {
    double real;
    double imaginary;
    struct complex (*add)(struct complex *);
} COMPLEX;

typedef struct compound {
    int (*fourtyTwo)(void);
    double (*plus)(double, double);
    void *(*returnsNull)(void);
    struct compound *(*returnsThis)(void);
} COMPOUND;

typedef struct values {
    char byteValue;
    short shortValue;
    int intValue;
    long longValue;
    float floatValue;
    double doubleValue;
    char charValue;
    bool booleanValue;
} VALUES;

int fourtyTwo(void) {
    return 42;
}

double plus(double a, double b) {
    return a + b;
}

void *identity(void *x) {
    return x;
}

int apply(int (*f)(int a, int b)) {
    return f(18, 32) + 10;
}

static int cnt_value = 0;
int count(void) {
    return ++cnt_value;
}

void *returnsNull(void) {
    return NULL;
}

void complexAdd(COMPLEX *a, COMPLEX *b) {
    a->real = a->real + b->real;
    a->imaginary = a->imaginary + b->imaginary;
}

void complexAddWithMethod(COMPLEX *a, COMPLEX *b) {
    a->add(b);
}

double complexSumReal(COMPLEX *array) {
    double result = 0;
    for (int i = 0; i < polyglot_get_array_size(array); i++)
        result += array[i].real;
    return result;
}

void complexCopy(COMPLEX *dst, COMPLEX *src) {
    for (int i = 0; i < polyglot_get_array_size(dst); i++)
        dst[i] = src[i];
}

COMPOUND compoundObject(void) {
    COMPOUND obj;
    obj.fourtyTwo = fourtyTwo;
    obj.plus = plus;
    obj.returnsNull = returnsNull;
    // obj.returnsThis = obj;
    return obj;
}

VALUES valuesObject(void) {
    VALUES obj;
    obj.byteValue = 0;
    obj.shortValue = 0;
    obj.intValue = 0;
    obj.longValue = 0;
    obj.floatValue = 0;
    obj.doubleValue = 0;
    obj.charValue = '0';
    obj.booleanValue = (1 == 0);
    return obj;
}

void addToArray(int *array, int index, int value) {
    array[index] += value;
}

void countUpWhile(int (*fn)(int)) {
    int counter = 0;
    while (fn(counter))
        counter++;
}

int main(void) {
    return 0;
}

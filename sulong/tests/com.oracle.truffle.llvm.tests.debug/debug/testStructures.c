/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates.
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

struct simpleStruct
{
    int a;
    float b;
    unsigned int c[3];
};

struct bitFieldsStruct
{
    unsigned int a : 8;
    unsigned int b : 8;
    unsigned int c : 8;
    unsigned int d : 8;
    unsigned int e : 8;
    unsigned int f : 8;
    int g : 8;
    int h : 8;
};

struct combinableStruct
{
    int a;
    int b;
};

struct splittableStruct
{
    long int a;
    long int b;
};

struct globalStruct
{
    int a;
    float b;
} myGlobalStruct;

// opt -mem2reg will reduce the struct arg to a single i64 value
__attribute__((noinline)) int testCombinedStructArg(struct combinableStruct str)
{
    printf("str.a = %d\nstr.b = %d\n", str.a, str.b);
    return 0;
}

// opt -mem2reg will reduce the struct arg to two separate i64 values
__attribute__((noinline)) int testSplittedStructArg(struct splittableStruct str)
{
    printf("str.a = %d\nstr.b = %d\n", str.a, str.b);
    return 0;
}

int start() __attribute__((constructor))
{
    myGlobalStruct.a = 123;
    myGlobalStruct.b = 124.5f;
    
    struct simpleStruct mySimpleStruct;
    mySimpleStruct.a = 15;
    mySimpleStruct.b = 17.3f;
    mySimpleStruct.c[0] = 102;
    mySimpleStruct.c[1] = 111;
    mySimpleStruct.c[2] = 111;

    struct bitFieldsStruct myBitFields;
    myBitFields.a = 255;
    myBitFields.b = 129;
    myBitFields.c = 128;
    myBitFields.d = 127;
    myBitFields.e = 126;
    myBitFields.f = 0;
    myBitFields.g = -1;
    myBitFields.h = 0;

    struct combinableStruct myCombinableStruct;
    myCombinableStruct.a = 128;
    myCombinableStruct.b = 256;
    testCombinedStructArg(myCombinableStruct);

    struct splittableStruct mySplittableStruct;
    mySplittableStruct.a = 128;
    mySplittableStruct.b = 256;
    testSplittedStructArg(mySplittableStruct);

    return 0;
}

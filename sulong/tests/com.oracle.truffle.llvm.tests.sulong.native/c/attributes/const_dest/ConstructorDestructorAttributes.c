/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

// This test is an extended version of
// SingleSource/Regression/C/ConstructorDestructorAttributes.c
// in the LLVMv3.2 Testsuite.

// the order of constructors with the same priorities is not
// specified and can vary between platforms and compilers

void ctor1() __attribute__((constructor(101)));

void ctor1() {
    printf("Create1!\n");
}

void ctor2() __attribute__((constructor(102)));

void ctor2() {
    printf("Create2!\n");
}

void ctor3() __attribute__((constructor(103)));

void ctor3() {
    printf("Create3!\n");
}

void ctor4() __attribute__((constructor(104)));

void ctor4() {
    printf("Create4!\n");
}

void dtor1() __attribute__((destructor(102)));

void dtor1() {
    printf("Destroy1!\n");
}

void dtor2() __attribute__((destructor(103)));

void dtor2() {
    printf("Destroy2!\n");
}

void dtor3() __attribute__((destructor(104)));

void dtor3() {
    printf("Destroy3!\n");
}

void dtor4() __attribute__((destructor(105)));

void dtor4() {
    printf("Destroy4!\n");
}

int main() {
    return 0;
}

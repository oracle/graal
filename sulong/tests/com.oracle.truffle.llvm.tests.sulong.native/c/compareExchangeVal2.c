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
#include <stdlib.h>
#include <stdio.h>

void testShort0() {
    short l[2];
    short cmp, repl;
    short *ptr = &(l[0]);
    int replaced;

    l[0] = 32;
    l[1] = 42;

    cmp = 32;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);

    l[0] = 32;
    l[1] = 42;

    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
}

void testShort1() {
    short l[2];
    short cmp, repl;
    short *ptr = &(l[1]);
    int replaced;

    l[0] = 32;
    l[1] = 42;

    cmp = 42;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);

    l[0] = 32;
    l[1] = 42;

    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
}

void testByte0() {
    char l[4];
    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;

    char cmp, repl;
    char *ptr = &(l[0]);
    int replaced;

    cmp = 12;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);

    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);
}

void testByte1() {
    char l[4];
    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;

    char cmp, repl;
    char *ptr = &(l[1]);
    int replaced;

    cmp = 22;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);

    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);
}

void testByte2() {
    char l[4];
    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;

    char cmp, repl;
    char *ptr = &(l[2]);
    int replaced;

    cmp = 32;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);

    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);
}

void testByte3() {
    char l[4];
    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;

    char cmp, repl;
    char *ptr = &(l[3]);
    int replaced;

    cmp = 42;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);

    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap(ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);
}

int main() {
    testShort0();
    testShort1();
    testByte0();
    testByte1();
    testByte2();
    testByte3();
}

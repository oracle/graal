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

void testLong() {
    long l, cmp, repl;
    long *ptr = &l;
    ;
    int replaced;

    l = 1L;
    cmp = 2L;
    repl = 3L;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = 1L;
    cmp = 1L;
    repl = 3L;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

void testInt() {
    int l, cmp, repl;
    int *ptr = &l;
    ;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

void testShort() {
    short l, cmp, repl;
    short *ptr = &l;
    ;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

void testByte() {
    char l, cmp, repl;
    char *ptr = &l;
    ;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

void testPointer() {
    // the llvm cmpxchg instruction supports pointers, but llvm maps this to an i64 comparison
    char origL = 1, origCmp = 2, origRepl = 3;
    char *l;
    char *cmp;
    char *repl;
    char **ptr = &l;
    int replaced;

    l = &origL;
    cmp = &origCmp;
    repl = &origRepl;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = &origL;
    cmp = &origL;
    repl = &origRepl;
    replaced = __sync_bool_compare_and_swap(ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

int main() {
    testLong();
    testInt();
    testShort();
    testByte();
    testPointer();
}

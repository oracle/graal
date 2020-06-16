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
#include <stdbool.h>

struct container {
    int (*callback)(int p1, int p2);
    int p1;
};

void printPointerToArray(int **a) {

    fprintf(stderr, "Native: a = %p\n", a);
    fprintf(stderr, "Native: *a = %p\n", *a);

    fprintf(stderr, "Native: *a[0] = %i\n", (*a)[0]);
    fprintf(stderr, "Native: *a[1] = %i\n", (*a)[1]);
    fprintf(stderr, "Native: *a[2] = %i\n", (*a)[2]);
}

void printArray(int *a) {

    fprintf(stderr, "Native: a = %p\n", a);

    fprintf(stderr, "Native: a[0] = %i\n", a[0]);
    fprintf(stderr, "Native: a[1] = %i\n", a[1]);
    fprintf(stderr, "Native: a[2] = %i\n", a[2]);
}

void *create_container(int (*callback)(int p1, int p2), int p1) {
    struct container *c = malloc(sizeof(struct container));
    c->callback = callback;
    c->p1 = p1;
    return c;
}

int add(int a, int b) {
    return a + b;
}

int (*get_callback_function())(int, int) {
    return &add;
}

void store_native_function(void *container) {
    struct container *c = (struct container *) container;
    c->callback = add;
}

int call_callback(void *container, int p2) {
    struct container *c = (struct container *) container;
    return c->callback(c->p1, p2);
}

int call_callback2(void *container) {
    struct container *c = (struct container *) container;
    return c->callback(20, 22);
}

int call_typecast(int (*fn)(void)) {
    int (*fn_cast)(int) = (int (*)(int)) fn;
    return fn_cast(42);
}

int nullPointerFunctionTest(void (*foo)()) {
    if (foo == 0) {
        return 42;
    } else {
        return 84;
    }
}

int callbackPointerArgTest(int (*callback)(void *), void *arg) {
    return callback(arg);
}

bool nativeInvert(bool value) {
    return !value;
}

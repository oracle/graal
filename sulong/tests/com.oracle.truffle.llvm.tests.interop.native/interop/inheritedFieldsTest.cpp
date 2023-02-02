/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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
#include <stdlib.h>
#include <graalvm/llvm/polyglot.h>

class A {
public:
    int a;
    A();
};

POLYGLOT_DECLARE_TYPE(A);

class B : public A {
public:
    int b;
    B();
};

POLYGLOT_DECLARE_TYPE(B);

A::A() {
    a = 3;
}
B::B() : A() {
    b = 4;
}

void *prepareA() {
    A *a = (A *) malloc(sizeof(A));
    a->a = 3;
    return polyglot_from_A(a);
}

void *prepareB() {
    B *b = (B *) malloc(sizeof(B));
    b->a = 3;
    b->b = 4;
    return polyglot_from_B(b);
}

//----------

struct C {
    int c;
};

POLYGLOT_DECLARE_TYPE(C);

class D : public C {
public:
    int d;
    D();
};

D::D() {
    c = 3;
    d = 4;
}

POLYGLOT_DECLARE_TYPE(D);

void *prepareC() {
    C *c = (C *) malloc(sizeof(C));
    c->c = 3;
    return polyglot_from_C(c);
}

void *prepareD() {
    D *d = (D *) malloc(sizeof(D));
    d->c = 3;
    d->d = 4;
    return polyglot_from_D(d);
}

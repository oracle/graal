/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

#include <graalvm/llvm/polyglot.h>

class A1 {
public:
    virtual int a1();
};

class A2 {
public:
    virtual int a2();
};

class A3 {
public:
    virtual int a3();
};

class A4 {
public:
    virtual int a4(int x);
};

POLYGLOT_DECLARE_TYPE(A1);
POLYGLOT_DECLARE_TYPE(A2);
POLYGLOT_DECLARE_TYPE(A3);
POLYGLOT_DECLARE_TYPE(A4);

class Impl : public A1, public A2, public A3, public A4 {
public:
    virtual int a1();
    virtual int a2();
    virtual int a4(int x);
    virtual int impl();
};

void *getA1() {
    return polyglot_from_A1(new Impl());
}

void *getA2() {
    return polyglot_from_A2(new Impl());
}

void *getA3() {
    return polyglot_from_A3(new Impl());
}

void *getA4() {
    return polyglot_from_A4(new Impl());
}

int A1::a1() {
    return 1;
}
int A2::a2() {
    return 2;
}
int A3::a3() {
    return 3;
}
int A4::a4(int x) {
    return 4 + x;
}
int Impl::a1() {
    return 11;
}
int Impl::a2() {
    return 12;
}
int Impl::a4(int x) {
    return 14 + x;
}
int Impl::impl() {
    return 10;
}

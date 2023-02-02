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

#include <graalvm/llvm/polyglot.h>

//-----------------------------------------test via polyglot API

class A {
public:
    int k;
    virtual int foo(int x);
};

int A::foo(int x) {
    return 0 * x;
} //dummy

// A constructor is required to ensure the vtable is emitted
A *testCreateA() {
    return new A();
}

POLYGLOT_DECLARE_TYPE(A);

int evaluateDirectly(A *a, int x) {
    return a->foo(x);
}

int evaluateWithPolyglotConversion(polyglot_value aObj, int x) {
    return evaluateDirectly(polyglot_as_A(aObj), x);
}

//------------------------------------------test native
class B1 {
public:
    virtual int f();
    int g();
};

class B2 : public B1 {
public:
    int f() override;
    int g();
};

int B1::f() {
    return 0;
}
int B2::f() {
    return 2;
}
int B1::g() {
    return 0;
}
int B2::g() {
    return 2;
}

int getB1F() {
    B1 *b2 = new B2();
    return b2->f();
}

int getB1G() {
    B1 *b2 = new B2();
    return b2->g();
}

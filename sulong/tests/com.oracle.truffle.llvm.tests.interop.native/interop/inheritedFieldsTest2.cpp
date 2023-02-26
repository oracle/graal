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

class A0 {
public:
    int a0;
};

class A1 : public A0 {
public:
    int a1;
};

class A2 : public A1 {
public:
    int a2;
};

class A3 : public A2 {
public:
    int a3;
};

class A4 : public A3 {
public:
    int a4;
};

POLYGLOT_DECLARE_TYPE(A0);
POLYGLOT_DECLARE_TYPE(A1);
POLYGLOT_DECLARE_TYPE(A2);
POLYGLOT_DECLARE_TYPE(A3);
POLYGLOT_DECLARE_TYPE(A4);

bool check0(polyglot_value a0Obj) {
    A0 *a0 = polyglot_as_A0(a0Obj);
    return a0->a0 == 0;
}

bool check1(polyglot_value a1Obj) {
    A1 *a1 = polyglot_as_A1(a1Obj);
    return (a1->a0 == 0) && (a1->a1 == 1);
}

bool check2(polyglot_value a2Obj) {
    A2 *a2 = polyglot_as_A2(a2Obj);
    return (a2->a0 == 0) && (a2->a1 == 1) && (a2->a2 == 2);
}

bool check3(polyglot_value a3Obj) {
    A3 *a3 = polyglot_as_A3(a3Obj);
    return (a3->a0 == 0) && (a3->a1 == 1) && (a3->a2 == 2) && (a3->a3 == 3);
}

bool check4(polyglot_value a4Obj) {
    A4 *a4 = polyglot_as_A4(a4Obj);
    return (a4->a0 == 0) && (a4->a1 == 1) && (a4->a2 == 2) && (a4->a3 == 3) && (a4->a4 == 4);
}

//-------------------------------

struct B0 {
    int b0;
};

class B1 : public B0 {
public:
    int b1;
};

class B2 : public B1 {
public:
    int b2;
};

POLYGLOT_DECLARE_TYPE(B0);
POLYGLOT_DECLARE_TYPE(B1);
POLYGLOT_DECLARE_TYPE(B2);

bool checkB0(polyglot_value b0Obj) {
    B0 *b0 = polyglot_as_B0(b0Obj);
    return b0->b0 == 0;
}

bool checkB1(polyglot_value b1Obj) {
    B1 *b1 = polyglot_as_B1(b1Obj);
    return (b1->b0 == 0) && (b1->b1 == 1);
}

bool checkB2(polyglot_value b2Obj) {
    B2 *b2 = polyglot_as_B2(b2Obj);
    return (b2->b0 == 0) && (b2->b1 == 1) && (b2->b2 == 2);
}

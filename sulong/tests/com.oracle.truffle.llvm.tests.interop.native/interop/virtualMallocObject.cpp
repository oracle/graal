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
#include <stdlib.h>
#include <truffle.h>

class Test {

public:
    void setA(long a);
    void setB(double b);
    void setC(float c);
    void setD(int d);
    void setE(unsigned char e);
    void setF(bool f);

    long getA(void);
    double getB(void);
    float getC(void);
    int getD(void);
    unsigned char getE(void);
    bool getF(void);

    void *operator new(size_t size) {
        return truffle_virtual_malloc(size);
    }

    void operator delete(void *p) {
        // free(p);
    }

private:
    long a;
    double b;
    float c;
    int d;
    unsigned char e;
    bool f;
};

void Test::setA(long v) {
    a = v;
}

void Test::setB(double v) {
    b = v;
}

void Test::setC(float v) {
    c = v;
}

void Test::setD(int v) {
    d = v;
}

void Test::setE(unsigned char v) {
    e = v;
}

void Test::setF(bool v) {
    f = v;
}

long Test::getA(void) {
    return a;
}

double Test::getB(void) {
    return b;
}

float Test::getC(void) {
    return c;
}

int Test::getD(void) {
    return d;
}

unsigned char Test::getE(void) {
    return e;
}

bool Test::getF(void) {
    return f;
}

// test functions
extern "C" long testGetA(void) {
    Test *t = new Test();

    t->setA(42);
    t->setB(13.4);
    t->setC(13.5f);
    t->setD(56);
    t->setE(5);
    t->setF(true);

    return t->getA();
}

extern "C" double testGetB(void) {
    Test *t = new Test();

    t->setA(42);
    t->setB(13.4);
    t->setC(13.5f);
    t->setD(56);
    t->setE(5);
    t->setF(true);

    return t->getB();
}

extern "C" float testGetC(void) {
    Test *t = new Test();

    t->setA(42);
    t->setB(13.4);
    t->setC(13.5f);
    t->setD(56);
    t->setE(5);
    t->setF(true);

    return t->getC();
}

extern "C" int testGetD(void) {
    Test *t = new Test();

    t->setA(42);
    t->setB(13.4);
    t->setC(13.5f);
    t->setD(56);
    t->setE(5);
    t->setF(true);

    return t->getD();
}

extern "C" unsigned char testGetE(void) {
    Test *t = new Test();

    t->setA(42);
    t->setB(13.4);
    t->setC(13.5f);
    t->setD(56);
    t->setE(5);
    t->setF(true);

    return t->getE();
}

extern "C" bool testGetF(void) {
    Test *t = new Test();

    t->setA(42);
    t->setB(13.4);
    t->setC(13.5f);
    t->setD(56);
    t->setE(5);
    t->setF(true);

    return t->getF();
}

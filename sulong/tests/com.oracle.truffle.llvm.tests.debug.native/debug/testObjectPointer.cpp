/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
#include <cstdio>

class MyClass {
private:
    int a;
    float b;
    double c;
    long d;
    char e;
    short f[3];

public:
    MyClass(int _a, float _b, double _c, long _d, char _e, short f1, short f2, short f3) {
        this->a = _a;
        this->b = _b;
        this->c = _c;
        this->d = _d;
        this->e = _e;
        this->f[0] = f1;
        this->f[1] = f2;
        this->f[2] = f3;
    }

    void myMethod() {
    }
};

static void myStaticMethod(MyClass &myClass) {
}

#define MYCLASS_ARGS 16, 3.2f, 4.657, 149237354238697, 'e', -32768, -1, 32767

MyClass globalObj(MYCLASS_ARGS);
MyClass *globalPtr = new MyClass(MYCLASS_ARGS);

// set constructor priority to ensure 'start' is
// not executed prior to the global initializers
int start() __attribute__((constructor(65536))) {
    MyClass localObj(MYCLASS_ARGS);
    MyClass *localPtr = new MyClass(MYCLASS_ARGS);

    localObj.myMethod();
    myStaticMethod(localObj);
    localPtr->myMethod();
    myStaticMethod(*localPtr);
    globalObj.myMethod();
    myStaticMethod(globalObj);
    globalPtr->myMethod();
    myStaticMethod(*globalPtr);

    return 0;
}

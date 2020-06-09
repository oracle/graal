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

union simpleUnion {
    int a;
    int b;
    int c;
};

// at O1, this is represented as a single float value
union floatUnion {
    float a;
    short b;
    short c;
    float d;
};

// at O1, this is represented as a single double value
union doubleUnion {
    float a;
    double b;
    int c;
    double d;
};

// at O1, this is represented as a single long value
union pointerUnion {
    short a;
    int b;
    int *c;
};

union simpleUnion myGlobalSimpleUnion;
union floatUnion myGlobalFloatUnion;
union doubleUnion myGlobalDoubleUnion;
union pointerUnion myGlobalPointerUnion;

int start() __attribute__((constructor)) {
    myGlobalSimpleUnion.a = 1 << 4;
    myGlobalSimpleUnion.b = 1 << 5;
    myGlobalSimpleUnion.c = 1 << 9;

    myGlobalFloatUnion.a = 5.9f;
    myGlobalFloatUnion.b = 1;
    myGlobalFloatUnion.c = 728;
    myGlobalFloatUnion.d = 0.0;

    myGlobalDoubleUnion.a = 9.2f;
    myGlobalDoubleUnion.b = 4.3;
    myGlobalDoubleUnion.c = 19;
    myGlobalDoubleUnion.d = 0.0;

    myGlobalPointerUnion.a = 14;
    myGlobalPointerUnion.b = 23;
    myGlobalPointerUnion.c = 0xabcdef;

    union simpleUnion mySimpleUnion;
    mySimpleUnion.a = 1 << 3;
    mySimpleUnion.b = 1 << 6;
    mySimpleUnion.c = 1 << 8;

    union floatUnion myFloatUnion;
    myFloatUnion.a = 3.7f;
    myFloatUnion.b = 1;
    myFloatUnion.c = 12345;
    myFloatUnion.d = 0.0;

    union doubleUnion myDoubleUnion;
    myDoubleUnion.a = 0.3f;
    myDoubleUnion.b = 7.6;
    myDoubleUnion.c = 5;
    myDoubleUnion.d = 0.0;

    union pointerUnion myPointerUnion;
    myPointerUnion.a = 213;
    myPointerUnion.b = 0x0f0f0f0f;
    myPointerUnion.c = 0xffffffff000000ff;

    return 0;
}

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
#include <stdio.h>

void voidFuncNoArgs(void) {
}

void voidFuncImplicitVarArgs() {
}

void voidFuncIntArg(__attribute__((unused)) int i) {
}

void voidFuncIntVarArgs(__attribute__((unused)) int i, ...) {
}

int intFuncNoArgs(void) {
    return 42;
}

int intFuncImplicitVarArgs() {
    return 42;
}

int intFuncIntArg(__attribute__((unused)) int i) {
    return 42;
}

int intFuncIntVarArgs(__attribute__((unused)) int i, ...) {
    return 42;
}

__attribute__((constructor)) int start() {
    void (*voidFuncNoArgsPtr)(void) = &voidFuncNoArgs;
    void (*voidFuncImplicitVarArgsPtr)() = &voidFuncImplicitVarArgs;
    void (*voidFuncIntArgPtr)(int) = &voidFuncIntArg;
    void (*voidFuncIntVarArgsPtr)(int, ...) = &voidFuncIntVarArgs;
    int (*intFuncNoArgsPtr)(void) = &intFuncNoArgs;
    int (*intFuncImplicitVarArgsPtr)() = &intFuncImplicitVarArgs;
    int (*intFuncIntArgPtr)(int) = &intFuncIntArg;
    int (*intFuncIntVarArgsPtr)(int, ...) = &intFuncIntVarArgs;

    __builtin_debugtrap();

    voidFuncNoArgsPtr();
    voidFuncImplicitVarArgsPtr();
    voidFuncIntArgPtr(42);
    voidFuncIntVarArgsPtr(42, 42, 42);

    int res;
    res = intFuncNoArgsPtr();
    res = intFuncImplicitVarArgsPtr();
    res = intFuncIntArgPtr(42);
    res = intFuncIntVarArgsPtr(42, 42, 42);

    return 0;
}

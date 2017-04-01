/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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


extern "C"
unsigned int sulong_eh_canCatch(void *ptr, void *excpType, void *catchType) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
    return 0; 
}

extern "C"
void *sulong_eh_unwindHeader(void *ptr) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
    
}

extern "C"
void *sulong_eh_getExceptionPointer(void *unwindHeader) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
    return (void*) 0;
}

extern "C"
void *sulong_eh_getThrownObject(void *unwindHeader) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
    return (void*) 0;
}

extern "C"
void sulong_eh_throw(void *ptr, void *type, void *destructor, void (*unexpectedHandler)(), void (*terminateHandler)()) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
}

extern "C"
void *sulong_eh_getDestructor(void *ptr) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
    return (void*) 0;
}

extern "C"
void *sulong_eh_getType(void *ptr) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
    return (void*) 0;}

extern "C"
void sulong_eh_incrementHandlerCount(void *ptr) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
}

extern "C"
void sulong_eh_decrementHandlerCount(void *ptr) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
}

extern "C"
int sulong_eh_getHandlerCount(void *ptr) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
    return 0;
}

extern "C"
void sulong_eh_setHandlerCount(void *ptr, int value) {
    fprintf(stderr, "Sulong exception handling not supported in Sulong v3.2 mode; use Sulong mode v3.8 or higher.\n");
}

extern "C"
void *getNullPointer() {
    return (void*) 0;
}
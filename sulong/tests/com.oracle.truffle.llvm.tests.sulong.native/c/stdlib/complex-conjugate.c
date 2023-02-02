/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
#include <complex.h>
#include <stdlib.h>

#ifndef _WIN32
#define _Dcomplex double complex
#define _DCOMPLEX_(x, y) ((double) (x) + (double) (y) *I)
#define _Fcomplex float complex
#define _FCOMPLEX_(x, y) ((float) (x) + (float) (y) *I)
#define conjf conj
#endif

void testDouble() {
    volatile _Dcomplex z1 = _DCOMPLEX_(1.0, 3.0);
    volatile _Dcomplex z2 = _DCOMPLEX_(0.0, 0.0);
    volatile _Dcomplex conjugate1 = conj(z1);
    volatile _Dcomplex conjugate2 = conj(z2);
    if (creal(conjugate1) != 1.00 | cimag(conjugate1) != -3.0) {
        abort();
    }
    if (creal(conjugate2) != 0.00 | cimag(conjugate2) != 0.0) {
        abort();
    }
}

void testFloat() {
    volatile _Fcomplex z1 = _FCOMPLEX_(1.0, 3.0);
    volatile _Fcomplex z2 = _FCOMPLEX_(0.0, 0.0);
    volatile _Fcomplex conjugate1 = conjf(z1);
    volatile _Fcomplex conjugate2 = conjf(z2);
    if (crealf(conjugate1) != 1.00 | cimagf(conjugate1) != -3.0) {
        abort();
    }
    if (crealf(conjugate2) != 0.00 | cimagf(conjugate2) != 0.0) {
        abort();
    }
}

int main() {
    testDouble();
    testFloat();

    return 0;
}

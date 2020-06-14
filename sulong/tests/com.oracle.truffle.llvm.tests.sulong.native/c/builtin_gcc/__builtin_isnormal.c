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
int main() {
    volatile float fNan = __builtin_nanf("");
    if (__builtin_isnormal(fNan)) {
        return 1;
    }
    volatile float fInf = __builtin_inff();
    if (__builtin_isnormal(fInf)) {
        return 1;
    }
    volatile float fZero = 0.f;
    if (__builtin_isnormal(fZero)) {
        return 1;
    }
    volatile float fOne = 1.f;
    if (!__builtin_isnormal(fOne)) {
        return 1;
    }
    volatile double dNan = __builtin_nan("");
    if (__builtin_isnormal(dNan)) {
        return 1;
    }
    volatile double dInf = __builtin_inf();
    if (__builtin_isnormal(dInf)) {
        return 1;
    }
    volatile double dZero = 0.;
    if (__builtin_isnormal(dZero)) {
        return 1;
    }
    volatile double dOne = 1.;
    if (!__builtin_isnormal(dOne)) {
        return 1;
    }
    volatile double lNan = __builtin_nanl("");
    if (__builtin_isnormal(lNan)) {
        return 1;
    }
    volatile double lInf = __builtin_infl();
    if (__builtin_isnormal(lInf)) {
        return 1;
    }
    volatile double lZero = 0.;
    if (__builtin_isnormal(lZero)) {
        return 1;
    }
    volatile double lOne = 1.;
    if (!__builtin_isnormal(lOne)) {
        return 1;
    }
    return 0;
}

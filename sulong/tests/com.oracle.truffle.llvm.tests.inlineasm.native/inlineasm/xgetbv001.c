/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
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
#include <cpuid.h>

#define OSXSAVE (1 << 27)
#define AVX (1 << 28)
#define AVX2 (1 << 5)

#define XCR0_X87 (1 << 0)
#define XCR0_SSE (1 << 1)
#define XCR0_AVX (1 << 2)

/*
 * The feature-detection sequence glibc and __builtin_cpu_supports use before trusting
 * CPUID's AVX bits: leaf-1 OSXSAVE, then XGETBV(0) to confirm the OS keeps x87/SSE/AVX
 * state enabled, then leaf-7/subleaf-0 for AVX2. Only invariants that hold on any
 * AVX2-capable host are asserted, so the native reference binary and Sulong agree.
 */
static inline unsigned int xgetbv_low(unsigned int xcr) {
    unsigned int lo;
    unsigned int hi;
    __asm__("xgetbv" : "=a"(lo), "=d"(hi) : "c"(xcr));
    return lo;
}

int main() {
    unsigned int a;
    unsigned int b;
    unsigned int c;
    unsigned int d;

    if (!__get_cpuid(0x1, &a, &b, &c, &d))
        return 1;
    if (!(c & OSXSAVE))
        return 2;
    if (!(c & AVX))
        return 3;

    unsigned int xcr0 = xgetbv_low(0);
    if ((xcr0 & (XCR0_X87 | XCR0_SSE | XCR0_AVX)) != (XCR0_X87 | XCR0_SSE | XCR0_AVX))
        return 4;

    if (!__get_cpuid_count(0x7, 0, &a, &b, &c, &d))
        return 5;
    if (!(b & AVX2))
        return 6;

    return 0;
}

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
#ifndef __clang__
#include <stdbool.h>
bool __builtin_mul_overflow(unsigned short, unsigned short, unsigned short *);
#endif

int main(int argc, const char **argv) {
    unsigned short res;

    if (__builtin_mul_overflow((unsigned short) 0x0, (unsigned short) 0x0, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x0, (unsigned short) 0x7FFF, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x0, (unsigned short) 0x8000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x1, (unsigned short) 0x7FFF, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x1, (unsigned short) 0x8000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x2, (unsigned short) 0x3FFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x2, (unsigned short) 0xC000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x2, (unsigned short) 0x7FFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x2, (unsigned short) 0x8000, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x4, (unsigned short) 0x7FFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x4, (unsigned short) 0x8000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x0F, (unsigned short) 0x8, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x1000, (unsigned short) 0x8, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x1000, (unsigned short) 0x1000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x7FFF, (unsigned short) 0x0, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x7FFF, (unsigned short) 0x1, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x7FFF, (unsigned short) 0x2, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x7FFF, (unsigned short) 0x4, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x7FFF, (unsigned short) 0x7FFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x7FFF, (unsigned short) 0x8000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x8000, (unsigned short) 0x0, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0x8000, (unsigned short) 0x1, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x8000, (unsigned short) 0x2, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x8000, (unsigned short) 0x7FFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0x8000, (unsigned short) 0x8000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0xFFFF, (unsigned short) 0x0, &res)) {
        return -1;
    }

    if (res != 0x0) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned short) 0xFFFF, (unsigned short) 0x1, &res)) {
        return -1;
    }

    if (res != 0xFFFF) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned short) 0xFFFF, (unsigned short) 0xFFFF, &res)) {
        return -1;
    }

    return 0;
}

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
bool __builtin_mul_overflow(unsigned int, unsigned int, unsigned int *);
#endif

int main(int argc, const char **argv) {
    unsigned int res;

#ifndef __clang__
#warning "Disable testcase for non clang compiler!"
    return 0;
#endif

    if (__builtin_mul_overflow((unsigned int) 0x0, (unsigned int) 0x0, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x0, (unsigned int) 0x7FFFFFFF, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x0, (unsigned int) 0x80000000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x1, (unsigned int) 0x7FFFFFFF, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x1, (unsigned int) 0x80000000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x2, (unsigned int) 0x3FFFFFFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x2, (unsigned int) 0xC0000000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x2, (unsigned int) 0x7FFFFFFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x2, (unsigned int) 0x80000000, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x4, (unsigned int) 0x7FFFFFFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x4, (unsigned int) 0x80000000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x0F, (unsigned int) 0x8, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x10000000, (unsigned int) 0x8, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x10000000, (unsigned int) 0x10000000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x7FFFFFFF, (unsigned int) 0x0, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x7FFFFFFF, (unsigned int) 0x1, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x7FFFFFFF, (unsigned int) 0x2, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x7FFFFFFF, (unsigned int) 0x4, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x7FFFFFFF, (unsigned int) 0x7FFFFFFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x7FFFFFFF, (unsigned int) 0x80000000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x80000000, (unsigned int) 0x0, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0x80000000, (unsigned int) 0x1, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x80000000, (unsigned int) 0x2, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x80000000, (unsigned int) 0x7FFFFFFF, &res)) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0x80000000, (unsigned int) 0x80000000, &res)) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0xFFFFFFFF, (unsigned int) 0x0, &res)) {
        return -1;
    }

    if (res != 0x0) {
        return -1;
    }

    if (__builtin_mul_overflow((unsigned int) 0xFFFFFFFF, (unsigned int) 0x1, &res)) {
        return -1;
    }

    if (res != 0xFFFFFFFF) {
        return -1;
    }

    if (!__builtin_mul_overflow((unsigned int) 0xFFFFFFFF, (unsigned int) 0xFFFFFFFF, &res)) {
        return -1;
    }

    return 0;
}

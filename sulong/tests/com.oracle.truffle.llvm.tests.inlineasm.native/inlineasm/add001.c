/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
#include "flags.h"

void test_add(int a, int b) {
    long out_flags;
    int out;
    __asm__ volatile("pushf\n"
                     "xorq %[flags], %[flags]\n"
                     "push %[flags]\n"
                     "popf\n"
                     "addl %[a], %[b]\n"
                     "pushf\n"
                     "pop %[flags]\n"
                     "popf\n"
                     : [flags] "=&r"(out_flags), [b] "=r"(out)
                     : [a] "r"(a), "1"(b));
    printf("%08x:%08x:%08x:%x:%x\n", a, b, out, (out_flags & CC_C) ? 1 : 0, (out_flags & CC_O) ? 1 : 0);
}

int main() {
    test_add(0x00000000, 0x00000000);
    test_add(0x00000000, 0x00000d0c);
    test_add(0x00000d0c, 0x00000000);
    test_add(0x00000d0c, 0x00000d0c);
    test_add(0xffffffff, 0x00000000);
    test_add(0xffffffff, 0x00000001);
    test_add(0xffffffff, 0x00000d0c);
    test_add(0xffffffff, 0x80000000);
    test_add(0xffffffff, 0xffffffff);
    test_add(0x80000000, 0x00000000);
    test_add(0x80000000, 0x00000d0c);
    test_add(0x80000000, 0x80000000);
    test_add(0x80000000, 0xffffffff);
}

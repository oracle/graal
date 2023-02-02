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

void test_adc(int a, int b, int cf) {
    long flags = cf ? CC_C : 0;
    long out_flags;
    int out;
    __asm__ volatile("pushf # this is an end-of-line comment\n"
                     "# this is a line comment\n"
                     "push %%rax #\n"
                     "#\n"
                     "/* this is a comment */\n"
                     "popf /* this is another comment */\n"
                     "/* */\n"
                     "/**/\n"
                     "adcl %[a], %[b] /**/\n"
                     "/* this is multi-line\n"
                     "comment */\n"
                     "pushf /* this is another multi-line\n"
                     "comment */ \n pop %%rax\n"
                     "popf\n"
                     : "=&a"(out_flags), [b] "=r"(out)
                     : [a] "r"(a), "1"(b), "a"(flags));
    printf("%08x:%08x:%x:%08x:%x:%x\n", a, b, cf, out, (out_flags & CC_C) ? 1 : 0, (out_flags & CC_O) ? 1 : 0);
}

int main() {
    test_adc(0x00000000, 0x00000000, 0x0);
    test_adc(0x00000000, 0x00000000, 0x1);
    test_adc(0x00000d0c, 0x00000000, 0x1);
    test_adc(0x00000d0c, 0x00000d0c, 0x1);
    test_adc(0x00000000, 0x00000d0c, 0x1);
    test_adc(0x00000d0c, 0x00000000, 0x0);
    test_adc(0x00000d0c, 0x00000d0c, 0x0);
    test_adc(0x00000000, 0x00000d0c, 0x0);
    test_adc(0xffffffff, 0x00000000, 0x0);
    test_adc(0xffffffff, 0x00000001, 0x0);
    test_adc(0xffffffff, 0x00000d0c, 0x0);
    test_adc(0xffffffff, 0x80000000, 0x0);
    test_adc(0xffffffff, 0xffffffff, 0x0);
    test_adc(0xffffffff, 0x00000000, 0x1);
    test_adc(0xffffffff, 0x00000001, 0x1);
    test_adc(0xffffffff, 0x00000d0c, 0x1);
    test_adc(0xffffffff, 0x80000000, 0x1);
    test_adc(0xffffffff, 0xffffffff, 0x1);
    test_adc(0x80000000, 0x00000000, 0x0);
    test_adc(0x80000000, 0x00000d0c, 0x0);
    test_adc(0x80000000, 0x80000000, 0x0);
    test_adc(0x80000000, 0xffffffff, 0x0);
    test_adc(0x80000000, 0x00000000, 0x1);
    test_adc(0x80000000, 0x00000d0c, 0x1);
    test_adc(0x80000000, 0x80000000, 0x1);
    test_adc(0x80000000, 0xffffffff, 0x1);
}

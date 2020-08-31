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
unsigned char __builtin_addcb(unsigned char, unsigned char, unsigned char, unsigned char *);
#endif

int main() {
    unsigned char carryout;

    __builtin_addcb((unsigned char) 0x0, (unsigned char) 0x0, 0, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0xFF, (unsigned char) 0x0, 0, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0x0, (unsigned char) 0xFF, 0, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0xFF, (unsigned char) 0x1, 0, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0x1, (unsigned char) 0xFF, 0, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0xFF, (unsigned char) 0xFF, 0, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0x0, (unsigned char) 0xFE, 1, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0x0, (unsigned char) 0xFF, 1, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0xFE, (unsigned char) 0x0, 1, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0xFF, (unsigned char) 0x0, 1, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcb((unsigned char) 0xFF, (unsigned char) 0xFF, 1, &carryout);
    if (carryout != 1) {
        return -1;
    }

    unsigned char res1 = __builtin_addcb((unsigned char) 0x0F, (unsigned char) 0x1, 0, &carryout);
    if (res1 != 0x10 || carryout != 0) {
        return -1;
    }

    unsigned char res2 = __builtin_addcb((unsigned char) 0x0F, (unsigned char) 0x1, 1, &carryout);
    if (res2 != 0x11 || carryout != 0) {
        return -1;
    }

    return 0;
}

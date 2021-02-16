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
unsigned short __builtin_addcs(unsigned short, unsigned short, unsigned short, unsigned short *);
#endif

int main() {
    unsigned short carryout;

    __builtin_addcs((unsigned short) 0x0, (unsigned short) 0x0, 0, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0xFFFF, (unsigned short) 0x0, 0, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0x0, (unsigned short) 0xFFFF, 0, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0xFFFF, (unsigned short) 0x1, 0, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0x1, (unsigned short) 0xFFFF, 0, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0xFFFF, (unsigned short) 0xFFFF, 0, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0x0, (unsigned short) 0xFFFE, 1, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0x0, (unsigned short) 0xFFFF, 1, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0xFFFE, (unsigned short) 0x0, 1, &carryout);
    if (carryout != 0) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0xFFFF, (unsigned short) 0x0, 1, &carryout);
    if (carryout != 1) {
        return -1;
    }

    __builtin_addcs((unsigned short) 0xFFFF, (unsigned short) 0xFFFF, 1, &carryout);
    if (carryout != 1) {
        return -1;
    }

    unsigned short res1 = __builtin_addcs((unsigned short) 0x0FFF, (unsigned short) 0x1, 0, &carryout);
    if (res1 != 0x1000 || carryout != 0) {
        return -1;
    }

    unsigned short res2 = __builtin_addcs((unsigned short) 0x0FFF, (unsigned short) 0x1, 1, &carryout);
    if (res2 != 0x1001 || carryout != 0) {
        return -1;
    }

    return 0;
}

/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
#include "stdio.h"

; /* A declaration needs to be put before the pragma, otherwise the pragma stays unterminated (i.e. unpopped) */

#pragma pack(push)
#pragma pack(1)
struct Struct {
    signed f0 : 14;
    unsigned f1 : 13;
    volatile signed f2 : 28;
    unsigned f3 : 23;
    const signed f4 : 12;
    volatile unsigned f5 : 21;
    volatile signed : 0;
};
#pragma pack(pop)

static struct Struct value[2] = { { -87, 27, 202, 441, 0, 0 }, { -87, 27, 202, 441, 0, 0 } };

void dump_ptr(unsigned char *ptr, int len) {
    for (int i = 0; i < len; i++, ptr++) {
        if (i % 16 == 0) {
            printf("\n%04x:", i);
        } else if (i % 8 == 0) {
            printf("  ");
        }
        printf(" %02x", *ptr);
    }
    printf("\n");
}

int main(int argc, __attribute__((unused)) char *argv[]) {
    dump_ptr((unsigned char *) value, 2 * sizeof(*value));
    dump_ptr((unsigned char *) &value[argc], sizeof(struct Struct));

    printf("%d\n", value[argc].f0);
    return 0;
}

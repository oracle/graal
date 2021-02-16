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
#include <stdio.h>
#include <stdlib.h>

int main() {
    // char
    if (printf("%*c\n", 5, 'a') != 6) {
        abort();
    }
    // int
    if (printf("%*d\n", 20, 1) != 21) {
        abort();
    }
    if (printf("%*d\n", 20, 12312312) != 21) {
        abort();
    }
    if (printf("%*d\n", 3, 2) != 4) {
        abort();
    }
    if (printf("%*d\n", 3, 221) != 4) {
        abort();
    }
    if (printf("%*d\n", 3, -221) != 5) {
        abort();
    }
    if (printf("%*d\n", 3, 5221) != 5) {
        abort();
    }
    if (printf("%*d\n", 0, 5221) != 5) {
        abort();
    }
    // long
    if (printf("%*ld\n", 3, 221L) != 4) {
        abort();
    }
    if (printf("%*ld\n", 3, -221L) != 5) {
        abort();
    }
    if (printf("%*ld\n", 3, 5221L) != 5) {
        abort();
    }
    // string
    if (printf("%*s\n", 5, "a") != 6) {
        abort();
    }
    if (printf("%*s\n", 5, "asdfg") != 6) {
        abort();
    }
    if (printf("%*s\n", 5, "asdfgasdf") != 10) {
        abort();
    }
    // double
    if (printf("%*f\n", 30, 324.324) != 31) {
        abort();
    }
}

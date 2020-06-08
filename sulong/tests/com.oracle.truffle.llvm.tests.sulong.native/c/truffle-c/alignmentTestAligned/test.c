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
#include <stdlib.h>

int main() {
    if (__alignof__(char) != 1) {
        abort();
    }
    if (__alignof__(unsigned char) != 1) {
        abort();
    }
    if (__alignof__(signed char) != 1) {
        abort();
    }
    if (__alignof__(short) != 2) {
        abort();
    }
    if (__alignof__(short int) != 2) {
        abort();
    }
    if (__alignof__(signed short) != 2) {
        abort();
    }
    if (__alignof__(signed short int) != 2) {
        abort();
    }
    if (__alignof__(unsigned short) != 2) {
        abort();
    }
    if (__alignof__(unsigned short int) != 2) {
        abort();
    }
    if (__alignof__(int) != 4) {
        abort();
    }
    if (__alignof__(signed int) != 4) {
        abort();
    }
    if (__alignof__(unsigned) != 4) {
        abort();
    }
    if (__alignof__(unsigned int) != 4) {
        abort();
    }
    if (__alignof__(long) != 8) {
        abort();
    }
    if (__alignof__(float) != 4) {
        abort();
    }
    if (__alignof__(double) != 8) {
        abort();
    }
    if (__alignof__(int *) != 8) {
        abort();
    }
    return 0;
}

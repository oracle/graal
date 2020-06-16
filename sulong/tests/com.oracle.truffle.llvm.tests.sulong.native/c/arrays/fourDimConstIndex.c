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

int array4D[2][2][3][2] = { {
                                { { 0, 1 }, { 2, 3 }, { 3, 4 } },
                                { { 5, 6 }, { 7, 8 }, { 9, 10 } },
                            },
                            { { { 11, 12 }, { 13, 14 }, { 14, 15 } }, { { 16, 17 }, { 18, 19 }, { 20, 21 } } } };

int main() {
    if (array4D[0][0][0][0] != 0) {
        abort();
    }
    if (array4D[0][0][0][1] != 1) {
        abort();
    }
    if (array4D[0][0][1][0] != 2) {
        abort();
    }
    if (array4D[0][0][1][1] != 3) {
        abort();
    }
    if (array4D[0][0][2][0] != 3) {
        abort();
    }
    if (array4D[0][0][2][1] != 4) {
        abort();
    }
    if (array4D[0][1][0][0] != 5) {
        abort();
    }
    if (array4D[0][1][0][1] != 6) {
        abort();
    }
    if (array4D[0][1][1][0] != 7) {
        abort();
    }
    if (array4D[0][1][1][1] != 8) {
        abort();
    }
    if (array4D[0][1][2][0] != 9) {
        abort();
    }
    if (array4D[0][1][2][1] != 10) {
        abort();
    }
    if (array4D[1][0][0][0] != 11) {
        abort();
    }
    if (array4D[1][0][0][1] != 12) {
        abort();
    }
    if (array4D[1][0][1][0] != 13) {
        abort();
    }
    if (array4D[1][0][1][1] != 14) {
        abort();
    }
    if (array4D[1][0][2][0] != 14) {
        abort();
    }
    if (array4D[1][0][2][1] != 15) {
        abort();
    }
    if (array4D[1][1][0][0] != 16) {
        abort();
    }
    if (array4D[1][1][0][1] != 17) {
        abort();
    }
    if (array4D[1][1][1][0] != 18) {
        abort();
    }
    if (array4D[1][1][1][1] != 19) {
        abort();
    }
    if (array4D[1][1][2][0] != 20) {
        abort();
    }
    if (array4D[1][1][2][1] != 21) {
        abort();
    }
}

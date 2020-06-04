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
#define LOOP_COUNT 10000

enum { A, B, C, D, E, F, G, H, I, J, K, L, M, N };

int main() {
    int i;
    int sum = 0;
    for (i = 0; i < LOOP_COUNT; i++) {
        switch (i % (N + 1)) {
            case A:
                sum += 1;
                break;
            case B:
                sum += 4;
                break;
            case C:
                sum += 3;
                break;
            case D:
                sum += 5;
                break;
            case E:
                sum += 3;
                break;
            case F:
                sum += 2;
                break;
            case G:
                sum += 2;
                break;
            case H:
                sum += 2;
                break;
            case I:
                sum += 4;
                break;
            case J:
                sum += 1;
                break;
            case K:
                sum += 2;
                break;
            case L:
                sum += 1;
                break;
            case M:
                sum += 4;
                break;
            case N:
                sum += 2;
                break;
        }
    }
    if (sum != 25717) {
        abort();
    }
    return 0;
}

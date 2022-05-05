/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
#include <stdint.h>

void abort();

int add(int a, int b) {
    return a + b;
}
int sub(int a, int b) {
    return a - b;
}
int mul(int a, int b) {
    return a * b;
}
int div(int a, int b) {
    return a / b;
}
int rem(int a, int b) {
    return a % b;
}

#ifdef _WIN32
int64_t arr[5];
void init_arr() {
    // Windows does not support function pointers as compile time constants.
    arr[0] = (int64_t) &add;
    arr[1] = (int64_t) &sub;
    arr[2] = (int64_t) &mul;
    arr[3] = (int64_t) &div;
    arr[4] = (int64_t) &rem;
}
#else
int64_t arr[5] = { (int64_t) &add, (int64_t) &sub, (int64_t) &mul, (int64_t) &div, (int64_t) &rem };
#endif

int main() {
#ifdef _WIN32
    init_arr();
#endif
    int i;
    int sum = 0;
    for (i = 0; i < 10000; i++) {
        int (*p)(int x, int y) = (int (*)(int x, int y)) arr[i % 5];
        sum += p(i, 2);
    }
    if (sum != 44991000) {
        abort();
    }
}

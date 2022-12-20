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
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

void testCase(double value, unsigned int expected) {
    unsigned int result = (unsigned int) value;
    if (result != expected) {
        printf("%d\n", result);
        abort();
    }
}

int main() {
    testCase(UINT_MAX, -1);
    testCase(UINT_MAX - 0.03, -2);
    testCase(UINT_MAX - 1, -2);
    testCase(UINT_MAX - 2, -3);
    testCase(UINT_MAX - 5, -6);

    testCase(UINT_MAX - 2.5, -4);
    testCase(UINT_MAX - 2.4, -4);
    testCase(UINT_MAX - 2.6, -4);

    testCase(UINT_MAX / 2, 2147483647);
    testCase(UINT_MAX / 2 + 1, -2147483648);
    testCase(UINT_MAX / 2 - 1.0, 2147483646);
    testCase(UINT_MAX / 2 + 1.999999999, -2147483647);

    testCase(0, 0);
    testCase(1.5, 1);
    testCase(INT_MAX, 2147483647);
}

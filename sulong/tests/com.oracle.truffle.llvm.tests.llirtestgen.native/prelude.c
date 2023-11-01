/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates.
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

#include <string.h>
#include <stdio.h>

#define FMT "%016lx %016lx %016lx %016lx: %s\n"

void run(const char *, const char *, char *);

void print_output(void *_output, const char *test) {
    long *output = _output;

    printf(FMT, output[0], output[1], output[2], output[3], test);
    memset(output, 0, 32);
}

void print_input(void *_input) {
    long *input = _input;

    printf("\n===========================\n");
    printf(FMT, input[0], input[1], input[2], input[3], "input/lhs");
    printf(FMT, input[4], input[5], input[6], input[7], "rhs");
    printf("\n");
}

void fill(void *_input, void *_output, long start, long increment) {
    long *input = _input;
    long *output = _output;

    int i;

    for (i = 0; i < (256 * 2 / 64); i++) {
        input[i] = start + (increment * i);
    }

    for (; i < ((256 + 128) * 2 / 64); i++) {
        output[i - (256 * 2 / 64)] = start + (increment * i);
    }
}

int main(void) {
    char _input[256 / 8 * 2] = { 0 };
    char _output[256 / 8] = { 0 };

    void *input = _input;
    void *output = _output;

    print_input(input);
    run(input, input + 32, output);

    // not 0xff...f to avoid NaN, otherwise the float test results are unspecified
    fill(input, output, 0xfefffffffeffffffl, 0);
    print_input(input);
    run(input, input + 32, output);

    fill(input, output, 0xf0f0f0f0f0f0f0f0l, 0);
    print_input(input);
    run(input, input + 32, output);

    fill(input, output, 0x0101010101010101l, 0x9456010101010101l);
    print_input(input);
    run(input, input + 32, output);

    // not 0xff...f to avoid NaN, otherwise the float test results are unspecified
    fill(input, output, 0xfefffffffeffffffl, -0x0101010101010101l);
    print_input(input);
    run(input, input + 32, output);

    fill(input, output, 0, 0x0101010101010101l);
    print_input(input);
    run(input, input + 32, output);

    return 0;
}

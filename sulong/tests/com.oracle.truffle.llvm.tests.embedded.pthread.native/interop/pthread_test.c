/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
#include <pthread.h>
#include <stdio.h>
#include <graalvm/llvm/polyglot.h>

pthread_t threads[3];

pthread_t get_self(int i) {
    pthread_t self = pthread_self();
    threads[i] = self;
    return self;
}

int check_different() {
    int i, j;
    for (i = 0; i < 3; i++) {
        for (j = i + 1; j < 3; j++) {
            if (threads[i] == threads[j]) {
                return 0;
            }
        }
    }
    return 1;
}

__thread void *global;

void *readGlobal() {
    return global;
}

void writeGlobal(void *object) {
    global = object;
}

char buffer[10240];

#if !defined(_WIN32)
// fmemopen is not supported under Windows
FILE *open_buffer() {
    return fmemopen(buffer, sizeof(buffer), "w");
}
#else
FILE *open_buffer() {
    return tmpfile();
}
#endif

void concurrent_put(FILE *f, int id) {
    for (int i = 0; i < 20; i++) {
        fprintf(f, "thread %d %d\n", id, i);
    }
}

void *finalize_buffer(FILE *f) {
    int length = ftell(f);
    fseek(f, 0, SEEK_SET);
    fread(buffer, 1, length, f);
    fclose(f);

    return polyglot_from_string_n(buffer, length, "ASCII");
}

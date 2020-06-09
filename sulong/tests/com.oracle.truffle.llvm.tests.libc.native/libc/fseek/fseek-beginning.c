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

void print(FILE *file) {
    char buf[20];
    while (fgets(buf, 20, file) != NULL) {
        printf("%s\n", buf);
    }
}

int main() {
    char name[L_tmpnam];
    FILE *file = fopen(tmpnam(name), "w");
    if (file == NULL) {
        printf("Failed to open file\n");
        abort();
    }
    fputs("a asd a xdfasdf abn asdfasdf asdfdfaa", file);
    fclose(file);
    FILE *read = fopen(name, "r");
    if (read == NULL) {
        printf("Failed to open file\n");
        abort();
    }
    if (fseek(read, 9, SEEK_SET) != 0) {
        abort();
    }
    print(read);
    if (fseek(read, 0, SEEK_SET) != 0) {
        abort();
    }
    print(read);
    if (fseek(read, 1000, SEEK_SET) != 0) {
        abort();
    }
    print(read);
    fclose(read);
    unlink(name);
}

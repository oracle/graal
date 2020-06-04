/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

//! [toolchain.h usage example]
#include <stdio.h>
#include <polyglot.h>
#include <llvm/api/toolchain.h>

#define BUFFER_SIZE 1024

void print_id() {
    char buffer[BUFFER_SIZE + 1];
    buffer[BUFFER_SIZE] = '\0';

    void *id = toolchain_api_identifier();
    polyglot_as_string(id, buffer, BUFFER_SIZE, "ascii");
    printf("ID=%s\n", buffer);
}

void print_cc() {
    char buffer[BUFFER_SIZE + 1];
    buffer[BUFFER_SIZE] = '\0';

    void *cc = toolchain_api_tool("CC");
    polyglot_as_string(cc, buffer, BUFFER_SIZE, "ascii");
    printf("CC=%s\n", buffer);
}

void print_ld_library_path() {
    char buffer[BUFFER_SIZE + 1];
    buffer[BUFFER_SIZE] = '\0';

    void *paths = toolchain_api_paths("LD_LIBRARY_PATH");
    uint64_t size = polyglot_get_array_size(paths);
    printf("LD_LIBRARY_PATH=");
    for (uint64_t i = 0; i < size; ++i) {
        polyglot_as_string(polyglot_get_array_element(paths, i), buffer, BUFFER_SIZE, "ascii");
        if (i) {
            printf(":");
        }
        printf("%s", buffer);
    }
    printf("\n");
}

int main() {
    print_cc();
    print_ld_library_path();
    print_id();
    return 0;
}
//! [toolchain.h usage example]

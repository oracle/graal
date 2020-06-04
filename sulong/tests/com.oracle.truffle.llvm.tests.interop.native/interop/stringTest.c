/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
#include <polyglot.h>
#include <wchar.h>

uint64_t test_get_string_size(void *str) {
    return polyglot_get_string_size(str);
}

int test_as_string_ascii(void *str) {
    char buffer[100];
    int bytes = polyglot_as_string(str, buffer, sizeof(buffer), "ascii");
    if (strncmp(buffer, "Hello, World!", sizeof(buffer)) == 0) {
        return bytes;
    } else {
        return -1;
    }
}

int test_as_string_utf8(void *str) {
    char buffer[100];
    int bytes = polyglot_as_string(str, buffer, sizeof(buffer), "utf-8");
    if (strncmp(buffer, "test unicode äáç€", sizeof(buffer)) == 0) {
        return bytes;
    } else {
        return -1;
    }
}

int test_as_string_utf32(void *str) {
    wchar_t buffer[100];
    int bytes = polyglot_as_string(str, buffer, sizeof(buffer), "utf-32le");
    if (wcsncmp(buffer, L"test unicode äáç€", sizeof(buffer)) == 0) {
        return bytes;
    } else {
        return -1;
    }
}

int test_as_string_overflow(void *str) {
    char buffer[5];
    int bytes = polyglot_as_string(str, buffer, sizeof(buffer), "ascii");
    if (strncmp(buffer, "Hello", sizeof(buffer)) == 0) {
        return bytes;
    } else {
        return -1;
    }
}

void *test_from_string(int variant) {
    static char ascii[] = "Hello, from Native!\0There is more!";
    static char utf8[] = "unicode from native ☺\0stuff after zero ☹";
    static wchar_t utf32[] = L"utf-32 works too ☺\0also with zero ☹";

    switch (variant) {
        case 1:
            return polyglot_from_string(ascii, "ascii");
        case 2:
            return polyglot_from_string_n(ascii, sizeof(ascii), "ascii");
        case 3:
            return polyglot_from_string(utf8, "utf-8");
        case 4:
            return polyglot_from_string_n(utf8, sizeof(utf8), "utf-8");
        case 5:
            return polyglot_from_string(utf32, "utf-32le");
        case 6:
            return polyglot_from_string_n(utf32, sizeof(utf32), "utf-32le");
    }
    return NULL;
}

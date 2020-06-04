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
#include <polyglot.h>
#include <limits.h>

char *strncpy(char *dest, const char *source, size_t n) {
    int i;
    for (i = 0; source[i] != '\0' && i < n; i++) {
        dest[i] = source[i];
    }

    while (i < n) {
        dest[i] = '\0';
        i++;
    }
    return dest;
}

char *strcpy(char *dest, const char *source) {
    int i = 0;
    do {
        dest[i] = source[i];
    } while (source[i++] != '\0');
    return dest;
}

size_t strlen(const char *s) {
    if (polyglot_has_array_elements(s)) {
        return (size_t) polyglot_get_array_size(s);
    }

    int len = 0;
    while (s[len] != 0) {
        len++;
    }
    return len;
}

int strcmp(const char *s1, const char *s2) {
    bool s1_has_size = polyglot_has_array_elements(s1);
    bool s2_has_size = polyglot_has_array_elements(s2);

    int size1 = s1_has_size ? polyglot_get_array_size(s1) : INT_MAX;
    int size2 = s2_has_size ? polyglot_get_array_size(s2) : INT_MAX;
    int len = size1 > size2 ? size2 : size1;
    for (int i = 0; i < len; i++) {
        char c1 = s1[i];
        char c2 = s2[i];
        if (c1 == 0 || c1 != c2) {
            return (unsigned char) c1 - (unsigned char) c2;
        }
    }

    if (size1 > len) {
        return s1[len];
    } else if (size2 > len) {
        return -s2[len];
    } else {
        return 0;
    }
}

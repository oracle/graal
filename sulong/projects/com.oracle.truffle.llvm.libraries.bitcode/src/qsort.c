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
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

static void sulong_swap(char *buffer, void *vp1, void *vp2, const size_t size) {
    memcpy(buffer, vp1, size);
    memcpy(vp1, vp2, size);
    memcpy(vp2, buffer, size);
}

static void sulong_qsort(char *buffer, char *v, long left, long right, int (*comp)(const void *, const void *), size_t size) {
    int i, last;
    if (left >= right) {
        return;
    }
    sulong_swap(buffer, &v[left * size], &v[((left + right) / 2) * size], size);
    last = left;
    for (i = left + 1; i <= right; i++) {
        if (comp(&(v[i * size]), &(v[left * size])) < 0) {
            last++;
            sulong_swap(buffer, &(v[last * size]), &(v[i * size]), size);
        }
    }
    sulong_swap(buffer, &(v[left * size]), &(v[last * size]), size);
    sulong_qsort(buffer, v, left, last - 1, comp, size);
    sulong_qsort(buffer, v, last + 1, right, comp, size);
}

void qsort(void *v, size_t number, size_t size, int (*comp)(const void *, const void *)) {
    char *buffer = (char *) malloc(size);
    sulong_qsort(buffer, v, 0, number - 1, comp, size);
    free(buffer);
}

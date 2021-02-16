/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
#include <string.h>

typedef struct pointstruct {
    int x;
    int y;
} Point;

Point *createPoint(int ix, int iy) {
    Point *p = (Point *) malloc(sizeof(Point *));
    p->x = ix;
    p->y = iy;
    return p;
}

void printPoint(Point *p) {
    if (p != NULL) {
        printf("Point: x=%i,y=%i\n", p->x, p->y);
    }
}

void swap(void *p1, void *p2, size_t size) {
    char buffer[size];
    memcpy(buffer, p1, size);
    memcpy(p1, p2, size);
    memcpy(p2, buffer, size);
}

__attribute__((constructor)) int main() {
    Point *p = createPoint(2, 3);
    void *q = createPoint(-4, -5);
    __builtin_debugtrap();
    printPoint(p);
    swap(p, q, sizeof(Point *));
    __builtin_debugtrap();
    printPoint(p);
    free(p);
    free((Point *) q);
    return 0;
}

/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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
#include <graalvm/llvm/polyglot.h>
#include <stdlib.h>

struct Point {
    int x;
    int y;
};

POLYGLOT_DECLARE_STRUCT(Point)

void *allocPoint(int x, int y) {
    struct Point *ret = malloc(sizeof(*ret));
    ret->x = x;
    ret->y = y;
    return polyglot_from_Point(ret);
}

void *allocPointUninitialized() {
    void *ret = malloc(sizeof(struct Point));
    return polyglot_from_typed(ret, polyglot_Point_typeid());
}

void freePoint(struct Point *point) {
    free(point);
}

int readPoint(struct Point *point) {
    return point->x * 1000 + point->y;
}

void *allocPointArray(int length) {
    struct Point *ret = calloc(length, sizeof(*ret));
    return polyglot_from_Point_array(ret, length);
}

int readPointArray(struct Point *array, int idx) {
    return readPoint(&array[idx]);
}

struct Nested {
    int64_t primArray[13];
    struct Point pointArray[5];
    struct Point *ptrArray[7];
    struct Point *aliasedPtr;
};

POLYGLOT_DECLARE_STRUCT(Nested)

void *allocNested() {
    struct Nested *ret = calloc(1, sizeof(*ret));
    for (int i = 0; i < 13; i++) {
        ret->primArray[i] = 3 * i + 1;
    }
    for (int i = 0; i < 7; i++) {
        ret->ptrArray[i] = allocPoint(2 * i, 2 * i + 1);
    }
    return polyglot_from_Nested(ret);
}

void freeNested(struct Nested *nested) {
    for (int i = 0; i < 7; i++) {
        freePoint(nested->ptrArray[i]);
    }
    free(nested);
}

int64_t hashNested(struct Nested *nested) {
    int64_t ret = 0;
    for (int i = 0; i < 13; i++) {
        ret = 13 * ret + nested->primArray[i];
    }
    for (int i = 0; i < 5; i++) {
        ret = 13 * ret + nested->pointArray[i].x;
        ret = 13 * ret + nested->pointArray[i].y;
    }
    for (int i = 0; i < 7; i++) {
        ret = 13 * ret + nested->ptrArray[i]->x;
        ret = 13 * ret + nested->ptrArray[i]->y;
    }
    return ret;
}

int getAliasedPtrIndex(struct Nested *nested) {
    return nested->aliasedPtr - nested->pointArray;
}

int findPoint(struct Nested *nested, struct Point *point) {
    for (int i = 0; i < 7; i++) {
        if (nested->ptrArray[i] == point) {
            return i;
        }
    }
    return -1;
}

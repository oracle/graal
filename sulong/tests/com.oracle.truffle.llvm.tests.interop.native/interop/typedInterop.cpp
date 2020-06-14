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
#include <polyglot.h>

struct Point {
    int x;
    int y;
    double (*length)();
    struct Point *(*add)(struct Point *p);
};

POLYGLOT_DECLARE_STRUCT(Point)

extern "C" int distSquared(void *a, void *b) {
    int distX = polyglot_as_Point(b)->x - polyglot_as_Point(a)->x;
    int distY = polyglot_as_Point(b)->y - polyglot_as_Point(a)->y;
    return distX * distX + distY * distY;
}

struct DoublePoint {
    double x;
    double y;
};

extern "C" int distSquaredDesugared(struct DoublePoint a, struct DoublePoint b) {
    int distX = b.x - a.x;
    int distY = b.y - a.y;
    return distX * distX + distY * distY;
}

struct ByValPoint {
    int x;
    long a;
    long b;
    int y;
};

extern "C" int distSquaredByVal(struct ByValPoint a, struct ByValPoint b) {
    int distX = b.x - a.x;
    int distY = b.y - a.y;
    return distX * distX + distY * distY;
}

extern "C" long byValGet(struct ByValPoint a) {
    return a.a + a.b;
}

struct NestedPoint {
    int x;
    struct {
        long a;
        long b;
    } nested;
    int y;
};

extern "C" int distSquaredNestedByVal(struct NestedPoint a, struct NestedPoint b) {
    int distX = b.x - a.x;
    int distY = b.y - a.y;
    return distX * distX + distY * distY;
}

extern "C" long nestedByValGetNested(struct NestedPoint a) {
    return a.nested.a + a.nested.b;
}

struct SmallNested {
    int x;
    struct {
        int y;
    } nested;
};

extern "C" long nestedByValGetSmallNested(struct SmallNested a) {
    return a.x + a.nested.y;
}

struct ArrStruct {
    int a;
    int b;
    int x[2];
};

extern "C" int arrStructSum(struct ArrStruct s) {
    return s.a + s.b + s.x[0] + s.x[1];
}

struct BigArrStruct {
    int a;
    int b;
    int x[5];
};

extern "C" int bigArrStructSum(struct BigArrStruct s) {
    int sum = 0;

    for (int i = 0; i < 5; i++) {
        sum += s.x[i];
    }

    return sum;
}

extern "C" void flipPoint(void *value) {
    struct Point *point = polyglot_as_Point(value);
    int tmp = point->x;
    point->x = point->y;
    point->y = tmp;
}

extern "C" int sumPoints(void *pointArray) {
    int sum;

    struct Point *arr = polyglot_as_Point_array(pointArray);
    int len = polyglot_get_array_size(pointArray);
    for (int i = 0; i < len; i++) {
        sum += arr[i].x + arr[i].y;
    }

    return sum;
}

extern "C" void fillPoints(void *pointArray, int x, int y) {
    struct Point *arr = polyglot_as_Point_array(pointArray);
    int len = polyglot_get_array_size(pointArray);

    for (int i = 0; i < len; i++) {
        arr[i].x = x;
        arr[i].y = y;
    }
}

extern "C" double modifyAndCall(void *value) {
    struct Point *point = polyglot_as_Point(value);
    point->x *= 2;
    point->y *= 2;
    return point->length();
}

extern "C" struct Point *addAndSwapPoint(struct Point *point, int ix, int iy) {
    struct Point incr = { ix, iy };
    struct Point *ret = point->add(&incr);
    int tmp = ret->x;
    ret->x = ret->y;
    ret->y = tmp;
    return ret;
}

struct Nested {
    struct Point arr[5];
    struct Point direct;
    struct Nested *next;
};

POLYGLOT_DECLARE_STRUCT(Nested)

extern "C" void fillNested(void *arg) {
    int value = 42;

    struct Nested *nested = polyglot_as_Nested(arg);
    while (nested) {
        for (int i = 0; i < 5; i++) {
            nested->arr[i].x = value++;
            nested->arr[i].y = value++;
        }
        nested->direct.x = value++;
        nested->direct.y = value++;

        nested = nested->next;
    }
}

struct BitFields {
    int x : 4;
    int y : 3;
    int z;
};

POLYGLOT_DECLARE_STRUCT(BitFields)

extern "C" int accessBitFields(void *arg) {
    struct BitFields *obj = polyglot_as_BitFields(arg);
    return obj->x + obj->y + obj->z;
}

struct FusedArray {
    struct Point origin;
    struct Point path[0];
};

POLYGLOT_DECLARE_STRUCT(FusedArray)

extern "C" void fillFusedArray(void *arg) {
    struct FusedArray *fused = polyglot_as_FusedArray(arg);
    int i;

    fused->origin.x = 3;
    fused->origin.y = 7;

    for (i = 0; i < 7; i++) {
        fused->path[i].x = 2 * i;
        fused->path[i].y = 5 * i;
    }
}

struct Complex {
    double re;
    double im;
};

POLYGLOT_DECLARE_STRUCT(Complex)

extern "C" long readTypeMismatch(struct Complex *c) {
    long *ptr = (long *) c;
    return *ptr;
}

extern "C" void writeTypeMismatch(struct Complex *c, long rawValue) {
    long *ptr = (long *) c;
    *ptr = rawValue;
}

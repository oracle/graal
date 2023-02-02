/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
#include <stdlib.h>
#include <graalvm/llvm/polyglot.h>

struct Point {
    int x;
    int y;
    double (*length)();
    struct Point *(*add)(struct Point *p);
};

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-function"
POLYGLOT_DECLARE_STRUCT(Point)
#pragma clang diagnostic pop

extern "C" int distSquared(polyglot_value a, polyglot_value b) {
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
    int64_t a;
    int64_t b;
    int y;
};

extern "C" int distSquaredByVal(struct ByValPoint a, struct ByValPoint b) {
    int distX = b.x - a.x;
    int distY = b.y - a.y;
    return distX * distX + distY * distY;
}

extern "C" int64_t byValGet(struct ByValPoint a) {
    return a.a + a.b;
}

struct NestedPoint {
    int x;
    struct {
        int64_t a;
        int64_t b;
    } nested;
    int y;
};

extern "C" int distSquaredNestedByVal(struct NestedPoint a, struct NestedPoint b) {
    int distX = b.x - a.x;
    int distY = b.y - a.y;
    return distX * distX + distY * distY;
}

extern "C" int64_t nestedByValGetNested(struct NestedPoint a) {
    return a.nested.a + a.nested.b;
}

struct SmallNested {
    int x;
    struct {
        int y;
    } nested;
};

extern "C" int64_t nestedByValGetSmallNested(struct SmallNested a) {
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

extern "C" void flipPoint(polyglot_value value) {
    struct Point *point = polyglot_as_Point(value);
    int tmp = point->x;
    point->x = point->y;
    point->y = tmp;
}

extern "C" polyglot_typeid getPointType() {
    return polyglot_Point_typeid();
}

extern "C" void flipPointDynamic(polyglot_value value, polyglot_typeid typeId) {
    struct Point *point = (struct Point *) polyglot_as_typed(value, typeId);
    int tmp = point->x;
    point->x = point->y;
    point->y = tmp;
}

extern "C" int sumPoints(polyglot_value pointArray) {
    int sum = 0;

    struct Point *arr = polyglot_as_Point_array(pointArray);
    int len = polyglot_get_array_size(pointArray);
    for (int i = 0; i < len; i++) {
        sum += arr[i].x + arr[i].y;
    }

    return sum;
}

extern "C" void fillPoints(polyglot_value pointArray, int x, int y) {
    struct Point *arr = polyglot_as_Point_array(pointArray);
    int len = polyglot_get_array_size(pointArray);

    for (int i = 0; i < len; i++) {
        arr[i].x = x;
        arr[i].y = y;
    }
}

extern "C" double modifyAndCall(polyglot_value value) {
    struct Point *point = polyglot_as_Point(value);
    point->x *= 2;
    point->y *= 2;
    return point->length();
}

extern "C" struct Point *addAndSwapPoint(struct Point *point, int ix, int iy) {
    struct Point incr = { ix, iy, NULL, NULL };
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

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-function"
POLYGLOT_DECLARE_STRUCT(Nested)
#pragma clang diagnostic pop

extern "C" void fillNested(polyglot_value arg) {
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

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-function"
POLYGLOT_DECLARE_STRUCT(BitFields)
#pragma clang diagnostic pop

extern "C" int accessBitFields(polyglot_value arg) {
    struct BitFields *obj = polyglot_as_BitFields(arg);
    return obj->x + obj->y + obj->z;
}

struct FusedArray {
    struct Point origin;
    struct Point path[0];
};

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-function"
POLYGLOT_DECLARE_STRUCT(FusedArray)
#pragma clang diagnostic pop

extern "C" void fillFusedArray(polyglot_value arg) {
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

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-function"
POLYGLOT_DECLARE_STRUCT(Complex)
#pragma clang diagnostic pop

extern "C" int64_t readTypeMismatch(struct Complex *c) {
    int64_t *ptr = (int64_t *) c;
    return *ptr;
}

extern "C" void writeTypeMismatch(struct Complex *c, int64_t rawValue) {
    int64_t *ptr = (int64_t *) c;
    *ptr = rawValue;
}

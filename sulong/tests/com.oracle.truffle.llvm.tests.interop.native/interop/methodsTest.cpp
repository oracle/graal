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
#include <graalvm/llvm/polyglot.h>
#include <stdlib.h>
#include <math.h>

class Point {
protected:
    int x;
    int y;

public:
    Point();
    int getX();
    int getY();
    void setX(int val);
    void setY(int val);
    double squaredEuclideanDistance(Point *other);
};

POLYGLOT_DECLARE_TYPE(Point)

class XtendPoint : public Point {
private:
    int z;

public:
    XtendPoint();
    int getZ();
    void setZ(int val);
    int getZ(int constant);
    int getX();
};

POLYGLOT_DECLARE_TYPE(XtendPoint)

//class methods

Point::Point() {
    x = 0;
    y = 0;
}

int Point::getX() {
    return x;
}

int Point::getY() {
    return y;
}

void Point::setX(int val) {
    x = val;
}

void Point::setY(int val) {
    y = val;
}

double Point::squaredEuclideanDistance(Point *other) {
    double dX = (double) (x - other->x);
    double dY = (double) (y - other->y);
    return dX * dX + dY * dY;
}

XtendPoint::XtendPoint() {
    z = 0;
}

int XtendPoint::getZ() {
    return z;
}

void XtendPoint::setZ(int dZ) {
    z = dZ;
}

int XtendPoint::getZ(int constantOffset) {
    return z + constantOffset;
}

int XtendPoint::getX() {
    return x * 2;
}

//functions
void *allocNativePoint() {
    Point *ret = (Point *) malloc(sizeof(*ret));
    return polyglot_from_Point(ret);
}

void *allocNativeXtendPoint() {
    XtendPoint *ret = (XtendPoint *) malloc(sizeof(*ret));
    return polyglot_from_XtendPoint(ret);
}

void swap(Point *p, Point *q) {
    Point tmp = *q;
    *q = *p;
    *p = tmp;
}

void freeNativePoint(Point *p) {
    free(p);
}

void freeNativeXtendPoint(XtendPoint *p) {
    free(p);
}

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
#include <stdio.h>

class SimpleClass {

private:
    int a;
    long b;
    double c;

public:
    SimpleClass(int _a, long _b, double _c) {
        this->a = _a;
        this->b = _b;
        this->c = _c;
        printf("SimpleClass Constructor 1\n");
    }

    SimpleClass(int _a, long _b) : a(_a), b(_b) {
        this->c = 305.7;
        printf("SimpleClass Constructor 2\n");
    }

    SimpleClass(int _a) : a(_a) {
        long _b = _a << 2;
        this->b = _b;
        double _c = _b * 5.4;
        this->c = _c * 2.0;
        printf("SimpleClass Constructor 3\n");
    }

    void print() {
        printf("a = %d\nb = %ld\nc = %f\n", this->a, this->b, this->c);
    }
};

class Point {

private:
    int x;
    int y;

public:
    Point(int _x, int _y) : x(_x), y(_y) {
        printf("Point Constructor\n");
    }

    int getX() {
        return this->x;
    }

    void setX(int newX) {
        this->x = newX;
    }

    int getY() {
        return this->y;
    }

    void setY(int newY) {
        this->y = newY;
    }
};

class Shape {

private:
    Point center;

public:
    Shape(Point _center) : center(_center) {
        printf("Shape Constructor\n");
    }

    void moveUp(int offset) {
        int newY = this->center.getY() + offset;
        this->center.setY(newY);
        printf("Shape::moveUp(int)\n");
    }

    void moveLeft(int offset) {
        int newX = this->center.getX() + offset;
        this->center.setX(newX);
        printf("Shape::moveLeft(int)\n");
    }
};

class Circle : public Shape {

private:
    int radius;

public:
    Circle(Point _center, int _radius) : Shape(_center) {
        this->radius = _radius;
        printf("Circle Constructor\n");
    }
};

class Rectangle : public Shape {

private:
    int width;
    int height;

public:
    Rectangle(Point _center, int _width, int _height) : Shape(_center) {
        this->width = _width;
        this->height = _height;
        printf("Rectangle Constructor\n");
    }
};

class Square : public Rectangle {

public:
    Square(Point _center, int length) : Rectangle(_center, length, length) {
        printf("Square Constructor\n");
    };
};

class SimpleSquare : public SimpleClass, public Square {

public:
    SimpleSquare(Point center, int length, int a) : Square(center, length), SimpleClass(a) {
        printf("SimpleSquare Constructor\n");
    }
};

int start() __attribute__((constructor)) {
    SimpleClass a(7, 28L, 302.4);
    a.print();

    SimpleClass b(7, 28L);
    b.print();

    SimpleClass *c = new SimpleClass(8);
    c->print();
    delete c;

    Circle myCircle(Point(1, 1), 3);
    myCircle.moveUp(10);

    Square mySquare(Point(3, 5), 5);
    mySquare.moveLeft(-3);

    SimpleSquare mySimpleSquare(Point(4, 2), 19, 43);
    mySimpleSquare.print();

    return 0;
}

/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.graal.compiler.hsail.test.lambda;

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;

/**
 * Base class for testing virtual method calls.
 */
abstract public class VirtualCallBase extends GraalKernelTester {

    static final int NUM = 20000;

    @Result public float[] outArray = new float[NUM];
    public Shape[] inShapeArray = new Shape[NUM];

    static abstract class Shape {

        abstract public float getArea();
    }

    static class Circle extends Shape {

        private float radius;

        Circle(float r) {
            radius = r;
        }

        @Override
        public float getArea() {
            return (float) (Math.PI * radius * radius);
        }
    }

    static class Square extends Shape {

        private float len;

        Square(float _len) {
            len = _len;
        }

        @Override
        public float getArea() {
            return len * len;
        }
    }

    static class Triangle extends Shape {

        private float base;
        private float height;

        Triangle(float base, float height) {
            this.base = base;
            this.height = height;
        }

        @Override
        public float getArea() {
            return (base * height / 2.0f);
        }
    }

    static class Rectangle extends Shape {

        private float base;
        private float height;

        Rectangle(float base, float height) {
            this.base = base;
            this.height = height;
        }

        @Override
        public float getArea() {
            return (base * height);
        }
    }

    Shape createShape(int kind, int size) {
        switch (kind) {
            case 0:
                return new Circle(size);
            case 1:
                return new Square(size);
            case 2:
                return new Triangle(size, size + 1);
            case 3:
                return new Rectangle(size, size + 1);
            default:
                return null;
        }
    }
}

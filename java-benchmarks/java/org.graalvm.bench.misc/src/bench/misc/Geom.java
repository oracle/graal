/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package bench.misc;

import org.openjdk.jmh.annotations.*;

import java.util.Random;

@State(Scope.Benchmark)
public class Geom {
    @Param("1000")
    static int geomCnt;
    private static Geobject[] samples;

    @Setup
    public static void init() {
        long seed = System.currentTimeMillis();
        samples = generate(0, new String[]{"circle", "rectangle", "square", "triangle"}, geomCnt, seed);
    }

    @Benchmark
    public static double computeArea() {
        return computeArea(samples);
    }

    static Geobject[] generate(int offset, String[] types, int count, long seed) {
        Random r = new Random(seed);
        Geobject[] arr = new Geobject[count];
        for (int i = 0; i < arr.length; i++) {
            String t = types[offset + i % (types.length - offset)];
            Geobject s;
            switch (t) {
                case "circle":
                    s = Geobject.circle(r.nextDouble());
                    break;
                case "rectangle":
                    s = Geobject.rectangle(r.nextDouble(), r.nextDouble());
                    break;
                case "square":
                    s = Geobject.square(r.nextDouble());
                    break;
                case "triangle":
                    s = Geobject.triangle(r.nextDouble(), r.nextDouble());
                    break;
                default:
                    throw new IllegalStateException("" + t);
            }
            arr[i] = s;
        }
        return arr;
    }

    static double computeArea(Geobject[] all) {
        double sum = 0;
        for (Geobject shape : all) {
            sum += shape.area();
        }
        return sum;
    }
}

abstract class Geobject {
    public static Geobject circle(double radius) {
        return new Circle(radius);
    }

    public static Geobject square(double side) {
        return new Square(side);
    }

    public static Geobject rectangle(double a, double b) {
        return new Rectangle(a, b);
    }

    public static Geobject triangle(double base, double height) {
        return new Triangle(base, height);
    }

    public abstract double area();

    static class Circle extends Geobject {
        private final double radius;

        Circle(double radius) {
            this.radius = radius;
        }

        @Override
        public double area() {
            return Math.PI * Math.pow(this.radius, 2);
        }
    }

    static class Square extends Geobject {
        private final double side;

        Square(double side) {
            this.side = side;
        }

        @Override
        public double area() {
            return Math.pow(side, 2);
        }
    }

    static class Rectangle extends Geobject {
        private final double a;
        private final double b;

        Rectangle(double a, double b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public double area() {
            return a * b;
        }
    }

    static class Triangle extends Geobject {

        private final double base;
        private final double height;

        Triangle(double base, double height) {
            this.base = base;
            this.height = height;
        }

        @Override
        public double area() {
            return base * height / 2;
        }
    }
}
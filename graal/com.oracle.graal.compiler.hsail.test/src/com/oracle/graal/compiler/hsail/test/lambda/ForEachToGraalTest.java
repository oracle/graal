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

import java.util.stream.IntStream;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.ArrayList;

/**
 * Several tests for the Sumatra APIs.
 */
public class ForEachToGraalTest {

    // Static and instance fields to test codegen for
    // each type of variable
    static int staticSize = 16;
    final int size = staticSize;

    static int printSize = 4;

    static int staticFactor = 3;
    final int factor = staticFactor;

    class MyPoint {

        int x;
        int y;

        public MyPoint(int _x, int _y) {
            x = _x;
            y = _y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    public MyPoint[] buildMyPointInputArray() {
        MyPoint[] inputs = new MyPoint[size];

        for (int i = 0; i < size; i++) {
            inputs[i] = new MyPoint(i, i + 1);
        }
        return inputs;
    }

    public int[] buildIntInputArray() {
        int[] inputs = new int[size];

        for (int i = 0; i < size; i++) {
            inputs[i] = i * 4;
        }
        return inputs;
    }

    @Test
    public void testForEachIntRangeNoCaptures() {
        int[] dest = new int[size];
        IntStream range = IntStream.range(0, dest.length).parallel();

        // System.out.println("testForEachIntRangeNoCaptures");

        range.forEach(p -> {
            dest[p] = p * factor;
        });

        for (int k = 0; k < dest.length; k++) {
            if (k < printSize) {
                // System.out.println(k + " ... " + dest[k]);
            }
            assertTrue(dest[k] == k * factor);
        }
    }

    @Test
    public void testForEachIntRangeNoCapturesUseStatic() {
        int[] dest = new int[size];
        IntStream range = IntStream.range(0, dest.length).parallel();

        // System.out.println("testForEachIntRangeNoCapturesUseStatic");

        range.forEach(p -> {
            dest[p] = p * staticFactor;
        });

        for (int k = 0; k < dest.length; k++) {
            if (k < printSize) {
                // System.out.println(k + " ... " + dest[k]);
            }
            assertTrue(dest[k] == k * staticFactor);
        }
    }

    @Test
    public void testForEachIntRangeOneCapture() {
        int[] dest = new int[size];
        IntStream range = IntStream.range(0, dest.length).parallel();
        int[] data = buildIntInputArray();

        range.forEach(p -> {
            dest[p] = p * factor + data[p];
        });

        for (int k = 0; k < dest.length; k++) {
            if (k < printSize) {
                // System.out.println(k + " ... " + dest[k]);
            }
            assertTrue(dest[k] == k * 3 + data[k]);
        }

    }

    @Test
    public void testForEachIntRangeOneCaptureUseStatic() {
        int[] dest = new int[size];
        IntStream range = IntStream.range(0, dest.length).parallel();
        int[] data = buildIntInputArray();

        range.forEach(p -> {
            dest[p] = p * staticFactor + data[p];
        });

        for (int k = 0; k < dest.length; k++) {
            // System.out.println( k + " ... " + dest[k] );
            assertTrue(dest[k] == k * staticFactor + data[k]);
        }

    }

    @Test
    public void testForEachObjectStreamNoCaptures() {
        MyPoint[] inputs = buildMyPointInputArray();

        Arrays.stream(inputs).parallel().forEach(p -> {
            // Swap the values
                        int tmp = p.x;
                        p.x = p.y + factor;
                        p.y = tmp;
                    });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs[k];
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.x == (p.y + 1 + factor));
        }
    }

    @Test
    public void testForEachObjectStreamNoCapturesUseStatic() {
        MyPoint[] inputs = buildMyPointInputArray();

        Arrays.stream(inputs).parallel().forEach(p -> {
            // Swap the values
                        int tmp = p.x;
                        p.x = p.y + staticFactor;
                        p.y = tmp;
                    });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs[k];
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.x == (p.y + 1 + staticFactor));
        }
    }

    @Test
    public void testForEachObjectStreamOneCapture() {
        MyPoint[] inputs = buildMyPointInputArray();
        int[] data = buildIntInputArray();

        Arrays.stream(inputs).parallel().forEach(p -> {
            p.y = data[p.x];
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs[k];
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.y == data[p.x]);
        }

    }

    @Test
    public void testForEachObjectStreamOneCaptureUseStatic() {
        MyPoint[] inputs = buildMyPointInputArray();
        int[] data = buildIntInputArray();

        Arrays.stream(inputs).parallel().forEach(p -> {
            p.y = data[p.x] + staticFactor;
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs[k];
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.y == (data[p.x] + +staticFactor));
        }

    }

    @Test
    public void testForEachObjectStreamTwoCaptures() {
        MyPoint[] inputs = buildMyPointInputArray();
        int[] data = buildIntInputArray();
        int[] data2 = buildIntInputArray();

        Arrays.stream(inputs).parallel().forEach(p -> {
            p.y = data[p.x] + data2[p.x];
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs[k];
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.y == data[p.x] + data2[p.x]);
        }

    }

    @Test
    public void testForEachObjectStreamTwoCapturesUseStatic() {
        MyPoint[] inputs = buildMyPointInputArray();
        int[] data = buildIntInputArray();
        int[] data2 = buildIntInputArray();

        Arrays.stream(inputs).parallel().forEach(p -> {
            p.y = data[p.x] + data2[p.x] + staticFactor;
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs[k];
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.y == data[p.x] + data2[p.x] + staticFactor);
        }

    }

    // This test should fall back to the regular Java path if
    // Graal throws NYI
    @Test
    public void testForEachIntRangeNoCapturesUseEscapingNew() {
        MyPoint[] dest = new MyPoint[size];
        IntStream range = IntStream.range(0, dest.length).parallel();

        range.forEach(p -> {
            dest[p] = new MyPoint(p + p, p);
        });

        for (int k = 0; k < dest.length; k++) {
            if (k < printSize) {
                // System.out.println(k + " ... " + dest[k]);
            }
            assertTrue(dest[k].getX() == (k + k));
        }
    }

    // This test should fall back to the regular Java path if
    // Graal throws NYI
    @Test
    public void testForEachIntRangeNoCapturesUseCall() {
        MyPoint[] dest = new MyPoint[size];
        ArrayList<MyPoint> list = new ArrayList<>(size);
        IntStream range = IntStream.range(0, dest.length).parallel();

        for (int i = 0; i < dest.length; i++) {
            list.add(new MyPoint(i + i, i));
        }

        range.forEach(p -> {
            dest[p] = list.get(p);
        });

        for (int k = 0; k < dest.length; k++) {
            if (k < printSize) {
                // System.out.println(k + " ... " + dest[k]);
            }
            assertTrue(dest[k].getX() == (k + k));
        }
    }
    // public static void main(String args[]) {
    // (new ForEachToGraalTest()).testForEachIntRange();
    // }

}

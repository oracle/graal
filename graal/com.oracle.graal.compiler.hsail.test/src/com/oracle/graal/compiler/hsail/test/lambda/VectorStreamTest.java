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

import java.util.Vector;
import java.util.stream.Stream;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Sumatra API tests which use a Stream derived from a Vector.
 */
public class VectorStreamTest {

    // Static and instance fields to test codegen for
    // each type of variable
    static int staticSize = 16;
    final int size = staticSize;

    static int staticFactor = 3;
    final int factor = staticFactor;

    class MyPoint {

        int x;
        int y;

        public MyPoint(int _x, int _y) {
            x = _x;
            y = _y;
        }
    }

    public Vector<MyPoint> buildMyPointInputArray() {
        Vector<MyPoint> inputs = new Vector<>(size);

        for (int i = 0; i < size; i++) {
            inputs.add(new MyPoint(i, i + 1));
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
    public void testForEachObjectStreamNoCaptures() {
        Vector<MyPoint> inputs = buildMyPointInputArray();

        Stream<MyPoint> s = inputs.stream();
        s = s.parallel();
        s.forEach(p -> {
            // Swap the values
            int tmp = p.x;
            p.x = p.y + factor;
            p.y = tmp;
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs.get(k);
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.x == (p.y + 1 + factor));
        }
    }

    @Test
    public void testForEachObjectStreamNoCapturesUseStatic() {
        Vector<MyPoint> inputs = buildMyPointInputArray();

        Stream<MyPoint> s = inputs.stream();
        s = s.parallel();
        s.forEach(p -> {
            // Swap the values
            int tmp = p.x;
            p.x = p.y + staticFactor;
            p.y = tmp;
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs.get(k);
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.x == (p.y + 1 + staticFactor));
        }
    }

    @Test
    public void testForEachObjectStreamOneCapture() {
        int[] data = buildIntInputArray();
        Vector<MyPoint> inputs = buildMyPointInputArray();

        Stream<MyPoint> s = inputs.stream();
        s = s.parallel();
        s.forEach(p -> {
            p.y = data[p.x];
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs.get(k);
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.y == data[p.x]);
        }

    }

    @Test
    public void testForEachObjectStreamOneCaptureUseStatic() {
        int[] data = buildIntInputArray();
        Vector<MyPoint> inputs = buildMyPointInputArray();

        Stream<MyPoint> s = inputs.stream();
        s = s.parallel();
        s.forEach(p -> {
            p.y = data[p.x] + staticFactor;
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs.get(k);
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.y == (data[p.x] + +staticFactor));
        }

    }

    @Test
    public void testForEachObjectStreamTwoCaptures() {
        int[] data = buildIntInputArray();
        int[] data2 = buildIntInputArray();
        Vector<MyPoint> inputs = buildMyPointInputArray();

        Stream<MyPoint> s = inputs.stream();
        s = s.parallel();
        s.forEach(p -> {
            p.y = data[p.x] + data2[p.x];
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs.get(k);
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.y == data[p.x] + data2[p.x]);
        }

    }

    @Test
    public void testForEachObjectStreamTwoCapturesUseStatic() {
        int[] data = buildIntInputArray();
        int[] data2 = buildIntInputArray();
        Vector<MyPoint> inputs = buildMyPointInputArray();

        Stream<MyPoint> s = inputs.stream();
        s = s.parallel();
        s.forEach(p -> {
            p.y = data[p.x] + data2[p.x] + staticFactor;
        });

        for (int k = 0; k < size; k++) {
            MyPoint p = inputs.get(k);
            // System.out.println( k + " ... p.x=" + p.x );
            assertTrue(p.y == data[p.x] + data2[p.x] + staticFactor);
        }

    }
}

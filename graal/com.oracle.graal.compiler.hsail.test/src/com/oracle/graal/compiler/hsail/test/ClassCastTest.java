/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail.test;

import org.junit.*;

/**
 * This test deliberately causes a ClassCastException to test throwing the exception back to the
 * java code.
 */
public class ClassCastTest extends SingleExceptionTestBase {

    static final int num = 20;

    static class BasePoint {
        int x;
        int y;

        public BasePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    static class MyPoint extends BasePoint {
        public MyPoint(int x, int y) {
            super(x, y);
        }
    }

    BasePoint[] inputs = new BasePoint[num];
    MyPoint[] outputs = new MyPoint[num];

    void setupArrays() {
        for (int i = 0; i < num; i++) {
            inputs[i] = new MyPoint(i, i + 1);
        }
        inputs[2] = new BasePoint(1, 1);
    }

    public void run(int gid) {
        // gid 2 should always throw ClassCastException
        MyPoint mp = (MyPoint) inputs[gid];
        mp.x = mp.y + 2;
        outputs[gid] = mp;
    }

    @Override
    public void runTest() {
        setupArrays();
        try {
            dispatchMethodKernel(num);
        } catch (Exception e) {
            recordException(e);
        }
    }

    @Test
    public void test() {
        super.testGeneratedHsail();
    }
}

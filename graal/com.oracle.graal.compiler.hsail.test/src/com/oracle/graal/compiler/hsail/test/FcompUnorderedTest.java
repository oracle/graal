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

package com.oracle.graal.compiler.hsail.test;

import org.junit.*;

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * This was a version of Mandel Test that happened to generate a Nan and so was useful for testing
 * ordered and unordered comparison. However, we should probably replace this with a test (or set of
 * tests) that is more focussed on Nan and comparisons.
 */
public class FcompUnorderedTest extends GraalKernelTester {

    static final int initialWidth = 5;
    static final int initialHeight = initialWidth;
    static final int range = 25;
    @Result private int[] rgb = new int[range];
    private float[] lxresult = new float[range];

    public static void run(int[] rgb, float xoffset, float yoffset, float scale, int gid) {
        final int width = initialWidth;
        final int height = initialHeight;
        final int maxIterations = 64;
        float lx = (((gid % width * scale) - ((scale / 2) * width)) / width) + xoffset;
        float ly = (((gid / width * scale) - ((scale / 2) * height)) / height) + yoffset;
        int count = 0;
        float zx = lx;
        float zy = ly;
        float newzx = 0f;
        /**
         * Iterate until the algorithm converges or until maxIterations are reached.
         */
        while (count < maxIterations && zx < 8) {
            newzx = zx * zx - zy * zy + lx;
            zy = 2 * zx * zy + ly;
            zx = newzx;
            count++;
        }
        rgb[gid] = count;
    }

    void setupPalette(int[] in) {
        for (int i = 0; i < in.length; i++) {
            in[i] = i;
        }
    }

    @Override
    public void runTest() {
        int[] palette = new int[256];
        setupPalette(palette);
        for (int i = 0; i < range; i++) {
            lxresult[i] = -1;
        }
        /*
         * Call it for a range, specifying testmethod args (but not the fields it uses or the gid
         * argument).
         */
        dispatchMethodKernel(range, rgb, 1.0f, 1.0f, 1.0f);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }

}

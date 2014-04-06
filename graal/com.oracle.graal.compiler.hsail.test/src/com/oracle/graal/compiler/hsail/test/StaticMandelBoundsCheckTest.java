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

import static org.junit.Assume.*;

import org.junit.*;

/**
 * Unit test that simulates the Mandelbrot application. The run method here is a static method
 * version of the original mandel kernel and the invoke parameters are for the starting point of the
 * mandel demo. Note: this will likely not pass the junit test on real hardware, but should pass on
 * the simulator.
 */
public class StaticMandelBoundsCheckTest extends SingleExceptionTestBase {

    static final int initWidth = 768;
    static final int initHeight = initWidth;
    static final int maxIterations = 64;
    static final int range = initWidth * initHeight;
    private int[] rgb = new int[range];

    public static void run(int[] rgb, int[] pallette, float xoffset, float yoffset, float scale, int gid) {
        final int width = initWidth;
        final int height = initHeight;
        float lx = (((gid % width * scale) - ((scale / 2) * width)) / width) + xoffset;
        float ly = (((gid / width * scale) - ((scale / 2) * height)) / height) + yoffset;
        int count = 0;
        float zx = lx;
        float zy = ly;
        float newzx = 0f;

        // Iterate until the algorithm converges or until maxIterations are reached.
        while (count < maxIterations && zx * zx + zy * zy < 8) {
            newzx = zx * zx - zy * zy + lx;
            zy = 2 * zx * zy + ly;
            zx = newzx;
            count++;
        }
        rgb[gid + 1] = pallette[count];   // will cause exception on last of range
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
        /**
         * Call it for a range, specifying testmethod args (but not the fields it uses or the gid
         * argument).
         */
        try {
            dispatchMethodKernel(range, rgb, palette, -1f, 0f, 3f);
        } catch (Exception e) {
            recordException(e);
        }
    }

    @Test
    public void test() {
        assumeTrue(runningOnSimulator());
        testGeneratedHsail();
    }
}

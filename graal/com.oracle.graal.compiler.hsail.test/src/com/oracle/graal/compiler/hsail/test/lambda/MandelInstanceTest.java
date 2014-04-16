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
import org.junit.Test;

/**
 * Tests mandel as an instance lambda.
 */

public class MandelInstanceTest extends GraalKernelTester {

    static final int WIDTH = 768;
    static final int HEIGHT = WIDTH;
    static final int maxIterations = 64;

    static final int RANGE = WIDTH * HEIGHT;
    @Result public int[] rgb = new int[RANGE];
    int[] palette = new int[256];

    void setupPalette(int[] in) {
        for (int i = 0; i < in.length; i++) {
            in[i] = i;
        }
    }

    @Override
    public void runTest() {
        setupPalette(palette);

        float x_offset = -1f;
        float y_offset = 0f;
        float scale = 3f;

        // call it for a range, specifying the lambda
        dispatchLambdaKernel(RANGE, (gid) -> {
            final int width = WIDTH;
            final int height = HEIGHT;
            float lx = (((gid % width * scale) - ((scale / 2) * width)) / width) + x_offset;
            float ly = (((gid / width * scale) - ((scale / 2) * height)) / height) + y_offset;

            int count = 0;
            float zx = lx;
            float zy = ly;
            float new_zx = 0f;

            // Iterate until the algorithm converges or until maxIterations are reached.
                        while (count < maxIterations && zx * zx + zy * zy < 8) {
                            new_zx = zx * zx - zy * zy + lx;
                            zy = 2 * zx * zy + ly;
                            zx = new_zx;
                            count++;
                        }

                        rgb[gid] = palette[count];
                    });
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }

    @Test
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }

}

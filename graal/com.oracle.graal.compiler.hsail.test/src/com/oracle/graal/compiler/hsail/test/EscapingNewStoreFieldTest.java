/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;
import org.junit.Test;

/**
 * Tests allocation of a Vec3 object stored in a field by workitem #1.
 */

public class EscapingNewStoreFieldTest extends GraalKernelTester {

    static final int NUM = 20;
    public float[] inArray = new float[NUM];
    @Result public Vec3 outField;

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            inArray[i] = i;
        }
    }

    public void run(int gid) {
        if (gid == 1) {
            float inval = inArray[gid];
            outField = new Vec3(inval + 1, inval + 2, inval + 3);
        }
    }

    @Override
    public void runTest() {
        setupArrays();

        dispatchMethodKernel(NUM);

        // see what happens if we do it again
        dispatchMethodKernel(NUM);
        System.gc();
    }

    @Override
    protected boolean supportsRequiredCapabilities() {
        return canHandleObjectAllocation();
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }

}

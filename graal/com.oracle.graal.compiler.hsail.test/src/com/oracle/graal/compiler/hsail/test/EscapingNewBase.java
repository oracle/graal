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
import java.util.Arrays;

/**
 * Base Class for tests that allocate escaping objects.
 */

public class EscapingNewBase extends GraalKernelTester {

    final int NUM = getRange();

    int getRange() {
        return 24;
    }

    @Result public Object[] outArray = new Object[NUM];
    public Object[] savedOutArray;
    @Result public boolean savedOutArrayMatch1;
    @Result public boolean savedOutArrayMatch2;
    @Result public boolean savedOutArrayMatch3;

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            outArray[i] = null;
        }
    }

    int getDispatches() {
        return 1;
    }

    @Override
    protected boolean supportsRequiredCapabilities() {
        return canHandleObjectAllocation();
    }

    @Override
    public void runTest() {
        setupArrays();

        dispatchMethodKernel(NUM);
        // use System.gc() to ensure new objects are in form that gc likes
        System.gc();
        savedOutArray = Arrays.copyOf(outArray, NUM);
        savedOutArrayMatch1 = Arrays.equals(outArray, savedOutArray);
        if (getDispatches() > 1) {
            // redispatch kernel without gc
            dispatchMethodKernel(NUM);
            savedOutArrayMatch2 = Arrays.equals(outArray, savedOutArray);
            // and one more time with gc
            dispatchMethodKernel(NUM);
            savedOutArrayMatch3 = Arrays.equals(outArray, savedOutArray);
            System.gc();
        }
    }

}

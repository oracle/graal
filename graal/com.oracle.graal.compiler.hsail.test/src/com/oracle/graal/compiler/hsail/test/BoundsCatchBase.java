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

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Base Class for tests that deopt but then catch the exception in the run routine itself.
 */
public abstract class BoundsCatchBase extends GraalKernelTester {

    abstract int getGlobalSize();

    final int num = getGlobalSize();
    @Result int[] outArray = new int[num];

    void setupArrays() {
        for (int i = 0; i < num; i++) {
            outArray[i] = -i;
        }
    }

    // Note: could not push the whole run routine here because
    // problems with indirect call to getDeoptGid
    int getOutval(int gid) {
        int adjustment = 0;
        int tmp = gid + 10;
        while (tmp > gid) {
            adjustment += tmp;
            tmp--;
        }
        int outval = (outArray[gid] * -1) + adjustment;
        return outval;
    }

    @Override
    protected boolean supportsRequiredCapabilities() {
        return getHSAILBackend().getRuntime().getConfig().useHSAILDeoptimization;
    }

    @Override
    public void runTest() {
        setupArrays();

        // we should not get an exception escaping from the kernel
        dispatchMethodKernel(num);
    }

}

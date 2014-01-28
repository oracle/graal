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

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;

/**
 * Tests the addition of elements from sixteen input arrays.
 */
public class StaticMethod16InArraysTest extends StaticMethodTwoIntArrays {

    @Override
    void setupArrays(int[] in) {
        for (int i = 0; i < num; i++) {
            in[i] = i;
            outArray[i] = -i;
        }
    }

    public static void run(int[] out, int[] ina, int[] inb, int[] inc, int[] ind, int[] ine, int[] inf, int[] ing, int[] inh, int[] ini, int[] inj, int[] ink, int[] inl, int[] inm, int[] inn,
                    int[] ino, int[] inp, int gid) {
        out[gid] = ina[gid] + inb[gid] + inc[gid] + ind[gid] + ine[gid] + inf[gid] + ing[gid] + inh[gid] + ini[gid] + inj[gid] + ink[gid] + inl[gid] + inm[gid] + inn[gid] + ino[gid] + inp[gid];
    }

    @Override
    public void runTest() {
        int[] inArray = new int[num];
        setupArrays(inArray);
        /**
         * DumpArrayParameters(inArray); Call it for a range, specifying testmethod args (but not
         * the fields it uses or the gid argument). Will put output in outArray.
         */
        dispatchMethodKernel(num, outArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray, inArray);
    }

    /**
     * This test fails because we don't have correct logic to handle more input parameters than
     * there are registers.
     */
    @Test(expected = java.lang.ClassCastException.class)
    @Ignore("until GPU backends can co-exist")
    public void test() {
        DebugConfig debugConfig = DebugScope.getConfig();
        DebugConfig noInterceptConfig = new DelegatingDebugConfig(debugConfig) {
            @Override
            public RuntimeException interceptException(Throwable e) {
                return null;
            }
        };

        try (DebugConfigScope s = Debug.setConfig(noInterceptConfig)) {
            testGeneratedHsail();
        }
    }

}

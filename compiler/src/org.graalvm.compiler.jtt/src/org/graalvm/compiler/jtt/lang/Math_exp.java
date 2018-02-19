/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.lang;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/*
 */
public class Math_exp extends JTTTest {

    public static double test(double arg) {
        return Math.exp(arg);
    }

    @Test
    public void run0() {
        runTest("test", java.lang.Double.NaN);
    }

    @Test
    public void run1() {
        runTest("test", java.lang.Double.NEGATIVE_INFINITY);
    }

    @Test
    public void run2() {
        runTest("test", java.lang.Double.POSITIVE_INFINITY);
    }

    @Test
    public void run3() {
        runTest("test", -1D);
    }

    @Test
    public void run4() {
        runTest("test", -0.0D);
    }

    @Test
    public void run5() {
        runTest("test", 0.0D);
    }

    @Test
    public void run6() {
        runTest("test", 1.0D);
    }

    @Test
    public void run7() {
        runTest("test", -1024D);
    }

    private static final long STEP = Long.MAX_VALUE / 1_000_000;

    /**
     * Tests a wider range of values.
     */
    @Test
    public void run8() {
        OptionValues options = getInitialOptions();
        ResolvedJavaMethod method = getResolvedJavaMethod("test");
        Object receiver = null;
        long testIteration = 0;
        for (long l = Long.MIN_VALUE;; l += STEP) {
            double d = Double.longBitsToDouble(l);
            Result expect = executeExpected(method, receiver, d);
            try {
                testAgainstExpected(options, method, expect, EMPTY, receiver, d);
                testIteration++;
            } catch (AssertionError e) {
                throw new AssertionError(String.format("%d: While testing %g [long: %d, hex: %x]", testIteration, d, l, l), e);
            }
            if (Long.MAX_VALUE - STEP < l) {
                break;
            }
        }
    }
}

/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.jtt.lang;

import jdk.graal.compiler.options.OptionValues;
import org.junit.Test;

/*
 */
public class Math_exp extends UnaryMath {

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

    @Test
    public void run8() {
        OptionValues options = getInitialOptions();
        testManyValues(options, getResolvedJavaMethod("test"));
    }
}

/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.util.Arrays;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class FloatArraysEqualsTest extends GraalCompilerTest {

    public static boolean testFloatArrayWithPEASnippet() {
        float[] canonicalFloatNaN = new float[1];
        canonicalFloatNaN[0] = Float.intBitsToFloat(0x7fc00000);
        float[] nonCanonicalFloatNaN = new float[1];
        nonCanonicalFloatNaN[0] = Float.intBitsToFloat(0x7f800001);
        return Arrays.equals(canonicalFloatNaN, nonCanonicalFloatNaN);
    }

    @Test
    public void testFloatArrayWithPEA() {
        ResolvedJavaMethod method = getResolvedJavaMethod("testFloatArrayWithPEASnippet");
        test(method, null);
    }

    public static boolean testDoubleArrayWithPEASnippet() {
        double[] canonicalDoubleNaN = new double[1];
        canonicalDoubleNaN[0] = Double.longBitsToDouble(0x7ff8000000000000L);
        double[] nonCanonicalDoubleNaN = new double[1];
        nonCanonicalDoubleNaN[0] = Double.longBitsToDouble(0x7ff0000000000001L);
        return Arrays.equals(canonicalDoubleNaN, nonCanonicalDoubleNaN);
    }

    @Test
    public void testDoubleArrayWithPEA() {
        ResolvedJavaMethod method = getResolvedJavaMethod("testDoubleArrayWithPEASnippet");
        test(method, null);
    }

    public static boolean testFloatArraySnippet(float[] a, float[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testFloatEquality() {
        float[] canonicalFloatNaN = new float[1];
        canonicalFloatNaN[0] = Float.intBitsToFloat(0x7fc00000);
        float[] nonCanonicalFloatNaN = new float[1];
        nonCanonicalFloatNaN[0] = Float.intBitsToFloat(0x7f800001);

        ResolvedJavaMethod method = getResolvedJavaMethod("testFloatArraySnippet");
        test(method, null, canonicalFloatNaN, nonCanonicalFloatNaN);
    }

    public static boolean testDoubleArraySnippet(double[] a, double[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testDoubleEquality() {
        double[] canonicalDoubleNaN = new double[1];
        canonicalDoubleNaN[0] = Double.longBitsToDouble(0x7ff8000000000000L);
        double[] nonCanonicalDoubleNaN = new double[1];
        nonCanonicalDoubleNaN[0] = Double.longBitsToDouble(0x7ff0000000000001L);

        ResolvedJavaMethod method = getResolvedJavaMethod("testDoubleArraySnippet");
        test(method, null, canonicalDoubleNaN, nonCanonicalDoubleNaN);
    }

}

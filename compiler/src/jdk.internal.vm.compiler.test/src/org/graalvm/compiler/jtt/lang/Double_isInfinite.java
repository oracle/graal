/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.lang;

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;

public class Double_isInfinite extends JTTTest {

    public static boolean snippet(double d) {
        return Double.isInfinite(d);
    }

    @Test
    public void runPos0() {
        runTest("snippet", +0.0d);
    }

    @Test
    public void runNeg0() {
        runTest("snippet", -0.0d);
    }

    @Test
    public void run1() {
        runTest("snippet", 1.0d);
    }

    @Test
    public void runPosInf() {
        runTest("snippet", Double.POSITIVE_INFINITY);
    }

    @Test
    public void runNegInf() {
        runTest("snippet", Double.NEGATIVE_INFINITY);
    }

    @Test
    public void runNaN() {
        runTest("snippet", Double.NaN);
    }

    public static void opaqueClassSnippet() {
        /*
         * GR-51558: This would cause an assertion failure in LIR constant load optimization if the
         * opaque is not removed.
         */
        Class<?> c = GraalDirectives.opaque(Object.class);
        if (c.getResource("resource.txt") == null) {
            GraalDirectives.deoptimize();
        }
    }

    @Test
    public void testOpaqueClass() {
        test("opaqueClassSnippet");
    }
}

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

package jdk.graal.compiler.nodes.test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import org.junit.Test;

public class XorNodeCanonicalizationTest extends GraalCompilerTest {

    public static int xorDeMorganSnippet(int x, int y) {
        return ~x ^ ~y;
    }

    public static int xorDeMorganReferenceSnippet(int x, int y) {
        return x ^ y;
    }

    @Test
    public void xorDeMorgan() {
        testAgainstReference("xorDeMorganReferenceSnippet", "xorDeMorganSnippet");
        test("xorDeMorganSnippet", 23, 42);
    }

    public static int xorSelfNegationLeftIntSnippet(int x) {
        return ~x ^ x;
    }

    public static int xorSelfNegationRightIntSnippet(int x) {
        return x ^ ~x;
    }

    public static int xorSelfNegationIntReferenceSnippet(@SuppressWarnings("unused") int x) {
        return -1;
    }

    @Test
    public void xorSelfNegationInt() {
        testAgainstReference("xorSelfNegationIntReferenceSnippet", "xorSelfNegationLeftIntSnippet");
        testAgainstReference("xorSelfNegationIntReferenceSnippet", "xorSelfNegationRightIntSnippet");
    }

    public static long xorSelfNegationLeftLongSnippet(long x) {
        return ~x ^ x;
    }

    public static long xorSelfNegationRightLongSnippet(long x) {
        return x ^ ~x;
    }

    public static long xorSelfNegationLongReferenceSnippet(@SuppressWarnings("unused") long x) {
        return -1L;
    }

    @Test
    public void xorSelfNegationLong() {
        testAgainstReference("xorSelfNegationLongReferenceSnippet", "xorSelfNegationLeftLongSnippet");
        testAgainstReference("xorSelfNegationLongReferenceSnippet", "xorSelfNegationRightLongSnippet");
    }

    private void testAgainstReference(String referenceSnippet, String testSnippet) {
        StructuredGraph referenceGraph = parseForCompile(getResolvedJavaMethod(referenceSnippet));
        StructuredGraph testGraph = parseForCompile(getResolvedJavaMethod(testSnippet));
        createCanonicalizerPhase().apply(testGraph, getDefaultHighTierContext());
        assertEquals(referenceGraph, testGraph, true, false);
    }
}

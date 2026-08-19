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

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;

public class OrNodeCanonicalizationTest extends GraalCompilerTest {

    public static int orDeMorganSnippet(int x, int y) {
        return ~x | ~y;
    }

    public static int orDeMorganReferenceSnippet(int x, int y) {
        return ~(x & y);
    }

    @Test
    public void orDeMorgan() {
        testAgainstReference("orDeMorganReferenceSnippet", "orDeMorganSnippet");
        test("orDeMorganSnippet", 23, 42);
    }

    public static int orSelfNegationLeftIntSnippet(int x) {
        return ~x | x;
    }

    public static int orSelfNegationRightIntSnippet(int x) {
        return x | ~x;
    }

    public static int orSelfNegationIntReferenceSnippet(@SuppressWarnings("unused") int x) {
        return -1;
    }

    @Test
    public void orSelfNegationInt() {
        testAgainstReference("orSelfNegationIntReferenceSnippet", "orSelfNegationLeftIntSnippet");
        testAgainstReference("orSelfNegationIntReferenceSnippet", "orSelfNegationRightIntSnippet");
        test("orSelfNegationLeftIntSnippet", 42);
        test("orSelfNegationRightIntSnippet", 42);
    }

    public static long orSelfNegationLeftLongSnippet(long x) {
        return ~x | x;
    }

    public static long orSelfNegationRightLongSnippet(long x) {
        return x | ~x;
    }

    public static long orSelfNegationLongReferenceSnippet(@SuppressWarnings("unused") long x) {
        return -1L;
    }

    @Test
    public void orSelfNegationLong() {
        testAgainstReference("orSelfNegationLongReferenceSnippet", "orSelfNegationLeftLongSnippet");
        testAgainstReference("orSelfNegationLongReferenceSnippet", "orSelfNegationRightLongSnippet");
        test("orSelfNegationLeftLongSnippet", 42L);
        test("orSelfNegationRightLongSnippet", 42L);
    }

    public static int orRepeatedReferenceSnippet(int x, int y) {
        return y | x;
    }

    public static int orRepeatedXFirstLeftAssocSnippet(int x, int y) {
        // duplicates removed during bytecode parsing
        return ((x | x) | x) | y;
    }

    @Test
    public void orRepeatedXFirstLeftAssoc() {
        testAgainstReference("orRepeatedReferenceSnippet", "orRepeatedXFirstLeftAssocSnippet");
    }

    public static int orRepeatedXFirstRightAssocSnippet(int x, int y) {
        return x | (x | (x | y));
    }

    @Test
    public void orRepeatedXFirstRightAssoc() {
        testAgainstReference("orRepeatedReferenceSnippet", "orRepeatedXFirstRightAssocSnippet");
    }

    public static int orRepeatedYFirstLeftAssocSnippet(int x, int y) {
        // reassociated to right-associative form during bytecode parsing
        return ((y | x) | x) | x;
    }

    @Test
    public void orRepeatedYFirstLeftAssoc() {
        testAgainstReference("orRepeatedReferenceSnippet", "orRepeatedYFirstLeftAssocSnippet");
    }

    public static int orRepeatedYFirstRightAssocSnippet(int x, int y) {
        // duplicates removed during bytecode parsing
        return y | (x | (x | x));
    }

    @Test
    public void orRepeatedYFirstRightAssoc() {
        testAgainstReference("orRepeatedReferenceSnippet", "orRepeatedYFirstRightAssocSnippet");
    }

    public static int orRepeatedMixedLeftAssocSnippet(int x, int y) {
        // reassociated to right-associative form during bytecode parsing
        return ((x | y) | x) | y;
    }

    @Test
    public void orRepeatedMixedLeftAssoc() {
        testAgainstReference("orRepeatedReferenceSnippet", "orRepeatedMixedLeftAssocSnippet");
    }

    public static int orRepeatedMixedRightAssocSnippet(int x, int y) {
        return x | (y | (x | y));
    }

    @Test
    public void orRepeatedMixedRightAssoc() {
        testAgainstReference("orRepeatedReferenceSnippet", "orRepeatedMixedRightAssocSnippet");
    }

    private void testAgainstReference(String referenceSnippet, String testSnippet) {
        StructuredGraph referenceGraph = parseForCompile(getResolvedJavaMethod(referenceSnippet));
        StructuredGraph testGraph = parseForCompile(getResolvedJavaMethod(testSnippet));
        createCanonicalizerPhase().apply(testGraph, getDefaultHighTierContext());
        assertEquals(referenceGraph, testGraph, true, false);
    }
}

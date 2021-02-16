/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.FilteredNodeIterable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.phases.common.ReassociationPhase;
import org.junit.Test;

import java.util.List;
import java.util.Random;

public class ReassociationTest extends GraalCompilerTest {

    private static final Random random = new Random(11);
    private static int rnd1 = random.nextInt();
    private static int rnd2 = random.nextInt();
    private static long rndL1 = random.nextLong();
    private static long rndL2 = random.nextLong();

    // Re-association tests with int type.
    @Test
    public void testAddAdd() {
        testReassociateConstant("testAddAddSnippet", "refAddAddSnippet");
    }

    public static int testAddAddSnippet() {
        return rnd1 + (rnd2 + 2) + 3;
    }

    public static int refAddAddSnippet() {
        return rnd1 + rnd2 + 5;
    }

    @Test
    public void testAddSubX() {
        testReassociateConstant("testAddSubXSnippet", "refAddSubXSnippet");
    }

    public static int testAddSubXSnippet() {
        return rnd1 + (3 - rnd2) + 2;
    }

    public static int refAddSubXSnippet() {
        return (rnd1 - rnd2) + 5;
    }

    @Test
    public void testAddSubY() {
        testReassociateConstant("testAddSubYSnippet", "refAddSubYSnippet");
    }

    public static int testAddSubYSnippet() {
        return rnd1 + (rnd2 - 3) + 2;
    }

    public static int refAddSubYSnippet() {
        return (rnd1 + rnd2) - 1;
    }

    @Test
    public void testSubAddX() {
        testReassociateConstant("testSubAddXSnippet", "refSubAddXSnippet");
    }

    public static int testSubAddXSnippet() {
        return (3 + rnd1) - rnd2 + 1;
    }

    public static int refSubAddXSnippet() {
        return 4 + (rnd1 - rnd2);
    }

    @Test
    public void testSubAddY() {
        testReassociateConstant("testSubAddYSnippet", "refSubAddYSnippet");
    }

    public static int testSubAddYSnippet() {
        return rnd1 - (3 + rnd2) + 1;
    }

    public static int refSubAddYSnippet() {
        return (rnd1 - rnd2) - 2;
    }

    @Test
    public void testSubSubYX() {
        testReassociateConstant("testSubSubYXSnippet", "refSubSubYXSnippet");
    }

    public static int testSubSubYXSnippet() {
        return rnd1 - (3 - rnd2) + 1;
    }

    public static int refSubSubYXSnippet() {
        return (rnd1 + rnd2) - 2;
    }

    @Test
    public void testSubSubYY() {
        testReassociateConstant("testSubSubYYSnippet", "refSubSubYYSnippet");
    }

    public static int testSubSubYYSnippet() {
        return rnd1 - (rnd2 - 3) + 1;
    }

    public static int refSubSubYYSnippet() {
        return (rnd1 - rnd2) + 4;
    }

    @Test
    public void testSubSubXX() {
        testReassociateConstant("testSubSubXXSnippet", "refSubSubXXSnippet");
    }

    public static int testSubSubXXSnippet() {
        return (3 - rnd1) - rnd2 + 1;
    }

    public static int refSubSubXXSnippet() {
        return 4 - (rnd1 + rnd2);
    }

    @Test
    public void testSubSubXY() {
        testReassociateConstant("testSubSubXYSnippet", "refSubSubXYSnippet");
    }

    public static int testSubSubXYSnippet() {
        return (rnd1 - 3) - rnd2 + 1;
    }

    public static int refSubSubXYSnippet() {
        return (rnd1 - rnd2) - 2;
    }

    @Test
    public void testMul() {
        testReassociateConstant("testMulSnippet", "refMulSnippet");
    }

    public static int testMulSnippet() {
        return rnd1 * (-3 * rnd2) * -5;
    }

    public static int refMulSnippet() {
        return (rnd1 * rnd2) * 15;
    }

    @Test
    public void testMulPositive() {
        testReassociateConstant("testMulPositiveSnippet", "refMulPositiveSnippet");
    }

    public static int testMulPositiveSnippet() {
        return rnd1 * (3 * rnd2) * 5;
    }

    public static int refMulPositiveSnippet() {
        return (rnd1 * rnd2) * 15;
    }

    @Test
    public void testMulPositive2() {
        testReassociateConstant("testMulPositive2Snippet", "refMulPositive2Snippet");
    }

    public static int testMulPositive2Snippet() {
        return rnd1 * (3 * rnd2) * 7;
    }

    public static int refMulPositive2Snippet() {
        return (rnd1 * rnd2) * 21;
    }

    @Test
    public void testAnd() {
        testReassociateConstant("testAndSnippet", "refAndSnippet");
    }

    public static int testAndSnippet() {
        return rnd1 & (3 & rnd2) & 2;
    }

    public static int refAndSnippet() {
        return (rnd1 & rnd2) & 2;
    }

    @Test
    public void testOr() {
        testReassociateConstant("testOrSnippet", "refOrSnippet");
    }

    public static int testOrSnippet() {
        return rnd1 | (3 | rnd2) | 4;
    }

    public static int refOrSnippet() {
        return (rnd1 | rnd2) | 7;
    }

    @Test
    public void testXor() {
        testReassociateConstant("testXorSnippet", "refXorSnippet");
    }

    public static int testXorSnippet() {
        return rnd1 ^ (3 ^ rnd2) ^ 1;
    }

    public static int refXorSnippet() {
        return (rnd1 ^ rnd2) ^ 2;
    }

    // Re-association tests with long type.
    @Test
    public void testAddAddLong() {
        testReassociateConstant("testAddAddLongSnippet", "refAddAddLongSnippet");
    }

    public static long testAddAddLongSnippet() {
        return rndL1 + (rndL2 + 2L) + 3L;
    }

    public static long refAddAddLongSnippet() {
        return rndL1 + rndL2 + 5L;
    }

    @Test
    public void testSubAddLong() {
        testReassociateConstant("testSubAddLongSnippet", "refSubAddLongSnippet");
    }

    public static long testSubAddLongSnippet() {
        return (3L + rndL1) - rndL2 + 1L;
    }

    public static long refSubAddLongSnippet() {
        return 4L + (rndL1 - rndL2);
    }

    @Test
    public void testMulLong() {
        testReassociateConstant("testMulLongSnippet", "refMulLongSnippet");
    }

    public static long testMulLongSnippet() {
        return rndL1 * (-3L * rndL2) * -5L;
    }

    public static long refMulLongSnippet() {
        return (rndL1 * rndL2) * 15L;
    }

    @Test
    public void testAndLong() {
        testReassociateConstant("testAndLongSnippet", "refAndLongSnippet");
    }

    public static long testAndLongSnippet() {
        return rndL1 & (3L & rndL2) & 2L;
    }

    public static long refAndLongSnippet() {
        return (rndL1 & rndL2) & 2L;
    }

    @Test
    public void testOrLong() {
        testReassociateConstant("testOrLongSnippet", "refOrLongSnippet");
    }

    public static long testOrLongSnippet() {
        return rndL1 | (3L | rndL2) | 4L;
    }

    public static long refOrLongSnippet() {
        return (rndL1 | rndL2) | 7L;
    }

    @Test
    public void testXorLong() {
        testReassociateConstant("testXorLongSnippet", "refXorLongSnippet");
    }

    public static long testXorLongSnippet() {
        return rndL1 ^ (3L ^ rndL2) ^ 1L;
    }

    public static long refXorLongSnippet() {
        return (rndL1 ^ rndL2) ^ 2L;
    }

    // Re-association overflow tests.
    @Test
    public void testOverflow1() {
        testReassociateConstant("testOverflow1Snippet", "refOverflow1Snippet");
    }

    public static int testOverflow1Snippet() {
        return rnd1 + (rnd2 + Integer.MAX_VALUE) + Integer.MIN_VALUE;
    }

    public static int refOverflow1Snippet() {
        return (rnd1 + rnd2) - 1;
    }

    @Test
    public void testOverflow2() {
        testReassociateConstant("testOverflow2Snippet", "refOverflow2Snippet");
    }

    public static long testOverflow2Snippet() {
        return rndL1 + (rndL2 + Long.MAX_VALUE) - Long.MIN_VALUE;
    }

    public static long refOverflow2Snippet() {
        return (rndL1 + rndL2) + (-1L);
    }

    @Test
    public void testOverflow3() {
        testReassociateConstant("testOverflow3Snippet", "refOverflow3Snippet");
    }

    public static int testOverflow3Snippet() {
        return rnd1 + (rnd2 - Integer.MIN_VALUE) - 5;
    }

    public static int refOverflow3Snippet() {
        return (rnd1 + rnd2) - 0x80000005;
    }

    @Test
    public void testOverflow4() {
        testReassociateConstant("testOverflow4Snippet", "refOverflow4Snippet");
    }

    public static long testOverflow4Snippet() {
        return rndL1 * (Long.MIN_VALUE * rndL2) * (-3L);
    }

    public static long refOverflow4Snippet() {
        return (rndL1 * rndL2) * (Long.MIN_VALUE * (-3L));
    }

    private void testReassociateConstant(String testMethod, String refMethod) {
        test(testMethod);
        StructuredGraph refGraph = parseEager(refMethod, AllowAssumptions.NO);
        createCanonicalizerPhase().apply(refGraph, getProviders());
        new ReassociationPhase(createCanonicalizerPhase()).apply(refGraph, getDefaultMidTierContext());
        createCanonicalizerPhase().apply(refGraph, getProviders());
        StructuredGraph testGraph = parseEager(testMethod, AllowAssumptions.NO);
        createCanonicalizerPhase().apply(testGraph, getProviders());
        new ReassociationPhase(createCanonicalizerPhase()).apply(testGraph, getDefaultMidTierContext());
        createCanonicalizerPhase().apply(testGraph, getProviders());
        assertEquals(refGraph, testGraph);
        // Test whether the final graph is the expected one.
        testFinalGraph(testMethod, refMethod);
    }

    // Test re-association for loop invariant expressions.
    static final int LENGTH = 1024;
    static int[] arr = new int[LENGTH];

    static {
        for (int i = 0; i < LENGTH; i++) {
            arr[i] = i + 3;
        }
    }

    public static int testLoopSnippet1() {
        for (int i = 0; i < LENGTH; i++) {
            arr[i] = i * rnd1 * i * rnd2;
            GraalDirectives.controlFlowAnchor();
        }
        return arr[100];
    }

    public static int refLoopSnippet1() {
        for (int i = 0; i < LENGTH; i++) {
            arr[i] = i * i * (rnd1 * rnd2);
            GraalDirectives.controlFlowAnchor();
        }
        return arr[100];
    }

    @Test
    public void testLoop1() {
        testReassociateInvariant("testLoopSnippet1", "refLoopSnippet1");
    }

    public static int testLoopSnippet2() {
        for (int i = 0; i < LENGTH; i++) {
            arr[i] = (i * 2) * (i * 4);
            GraalDirectives.controlFlowAnchor();
        }
        return arr[100];
    }

    public static int refLoopSnippet2() {
        for (int i = 0; i < LENGTH; i++) {
            arr[i] = i * i * 8;
            GraalDirectives.controlFlowAnchor();
        }
        return arr[100];
    }

    @Test
    public void testLoop2() {
        testReassociateInvariant("testLoopSnippet2", "refLoopSnippet2");
    }

    private void testReassociateInvariant(String testMethod, String refMethod) {
        test(testMethod);
        testFinalGraph(testMethod, refMethod);
    }

    private void testFinalGraph(String testMethod, String refMethod) {
        StructuredGraph expected = getFinalGraph(testMethod);
        StructuredGraph actual = getFinalGraph(refMethod);
        assertEquals(expected, actual);
    }

    // Test no re-associations inside loop.
    public static int testLoopSnippet3() {
        int inv1 = rnd1;
        int inv2 = rnd2;
        for (int i = 0; i < LENGTH; i++) {
            int var = i * i;
            arr[i] = var + (inv1 + inv2 - 3);
            GraalDirectives.controlFlowAnchor();
        }
        return arr[100];
    }

    @Test
    public void testLoop3() {
        testGraphNoChange("testLoopSnippet3");
    }

    public static int testLoopSnippet4() {
        int inv = rnd1 + rnd2;
        for (int i = 0; i < LENGTH; i += (inv + 1)) {
            arr[i] = i;
            GraalDirectives.controlFlowAnchor();
        }
        return arr[rnd2];
    }

    @Test
    public void testLoop4() {
        testGraphNoChange("testLoopSnippet4");
    }

    public static int testLoopSnippet5() {
        int inv = rnd1 + rnd2;
        int i = LENGTH;
        for (; i > 0;) {
            i -= inv + 4;
            GraalDirectives.controlFlowAnchor();
        }
        return i;
    }

    @Test
    public void testLoop5() {
        testGraphNoChange("testLoopSnippet5");
    }

    private void testGraphNoChange(String method) {
        test(method);
        // Assert graph is not changed after canonicalization.
        StructuredGraph graph = parseEager(method, AllowAssumptions.NO);
        FilteredNodeIterable<Node> binarys = graph.getNodes().filter(node -> node instanceof BinaryArithmeticNode);
        List<Node> oldBinarys = binarys.snapshot();
        new ReassociationPhase(createCanonicalizerPhase()).apply(graph, getDefaultMidTierContext());
        boolean changed = false;
        for (Node node : oldBinarys) {
            if (node.isDeleted()) {
                changed = true;
                break;
            }
        }
        assertFalse(changed);
    }
}

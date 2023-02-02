/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Random;

import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;

public class ReassociateAndCanonicalTest extends GraalCompilerTest {

    private static final Random random = new Random(11);
    public static int rnd = random.nextInt();
    private static float rndF = random.nextFloat();
    private static double rndD = random.nextDouble();

    @Test
    public void test1() {
        compareGraphs("test1Snippet", "ref1Snippet");
    }

    public static int test1Snippet() {
        return 1 + (rnd + 2);
    }

    public static int ref1Snippet() {
        return rnd + 3;
    }

    @Test
    public void test2() {
        compareGraphs("test2Snippet", "ref2Snippet");
    }

    public static int test2Snippet() {
        return rnd + 3;
    }

    public static int ref2Snippet() {
        return (rnd + 2) + 1;
    }

    @Test
    public void test3() {
        compareGraphs("test3Snippet", "ref3Snippet");
    }

    public static int test3Snippet() {
        return rnd + 3;
    }

    public static int ref3Snippet() {
        return 1 + (2 + rnd);
    }

    @Test
    public void test4() {
        compareGraphs("test4Snippet", "ref4Snippet");
    }

    public static int test4Snippet() {
        return rnd + 3;
    }

    public static int ref4Snippet() {
        return (2 + rnd) + 1;
    }

    @Test
    public void test5() {
        compareGraphs("test5Snippet", "ref5Snippet");
    }

    public static int test5Snippet() {
        return -1 - rnd;
    }

    public static int ref5Snippet() {
        return 1 - (rnd + 2);
    }

    @Test
    public void test6() {
        compareGraphs("test6Snippet", "ref6Snippet");
    }

    public static int test6Snippet() {
        return rnd + 1;
    }

    public static int ref6Snippet() {
        return (rnd + 2) - 1;
    }

    @Test
    public void test7() {
        compareGraphs("test7Snippet", "ref7Snippet");
    }

    public static int test7Snippet() {
        return -1 - rnd;
    }

    public static int ref7Snippet() {
        return 1 - (2 + rnd);
    }

    @Test
    public void test8() {
        compareGraphs("test8Snippet", "ref8Snippet");
    }

    public static int test8Snippet() {
        return rnd + 1;
    }

    public static int ref8Snippet() {
        return (2 + rnd) - 1;
    }

    @Test
    public void test9() {
        compareGraphs("test9Snippet", "ref9Snippet");
    }

    public static int test9Snippet() {
        return rnd - 1;
    }

    public static int ref9Snippet() {
        return 1 + (rnd - 2);
    }

    @Test
    public void test10() {
        compareGraphs("test10Snippet", "ref10Snippet");
    }

    public static int test10Snippet() {
        return rnd - 1;
    }

    public static int ref10Snippet() {
        return (rnd - 2) + 1;
    }

    @Test
    public void test11() {
        compareGraphs("test11Snippet", "ref11Snippet");
    }

    public static int test11Snippet() {
        return -rnd + 3;
    }

    public static int ref11Snippet() {
        return 1 + (2 - rnd);
    }

    @Test
    public void test12() {
        compareGraphs("test12Snippet", "ref12Snippet");
    }

    public static int test12Snippet() {
        return -rnd + 3;
    }

    public static int ref12Snippet() {
        return (2 - rnd) + 1;
    }

    @Test
    public void test13() {
        compareGraphs("test13Snippet", "ref13Snippet");
    }

    public static int test13Snippet() {
        return -rnd + 3;
    }

    public static int ref13Snippet() {
        return 1 - (rnd - 2);
    }

    @Test
    public void test14() {
        compareGraphs("test14Snippet", "ref14Snippet");
    }

    public static int test14Snippet() {
        return rnd - 3;
    }

    public static int ref14Snippet() {
        return (rnd - 2) - 1;
    }

    @Test
    public void test15() {
        compareGraphs("test15Snippet", "ref15Snippet");
    }

    public static int test15Snippet() {
        return rnd + -1;
    }

    public static int ref15Snippet() {
        return 1 - (2 - rnd);
    }

    @Test
    public void test16() {
        compareGraphs("test16Snippet", "ref16Snippet");
    }

    public static int test16Snippet() {
        return -rnd + 1;
    }

    public static int ref16Snippet() {
        return (2 - rnd) - 1;
    }

    @Test
    public void testMathMax() {
        if (getTarget().arch instanceof AArch64 || (getTarget().arch instanceof AMD64 && ((AMD64) getTarget().arch).getFeatures().contains(AMD64.CPUFeature.AVX))) {
            // Test only on AArch64 and AMD64/AVX as they use MinNode/MaxNode to intrinsify
            // Math.min/max with float/double types that can be re-associated.
            compareGraphs("testMathMaxFloatSnippet", "refMathMaxFloatSnippet");
            compareGraphs("testMathMinFloatSnippet", "refMathMinFloatSnippet");
            compareGraphs("testMathMaxDoubleSnippet", "refMathMaxDoubleSnippet");
            compareGraphs("testMathMinDoubleSnippet", "refMathMinDoubleSnippet");
        }
    }

    public static float testMathMaxFloatSnippet() {
        return Math.max(Math.max(3.0f, rndF), 2.0f);
    }

    public static float refMathMaxFloatSnippet() {
        return Math.max(rndF, 3.0f);
    }

    public static float testMathMinFloatSnippet() {
        return Math.min(Math.min(3.0f, rndF), 2.0f);
    }

    public static float refMathMinFloatSnippet() {
        return Math.min(rndF, 2.0f);
    }

    public static double testMathMaxDoubleSnippet() {
        return Math.max(Math.max(3.0d, rndD), 2.0d);
    }

    public static double refMathMaxDoubleSnippet() {
        return Math.max(rndD, 3.0d);
    }

    public static double testMathMinDoubleSnippet() {
        return Math.min(Math.min(3.0d, rndD), 2.0d);
    }

    public static double refMathMinDoubleSnippet() {
        return Math.min(rndD, 2.0d);
    }

    private <T extends Node & IterableNodeType> void compareGraphs(String testMethodName, String refMethodName) {
        test(testMethodName);
        StructuredGraph testGraph = parseEager(testMethodName, AllowAssumptions.NO);
        createCanonicalizerPhase().apply(testGraph, getProviders());
        StructuredGraph refGraph = parseEager(refMethodName, AllowAssumptions.NO);
        createCanonicalizerPhase().apply(refGraph, getProviders());
        assertEquals(testGraph, refGraph);
    }
}

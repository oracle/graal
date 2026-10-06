/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.loop.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static jdk.graal.compiler.api.directives.GraalDirectives.injectIterationCount;

import org.junit.Test;

import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;

public class AggressivePartialUnrollTest extends GraalCompilerTest {

    OptionValues getOptions() {
        return new OptionValues(getInitialOptions(), AggressivePartialUnrollPhase.Options.AggressivePartialUnroll, true, GraalOptions.OptDuplication, false,
                        GraalOptions.SpeculativeGuardMovement, true, AggressivePartialUnrollPhase.Options.MultiExitCostFactorSink, 0,
                        AggressivePartialUnrollPhase.Options.MultiExitCostFactor, 0, DefaultLoopPolicies.Options.InvertVectorizableLoops, true);
    }

    OptionValues getOptionsPreMainPostOnly() {
        return new OptionValues(getInitialOptions(), AggressivePartialUnrollPhase.Options.AggressivePartialUnroll, true, AggressivePartialUnrollPhase.Options.InsertPreMainPostOnly, true,
                        GraalOptions.OptDuplication, false, GraalOptions.SpeculativeGuardMovement, true, AggressivePartialUnrollPhase.Options.MultiExitCostFactorSink, 0,
                        AggressivePartialUnrollPhase.Options.MultiExitCostFactor, 0, DefaultLoopPolicies.Options.InvertVectorizableLoops, true);
    }

    boolean checkMidTier = true;
    boolean verifyMainLoopUnrolledMidTier = true;
    boolean verifyOnlySimpleLoopsMidTier = false;

    boolean checkHighTier = false;
    boolean verifyMainLoopUnrolledHighTier = true;
    boolean verifyOnlySimpleLoopsHighTier = false;

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        if (!checkMidTier) {
            return;
        }
        NodeIterable<LoopBeginNode> loops = graph.getNodes().filter(LoopBeginNode.class);
        for (LoopBeginNode loop : loops) {
            if (loop.isMainLoop()) {
                if (!verifyMainLoopUnrolledMidTier) {
                    return;
                }
                int unrollFactor = loop.getUnrollFactor();
                if (AggressivePartialUnrollPhase.Options.InsertPreMainPostOnly.getValue(graph.getOptions()) || unrollFactor > 1) {
                    return;
                }
                fail("Loop %s should be unrolled after pre/main/post creation", loop);
            }
            if (verifyOnlySimpleLoopsMidTier && !loop.isSimpleLoop()) {
                fail("expected only simple loops (no pre/main/post loops), got %s", loop);
            }
        }
        if (!verifyOnlySimpleLoopsMidTier) {
            fail("expected a main loop");
        }
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        if (!checkHighTier) {
            return;
        }
        NodeIterable<LoopBeginNode> loops = graph.getNodes().filter(LoopBeginNode.class);
        for (LoopBeginNode loop : loops) {
            if (loop.isMainLoop()) {
                if (!verifyMainLoopUnrolledHighTier) {
                    return;
                }
                int unrollFactor = loop.getUnrollFactor();
                if (AggressivePartialUnrollPhase.Options.InsertPreMainPostOnly.getValue(graph.getOptions()) || unrollFactor > 1) {
                    return;
                }
                fail("Loop %s should be unrolled after pre/main/post creation", loop);
            }
            if (verifyOnlySimpleLoopsHighTier && !loop.isSimpleLoop()) {
                fail("expected only simple loops (no pre/main/post loops), got %s", loop);
            }
        }
        if (!verifyOnlySimpleLoopsHighTier) {
            fail("expected a main loop");
        }
        super.checkHighTierGraph(graph);
    }

    public static final int SIZE = 1024;

    public static class Context {
        public final byte[] input = new byte[SIZE];
        public boolean result = false;
    }

    private void testVariants(String name, Object... args) {
        // Test pre/main/post no unroll and regular unroll
        test(getOptionsPreMainPostOnly(), name, args);
        test(getOptions(), name, args);
    }

    public void singleByte(Context context) {
        for (int i = 0; injectIterationCount(1000, i < SIZE); i++) {
            context.result &= context.input[i] < 0;
        }
    }

    @Test
    public void testMicroBench() {
        for (int i = 0; injectIterationCount(1000, i < 1000); i++) {
            singleByte(new Context());
        }
        testVariants("singleByte", new Context());
    }

    public void singleByteZero(Context context) {
        for (int i = 0; injectIterationCount(1024, i < SIZE); i++) {
            context.result &= context.input[i] < 0;
        }
    }

    @Test
    public void testMicroBenchZeroProbability() {
        for (int i = 0; i < 1000; i++) {
            singleByteZero(new Context());
        }
        testVariants("singleByteZero", new Context());
    }

    public static Object SideEffectO;

    public static int escapingPhiTest(int iterations) {
        Integer phi = iterations;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            Integer x = GraalDirectives.sideEffect(i);
            SideEffectO = x;
            phi += x;
        }
        GraalDirectives.blackhole(phi);
        return phi;
    }

    @Test
    public void testEAOpportunity() {
        testVariants("escapingPhiTest", 12);
    }

    @Test
    public void testSimpleCF() {
        testVariants("testSimpleSnippetCF", 12);
    }

    public static int[] Array = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};

    public static int testSimpleSnippetCF(int iterations) {
        int a = 0;
        int b = 0;
        int c = 0;
        int phi = 0;

        Integer integer = 0;
        int phiToggle = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            phi += i;
            if (i < Array.length - 1) {
                int a1 = Array[i];
                int a2 = Array[i + 1];
                SideEffect += a1 + a2;
            }
            int t2 = a + b;
            c = b;
            b = a;
            a = t2 + t2;
            if (phiToggle == 1) {
                SideEffect = a;
                phiToggle = 0;
            } else {
                phiToggle = 1;
            }
            SideEffect = c;
        }
        return phi * c * integer;
    }

    public static int SideEffect;

    @Test
    public void testLoopCarried() {
        testVariants("testLoopCarriedSnippet", 128);
    }

    static volatile int volatileInt = 3;

    public static int testLoopCarriedSnippet(int iterations) {
        int a = 0;
        int b = 0;
        int c = 0;

        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            int t1 = volatileInt;
            int t2 = a + b;
            if (GraalDirectives.sideEffect(iterations) == 32) {
                GraalDirectives.sideEffect();
            } else {
                GraalDirectives.sideEffect();
            }
            GraalDirectives.sideEffect();
            c = b;
            b = a;
            a = t1 + t2;
        }

        return c;
    }

    public static int testLoopCarriedReference(int iterations) {
        int a = 0;
        int b = 0;
        int c = 0;

        for (int i = 0; injectIterationCount(1000, i < iterations); i += 2) {
            int t1 = volatileInt;
            int t2 = a + b;
            c = b;
            b = a;
            if (GraalDirectives.sideEffect(iterations) == 32) {
                GraalDirectives.sideEffect();
            } else {
                GraalDirectives.sideEffect();
            }
            GraalDirectives.sideEffect();
            a = t1 + t2;
            t1 = volatileInt;
            t2 = a + b;
            c = b;
            b = a;
            a = t1 + t2;
        }

        return c;
    }

    @Test
    public void testUnsignedLoopCarried() {
        testVariants("testUnsignedLoopCarriedSnippet", 10, 100);
    }

    public static int testUnsignedLoopCarriedSnippet(int start, int end) {
        int a = 0;
        int b = 0;
        int c = 0;

        for (int i = start; injectIterationCount(1000, Integer.compareUnsigned(i, end) < 0); i++) {
            int t1 = volatileInt;
            int t2 = a + b;
            c = b;
            if (GraalDirectives.sideEffect(start) == 32) {
                GraalDirectives.sideEffect();
            } else {
                GraalDirectives.sideEffect();
            }
            GraalDirectives.sideEffect();
            b = a;
            a = t1 + t2;
        }

        return c;
    }

    @Test
    public void testLoopCarried2() {
        testVariants("testLoopCarried2Snippet", 32, 128);
    }

    public static int testLoopCarried2Snippet(int start, int end) {
        int a = 0;
        int b = 0;
        int c = 0;

        for (int i = start; injectIterationCount(1000, i < end); i++) {
            int t1 = volatileInt;
            int t2 = a + b;
            c = b;
            b = a;
            a = t1 + t2;
        }

        return c;
    }

    public static long init = Runtime.getRuntime().totalMemory();
    private int x;
    private int z;

    public int[] testComplexSnippet(int d) {
        x = 3;
        int y = 5;
        z = 7;
        for (int i = 0; i < d; i++) {
            for (int j = 0; injectIterationCount(1000, j < i); j++) {
                GraalDirectives.neverWriteSink();
                z += x;
            }
            y = x ^ z;
            if ((i & 4) == 0) {
                z--;
            } else if ((i & 8) == 0) {
                Runtime.getRuntime().totalMemory();
            }
        }
        return new int[]{x, y, z};
    }

    @Test
    public void testComplex() {
        testVariants("testComplexSnippet", 128);
    }

    public static long testSignExtensionSnippet(long arg) {
        long r = 1;
        for (int i = 0; injectIterationCount(1000, i < arg); i++) {
            r *= i;
            if (GraalDirectives.sideEffect(i) == 32) {
                GraalDirectives.sideEffect((int) arg);
            } else {
                GraalDirectives.sideEffect(i);
            }
            GraalDirectives.sideEffect();
        }
        return r;
    }

    @Test
    public void testSignExtension() {
        testVariants("testSignExtensionSnippet", 9L);
    }

    public static Object objectPhi(int n) {
        Integer v = Integer.valueOf(200);
        GraalDirectives.blackhole(v); // Prevents PEA
        Integer r = 1;

        for (int i = 0; injectIterationCount(100, i < n); i++) {
            GraalDirectives.blackhole(r); // Create a phi of two loop invariants
            r = v;
        }

        return r;
    }

    @Test
    public void testObjectPhi() {
        testVariants("objectPhi", 1);
    }

    public static int testEarlyContinueSnippet(int iterations) {
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            int x = GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.1, x == 42)) {
                GraalDirectives.sideEffect(2);
                continue;
            }
            GraalDirectives.sideEffect(i);
        }
        return 1;
    }

    @Test
    public void testEarlyContinue() {
        testVariants("testEarlyContinueSnippet", 1000);
    }

    public static int testEarlyMultiContinueSnippet(int iterations) {
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            int x = GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.001, x == 42)) {
                GraalDirectives.sideEffect(2);
                continue;
            }
            if (injectBranchProbability(0.001, x == 43)) {
                GraalDirectives.sideEffect(3);
                continue;
            }
            if (injectBranchProbability(0.001, x == 44)) {
                GraalDirectives.sideEffect(4);
                continue;
            }
            GraalDirectives.sideEffect(i);
        }
        return 1;
    }

    @Test
    public void testEarlyMultiContinue() {
        testVariants("testEarlyMultiContinueSnippet", 1000);
    }

    public static int testEarlyContinueComplexSnippet(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            int x = GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.1, x == 40)) {
                GraalDirectives.sideEffect(2);
                result += 120;
                continue;
            }
            result++;
            GraalDirectives.sideEffect(i);
        }
        GraalDirectives.sideEffect(result);
        return result;
    }

    @Test
    public void testEarlyContinueComplexData() {
        testVariants("testEarlyContinueComplexSnippet", 1000);
        testVariants("testEarlyContinueComplexSnippet", 10);
        testVariants("testEarlyContinueComplexSnippet", 100);
    }

    public static int testEarlyContinueMultiContinueComplexSnippet(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            int x = GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.001, x == 18)) {
                GraalDirectives.sideEffect(3);
                result += 11;
                continue;
            }
            if (injectBranchProbability(0.001, x == 40)) {
                GraalDirectives.sideEffect(2);
                result += 120;
                continue;
            }
            result++;
            GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.001, x == 9)) {
                GraalDirectives.sideEffect(2);
                result += 27;
                continue;
            }
        }
        GraalDirectives.sideEffect(result);
        return result;
    }

    @Test
    public void testEarlyContinueMultiContinueComplex() {
        OptionValues options = new OptionValues(getOptionsPreMainPostOnly(), GraalOptions.LoopPeeling, false, AggressivePartialUnrollPhase.Options.PartialUnrollCostReductionFactorMidTier, 8,
                        AggressivePartialUnrollPhase.Options.PartialUnrollCostReductionFactorHighTier, 4);
        test(options, "testEarlyContinueMultiContinueComplexSnippet", 1000);
        test(options, "testEarlyContinueMultiContinueComplexSnippet", 10);
        test(options, "testEarlyContinueMultiContinueComplexSnippet", 100);

        OptionValues options1 = new OptionValues(getOptions(), GraalOptions.LoopPeeling, false, AggressivePartialUnrollPhase.Options.PartialUnrollCostReductionFactorMidTier, 8,
                        AggressivePartialUnrollPhase.Options.PartialUnrollCostReductionFactorHighTier, 4);
        test(options1, "testEarlyContinueMultiContinueComplexSnippet", 1000);
        test(options1, "testEarlyContinueMultiContinueComplexSnippet", 10);
        test(options1, "testEarlyContinueMultiContinueComplexSnippet", 100);
    }

    public static int testEarlyExitReturnSnippet(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            GraalDirectives.sideEffect(1);
            if (injectBranchProbability(0.01, GraalDirectives.sideEffect(i) == 42)) {
                return 1;
            }
            GraalDirectives.sideEffect(i);
            result += i;
        }
        return result;
    }

    @Test
    public void testEarlyExitReturn() {
        testVariants("testEarlyExitReturnSnippet", 1000);
    }

    public static int testEarlyExitReturnComplexDataSnippet(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            GraalDirectives.sideEffect(result);
            if (injectBranchProbability(0.01, GraalDirectives.sideEffect(i) == 42)) {
                return result * 23;
            }
            GraalDirectives.sideEffect(i);
        }
        result = result * iterations;
        return result;
    }

    @Test
    public void testEarlyExitReturnComplexData() {
        testVariants("testEarlyExitReturnComplexDataSnippet", 1000);
    }

    public static int testEarlyExitMultiReturnSnippet(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            GraalDirectives.sideEffect(1);
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 42)) {
                return 1;
            }
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 43)) {
                return 2;
            }
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 44)) {
                return 3;
            }
            GraalDirectives.sideEffect(i);
        }
        return result;
    }

    @Test
    public void testEarlyExitMultiReturn() {
        testVariants("testEarlyExitMultiReturnSnippet", 1000);
    }

    public static int testEarlyExitMultiReturnComplexDataSnippet(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            GraalDirectives.sideEffect(1);
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 128)) {
                result++;
                return result;
            }
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 100)) {
                return 2;
            }
            result++;
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 44)) {
                return result;
            }
            GraalDirectives.sideEffect(result);
        }
        return result;
    }

    @Test
    public void testEarlyExitMultiComplexDataReturn01() {
        testVariants("testEarlyExitMultiReturnComplexDataSnippet", 1000);
        testVariants("testEarlyExitMultiReturnComplexDataSnippet", 10);
        testVariants("testEarlyExitMultiReturnComplexDataSnippet", 100);
    }

    public static int testEarlyExitMultiReturnComplexDataSnippet02(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            GraalDirectives.sideEffect(1);
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 128)) {
                result++;
                return result;
            }
            result--;
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 44)) {
                return result;
            }
            if (injectBranchProbability(0.001, GraalDirectives.sideEffect(i) == 100)) {
                return 2;
            }
            result++;
            GraalDirectives.sideEffect(result);
        }
        return result;
    }

    @Test
    public void testEarlyExitMultiComplexDataReturn02() {
        testVariants("testEarlyExitMultiReturnComplexDataSnippet02", 1000);
        testVariants("testEarlyExitMultiReturnComplexDataSnippet02", 10);
        testVariants("testEarlyExitMultiReturnComplexDataSnippet02", 100);
    }

    public static int testEarlyExitBreakSnippet(int iterations) {
        int phi = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            phi += i;
            int x = GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.01, x == 42)) {
                GraalDirectives.sideEffect(2);
                break;
            }
            GraalDirectives.sideEffect(i);
        }
        return 1 + GraalDirectives.sideEffect(iterations) + phi;
    }

    @Test
    public void testEarlyExitBreak() {
        testVariants("testEarlyExitBreakSnippet", 1000);
    }

    public static int testEarlyExitMultiBreakSnippet(int iterations) {
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            int x = GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.001, x == 42)) {
                GraalDirectives.sideEffect(2);
                break;
            }
            if (injectBranchProbability(0.001, x == 43)) {
                GraalDirectives.sideEffect(3);
                break;
            }
            if (injectBranchProbability(0.001, x == 44)) {
                GraalDirectives.sideEffect(4);
                break;
            }
            GraalDirectives.sideEffect(i);
        }
        return 1;
    }

    @Test
    public void testEarlyExitMultiBreak() {
        testVariants("testEarlyExitMultiBreakSnippet", 1000);
    }

    public static int testEarlyExitBreakComplexSnippet(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            int x = GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.01, x == 40)) {
                GraalDirectives.sideEffect(2);
                result += 120;
                break;
            }
            result++;
            GraalDirectives.sideEffect(i);
        }
        GraalDirectives.sideEffect(result);
        return result;
    }

    @Test
    public void testEarlyExitBreakComplexData() {
        testVariants("testEarlyExitBreakComplexSnippet", 1000);
        testVariants("testEarlyExitBreakComplexSnippet", 10);
        testVariants("testEarlyExitBreakComplexSnippet", 100);
    }

    public static int testEarlyExitMultiBreakComplexSnippet(int iterations) {
        int result = 0;
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            int x = GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.001, x == 18)) {
                GraalDirectives.sideEffect(3);
                result += 11;
                break;
            }
            if (injectBranchProbability(0.001, x == 40)) {
                GraalDirectives.sideEffect(2);
                result += 120;
                break;
            }
            result++;
            GraalDirectives.sideEffect(i);
            if (injectBranchProbability(0.001, x == 9)) {
                GraalDirectives.sideEffect(2);
                result += 27;
                break;
            }
        }
        GraalDirectives.sideEffect(result);
        return result;
    }

    @Test
    public void testEarlyExitMultiBreakComplexData() {
        testVariants("testEarlyExitMultiBreakComplexSnippet", 1000);
        testVariants("testEarlyExitMultiBreakComplexSnippet", 10);
        testVariants("testEarlyExitMultiBreakComplexSnippet", 100);
    }

    public static int lastIndexOfSnippet(char[] value, char ch) {
        for (int i = value.length - 1; injectIterationCount(1000, i >= 0); i--) {
            if (injectBranchProbability(0.01, value[i] == ch)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void testLastIndexOf() {
        test(getOptionsPreMainPostOnly(), "lastIndexOfSnippet", new char[]{'a', 'b', 'c', 'd', 'c'}, 'c');
        test(getOptions(), "lastIndexOfSnippet", new char[]{'a', 'b', 'c', 'd', 'c'}, 'c');
    }

    public static int lastIndexOfSnippet02(char[] val, int ch, int fromIndex) {
        if (ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            final char[] value = val;
            int i = Math.min(fromIndex, value.length - 1);
            for (; injectIterationCount(1000, i >= 0); i--) {
                if (injectBranchProbability(0.01, value[i] == ch)) {
                    return i;
                }
            }

            return -1;
        } else {
            return 0;
        }
    }

    @Test
    public void testLastIndexOf02() {
        test(getOptionsPreMainPostOnly(), "lastIndexOfSnippet02", new char[]{'a', 'b', 'c', 'd', 'c'}, (int) 'c', 1);
        test(getOptions(), "lastIndexOfSnippet02", new char[]{'a', 'b', 'c', 'd', 'c'}, (int) 'c', 1);
    }

    public static int nestedLoops(int iterations1, int iterations2) {
        int result = 0;
        outer: for (int i = 0; i < iterations1; i++) {
            int x = GraalDirectives.sideEffect(i);
            for (int j = 0; injectIterationCount(1000, j < iterations2); j++) {
                x += GraalDirectives.sideEffect(j);
                if (injectBranchProbability(0.01, x == 40)) {
                    GraalDirectives.sideEffect(2);
                    result += 120;
                    continue outer;
                }
            }
            result++;
            GraalDirectives.sideEffect(i);
        }
        GraalDirectives.sideEffect(result);
        return result;
    }

    @Test
    public void testInnerOuter01() {
        testVariants("nestedLoops", 10, 10);
    }

    static int A;

    public static int snippetEarlyExitReturnHighMidTier(int iterations) {
        int result = 1;
        for (int i = 1; injectIterationCount(98, i <= iterations); i++) {
            GraalDirectives.neverWriteSink();
            A = i * result;
        }

        // this loop will be pre-main-post inserted in the high tier, but not unrolled because of
        // the options supplied
        for (int i = 0; injectIterationCount(1000, i < iterations); i++) {
            if (i == GraalDirectives.sideEffect(i)) {
                return i;
            }
            if (i == GraalDirectives.sideEffect(i + 1)) {
                return i + 1;
            }
            result += i;
        }
        return result;
    }

    /**
     * Test that verifies that high tier and mid tier unrolling collaborate with respect to early
     * loop exits with one another. If we unroll a loop in high tier that has early loop exits then
     * mid tier unrolling must not unroll it again since high tier unrolling or other phases may
     * create early exits without a merge.
     */
    @Test
    public void testHighTierUnrollMidTierUnrollMultiExit() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false,
                        /* frequency is not important for this test */AggressivePartialUnrollPhase.Options.PartialUnrollMinFrequency, 0,
                        /* high tier should create pre-main-post */AggressivePartialUnrollPhase.Options.PartialUnrollCostReductionFactorHighTier, 100,
                        /* but only unroll it once */AggressivePartialUnrollPhase.Options.PartialUnrollMaxIterationsHighTier, 2,
                        /* mid tier should also unroll it */AggressivePartialUnrollPhase.Options.PartialUnrollCostReductionFactorMidTier, 100,
                        AggressivePartialUnrollPhase.Options.PartialUnrollMaxIterationsMidTier, 128);
        test(options, "snippetEarlyExitReturnHighMidTier", 1000);
    }

    public static int snippetEarlyExitReturnHighMidTier2(int iterations) {
        int result = 1;
        for (int i = 1; injectIterationCount(98, i <= iterations); i++) {
            GraalDirectives.neverWriteSink();
            A = i * result;
        }

        // this loop will be pre-main-post inserted in the high tier, but not unrolled because of
        // the options supplied
        for (int i = 0; GraalDirectives.injectIterationCount(10, i < iterations); i++) {
            if (GraalDirectives.injectBranchProbability(0.1, i == GraalDirectives.sideEffect(i))) {
                return i;
            }
            if (GraalDirectives.injectBranchProbability(0.1, i == GraalDirectives.sideEffect(i + 1))) {
                return i + 1;
            }
            result += i;
        }
        return result;
    }

    @Test
    public void testHighTierNotUnrollMidTierUnrollMultiExit() {
        verifyMainLoopUnrolledMidTier = false;
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false,
                        /* frequency is not important for this test */AggressivePartialUnrollPhase.Options.PartialUnrollMinFrequency, 2,
                        /* high tier should create pre-main-post */AggressivePartialUnrollPhase.Options.PartialUnrollCostReductionFactorHighTier, 100,
                        /* but only unroll it once */AggressivePartialUnrollPhase.Options.PartialUnrollMaxIterationsHighTier, 2,
                        /* mid tier should also unroll it */AggressivePartialUnrollPhase.Options.PartialUnrollCostReductionFactorMidTier, 100,
                        AggressivePartialUnrollPhase.Options.PartialUnrollMaxIterationsMidTier, 128);
        test(options, "snippetEarlyExitReturnHighMidTier2", 1000);
        verifyMainLoopUnrolledMidTier = true;
    }

    public static int snippetLoopFrequency16(int iterations) {
        int result = 1;
        for (int i = 0; injectIterationCount(16, i < iterations); i++) {
            result += GraalDirectives.sideEffect(i);
        }
        return result;
    }

    @Test
    public void testDoNotBuildUselessPreMainPostLoops() {
        // Make sure we actually unroll the main loop if we create one, even very close to the
        // unrolling limit.
        verifyMainLoopUnrolledMidTier = true;
        test(new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.PartialUnrollMinFrequency, 15), "snippetLoopFrequency16", 16);
        test(new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.PartialUnrollMinFrequency, 16), "snippetLoopFrequency16", 16);
        verifyMainLoopUnrolledMidTier = false;

        // Don't insert pre/main/post loops if we won't partially unroll the main loop.
        verifyOnlySimpleLoopsMidTier = true;
        test(new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.PartialUnrollMinFrequency, 17), "snippetLoopFrequency16", 16);
        test(new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.PartialUnrollMinFrequency, 18), "snippetLoopFrequency16", 16);
        verifyOnlySimpleLoopsMidTier = false;
    }

    @Test
    public void testUnsignedStart() {
        verifyMainLoopUnrolledMidTier = true;
        testVariants("testUnsignedInverted", 100);
        verifyMainLoopUnrolledMidTier = false;
    }

    public static int testUnsignedInverted(int end) {
        int i = 1;
        if (Integer.compareUnsigned(i, end + 1) < 0) {
            do {
                GraalDirectives.sideEffect(i);
                i++;
            } while (injectIterationCount(1000, Integer.compareUnsigned(i, end) < 0));
        }
        return i;
    }

    @Test
    public void testIDiv1() {
        for (int i = -1; i < 64; i++) {
            test("idivSnippet1", i);
        }
    }

    static int S = 100;

    public static int idivSnippet1(int iterations) {
        int res = 0;
        for (int i = 1; injectBranchProbability(0.99, i < iterations); i++) {
            res += 100 / i;
        }

        return res;
    }

    @Test
    public void testIDiv2() {
        for (int i = -1; i < 64; i++) {
            test("idivSnippet2", i);
        }
    }

    public static int idivSnippet2(int iterations) {
        int res = 0;
        for (int i = 1; injectBranchProbability(0.99, i < iterations); i++) {
            res += 100 / i * 128;
        }

        return res;
    }

    public static int testUnsignedInverted1(int init1) {
        int i = init1;
        if (Integer.compareUnsigned(i, 7 - 4) >= 0) {
            do {
                GraalDirectives.sideEffect(i);
                if (injectIterationCount(1000, Integer.compareUnsigned(i, 7) < 0)) {
                    break;
                }
                i = i - 4;
            } while (true);
        }
        return i;
    }

    @Test
    public void testUnsignedCheck() {
        verifyMainLoopUnrolledMidTier = false;
        verifyOnlySimpleLoopsMidTier = true;
        for (int i = 100; i >= 0; i--) {
            OptionValues opt = new OptionValues(getOptions(), AggressivePartialUnrollPhase.Options.ForceUnroll, true, AggressivePartialUnrollPhase.Options.HighTierPartialUnrolling, false,
                            GraalOptions.LoopPeeling, false);
            test(opt, "testUnsignedInverted1", i);
        }
        verifyMainLoopUnrolledMidTier = true;
        verifyOnlySimpleLoopsMidTier = false;
    }

    public static int snippetUselessPhi(Object param) {
        int i = 0;
        Object phi = param;
        while (true) {
            phi = GraalDirectives.guardingNonNull(phi);
            if (i++ < 1001001) {
                continue;
            } else {
                break;
            }
        }

        return i;
    }

    @Test
    public void testUselessPhi() {
        checkMidTier = false;
        checkHighTier = true;
        OptionValues opt = new OptionValues(getOptions(), AggressivePartialUnrollPhase.Options.ForceUnroll, true, AggressivePartialUnrollPhase.Options.HighTierPartialUnrolling, true,
                        GraalOptions.LoopPeeling, false, AggressivePartialUnrollPhase.Options.MidTierPartialUnrolling, false,
                        GraalOptions.PreferUnsignedComparison, false, GraalOptions.EarlyGVN, false, GraalOptions.EarlyLICM, false, AggressivePartialUnrollPhase.Options.UnrollEmptyLoops, true);
        test(opt, "snippetUselessPhi", new Object());
        checkMidTier = true;
        checkHighTier = false;
    }

    private static synchronized int nextInt(int i) {
        GraalDirectives.sideEffect(i);
        return i;
    }

    public int snippetSync(int limit) {
        int res = 0;
        for (int i = 0; i < limit; i++) {
            res += nextInt(i);
        }
        return res;
    }

    @Test
    public void testSync() {
        resetCache();
        // some profiles
        snippetSync(10000);
        test("snippetSync", 10000);
    }

    @Test
    public void testSyncInverted() {
        resetCache();
        // some profiles
        snippetSync(10000);
        OptionValues opt = new OptionValues(getInitialOptions(), LoopInversionPhase.Options.HighTierInversion, true, AggressivePartialUnrollPhase.Options.HighTierPartialUnrolling, false);
        test(opt, "snippetSync", 10000);
    }

    public int snippetSyncReorderable(int limit) {
        int res = 0;
        for (int i = 0; i < limit; i++) {
            res += nextInt(i);
            S += res;
        }
        return res;
    }

    @Test
    public void testSyncReorder() {
        resetCache();
        // some profiles
        snippetSyncReorderable(10000);
        test("snippetSyncReorderable", 10000);
    }

    @Test
    public void testSyncReorderInverted() {
        resetCache();
        // some profiles
        snippetSyncReorderable(10000);
        OptionValues opt = new OptionValues(getInitialOptions(), LoopInversionPhase.Options.HighTierInversion, true, AggressivePartialUnrollPhase.Options.HighTierPartialUnrolling, false);
        test(opt, "snippetSyncReorderable", 10000);
    }

    public static int snippetDoubleUsage(int limit) {
        int i = 0;
        while (true) {
            boolean condition = i++ < limit;
            if (condition) {
                GraalDirectives.sideEffect();
                if (i == 123) {
                    GraalDirectives.deoptimizeAndInvalidate();
                    // force a second usage of the condition
                    GraalDirectives.sideEffect(condition ? 1 : 0);
                }
            } else {
                break;
            }
        }

        return i;
    }

    @Test
    public void testDoubleUsages() {
        OptionValues opt = new OptionValues(getOptions(), jdk.graal.compiler.core.phases.MidTier.Options.StripMineCountedLoops, false);
        test(opt, "snippetDoubleUsage", 1000);
    }

}

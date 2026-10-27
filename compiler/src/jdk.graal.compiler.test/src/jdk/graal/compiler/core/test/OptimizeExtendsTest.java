/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.OptimizeExtendsPhase;
import jdk.graal.compiler.phases.schedule.PartialRedundancySchedulePhase;

/**
 * Test (Zero|Sign)Extend Nodes are minimized.
 */

public class OptimizeExtendsTest extends GraalCompilerTest {

    /**
     * Checks that combining precedes final canonicalization, and that read folding and
     * upper-register-bit analysis follow fixed reads and final canonicalization.
     */
    @Test
    public void testPhaseOrdering() {
        OptimizeExtendsPhase combine = new OptimizeExtendsPhase(OptimizeExtendsPhase.Mode.COMBINE);
        OptimizeExtendsPhase foldReads = new OptimizeExtendsPhase(OptimizeExtendsPhase.Mode.FOLD_READS);
        GraphState state = GraphState.defaultGraphState();
        Assert.assertTrue("Combining requires low-tier lowering", combine.notApplicableTo(state).isPresent());
        state.setAfterStage(StageFlag.LOW_TIER_LOWERING);
        Assert.assertTrue("Combining is allowed after low-tier lowering", combine.notApplicableTo(state).isEmpty());
        Assert.assertTrue("Read folding requires fixed reads and final canonicalization", foldReads.notApplicableTo(state).isPresent());

        GraphState canonicalized = state.copy();
        canonicalized.setAfterStage(StageFlag.FINAL_CANONICALIZATION);
        Assert.assertTrue("Combining must precede final canonicalization", combine.notApplicableTo(canonicalized).isPresent());
        Assert.assertTrue("Read folding requires fixed reads even after final canonicalization", foldReads.notApplicableTo(canonicalized).isPresent());

        state.setAfterStage(StageFlag.FIXED_READS);
        state.setAfterStage(StageFlag.PARTIAL_REDUNDANCY_SCHEDULE);
        Assert.assertTrue("Combining is allowed after read scheduling before final canonicalization", combine.notApplicableTo(state).isEmpty());
        Assert.assertTrue("Read folding must follow final canonicalization", foldReads.notApplicableTo(state).isPresent());
        state.setAfterStage(StageFlag.FINAL_CANONICALIZATION);
        Assert.assertTrue("Read folding is allowed after reads are fixed and canonicalized", foldReads.notApplicableTo(state).isEmpty());
    }

    long testSnippet1(byte[] value, int sumKind) {
        long result = 0;
        byte op = value[0];
        switch (sumKind) {
            case 1:
                result += Long.sum(op, result);
                break;
            case 2:
                result += Integer.sum(op, (int) result);
                break;
            case 3:
                result += Character.toLowerCase(op);
                break;
            case 4:
                result += op;
                break;
        }
        return result;
    }

    long testSnippet2(char[] value, int sumKind) {
        long result = 0;
        char op = value[0];
        switch (sumKind) {
            case 1:
                result += Long.sum(op, result);
                break;
            case 2:
                result += Integer.sum(op, (int) result);
                break;
            case 3:
                result += Character.toLowerCase(op);
                break;
            case 4:
                result += (short) op;
                break;
            case 5:
                result += (byte) op;
                break;
        }
        return result;
    }

    long testSnippet3(short[] value, int sumKind) {
        long result = 0;
        short op = value[0];
        switch (sumKind) {
            case 1:
                result += Long.sum(op, result);
                break;
            case 2:
                result += Integer.sum(op, (int) result);
                break;
            case 3:
                result += Character.toLowerCase(op);
                break;
            case 4:
                result += op;
                break;
            case 5:
                result += (byte) op;
                break;
        }
        return result;
    }

    long testSnippet4(int[] value, int sumKind) {
        long result = 0;
        int op = value[0];
        switch (sumKind) {
            case 1:
                result += Long.sum(op, result);
                break;
            case 2:
                result += Integer.sum(op, (int) result);
                break;
            case 3:
                result += Character.toLowerCase(op);
                break;
            case 4:
                result += (short) op;
                break;
            case 5:
                result += (byte) op;
                break;
        }
        return result;
    }

    /**
     * Checks that scheduling keeps the conversions off the path where their values are not used.
     */
    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (graph.method().getName().equals("conditionalUnsigned") && PartialRedundancySchedulePhase.Options.PartialRedundancyScheduling.getValue(graph.getOptions())) {
            var schedule = graph.getLastSchedule();
            var cfg = schedule.getCFG();
            for (ZeroExtendNode extend : graph.getNodes().filter(ZeroExtendNode.class)) {
                Assert.assertNotSame("The path where the value is not used must not execute the conversion", cfg.getStartBlock(), schedule.getNodeToBlockMap().get(extend));
            }
            Assert.assertEquals("The conversions must remain in their branches", 2, graph.getNodes().filter(ZeroExtendNode.class).count());
        } else if (graph.method().getName().equals("conditionalSigned") && PartialRedundancySchedulePhase.Options.PartialRedundancyScheduling.getValue(graph.getOptions())) {
            Assert.assertEquals("Folding the read must preserve the signed conversions in their branches", 2,
                            graph.getNodes().filter(node -> node instanceof SignExtendNode extend && extend.getInputBits() == 8).count());
        } else if (graph.method().getName().equals("unsignedArithmetic") && getProviders().getLowerer().narrowsUseCastValue()) {
            Assert.assertEquals(1, graph.getNodes().filter(ZeroExtendNode.class).count());
            for (ZeroExtendNode extend : graph.getNodes().filter(ZeroExtendNode.class)) {
                Assert.assertFalse("The late phase must prove the arithmetic input has zero upper bits", extend.requiresExplicitZeroExtend());
            }
        }
    }

    /** Integer arithmetic clears upper register bits before the unsigned conversion. */
    public static long unsignedArithmetic(int input) {
        return Integer.toUnsignedLong(input + 1);
    }

    @Test
    public void testUnsignedArithmetic() {
        for (int input : new int[]{0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            test("unsignedArithmetic", input);
        }
    }

    /**
     * Uses the unsigned conversion in two branches but not on the third path. The side effects
     * preserve the branches so that scheduling cannot replace them with conditional moves.
     */
    public static long conditionalUnsigned(int input, int mode) {
        if (mode == 0) {
            return GraalDirectives.sideEffect(Integer.toUnsignedLong(input)) + 1;
        } else if (mode == 1) {
            return GraalDirectives.sideEffect(Integer.toUnsignedLong(input)) + 2;
        } else {
            return 42;
        }
    }

    /**
     * Checks conversion placement when partial scheduling is enabled and execution with either
     * setting.
     */
    @Test
    public void testConditionalUnsigned() {
        for (boolean partialScheduling : new boolean[]{true, false}) {
            OptionValues options = new OptionValues(getInitialOptions(), PartialRedundancySchedulePhase.Options.PartialRedundancyScheduling, partialScheduling);
            for (int input : new int[]{0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                for (int mode = 0; mode < 3; mode++) {
                    test(options, "conditionalUnsigned", input, mode);
                }
            }
        }
    }

    /**
     * Needs an unsigned load on every path and signed conversions in two branches. Folding the
     * unsigned extension into the read must not combine the signed conversions again.
     */
    public static long conditionalSigned(byte[] array, int mode) {
        byte input = array[0];
        GraalDirectives.sideEffect(Integer.toUnsignedLong(Byte.toUnsignedInt(input)));
        if (mode == 0) {
            return GraalDirectives.sideEffect((int) input) + 1L;
        } else if (mode == 1) {
            return GraalDirectives.sideEffect((long) input) + 2;
        } else {
            return 42;
        }
    }

    @Test
    public void testConditionalSigned() {
        OptionValues options = new OptionValues(getInitialOptions(), PartialRedundancySchedulePhase.Options.PartialRedundancyScheduling, true);
        for (byte input : new byte[]{0, -1, Byte.MIN_VALUE, Byte.MAX_VALUE}) {
            for (int mode = 0; mode < 3; mode++) {
                test(options, "conditionalSigned", new byte[]{input}, mode);
            }
        }
    }

    @Test
    public void test() {
        test("testSnippet1", new byte[]{1}, 2);
        test("testSnippet2", new char[]{1}, 2);
        test("testSnippet3", new short[]{1}, 2);
        test("testSnippet4", new int[]{1}, 2);
    }
}

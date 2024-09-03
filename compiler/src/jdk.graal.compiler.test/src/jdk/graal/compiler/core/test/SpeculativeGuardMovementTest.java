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
package jdk.graal.compiler.core.test;

import java.util.List;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationReason;

public class SpeculativeGuardMovementTest extends GraalCompilerTest {

    public static void snippet01(int init, int limit, int offset) {
        for (int i = init; GraalDirectives.injectIterationCount(1000, i < limit); i++) {
            if (Integer.compareUnsigned(i + offset, Integer.MIN_VALUE + 5) > 0) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                throw new IndexOutOfBoundsException();
            }
        }
    }

    @Test
    public void testOverflowUnsignedGuard() {
        int init = 0;
        int limit = 10;
        int offset = Integer.MAX_VALUE;
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false);
        InstalledCode code = getCode(getResolvedJavaMethod("snippet01"), opt);
        assertTrue(code.isValid());
        try {
            code.executeVarargs(init, limit, offset);
            throw new RuntimeException("should have thrown");
        } catch (Throwable e) {
            if (!(e instanceof IndexOutOfBoundsException)) {
                throw new RuntimeException("unexpected exception " + e);
            }
        }
    }

    public static int snippetOverflowInt(int min, int max, int[] arr) {
        if (arr == null) {
            return 0;
        }
        int counter = 0;
        int i;
        int result = 0;
        for (i = min; GraalDirectives.injectIterationCount(1000, i <= max); i++) {
            counter++;
            if (counter >= 3) {
                result += arr[i];
                if (counter == 1222) {
                    GraalDirectives.controlFlowAnchor();
                    continue;
                }
                return -1;
            }
            GraalDirectives.neverStripMine();
            GraalDirectives.neverWriteSink();
        }
        return counter + result;
    }

    @Test
    public void testOverflow() {
        final int min = Byte.MAX_VALUE - 5;
        final int max = Byte.MAX_VALUE;
        int[] arr = new int[1000];
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false, GraalOptions.LoopUnswitch, false);
        test(opt, "snippetOverflowInt", min, max, arr);
    }

    public static void snippetInstanceOf(int bound, A a) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < bound; i++) {
            GraalDirectives.sideEffect();
            if (GraalDirectives.injectBranchProbability(0.0000000001, i > 12312)) {
                Object o = optAway(a);
                if (o instanceof B) {
                    GraalDirectives.controlFlowAnchor();
                    GraalDirectives.blackhole(o);
                }
            }
        }
    }

    public static void snippetInstanceOfHoisted(int bound, A a) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < bound; i++) {
            GraalDirectives.sideEffect();
            if (GraalDirectives.injectBranchProbability(0.01, i > 12312)) {
                Object o = optAway(a);
                if (o instanceof B) {
                    GraalDirectives.controlFlowAnchor();
                    GraalDirectives.blackhole(o);
                }
            }
        }
    }

    static class A {

    }

    static class B extends A {

    }

    static class B1 extends B {

    }

    static class C extends A {

    }

    @BytecodeParserNeverInline
    static Object optAway(Object o) {
        return o;
    }

    @Test
    public void testInstanceOf() {
        // >15k iterations needed to get java profiles
        for (int i = 0; i < 15000; i++) {
            snippetInstanceOf(i, new B1());
            snippetInstanceOfHoisted(i, new B1());
        }
        assertTrue(instanceOfGuardPresent("snippetInstanceOf"), "Unexpected graph after parsing! InstanceOf guard expected.");
        assertTrue(instanceOfGuardPresent("snippetInstanceOfHoisted"), "Unexpected graph after parsing! InstanceOf guard expected.");

        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false, GraalOptions.LoopUnswitch, false);

        test(opt, "snippetInstanceOf", 0, new C());
        int deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.TypeCheckedInliningViolated, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 1, String.format("Guard should still be inside loop. Actual loop depth: %s", deoptLoopDepth));

        test(opt, "snippetInstanceOfHoisted", 0, new C());
        deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.TypeCheckedInliningViolated, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 0, String.format("Guard should be hoisted from loop. Actual loop depth: %s", deoptLoopDepth));
    }

    private boolean instanceOfGuardPresent(String toParse) {
        StructuredGraph g = parseEager(toParse, AllowAssumptions.YES);
        assertTrue(g.getNodes().filter(InstanceOfNode.class).count() == 1, "Unexpected graph after parsing! Single InstanceOfNode expected.");

        InstanceOfNode iOf = g.getNodes().filter(InstanceOfNode.class).first();
        assertTrue(iOf.profile() != null, "InstanceOfNode needs a profile to create a guard!");
        return iOf.usages().count() == 1 && iOf.usages().first() instanceof FixedGuardNode;
    }

    private static int findDeoptLoopDepth(DeoptimizationReason reason, StructuredGraph g) {
        ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(g);
        assertTrue(cfg.getLoops().size() > 0, "Loop(s) in graph expected!");

        List<DeoptimizeNode> deopts = g.getNodes(DeoptimizeNode.TYPE).stream().filter(n -> n.getReason().equals(reason)).toList();
        assertTrue(deopts.size() == 1, String.format("Exactly one DeoptimizeNode with reason %s in graph expected!", reason));

        CFGLoop<HIRBlock> loop = cfg.getNodeToBlock().get(deopts.get(0)).getFirstPredecessor().getLoop();
        return loop == null ? 0 : loop.getDepth();
    }

    public static void snippetInstanceOfLoopNest(int bound, A[] a) {
        if (a == null) {
            return;
        }
        for (int j = 0; j < 50; j++) {
            if (j >= a.length) {
                return;
            }
            A a1 = a[j];
            if (a1 == null) {
                return;
            }
            GraalDirectives.blackhole(a1);
            // 1) instanceof could be moved here but fq ~= 50
            for (int k = 0; k < 50; k++) {
                if (GraalDirectives.injectBranchProbability(0.01, bound > 14500)) {
                    // 2) fq ~= 2.5 --> instanceof is moved here
                    for (int i = 0; GraalDirectives.injectIterationCount(100000, i < bound); i++) {
                        if (GraalDirectives.injectBranchProbability(0.01, i > 12312)) {
                            if (a1 instanceof B) {
                                GraalDirectives.controlFlowAnchor();
                                GraalDirectives.blackhole(a1);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void snippetInstanceOfHoistedLoopNest(int bound, A a) {
        if (a == null) {
            return;
        }
        // 3) instanceof should be moved here (fq ~= 1)
        for (int j = 0; j < 50; j++) {
            // 2) ignores moving here (fq ~= 50)
            for (int k = 0; k < 50; k++) {
                // fq ~= 2500
                if (GraalDirectives.injectBranchProbability(0.01, bound > 14500)) {
                    // 1) could move instanceof here (fq ~= 25)
                    for (int i = 0; GraalDirectives.injectIterationCount(100000, i < bound); i++) {
                        if (GraalDirectives.injectBranchProbability(0.01, i > 12312)) {
                            if (a instanceof B) {
                                GraalDirectives.controlFlowAnchor();
                                GraalDirectives.blackhole(a);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testInstanceOfLoopNest() {
        // >15k iterations needed to get java profiles
        for (int i = 0; i < 15000; i++) {
            snippetInstanceOfLoopNest(i, bArray());
            snippetInstanceOfHoistedLoopNest(i, new B1());
        }
        assertTrue(instanceOfGuardPresent("snippetInstanceOfLoopNest"), "Unexpected graph after parsing! InstanceOf guard expected.");
        assertTrue(instanceOfGuardPresent("snippetInstanceOfHoistedLoopNest"), "Unexpected graph after parsing! InstanceOf guard expected.");

        OptionValues opt = getInitialOptions();

        test(opt, "snippetInstanceOfLoopNest", 0, null);
        int deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.TypeCheckedInliningViolated, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 2, String.format("InstanceOf should have just be hoisted from innermost loop. Actual loop depth: %s", deoptLoopDepth));

        test(opt, "snippetInstanceOfHoistedLoopNest", 0, null);
        deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.TypeCheckedInliningViolated, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 0, String.format("InstanceOf should be hoisted above all loop. Actual loop depth: %s", deoptLoopDepth));
    }

    private static B1[] bArray() {
        B1[] a = new B1[100];
        for (int i = 0; i < 100; i++) {
            a[i] = new B1();
        }
        return a;
    }

    public static void snippetCompare(int bound, int[] a) {
        if (a == null) {
            return;
        }
        for (int i = 0; GraalDirectives.injectIterationCount(100000, i < bound); i++) {
            GraalDirectives.sideEffect();
            GraalDirectives.controlFlowAnchor();
            if (GraalDirectives.injectBranchProbability(0.0000000001, i > 12312)) {
                int[] o = optAway(a);
                if (a[i] == 42) {
                    GraalDirectives.blackhole(o);
                }
            }
        }
    }

    public static void snippetCompareHoisted(int bound, int[] a) {
        if (a == null) {
            return;
        }
        for (int i = 0; GraalDirectives.injectIterationCount(100000, i < bound); i++) {
            GraalDirectives.sideEffect();
            GraalDirectives.controlFlowAnchor();
            if (GraalDirectives.injectBranchProbability(0.01, i > 12312)) {
                int[] o = optAway(a);
                if (a[i] == 42) {
                    GraalDirectives.blackhole(o);
                }
            }
        }
    }

    @BytecodeParserNeverInline
    static int[] optAway(int[] o) {
        return o;
    }

    @Test
    public void testCompare() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false, GraalOptions.LoopUnswitch, false);

        test(opt, "snippetCompare", 0, new int[0]);
        int deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.BoundsCheckException, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 1, String.format("Guard should still be inside loop. Actual loop depth: %s", deoptLoopDepth));

        test(opt, "snippetCompareHoisted", 0, new int[0]);
        deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.BoundsCheckException, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 0, String.format("Guard should be hoisted from loop. Actual loop depth: %s", deoptLoopDepth));
    }
}

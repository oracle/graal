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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;

public class LoopInversionGuardTest extends GraalCompilerTest {
    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        // Keep exact arithmetic exceptions speculative, as in production compilation.
        return OptimisticOptimizations.ALL;
    }

    public static long exactIncrementBeforeTest(long start, long limit) {
        long sum = 0;
        long i = start;
        while (true) {
            long next = Math.subtractExact(i, -1L);
            if (i >= limit) {
                break;
            }
            sum += next;
            i = next;
        }
        return sum;
    }

    @Test
    public void testExactIncrementBeforeTest() throws Exception {
        long[][] inputs = {{Long.MAX_VALUE - 5, Long.MAX_VALUE}, {Long.MAX_VALUE - 1, Long.MAX_VALUE}, {Long.MAX_VALUE, Long.MAX_VALUE}};
        for (long[] input : inputs) {
            // Compile before executing overflow inputs and use fresh code after deoptimization.
            InstalledCode code = getCode(getResolvedJavaMethod("exactIncrementBeforeTest"), null, true, false, getInitialOptions());
            Assert.assertEquals(55L, code.executeVarargs(0L, 10L));
            Assert.assertEquals(-14L, code.executeVarargs(Long.MAX_VALUE - 5, Long.MAX_VALUE - 1));
            Assert.assertEquals(0L, code.executeVarargs(10L, 0L));
            try {
                Object result = code.executeVarargs(input[0], input[1]);
                Assert.fail("Expected overflow on the exiting header visit for start=" + input[0] + ", got " + result);
            } catch (ArithmeticException expected) {
                // The header increment also executes when the loop body is not entered.
            }
        }
    }

    public static int countedLoop(int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
        }
        return sum;
    }

    private void checkGuard(boolean outsideAnchor, boolean bodyAnchor, boolean invariant, boolean canInvert) {
        StructuredGraph graph = parseEager("countedLoop", AllowAssumptions.YES);
        Loop loop = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph).loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        IfNode test = LoopInversionPhase.findLoopInversionTest(loop, true);
        Assert.assertNotNull(test);
        AbstractBeginNode anchor = outsideAnchor ? graph.start() : loop.loopBegin();
        if (bodyAnchor) {
            anchor = test.trueSuccessor() == loop.counted().getCountedExit() ? test.falseSuccessor() : test.trueSuccessor();
        }
        ValueNode value = invariant ? graph.getParameter(0) : loop.counted().getLimitCheckedIV().valueNode();
        LogicNode condition = graph.addOrUnique(new IntegerLessThanNode(value, ConstantNode.forInt(42, graph)));
        graph.addOrUnique(new GuardNode(condition, anchor, DeoptimizationReason.ArithmeticException, DeoptimizationAction.InvalidateReprofile, false,
                        SpeculationLog.NO_SPECULATION, null));
        // Recompute loop membership so the floating guard is included.
        loop = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph).loops().get(0);
        Assert.assertEquals(canInvert, LoopInversionPhase.findLoopInversionTest(loop, true) != null);
    }

    @Test
    public void testHeaderGuard() {
        checkGuard(false, false, false, false);
    }

    @Test
    public void testOutsideAnchoredGuard() {
        checkGuard(true, false, false, false);
    }

    @Test
    public void testBodyGuard() {
        checkGuard(false, true, false, true);
    }

    @Test
    public void testInvariantGuard() {
        checkGuard(true, false, true, true);
    }

    @Test
    public void testInvariantHeaderGuard() {
        checkGuard(false, false, true, false);
    }
}

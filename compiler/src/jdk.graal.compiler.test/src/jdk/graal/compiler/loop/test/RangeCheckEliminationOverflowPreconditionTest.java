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

import java.util.Objects;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;
import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.loop.phases.RangeCheckEliminationPhase;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.code.InstalledCode;

/**
 * Tests different scale*innerLoopTrip overflow scenarios to check that preconditions of
 * {@link RangeCheckEliminationPhase} are met.
 */
public class RangeCheckEliminationOverflowPreconditionTest extends GraalCompilerTest {

    boolean expectStripMinedLoops = true;

    boolean expectRangeCheckElimination = true;

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        assert nodeCountBefore >= 0;
        assert nodeCountAfter >= 0;

        int difference = nodeCountAfter - nodeCountBefore;
        if (expectRangeCheckElimination) {

            // range check elimination always adds a few nodes
            assert difference > 10 : "Must eliminate range checks and thus add a few new nodes but before=" + nodeCountBefore + " after=" + nodeCountAfter + " difference=" + difference;
        } else {
            assert difference == 0 : "Must NOT eliminate range checks and thus add a few new nodes but before=" + nodeCountBefore + " after=" + nodeCountAfter + " difference=" + difference;
        }

        nodeCountBefore = -1;
        nodeCountAfter = -1;

        if (!expectStripMinedLoops) {
            return;
        }
        boolean foundStripMined = false;
        for (LoopBeginNode lb : graph.getNodes(LoopBeginNode.TYPE)) {
            if (lb.isAnyStripMinedInner() || lb.isAnyStripMinedOuter()) {
                foundStripMined = true;
            }
        }
        Assert.assertTrue("Must strip mine loops", foundStripMined);
    }

    static final long veryLargeScale = 1 << 29;

    private static long snippetTest01(long range, long j) {
        if (range < 0) {
            return -1;
        }
        long i = 0;
        for (; i < 100; i++) {
            if (i == j) {
                Objects.checkIndex(veryLargeScale * i, range);
            }
        }
        return i;
    }

    public static boolean LOG = false;

    /**
     * Note that this test uses node count before and after the RangeCheckEliminationPhase
     * as a proxy to determine if the phase performed an optimization.
     */
    private static int nodeCountBefore = -1;
    private static int nodeCountAfter = -1;

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts).copy();

        var pos = s.getMidTier().findPhase(RangeCheckEliminationPhase.class);
        pos.previous();
        pos.add(new BasePhase<MidTierContext>() {
            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, MidTierContext context) {
                nodeCountBefore = graph.getNodeCount();
            }
        });
        pos = s.getMidTier().findPhase(RangeCheckEliminationPhase.class);
        pos.add(new BasePhase<MidTierContext>() {
            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, MidTierContext context) {
                nodeCountAfter = graph.getNodeCount();
            }
        });

        return s;
    }

    @Test
    public void test01NoOptimizedRangeChecks() {
        expectRangeCheckElimination = false;
        expectStripMinedLoops = true;

        OptionValues opt = new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.AggressivePartialUnroll, false,
                        RangeCheckEliminationPhase.Options.RCELogRangeCheckValues, LOG, GraalOptions.OptDeoptimizationGrouping, false,
                        RangeCheckEliminationPhase.Options.ForceRCE, true, GraalOptions.SpeculativeGuardMovement, false, VectorIntrinsics.Options.Vectorization, false,
                        GraalOptions.LoopPeeling, false, GraalOptions.LoopUnswitch, false, GraalOptions.FullUnroll, false,
                        jdk.graal.compiler.core.phases.MidTier.Options.OptExactArithmetic, false,
                        /*
                         * innerLoopIV.extremum * scale (1<<29) overflows int range and thus no
                         * range check elimination possible
                         */
                        CountedStripMiningPhase.Options.CountedStripMiningInnerLoopTrips, 1024);

        InstalledCode code = getCode(getResolvedJavaMethod("snippetTest01"), opt);
        runAndExpectException(code, IndexOutOfBoundsException.class, (long) Integer.MAX_VALUE, (long) 10);
        assert !code.isValid() : "Code must have deoptimized in the first invocation of the method";
        if (LOG) {
            TTY.printf("First run over%n");
        }
        code = getCode(getResolvedJavaMethod("snippetTest01"), opt);
        runAndExpectException(code, IndexOutOfBoundsException.class, (long) Integer.MAX_VALUE, (long) 10);
        assert !code.isValid();
    }

    @Test
    public void test0OptimizedRangeChecks() {
        expectRangeCheckElimination = true;
        expectStripMinedLoops = true;

        OptionValues opt = new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.AggressivePartialUnroll, false,
                        RangeCheckEliminationPhase.Options.RCELogRangeCheckValues, LOG, GraalOptions.OptDeoptimizationGrouping, false,
                        RangeCheckEliminationPhase.Options.ForceRCE, true, GraalOptions.SpeculativeGuardMovement, false, VectorIntrinsics.Options.Vectorization, false,
                        GraalOptions.LoopPeeling, false, GraalOptions.LoopUnswitch, false, GraalOptions.FullUnroll, false,
                        jdk.graal.compiler.core.phases.MidTier.Options.OptExactArithmetic, false,
                        /*
                         * innerLoopIV.extremum * scale (1<<29) DOES NOT overflow int range and thus
                         * range check elimination is possible
                         */
                        CountedStripMiningPhase.Options.CountedStripMiningInnerLoopTrips, 2);

        InstalledCode code = getCode(getResolvedJavaMethod("snippetTest01"), opt);
        runAndExpectException(code, IndexOutOfBoundsException.class, (long) Integer.MAX_VALUE, (long) 10);
        assert !code.isValid() : "Code must have deoptimized in the first invocation of the method";
        if (LOG) {
            TTY.printf("First run over%n");
        }
        code = getCode(getResolvedJavaMethod("snippetTest01"), opt);
        runAndExpectException(code, IndexOutOfBoundsException.class, (long) Integer.MAX_VALUE, (long) 10);
        assert !code.isValid();
    }

    private static void runAndExpectException(InstalledCode code, Class<?> exceptionClass, Object... args) {
        boolean exception = false;
        try {
            Object result = code.executeVarargs(args);
            TTY.printf("Execution result = %s%n", result);
        } catch (Throwable t) {
            if (t.getClass().equals(exceptionClass)) {
                exception = true;
            } else {
                throw GraalError.shouldNotReachHere(t);
            }
        }
        if (!exception) {
            throw new RuntimeException("Expected exception not thrown");
        }
    }

}

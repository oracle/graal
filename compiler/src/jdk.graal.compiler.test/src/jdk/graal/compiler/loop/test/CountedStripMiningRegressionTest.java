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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.junit.Assert;

import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationReason;

@SuppressWarnings("all")
public class CountedStripMiningRegressionTest extends GraalCompilerTest {

    public static int method2() {
        int var0 = -1038903517;
        int[] var12 = new int[3];
        for (int i = 0; i < 3; i++) {
            var0 = var0 + -44872;
        }
        return var0;
    }

    public static int longIntLoop(int limit) {
        int result = 0;
        for (int i = 0; i < limit; i++) {
            result += i;
        }
        return result;
    }

    @Test
    public void testLongIntLoopIsStripMined() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.FullUnroll, false,
                        GraalOptions.LoopUnswitch, false,
                        VectorIntrinsics.Options.Vectorization, false,
                        jdk.graal.compiler.core.phases.MidTier.Options.StripMineCountedLoops, true,
                        CountedStripMiningPhase.Options.CountedStripMiningMinFrequency, 0,
                        CountedStripMiningPhase.Options.CountedStripMiningInnerLoopTrips, 10,
                        CountedStripMiningPhase.Options.StripMineALot, false);
        StructuredGraph graph = parseEager("longIntLoop", AllowAssumptions.NO, options);
        createSuites(options).getHighTier().apply(graph, getDefaultHighTierContext());
        createSuites(options).getMidTier().apply(graph, getDefaultMidTierContext());
        Assert.assertTrue(graph.getNodes(LoopBeginNode.TYPE).filter(node -> ((LoopBeginNode) node).isAnyStripMinedOuter()).isNotEmpty());
    }

    @Test
    public void testGR62550() throws InvalidInstalledCodeException {
        deleteAllFloatingGuardsPriorStripMining = true;
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false, GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false, GraalOptions.LoopUnswitch, false,
                        VectorIntrinsics.Options.Vectorization, false, CountedStripMiningPhase.Options.StripMineALot, true, GraalOptions.PartialEscapeAnalysis, false, GraalOptions.OptReadElimination,
                        false, GraalOptions.OptDuplication, false, GraalOptions.SpeculativeGuardMovement, false);

        int expected = method2();

        InstalledCode ic = getCode(getResolvedJavaMethod("method2"), opt);
        int real = (int) ic.executeVarargs();
        Assert.assertEquals(expected, real);

        deleteAllFloatingGuardsPriorStripMining = false;
    }

    boolean deleteAllFloatingGuardsPriorStripMining;

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts).copy();
        if (!deleteAllFloatingGuardsPriorStripMining) {
            return s;
        }

        var pos = s.getMidTier().findPhase(CountedStripMiningPhase.class);
        pos.previous();
        pos.add(new BasePhase<MidTierContext>() {

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, MidTierContext context) {
                for (FloatingReadNode read : graph.getNodes().filter(FloatingReadNode.class).snapshot()) {
                    GuardingNode g = read.getGuard();
                    if (g instanceof GuardNode guard) {
                        if (guard.getReason() == DeoptimizationReason.BoundsCheckException) {
                            guard.replaceAtUsages(guard.getAnchor().asNode());
                            guard.safeDelete();
                        }
                    }
                }
            }

        });
        return s;
    }

    private static final int stride = Integer.MAX_VALUE / 1024 / 1024;

    // ported from c2
    public static int test3(int start, int stop, byte[] array, int offset) {
        int i = start;
        int j = 0;
        int res = 0;
        if (stop < start) {
            do {
                synchronized (new Object()) {
                }
                res += array[i - offset];
                i -= stride;
                j++;
            } while (i > stop);
        }
        return res;
    }

    @Test
    public void testLoopLimitOverflowC2() throws InvalidInstalledCodeException {
        byte[] array = new byte[Integer.MAX_VALUE - 2];
        for (int i = 0; i < 20_000; i++) {
            test3(1000 * stride, 0, array, 0);
        }
        try {
            getCode(getResolvedJavaMethod("test3")).executeVarargs(stride, Integer.MIN_VALUE + stride - 1, array, stride);
            throw new RuntimeException("ArrayIndexOutOfBoundsException not throw");
        } catch (ArrayIndexOutOfBoundsException ignore) {
            // Good.
        }
    }

    // ported from jdk/test/hotspot/jtreg/gc/stress/TestStressIHOPMultiThread.java
    // Checkstyle: stop/
    private final int CHUNK_SIZE = 100000;

    private volatile boolean running = true;

    private class AllocationThread extends Thread {

        private final List<Object> garbage;

        private final long amountOfGarbage;
        private final int threadId;

        public AllocationThread(int id, long amount) {
            super("Thread " + id);
            threadId = id;
            amountOfGarbage = amount;
            garbage = new LinkedList<>();
        }

        public void allocate(long amount) {
            long allocated = 0;
            while (allocated < amount && running) {
                garbage.add(new byte[CHUNK_SIZE]);
                allocated += CHUNK_SIZE;
            }
        }

    }
    // Checkstyle: resume

    @Test
    public void testGCThreadStress() throws InvalidInstalledCodeException {
        getCode(getResolvedJavaMethod(AllocationThread.class, "allocate"));
    }
}

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

import jdk.graal.compiler.loop.phases.SimulationBasedLoopPeeling;
import jdk.graal.compiler.loop.phases.SimulationBasedLoopPolicies;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.virtual.EscapeObjectState;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

public class IterativeLoopPeelingFrameStateTest extends GraalCompilerTest {

    private static final OptionValues OPTIONS = new OptionValues(getInitialOptions(), //
                    GraalOptions.PartialUnroll, false, //
                    GraalOptions.LoopPeeling, false, // don't run automatically, we run it manually
                    LoopPolicies.Options.PeelALot, true, //
                    LoopPeelingPhase.Options.IterativePeelingLimit, 3, //
                    LoopPeelingPhase.Options.IncrementalCanonDuringPeel, false);

    public static class MyObject {
        public int field;
        public MyObject next;

        MyObject(int field, MyObject next) {
            this.field = field;
            this.next = next;
        }
    }

    public static void innerLoop(int limit, long[] array) {
        for (int i = 0; GraalDirectives.injectIterationCount(10, i < limit); i++) {
            array[i] = i;
        }
    }

    public static void innerMethod(int limit, long[] array) {
        innerLoop(limit, array);
    }

    public static void snippet(int limit, long[] array) {
        // Make a virtual object in an outer state, alive across the write in the loop and all its
        // peeled copies.
        MyObject object = new MyObject(42, null);
        innerMethod(limit, array);
        GraalDirectives.blackhole(object);
    }

    /**
     * LoopPeelingPhase performs incremental canonicalization which will common up any duplicate
     * FrameStates so this check must be performed directly after the operation of the main phase.
     */
    public static class CheckingLoopPeelingPhase extends LoopPeelingPhase {
        public CheckingLoopPeelingPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
            super(policies, canonicalizer);
        }

        @Override
        protected void run(StructuredGraph graph, CoreProviders context) {
            Mark beforePeeling = graph.getMark();
            super.run(graph, context);
            LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
            Assert.assertEquals("number of loops", 1, loops.loops().size());
            Loop loop = loops.loops().get(0);
            Assert.assertEquals("peelings", 3, loop.loopBegin().peelings());
            NodeIterable<WriteNode> peeledWrites = graph.getNodes(WriteNode.TYPE).filter(write -> graph.isNew(beforePeeling, write));
            Assert.assertEquals("writes", 3, peeledWrites.count());

            /*
             * Iterative peeling must not create shared states and virtual mappings: Each peeled
             * copy must have fresh duplicates of these nodes.
             */
            for (WriteNode write : peeledWrites) {
                FrameState state = write.stateAfter();
                while (state != null) {
                    Assert.assertEquals("state " + state + " usages:", 1, state.getUsageCount());
                    NodeInputList<EscapeObjectState> escapeObjectStates = state.virtualObjectMappings();
                    if (escapeObjectStates != null) {
                        for (EscapeObjectState objectState : escapeObjectStates) {
                            Assert.assertEquals("state " + state + " object state " + objectState + " usages:", 1, objectState.getUsageCount());
                        }
                    }
                    state = state.outerFrameState();
                }
            }
        }
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        LoopPolicies policies = new SimulationBasedLoopPolicies(SimulationBasedLoopPeeling.getHighTierPeelingFactors(graph.getOptions()));
        new CheckingLoopPeelingPhase(policies, CanonicalizerPhase.create()).apply(graph, getDefaultHighTierContext());
    }

    @Test
    public void testIterativePeelingFrameState() {
        test(OPTIONS, "snippet", 10, new long[10]);
    }
}

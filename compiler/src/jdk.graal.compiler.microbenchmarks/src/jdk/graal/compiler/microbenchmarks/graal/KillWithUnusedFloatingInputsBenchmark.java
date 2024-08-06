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
package jdk.graal.compiler.microbenchmarks.graal;

import org.openjdk.jmh.annotations.Benchmark;

import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.microbenchmarks.graal.util.GraalState;
import jdk.graal.compiler.microbenchmarks.graal.util.GraphState;
import jdk.graal.compiler.microbenchmarks.graal.util.MethodSpec;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.JavaKind;

public class KillWithUnusedFloatingInputsBenchmark extends GraalBenchmark {

    @Benchmark
    public void killWithUnusedFloatingInputsTinyBenchmark(TinyGraph s) {
        s.graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()).forEach(GraphUtil::killWithUnusedFloatingInputs);
    }

    @Benchmark
    public void killAllWithUnusedFloatingInputsTinyBenchmark(TinyGraph s) {
        GraphUtil.killAllWithUnusedFloatingInputs(s.graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()), false);
    }

    @Benchmark
    public void killWithUnusedFloatingInputsSmallBenchmark(SmallGraph s) {
        s.graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()).forEach(GraphUtil::killWithUnusedFloatingInputs);
    }

    @Benchmark
    public void killAllWithUnusedFloatingInputsSmallBenchmark(SmallGraph s) {
        GraphUtil.killAllWithUnusedFloatingInputs(s.graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()), false);
    }

    @Benchmark
    public void killWithUnusedFloatingInputsMediumBenchmark(MediumGraph s) {
        s.graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()).forEach(GraphUtil::killWithUnusedFloatingInputs);
    }

    @Benchmark
    public void killAllWithUnusedFloatingInputsMediumBenchmark(MediumGraph s) {
        GraphUtil.killAllWithUnusedFloatingInputs(s.graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()), false);
    }

    // too long running
    public void killWithUnusedFloatingInputsLargeBenchmark(LargeGraph s) {
        s.graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()).forEach(GraphUtil::killWithUnusedFloatingInputs);
    }

    // too long running
    public void killAllWithUnusedFloatingInputsLargeBenchmark(LargeGraph s) {
        GraphUtil.killAllWithUnusedFloatingInputs(s.graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()), false);
    }

    public static int snippet(int base, int mul) {
        // the base graph which is later enlarged
        return base * mul;
    }

    @MethodSpec(declaringClass = KillWithUnusedFloatingInputsBenchmark.class, name = "snippet")
    public static class TinyGraph extends EnlargedGraph {
        public TinyGraph() {
            super(1);
        }
    }

    @MethodSpec(declaringClass = KillWithUnusedFloatingInputsBenchmark.class, name = "snippet")
    public static class SmallGraph extends EnlargedGraph {
        public SmallGraph() {
            super(10);
        }
    }

    @MethodSpec(declaringClass = KillWithUnusedFloatingInputsBenchmark.class, name = "snippet")
    public static class MediumGraph extends EnlargedGraph {
        public MediumGraph() {
            super(100);
        }
    }

    @MethodSpec(declaringClass = KillWithUnusedFloatingInputsBenchmark.class, name = "snippet")
    public static class LargeGraph extends EnlargedGraph {
        public LargeGraph() {
            super(1000);
        }
    }

    public static class EnlargedGraph extends GraphState {
        private final int sizeParam;

        public EnlargedGraph(int sizeParam) {
            this.sizeParam = sizeParam;
            modifyGraph(originalGraph);
        }

        @Override
        protected StructuredGraph preprocessOriginal(StructuredGraph structuredGraph) {
            compileUntilModification(structuredGraph);
            return structuredGraph;
        }

        private static void compileUntilModification(StructuredGraph g) {
            GraalState graal = new GraalState();
            PhaseSuite<HighTierContext> highTier = graal.backend.getSuites().getDefaultSuites(graal.options, graal.backend.getTarget().arch).getHighTier();
            highTier.apply(g, new HighTierContext(graal.providers, graal.backend.getSuites().getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL));
        }

        private void modifyGraph(StructuredGraph g) {
            ReturnNode ret = g.getNodes(ReturnNode.TYPE).first();
            ValueNode val = ret.result();

            /*
             * Creates "sizeParam" FrameStates without usages. Each of the these FrameStates has
             * "sizeParam" inputs.
             */
            FrameStateBuilder fb = new FrameStateBuilder(null, g.method(), g);
            ValueNode[] vals = new ValueNode[sizeParam];
            JavaKind[] kinds = new JavaKind[sizeParam];
            for (int i = 0; i < sizeParam; i++) {
                vals[i] = val;
                kinds[i] = JavaKind.Int;
            }

            for (int j = 0; j < sizeParam; j++) {
                FrameState fs = fb.create(1, null, false, kinds, vals);
                g.addOrUnique(fs);
            }
        }
    }
}

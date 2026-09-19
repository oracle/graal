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
package jdk.graal.compiler.core.test;

import java.io.Serializable;
import java.util.Optional;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.EarlyExpandCheckCastPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.truffle.host.HostInliningPhase;

/**
 * Regression test to mimic the interplay between different transformations which have led to a
 * segfault in the past.
 *
 * {@link EarlyExpandCheckCastPhase} expands check casts into null check and type check.
 *
 * <pre>
 *   if (val == null)     if (non-null value instanceof TYPE)
 *      /         \         |                   \
 *   anchor      ...       anchor              ...
 *     |                   |
 *   Pi (always-null)     Pi (non-null type)
 *                  \    /
 *                   Phi
 * </pre>
 *
 * The always-null branch can be unreachable though, but in a way that it is not visible by the
 * compiler and its conditional elimination:
 *
 * <pre>
 *  --------- Anchor where it is proven that val != null
 *  |
 *  |         if (val == null)     if (non-null value instanceof TYPE)
 *  |            /         \         |                   \
 *  |         anchor       (...)   anchor               (...)
 *  |      (unreachable)             |
 *  |            |                   |
 *  |        Pi (always-null)   Pi (non-null type)
 *  |                       \   /
 *  |                        Phi
 *  |                         |
 *  -------------------- Pi (non-null)
 *                            |
 *                           Read
 * </pre>
 *
 * The non-nullness ensured by the last Pi leads to subsequent null checks being folded away. The
 * always-null Pi is thus necessary to keep an anchoring point for the null value in the unreachable
 * path. Previously, always-null Pis would be folded to a null constant. In that case, after
 * {@link DuplicationPhase}, the always-null branch would look like:
 *
 * <pre>
 *  --------- Anchor where it is proven that val != null
 *  |
 *  |   if (val == null)
 *  |        |        \
 *  |       NULL     (...)
 *  |        |
 *  ------Pi (non-null)
 *           |
 *          Read
 * </pre>
 * <p>
 * The null value (from the unreachable branch) can be hoisted to above the null check along a
 * subsequent read, causing a segfault in the process.
 * </p>
 * This test artificially creates a pattern like the one above, by (1) introducing a non-null Pi
 * after the checkcast value and (2) hoisting the blackhole to before the null check, to mimic an
 * {@link jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy#EARLIEST EARLIEST}
 * scheduling for the floating read.
 *
 */
public class CheckCastCanonicalizationTest extends GraalCompilerTest {

    public static void snippet(Object o, boolean notNull) {
        if (notNull) {
            Serializable a = (Serializable) o;
            GraalDirectives.blackhole(a.getClass());
        }
    }

    @Test
    public void test() {
        OptionValues opts = new OptionValues(getInitialOptions(), DuplicationOptions.DuplicateALot, true, DuplicationOptions.DuplicationMinBranchFrequency, 0.0d);
        test(opts, "snippet", "input", true);
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts).copy();
        s.getHighTier().findPhase(HostInliningPhase.class).add(new InsertPiPhase(createCanonicalizerPhase()));
        s.getHighTier().findPhase(DuplicationPhase.class).add(new HoistBlackHolePhase());
        return s;
    }

    /**
     * Used to introduce a non-null Pi after the checkcast value, to remove subsequent null checks
     * and enable the segfault.
     */
    private static final class InsertPiPhase extends PostRunCanonicalizationPhase<HighTierContext> {
        InsertPiPhase(CanonicalizerPhase canonicalizer) {
            super(canonicalizer);
        }

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, HighTierContext context) {
            AbstractBeginNode anchor = graph.getNodes(IfNode.TYPE).first().falseSuccessor();
            ValueNode checkedVal = graph.getNodes().filter(IsNullNode.class).first().getValue();

            ValueNode pi = graph.addOrUnique(PiNode.create(checkedVal, ((ObjectStamp) checkedVal.stamp(NodeView.DEFAULT)).asNonNull(), anchor));
            checkedVal.replaceAtUsages(pi, n -> n != pi);
        }
    }

    /**
     * Used for hoisting blackholes to the earliest scheduling point of their input. This mimics an
     * earliest schedule for the FloatingRead going into the blackhole.
     */
    private static final class HoistBlackHolePhase extends BasePhase<HighTierContext> {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, HighTierContext context) {
            SchedulePhase.run(graph, SchedulePhase.SchedulingStrategy.EARLIEST, ControlFlowGraph.computeForSchedule(graph), context, false);
            for (BlackholeNode blackhole : graph.getNodes().filter(BlackholeNode.class)) {
                HIRBlock block = graph.getLastSchedule().blockFor(blackhole.getValue());
                GraphUtil.unlinkFixedNode(blackhole);
                graph.addBeforeFixed(block.getEndNode(), blackhole);
            }
        }

    }
}

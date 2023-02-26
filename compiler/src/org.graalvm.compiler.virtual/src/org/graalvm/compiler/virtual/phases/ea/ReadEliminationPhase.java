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
package org.graalvm.compiler.virtual.phases.ea;

import static org.graalvm.compiler.core.common.GraalOptions.EscapeAnalyzeOnly;

import java.util.Optional;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator;
import org.graalvm.word.LocationIdentity;

/**
 * This phase performs read and (simple) write elimination on a graph. It operates on multiple
 * granularities, i.e., before and after high-tier lowering. The phase iterates the graph in a
 * reverse-post-order fashion {@linkplain ReentrantBlockIterator} and tracks the currently active
 * value for a specific {@linkplain LocationIdentity}, which allows the removal of subsequent reads
 * if no writes happen in between, etc. if the value read from memory is in a virtual register
 * (node).
 *
 * A trivial example for read elimination can be seen below:
 *
 * <pre>
 * int i = object.fieldValue;
 * // code not changing object.fieldValue but using i
 * consume(object.fieldValue);
 * </pre>
 *
 * Read elimination will transform this piece of code to the code below and remove the second,
 * unnecessary, memory read of the field:
 *
 * <pre>
 * int i = object.fieldValue;
 * // code not changing object.fieldValue but using i
 * consume(i);
 * </pre>
 */
public class ReadEliminationPhase extends EffectsPhase<CoreProviders> {

    protected final boolean considerGuards;

    public ReadEliminationPhase(CanonicalizerPhase canonicalizer) {
        super(1, canonicalizer, true);
        this.considerGuards = true;
    }

    public ReadEliminationPhase(CanonicalizerPhase canonicalizer, boolean considerGuards) {
        super(1, canonicalizer, true);
        this.considerGuards = considerGuards;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.when(graphState.isAfterStage(StageFlag.FLOATING_READS) && graphState.isBeforeStage(StageFlag.FIXED_READS),
                                        "This phase must not be applied while reads are floating"));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (VirtualUtil.matches(graph, EscapeAnalyzeOnly.getValue(graph.getOptions()))) {
            runAnalysis(graph, context);
        }
    }

    @Override
    protected Closure<?> createEffectsClosure(CoreProviders context, ScheduleResult schedule, ControlFlowGraph cfg) {
        assert schedule == null;
        return new ReadEliminationClosure(cfg, considerGuards);
    }

    @Override
    public float codeSizeIncrease() {
        return 2f;
    }
}

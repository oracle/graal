/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.virtual.phases.ea;

import static jdk.graal.compiler.core.common.GraalOptions.EscapeAnalyzeOnly;

import java.util.Optional;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase.CustomSimplification;
import jdk.graal.compiler.phases.common.FixReadsPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;

/**
 * A read elimination like the regular {@link ReadEliminationPhase} except it's designed to run in
 * low tier after {@link FixReadsPhase} and partial redundancy scheduling.
 *
 * Since after partial redundancy scheduling we must not apply GVN (since it would reverse partial
 * redundancy elimination) we explicitly allow the canonicalization ({@link CanonicalizerPhase}) of
 * {@link CompressionNode} and {@link AddressNode} because they are integral for late read
 * elimination.
 */
public class LowTierReadEliminationPhase extends EffectsPhase<LowTierContext> {
    private static final int MAX_LOW_TIER_READ_ELIMINATION_ITERATIONS = 2;

    protected final boolean considerGuards;

    public LowTierReadEliminationPhase(CanonicalizerPhase canonicalizer) {
        super(MAX_LOW_TIER_READ_ELIMINATION_ITERATIONS, canonicalizer.copyWithCustomSimplification(new CustomSimplification() {

            @Override
            public void simplify(Node node, SimplifierTool tool) {
                if (node instanceof CompressionNode || node instanceof AddressNode) {
                    NodeClass<?> nodeClass = node.getNodeClass();
                    CanonicalizerPhase.gvn(node, nodeClass);
                }
            }
        }), true);
        this.considerGuards = true;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.when(!graphState.isAfterStage(StageFlag.FIXED_READS), "Must be applied after fixing reads"));
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        if (VirtualUtil.matches(graph, EscapeAnalyzeOnly.getValue(graph.getOptions()))) {
            runAnalysis(graph, context);
        }
    }

    @Override
    protected Closure<?> createEffectsClosure(LowTierContext context, ScheduleResult schedule, ControlFlowGraph cfg, OptionValues options) {
        assert schedule == null;
        return new ReadEliminationClosure(cfg, considerGuards);
    }

    @Override
    public float codeSizeIncrease() {
        return 2f;

    }

    @Override
    protected void postIteration(final StructuredGraph graph, final LowTierContext context, EconomicSet<Node> changedNodes) {
        if (canonicalizer != null) {
            /*
             * Ensure we do the right amount of GVN for read elimination - that is all compression
             * nodes and address nodes hanging off any deleted reads in the first iteration.
             */
            NodeBitMap nbm = graph.createNodeBitMap();
            for (Node n : changedNodes) {
                if (n.isAlive()) {
                    nbm.mark(n);
                } else {
                    assert n.isDeleted() : "If the node is no longer alive it must be delted, not new " + n;
                    if (n instanceof ReadNode r) {
                        AddressNode adr = r.getAddress();
                        if (adr != null && adr.isAlive()) {
                            for (Node usage : adr.usages()) {
                                nbm.markAll(usage.usages());
                            }
                        }
                    }
                }
            }
            canonicalizer.applyIncremental(graph, context, nbm);
        }
    }

}

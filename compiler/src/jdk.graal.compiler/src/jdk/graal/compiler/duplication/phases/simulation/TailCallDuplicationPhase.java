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
package jdk.graal.compiler.duplication.phases.simulation;

import java.util.Optional;

import jdk.graal.compiler.duplication.util.DuplicationUtil;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.MultiReturnNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

/**
 * Splits small tails of opted-in bytecode handlers after final escape analysis, including conditional
 * regions feeding those tails. Reconsiders newly exposed merges after each duplication instead of
 * using a snapshot of simulated candidates.
 */
public class TailCallDuplicationPhase extends BasePhase<CoreProviders> {
    private static final int MAX_TAIL_SIZE = 32;
    private final CanonicalizerPhase canonicalizer;

    public TailCallDuplicationPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunAfter(this, StageFlag.FINAL_PARTIAL_ESCAPE, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.HIGH_TIER_LOWERING, graphState),
                        canonicalizer.notApplicableTo(graphState));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        int initialNodeCount = graph.getNodeCount();
        DuplicationUtil util = new DuplicationUtil(graph, GraphUtil.getDefaultSimplifier(context, canonicalizer.getCanonicalizeReads(), graph.getAssumptions(), graph.getOptions()));
        // Bound both graph growth and work even if canonicalization keeps exposing new tails.
        for (int remaining = initialNodeCount; remaining > 0 && graph.getNodeCount() < 2L * initialNodeCount; remaining--) {
            // Return metadata must stay with its return. Sharing it across successors would let
            // conditional duplication turn it into an ordinary value phi, losing the tail call.
            for (ReturnNode ret : graph.getNodes(ReturnNode.TYPE)) {
                if (ret.result() instanceof MultiReturnNode multiReturn && multiReturn.shouldEncourageTailDuplication() && !multiReturn.hasExactlyOneUsage()) {
                    ret.replaceFirstInput(multiReturn, multiReturn.copyWithInputs());
                }
            }
            boolean duplicated = false;
            for (ReturnNode ret : graph.getNodes(ReturnNode.TYPE)) {
                if (!(ret.result() instanceof MultiReturnNode multiReturn) || !multiReturn.shouldEncourageTailDuplication()) {
                    continue;
                }
                int size = 0;
                FixedNode regionEnd = ret;
                for (FixedNode node : GraphUtil.predecessorIterable(ret)) {
                    if (node instanceof ControlFlowAnchored) {
                        break;
                    }
                    if (node instanceof ControlSplitNode) {
                        // State-dependent checks can hide the merge feeding the dispatch tail.
                        // Split the nearest conditional region, then reconsider its returns.
                        if (!(node instanceof IfNode)) {
                            break;
                        }
                        regionEnd = node;
                    }
                    if (node instanceof AbstractMergeNode) {
                        if (node instanceof MergeNode merge && size < MAX_TAIL_SIZE && DuplicationUtil.findRegionEnd(merge, true, regionEnd == ret) == regionEnd) {
                            DuplicationUtil.DuplicationRegion region = regionEnd == ret
                                            ? new DuplicationUtil.SinkRegion(merge, ret, merge.forwardEndAt(0))
                                            : new DuplicationUtil.SplitRegion(merge, regionEnd, null, merge.forwardEndAt(0));
                            util.duplicate(merge, region, canonicalizer, context);
                            duplicated = true;
                        }
                        break;
                    }
                    size += node.estimatedNodeSize().value;
                }
                if (duplicated) {
                    break;
                }
            }
            if (!duplicated) {
                break;
            }
        }
    }
}

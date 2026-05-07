/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * A fixed node that represents a speculation fence in the graph.
 * <p>
 * This node distinguishes block-entry fences from immovable fences. Block-entry fences protect entry
 * into a guarded block, and canonicalization normalizes them to the start of that block. Immovable
 * fences model explicitly placed fences and must remain at their insertion point.
 *
 * <pre>
 * Block-entry fence:
 *
 * IfNode
 * someSuccessor:
 *     BeginNode
 *     SpeculationFenceNode(block entry)
 *     ... other nodes in block ...
 *
 * Immovable fence:
 *
 * BeginNode
 * ... other nodes ...
 * SpeculationFenceNode(immovable)
 * ... other nodes ...
 * </pre>
 *
 * {@link jdk.graal.compiler.phases.common.InsertGuardFencesPhase} inserts block-entry fences.
 * Canonicalization normalizes each one immediately after the nearest {@link AbstractBeginNode},
 * because the fence only needs to execute before any fixed node in the protected block. This keeps
 * the fence as early as possible in that block.
 * <p>
 * An immovable fence, such as a {@link #memoryBarrier()} intrinsic fence, may occur after other
 * fixed nodes in a block. It models a fence explicitly placed by a user at a particular program
 * point, so canonicalization does not normalize it to the block entry.
 * <p>
 * Older code stored block-entry fences as metadata on {@link AbstractBeginNode begin nodes}. That
 * made the fence vulnerable to graph rewrites that delete begin nodes, reduce trivial merges, remove
 * loop exits, or expand short-circuit logic into new control flow. Representing the fence as a
 * fixed control-flow node keeps the mitigation on the protected path across those rewrites.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_4, size = NodeSize.SIZE_4)
public class SpeculationFenceNode extends FixedWithNextNode implements LIRLowerable, Simplifiable, NodeWithIdentity {
    public static final NodeClass<SpeculationFenceNode> TYPE = NodeClass.create(SpeculationFenceNode.class);

    /**
     * True for fences that protect entry into a block. Canonicalization may move these fences to
     * the start of the same block. When false, this node is an immovable fence.
     */
    private final boolean blockEntryFence;

    /**
     * Creates an immovable speculation fence.
     */
    public SpeculationFenceNode() {
        this(false);
    }

    private SpeculationFenceNode(boolean blockEntryFence) {
        super(TYPE, StampFactory.forVoid());
        this.blockEntryFence = blockEntryFence;
    }

    /**
     * Creates a block-entry speculation fence.
     */
    public static SpeculationFenceNode forBlockEntry() {
        return new SpeculationFenceNode(true);
    }

    public boolean isBlockEntryFence() {
        return blockEntryFence;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (!blockEntryFence) {
            return;
        }

        /*
         * Keep block-entry fences immediately after the nearest begin. A block-entry fence protects
         * all control that enters that begin's block, so no fixed node in the block should execute
         * before the fence. If a rewrite leaves the fence later in a block, move it back to the
         * block entry.
         *
         * If a speculation fence already follows the begin, it protects this block entry before any
         * later fixed node can execute, so a second block-entry fence is redundant.
         */
        AbstractBeginNode begin = AbstractBeginNode.prevBegin(this);
        if (begin == null) {
            return;
        }

        FixedNode afterBegin = begin.next();
        if (afterBegin == this) {
            return;
        }
        if (afterBegin instanceof SpeculationFenceNode) {
            graph().removeFixed(this);
            return;
        }

        GraphUtil.unlinkFixedNode(this);
        graph().addAfterFixed(begin, this);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().emitSpeculationFence();
    }

    /**
     * Emits an immovable speculation fence at this program point.
     */
    @NodeIntrinsic
    public static native void memoryBarrier();
}

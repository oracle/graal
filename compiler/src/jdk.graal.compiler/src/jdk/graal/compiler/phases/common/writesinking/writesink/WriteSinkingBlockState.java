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

package jdk.graal.compiler.phases.common.writesinking.writesink;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ProfileData.LoopFrequencyData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheData;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheEntry;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CommitOrder;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteMergeProcessor;
import jdk.graal.compiler.phases.common.writesinking.data.WritesOrdering;

import jdk.vm.ci.meta.JavaKind;

/**
 * The state used during exploration of the graph for performing {@linkplain LoopWriteSinker write
 * sinking}. This class also handles the graph modifications that happen on registering a write,
 * and/or committing a write.
 */
public final class WriteSinkingBlockState
                extends MovableWriteMergeProcessor.DefaultProcessableState {
    private final StructuredGraph graph;
    private final ControlFlowGraph cfg;
    private final EconomicMap<LoopBeginNode, LoopFrequencyData> localLoopFrequencyData;

    /**
     * Creates an empty traversal state for the graph currently being rewritten.
     */
    public WriteSinkingBlockState(StructuredGraph graph, ControlFlowGraph cfg) {
        this.graph = graph;
        this.cfg = cfg;
        this.localLoopFrequencyData = cfg.getLocalLoopFrequencyData();
    }

    private WriteSinkingBlockState(WriteSinkingBlockState other) {
        this(other, other.data.cloneState());
    }

    private WriteSinkingBlockState(WriteSinkingBlockState other, MovableWriteState replacement) {
        super(replacement);
        this.graph = other.graph;
        this.cfg = other.cfg;
        this.localLoopFrequencyData = other.localLoopFrequencyData;
    }

    /**
     * Creates an independent copy for block-iterator branch processing.
     */
    public WriteSinkingBlockState cloneState() {
        return new WriteSinkingBlockState(this);
    }

    /**
     * Records the existence of the given write node in this state, and updates the state
     * accordingly. Also takes care of removing the write from the graph.
     *
     * @param entry The movable-write entry in which to put the generated cache data.
     * @param write The write node being registered.
     * @param value The value of the write.
     */
    @SuppressWarnings("try")
    public void registerWrite(CacheEntry entry, WriteNode write, ValueNode value) {
        DebugContext debug = this.graph.getDebug();
        try (DebugContext.Scope s = debug.scope("WriteSinking")) {
            debug.log(DebugContext.INFO_LEVEL, "Sinkable write node \"%s\" with value \"%s\"  and location %s is recorded and removed from loop ", write, value, write.getLocationIdentity());
        }

        addCacheEntry(entry, value, write);
        GraphUtil.unlinkFixedNode(write);
        assert write.hasNoUsages() : "Assert fixed node has no usage before deleting " + write;
        write.safeDelete();
    }

    /**
     * Given a {@link CommitOrder}, this method commits the sinking writes from this state at the
     * commit point. Writes are committed in the original per-location order; see
     * {@link WritesOrdering}.
     *
     * @param commitOrder The Order structure after collecting commit information.
     * @param commitPoint Where to insert committed writes.
     * @return true if at least one write was committed, false otherwise.
     */
    public boolean commitOrder(CommitOrder commitOrder, FixedNode commitPoint) {
        if (!commitOrder.isEmpty()) {
            assert commitOrder.getData() == data : "Commitorder data " + commitOrder.getData() + " must agree " + data;
            commitOrder.commit((key, cacheData) -> commit(key, commitPoint, cacheData));
            return true;
        }
        return false;
    }

    @SuppressWarnings("try")
    private void commit(CacheEntry entry, Node commitPoint, CacheData cacheData) {
        LocationIdentity id = entry.identity;
        assert commitPoint instanceof FixedNode : commitPoint;
        FixedNode fixedCommitPoint = (FixedNode) commitPoint;
        FixedWithNextNode toAdd;
        DebugContext debug = commitPoint.getDebug();
        boolean conditional = cacheData.isConditional();
        try (DebugCloseable position = commitPoint.withNodeSourcePosition()) {
            if (conditional) {
                toAdd = createConditionalWrite(cacheData, entry);
            } else {
                toAdd = new WriteNode(entry.address, id, cacheData.value, cacheData.barrier, MemoryOrderMode.PLAIN);
            }
        }
        HIRBlock commitBlock = cfg.blockFor(fixedCommitPoint);
        WriteSinkingDiagnostics.recordWriteInserted(debug, commitBlock, conditional);
        graph.addBeforeFixed(fixedCommitPoint, graph.add(toAdd));
    }

    private ConditionalWriteNode createConditionalWrite(CacheData cacheData, CacheEntry entry) {
        LoopBeginNode loopBegin = cacheData.conditionalMerge;

        // Create the boolean phi that will be used to determine if loop was taken
        ValueNode[] condPhiValues = new ValueNode[loopBegin.phiPredecessorCount()];
        condPhiValues[0] = ConstantNode.forBoolean(false, graph);
        for (int i = 1; i < condPhiValues.length; i++) {
            condPhiValues[i] = ConstantNode.forBoolean(true, graph);
        }
        PhiNode condPhi = graph.addOrUnique(new ValuePhiNode(StampFactory.forKind(JavaKind.Boolean), loopBegin, condPhiValues));

        // Create the condition and spawn profile information
        LogicNode logicNode = graph.addOrUnique(new IntegerEqualsNode(ConstantNode.forBoolean(true, graph), condPhi));
        ProfileData.BranchProbabilityData profile = ProfileData.BranchProbabilityData.create(1 - (1 / localLoopFrequencyData.get(loopBegin).getLoopFrequency()),
                        localLoopFrequencyData.get(loopBegin).getProfileSource());

        // Create the conditional write itself.
        return new ConditionalWriteNode(logicNode, entry.address, cacheData.value, entry.identity, cacheData.barrier, profile);
    }

    /**
     * Records a sunk write in the state.
     */
    public void addCacheEntry(CacheEntry identifier, ValueNode value, WriteNode write) {
        data.addEntry(identifier, value, write);
    }

    /**
     * Returns a compact debug representation of the movable writes.
     */
    @Override
    public String toString() {
        return data.toString();
    }

    /**
     * Returns {@code true} when this state has movable writes for {@code locationIdentity}.
     */
    public boolean containsLocation(LocationIdentity locationIdentity) {
        return data.containsLocation(locationIdentity);
    }
}

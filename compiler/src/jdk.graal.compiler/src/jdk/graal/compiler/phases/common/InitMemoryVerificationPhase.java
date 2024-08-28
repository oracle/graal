/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.graph.ReentrantNodeIterator;

public class InitMemoryVerificationPhase extends BasePhase<CoreProviders> {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifApplied(this, GraphState.StageFlag.LOW_TIER_LOWERING, graphState);
    }

    /*
     * Allocation: register the node
     * Memory kill to init: mark it
     * Memory access to init with unpublished kills: error
     * return with unpublished kills: error
     * publish writes: unregister the node.
     * store_store barrier: clear writes
     */

    private static final class AllocData {
        private final EconomicSet<AbstractNewObjectNode> unpublished;
        private int liveWrites;

        AllocData() {
            this.unpublished = EconomicSet.create();
            this.liveWrites = 0;
        }

        AllocData(AllocData copy) {
            this.unpublished = EconomicSet.create(copy.unpublished);
            this.liveWrites = copy.liveWrites;
        }

        private void register(AbstractNewObjectNode newObjectNode) {
            unpublished.add(newObjectNode);
        }

        private void merge(AllocData other) {
            this.unpublished.addAll(other.unpublished);
            this.liveWrites += other.liveWrites;
        }

        private boolean hasNoLiveKills() {
            return liveWrites == 0;
        }

        private boolean checkNoUnpublishedAllocs() {
            assert unpublished.isEmpty() : Assertions.errorMessageContext("unpublished allocs", unpublished);
            return true;
        }

        private void markWrite() {
            liveWrites++;
        }

        private void publish(AbstractNewObjectNode node) {
            assert unpublished.contains(node) : "trying to publish non registered alloc: " + node;
            unpublished.remove(node);
            liveWrites = 0;
        }

        private void membar() {
            liveWrites = 0;
        }
    }

    private static class InitWriteTestDataClosure extends ReentrantNodeIterator.NodeIteratorClosure<AllocData> {
        private void processAlloc(AbstractNewObjectNode allocation, AllocData state) {
            if (allocation.emitMemoryBarrier() && allocation.fillContents()) {
                // Will emit memory barrier upon lowering, can ignore for now
                return;
            }
            state.register(allocation);
        }

        private void processMemoryKill(SingleMemoryKill checkpoint, AllocData state) {
            processMemoryKill(checkpoint.getKilledLocationIdentity(), checkpoint, state);
        }

        private void processMemoryKill(MultiMemoryKill checkpoint, AllocData state) {
            for (LocationIdentity identity : checkpoint.getKilledLocationIdentities()) {
                processMemoryKill(identity, checkpoint, state);
            }
        }


        private void processMemoryKill(LocationIdentity identity, MemoryKill checkpoint, AllocData state) {
            if (!identity.isInit()) {
                // We're only concerned with init memory
                return;
            }
            state.markWrite();
        }

        private void processNonKillAccess(MemoryAccess access, AllocData state) {
            if (!access.getLocationIdentity().isInit()) {
                // We're only concerned with init memory
                return;
            }
            assert state.hasNoLiveKills() : "reading from init memory when there are live writes";
        }

        private void markPublished(ValueNode node, AllocData currentState) {
            if (node instanceof ValuePhiNode phi) {
                for (ValueNode value : phi.values()) {
                    markPublished(value, currentState);
                }
            } else if (node instanceof AbstractNewObjectNode newObject) {
                currentState.publish(newObject);
            }
        }

        private void processPublish(PublishWritesNode publish, AllocData currentState) {
            markPublished(publish.allocation(), currentState);
        }

        private void processBarrier(MembarNode membar, AllocData currentState) {
            if (!membar.getKilledLocationIdentity().isInit()) {
                return;
            }
            if (!membar.getFenceKind().equals(MembarNode.FenceKind.ALLOCATION_INIT)) {
                return;
            }
            currentState.membar();
        }

        @Override
        protected AllocData processNode(FixedNode node, AllocData currentState) {
            if (node instanceof AbstractNewObjectNode newObjectNode) {
                processAlloc(newObjectNode, currentState);
            } else if (MemoryKill.isSingleMemoryKill(node)) {
                processMemoryKill(MemoryKill.asSingleMemoryKill(node), currentState);
            } else if (MemoryKill.isMultiMemoryKill(node)) {
                processMemoryKill(MemoryKill.asMultiMemoryKill(node), currentState);
            } else if (node instanceof AddressableMemoryAccess access) {
                processNonKillAccess(access, currentState);
            } else if (node instanceof PublishWritesNode anchorNode) {
                processPublish(anchorNode, currentState);
            } else if (node instanceof MembarNode membar) {
                processBarrier(membar, currentState);
            } else if (node instanceof ReturnNode) {
                assert currentState.checkNoUnpublishedAllocs();
            }
            return currentState;
        }

        @Override
        protected AllocData merge(AbstractMergeNode merge, List<AllocData> states) {
            AllocData merged = new AllocData();
            for (AllocData state : states) {
                merged.merge(state);
            }
            return merged;
        }

        @Override
        protected AllocData afterSplit(AbstractBeginNode node, AllocData oldState) {
            return new AllocData(oldState);
        }

        @Override
        protected EconomicMap<LoopExitNode, AllocData> processLoop(LoopBeginNode loop, AllocData initialState) {
            ReentrantNodeIterator.LoopInfo<AllocData> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);
            return loopInfo.exitStates;
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        ReentrantNodeIterator.apply(new InitWriteTestDataClosure(), graph.start(), new AllocData());
    }

}

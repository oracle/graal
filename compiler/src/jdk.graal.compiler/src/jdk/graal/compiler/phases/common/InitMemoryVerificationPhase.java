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
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.RecursivePhase;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.phases.graph.ReentrantNodeIterator;
import jdk.vm.ci.code.MemoryBarriers;

/**
 * Verifies the following two invariants related to {@link LocationIdentity#INIT_LOCATION INIT}
 * memory in the graph:
 * <ul>
 * <li>Newly allocated objects are published by a {@link PublishWritesNode} <b>after</b> all
 * initializing writes have been performed. By having later uses of the allocated object depend on
 * the {@link PublishWritesNode}, we prevent these uses from floating above the initializing writes.
 * </li>
 * <li>A {@link MemoryBarriers#STORE_STORE STORE_STORE} barrier is emitted after all initializing
 * writes have been performed. This ensures all allocation initializations before this fence have
 * completed before the object can become visible to a different thread.</li>
 * </ul>
 * <p>
 * See {@link PublishWritesNode} for a description of init memory semantics in the Graal IR.
 */
public class InitMemoryVerificationPhase extends VerifyPhase<CoreProviders> implements RecursivePhase {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        /*
         * CommitAllocationNodes are lowered to raw allocations in mid tier, so it doesn't make
         * sense to verify this property beforehand.
         */
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunAfter(this, GraphState.StageFlag.MID_TIER_LOWERING, graphState),
                        NotApplicable.unlessRunBefore(this, GraphState.StageFlag.LOW_TIER_LOWERING, graphState));
    }

    /**
     * Verifies that all init writes are guarded by a {@link MemoryBarriers#STORE_STORE STORE_STORE}
     * barrier. Since we can't determine in a generic way which allocation is a given
     * {@link MemoryKill} writing to, we simply count the number of memory kills that are "live"
     * (not protected by a barrier) at any point in the control flow graph..
     */
    private static final class InitBarrierVerificationClosure extends ReentrantNodeIterator.NodeIteratorClosure<Integer> {
        @Override
        protected Integer processNode(FixedNode node, Integer kills) {
            int liveKills = kills;
            if (node instanceof MembarNode memBar) {
                if (memBar.getFenceKind().isInit()) {
                    liveKills = 0;
                } else if (liveKills > 0) {
                    throw new VerificationError("%s is a non-init barrier, but there are %d live init writes", memBar, liveKills);
                }
            } else if (MemoryKill.isMemoryKill(node) && ((MemoryKill) node).killsInit()) {
                // memory anchors don't actually perform any writes
                if (!(node instanceof MemoryAnchorNode)) {
                    liveKills++;
                }
            } else if (node instanceof ReturnNode && liveKills > 0) {
                throw new VerificationError("%d writes to init memory not guarded by an init barrier at node %s", liveKills, node);
            }
            return liveKills;
        }

        @Override
        protected Integer merge(AbstractMergeNode merge, List<Integer> states) {
            int sum = 0;
            for (int kills : states) {
                sum += kills;
            }
            return sum;
        }

        @Override
        protected Integer afterSplit(AbstractBeginNode node, Integer kills) {
            return kills;
        }

        @Override
        protected EconomicMap<LoopExitNode, Integer> processLoop(LoopBeginNode loop, Integer kills) {
            ReentrantNodeIterator.LoopInfo<Integer> loopInfo = ReentrantNodeIterator.processLoop(this, loop, kills);
            return loopInfo.exitStates;
        }
    }

    /**
     * Verifies that all {@link AbstractNewObjectNode AbstractNewObjectNodes} are published by a
     * {@link PublishWritesNode}.
     */
    private static final class AllocPublishVerificationClosure extends ReentrantNodeIterator.NodeIteratorClosure<EconomicSet<AbstractNewObjectNode>> {
        private static void processAlloc(AbstractNewObjectNode allocation, EconomicSet<AbstractNewObjectNode> unpublished) {
            if (allocation.emitMemoryBarrier() && allocation.fillContents()) {
                // Allocation was not lowered from a CommitAllocationNode, can ignore
                return;
            }
            unpublished.add(allocation);
        }

        private static void markPublished(ValueNode node, EconomicSet<AbstractNewObjectNode> unpublished) {
            if (node instanceof ValuePhiNode phi) {
                for (ValueNode value : phi.values()) {
                    markPublished(value, unpublished);
                }
            } else if (node instanceof AbstractNewObjectNode newObject) {
                unpublished.remove(newObject);
            }
        }

        @Override
        protected EconomicSet<AbstractNewObjectNode> processNode(FixedNode node, EconomicSet<AbstractNewObjectNode> unpublished) {
            if (node instanceof AbstractNewObjectNode allocation) {
                processAlloc(allocation, unpublished);
            } else if (node instanceof PublishWritesNode publish) {
                markPublished(publish.allocation(), unpublished);
            } else if (node instanceof ReturnNode && !unpublished.isEmpty()) {
                throw new VerificationError("unpublished allocations at node %s: %s", unpublished, node);
            }
            return unpublished;
        }

        @Override
        protected EconomicSet<AbstractNewObjectNode> merge(AbstractMergeNode merge, List<EconomicSet<AbstractNewObjectNode>> states) {
            EconomicSet<AbstractNewObjectNode> merged = EconomicSet.create();
            for (EconomicSet<AbstractNewObjectNode> state : states) {
                merged.addAll(state);
            }
            return merged;
        }

        @Override
        protected EconomicSet<AbstractNewObjectNode> afterSplit(AbstractBeginNode node, EconomicSet<AbstractNewObjectNode> oldState) {
            return EconomicSet.create(oldState);
        }

        @Override
        protected EconomicMap<LoopExitNode, EconomicSet<AbstractNewObjectNode>> processLoop(LoopBeginNode loop, EconomicSet<AbstractNewObjectNode> initialState) {
            ReentrantNodeIterator.LoopInfo<EconomicSet<AbstractNewObjectNode>> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);
            return loopInfo.exitStates;
        }
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        ReentrantNodeIterator.apply(new InitBarrierVerificationClosure(), graph.start(), 0);
        ReentrantNodeIterator.apply(new AllocPublishVerificationClosure(), graph.start(), EconomicSet.create());
    }
}

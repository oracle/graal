/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.phases.schedule;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;

public final class MemoryScheduleVerification extends BlockIteratorClosure<Set<FloatingReadNode>> {

    private final BlockMap<List<Node>> blockToNodesMap;

    public static boolean check(Block startBlock, BlockMap<List<Node>> blockToNodesMap) {
        ReentrantBlockIterator.apply(new MemoryScheduleVerification(blockToNodesMap), startBlock);
        return true;
    }

    private MemoryScheduleVerification(BlockMap<List<Node>> blockToNodesMap) {
        this.blockToNodesMap = blockToNodesMap;
    }

    @Override
    protected Set<FloatingReadNode> getInitialState() {
        return CollectionsFactory.newSet();
    }

    @Override
    protected Set<FloatingReadNode> processBlock(Block block, Set<FloatingReadNode> currentState) {
        AbstractBeginNode beginNode = block.getBeginNode();
        if (beginNode instanceof AbstractMergeNode) {
            AbstractMergeNode abstractMergeNode = (AbstractMergeNode) beginNode;
            for (PhiNode phi : abstractMergeNode.phis()) {
                if (phi instanceof MemoryPhiNode) {
                    MemoryPhiNode memoryPhiNode = (MemoryPhiNode) phi;
                    addFloatingReadUsages(currentState, memoryPhiNode);
                }
            }
        }
        for (Node n : blockToNodesMap.get(block)) {
            if (n instanceof MemoryCheckpoint) {
                if (n instanceof MemoryCheckpoint.Single) {
                    MemoryCheckpoint.Single single = (MemoryCheckpoint.Single) n;
                    processLocation(n, single.getLocationIdentity(), currentState);
                } else if (n instanceof MemoryCheckpoint.Multi) {
                    MemoryCheckpoint.Multi multi = (MemoryCheckpoint.Multi) n;
                    for (LocationIdentity location : multi.getLocationIdentities()) {
                        processLocation(n, location, currentState);
                    }
                }

                addFloatingReadUsages(currentState, n);
            } else if (n instanceof MemoryNode) {
                addFloatingReadUsages(currentState, n);
            } else if (n instanceof FloatingReadNode) {
                FloatingReadNode floatingReadNode = (FloatingReadNode) n;
                if (floatingReadNode.getLastLocationAccess() != null && floatingReadNode.getLocationIdentity().isMutable()) {
                    if (currentState.contains(floatingReadNode)) {
                        // Floating read was found in the state.
                        currentState.remove(floatingReadNode);
                    } else {
                        throw new RuntimeException("Floating read node " + n + " was not found in the state, i.e., it was killed by a memory check point before its place in the schedule. Block=" +
                                        block + ", block begin: " + block.getBeginNode() + " block loop: " + block.getLoop() + ", " + blockToNodesMap.get(block).get(0));
                    }
                }

            }
        }
        return currentState;
    }

    private static void addFloatingReadUsages(Set<FloatingReadNode> currentState, Node n) {
        for (FloatingReadNode read : n.usages().filter(FloatingReadNode.class)) {
            if (read.getLastLocationAccess() == n && read.getLocationIdentity().isMutable()) {
                currentState.add(read);
            }
        }
    }

    private void processLocation(Node n, LocationIdentity location, Set<FloatingReadNode> currentState) {
        assert n != null;
        if (location.isImmutable()) {
            return;
        }

        for (FloatingReadNode r : cloneState(currentState)) {
            if (r.getLocationIdentity().overlaps(location)) {
                // This read is killed by this location.
                currentState.remove(r);
            }
        }
    }

    @Override
    protected Set<FloatingReadNode> merge(Block merge, List<Set<FloatingReadNode>> states) {
        Set<FloatingReadNode> result = states.get(0);
        for (int i = 1; i < states.size(); ++i) {
            result.retainAll(states.get(i));
        }
        return result;
    }

    @Override
    protected Set<FloatingReadNode> cloneState(Set<FloatingReadNode> oldState) {
        Set<FloatingReadNode> result = CollectionsFactory.newSet();
        result.addAll(oldState);
        return result;
    }

    @Override
    protected List<Set<FloatingReadNode>> processLoop(Loop<Block> loop, Set<FloatingReadNode> initialState) {
        HIRLoop l = (HIRLoop) loop;
        for (MemoryPhiNode memoryPhi : ((LoopBeginNode) l.getHeader().getBeginNode()).phis().filter(MemoryPhiNode.class)) {
            for (FloatingReadNode r : cloneState(initialState)) {
                if (r.getLocationIdentity().overlaps(memoryPhi.getLocationIdentity())) {
                    initialState.remove(r);
                }
            }
        }
        return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
    }
}

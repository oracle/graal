/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.vectorization;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.tiers.LowTierContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class BlockInfo {

    private final Block block;
    private final LowTierContext context;
    private final NodeView view;

    private final NodeMap<Block> nodeToBlockMap;

    private final NodeMap<Integer> depthMap;

    BlockInfo(StructuredGraph graph, Block block, LowTierContext context, NodeView view) {
        this.block = block;
        this.context = context;
        this.view = view;

        this.nodeToBlockMap = graph.getLastSchedule().getNodeToBlockMap();

        this.depthMap = new NodeMap<>(graph);
    }

    // Isomorphism Check

    /**
     * Check whether the left and right node of a potential pack are isomorphic.
     * "Isomorphic statements are those that contain the same operations in the same order."
     *
     * @param left Left node of the potential pack
     * @param right Right node of the potential pack
     * @return Boolean indicating whether the left and right node of a potential pack are
     *         isomorphic.
     */
    boolean isomorphic(ValueNode left, ValueNode right) {
        // Trivial case, isomorphic if the same
        if (left == right || left.equals(right)) {
            return true;
        }

        // Are left & right the same action?
        if (!left.getNodeClass().equals(right.getNodeClass())) {
            return false;
        }

        // Is the input count the same? (accounts for inputs that are null)
        if (left.inputs().count() != right.inputs().count()) {
            return false;
        }

        // Ensure that both nodes have compatible stamps
        if (!Util.getStamp(left, view).isCompatible(Util.getStamp(right, view))) {
            return false;
        }

        // Conservatively bail if we have a FAN and non-FAN
        if (left instanceof FixedAccessNode != right instanceof FixedAccessNode) {
            return false;
        }

        // Ensure that both fixed access nodes are accessing the same array
        if (left instanceof FixedAccessNode && !sameBaseAddress(left, right)) {
            return false;
        }

        return true;
    }

    // Adjacency Check

    /**
     * Ensure that there is no data path between left and right. This version operates on Nodes,
     * avoiding the need to check for FAN at the callsite. Pre: left and right are isomorphic
     *
     * @param left Left node of the potential pack
     * @param right Right node of the potential pack
     * @return Are the two statements independent? Only independent statements may be packed.
     */
    boolean adjacent(Node left, Node right) {
        return left instanceof FixedAccessNode &&
                right instanceof FixedAccessNode &&
                adjacent((FixedAccessNode) left, (FixedAccessNode) right);
    }

    /**
     * Check whether s1 is immediately before s2 in memory, if both are primitive.
     *
     * @param s1 First FixedAccessNode to check
     * @param s2 Second FixedAccessNode to check
     * @return Boolean indicating whether s1 is immediately before s2 in memory
     */
    private boolean adjacent(FixedAccessNode s1, FixedAccessNode s2) {
        return adjacent(s1, s2, Util.getStamp(s1, view), Util.getStamp(s2, view));
    }

    /**
     * Check whether s1 is immediately before s2 in memory, if both are primitive. This function
     * exists as not all nodes carry the right kind information. TODO: Find a better way to deal
     * with this
     *
     * @param s1 First FixedAccessNode to check
     * @param s2 Second FixedAccessNode to check
     * @param s1s Stamp of the first FixedAccessNode
     * @param s2s Stamp of the second FixedAccessNode
     * @return Boolean indicating whether s1 is immediately before s2 in memory
     */
    private boolean adjacent(FixedAccessNode s1, FixedAccessNode s2, Stamp s1s, Stamp s2s) {
        final AddressNode s1a = s1.getAddress();
        final AddressNode s2a = s2.getAddress();

        final JavaKind s1k = s1s.javaType(context.getMetaAccess()).getJavaKind();
        final JavaKind s2k = s2s.javaType(context.getMetaAccess()).getJavaKind();

        // Only use superword on primitives
        if (!s1k.isPrimitive() || !s2k.isPrimitive()) {
            return false;
        }

        // Only use superword on nodes in the current block
        if (notInBlock(s1) || notInBlock(s2)) {
            return false;
        }

        // Only use superword on types that are comparable
        if (s1a.getBase() != null && s2a.getBase() != null && !s1a.getBase().equals(s2a.getBase())) {
            return false;
        }

        // Ensure induction variables are the same
        if (!getInductionVariables(s1a).equals(getInductionVariables(s2a))) {
            return false;
        }

        return s2a.getMaxConstantDisplacement() - s1a.getMaxConstantDisplacement() == s1k.getByteCount();
    }

    /**
     * Determine whether two nodes are accesses with the same base address.
     * @param left Left access node
     * @param right Right access node
     * @return Boolean indicating whether base is the same.
     *         If nodes are not access nodes, this is false.
     */
    static boolean sameBaseAddress(Node left, Node right) {
        if (!(left instanceof FixedAccessNode) || !(right instanceof FixedAccessNode)) {
            return false;
        }

        final AddressNode leftAddress = ((FixedAccessNode) left).getAddress();
        final AddressNode rightAddress = ((FixedAccessNode) right).getAddress();

        if (leftAddress.getBase() == null  || rightAddress.getBase() == null) {
            return false;
        }

        if (!leftAddress.getBase().equals(rightAddress.getBase())) {
            return false;
        }

        return true;
    }

    private static Set<Node> getInductionVariables(AddressNode address) {
        final Set<Node> ivs = new HashSet<>();
        final Deque<Node> bfs = new ArrayDeque<>();
        bfs.add(address);

        while (!bfs.isEmpty()) {
            final Node node = bfs.remove();
            if (node instanceof AddressNode) {
                final AddressNode addressNode = (AddressNode) node;
                if (addressNode.getIndex() != null) {
                    bfs.add(addressNode.getIndex());
                }
            } else if (node instanceof AddNode) {
                final AddNode addNode = (AddNode) node;
                bfs.add(addNode.getX());
                bfs.add(addNode.getY());
            } else if (node instanceof LeftShiftNode) {
                final LeftShiftNode leftShiftNode = (LeftShiftNode) node;
                bfs.add(leftShiftNode.getX());
                bfs.add(leftShiftNode.getY());
            } else if (node instanceof SignExtendNode) {
                final SignExtendNode signExtendNode = (SignExtendNode) node;
                bfs.add(signExtendNode.getValue());
            } else if (node instanceof ConstantNode) {
                // constant nodes are leaf nodes
            } else {
                ivs.add(node);
            }
        }

        return ivs;
    }

    // Independence Check

    /**
     * Ensure that there is no data path between left and right. Pre: left and right are
     * isomorphic
     *
     * @param left Left node of the potential pack
     * @param right Right node of the potential pack
     * @return Are the two statements independent? Only independent statements may be packed.
     */
    boolean independent(Node left, Node right) {
        // Calculate depth from how far into block.getNodes() we are.
        final int leftDepth = findDepth(left);
        final int rightDepth = findDepth(right);

        if (leftDepth == rightDepth) {
            return !left.equals(right);
        }

        final int shallowDepth = Math.min(leftDepth, rightDepth);
        final Node deep = leftDepth == shallowDepth ? right : left;
        final Node shallow = leftDepth == shallowDepth ? left : right;

        // Ensure that there is no membar between these two nodes
        final Set<MembarNode> membars = new HashSet<>();
        int membarCount = 0;

        for (Node shallowSucc : shallow.cfgSuccessors()) {
            if (shallowSucc instanceof MembarNode) {
                membars.add((MembarNode) shallowSucc);
                membarCount++;
            }
        }

        for (Node deepPred : deep.cfgPredecessors()) {
            if (deepPred instanceof MembarNode) {
                membars.add((MembarNode) deepPred);
                membarCount++;
            }
        }

        if (membarCount != membars.size()) {
            // The total number of membars != cardinality of membar set, so there is overlap
            return false;
        }

        return hasNoPath(shallow, deep);
    }

    private int findDepth(Node node) {
        Integer depth = depthMap.get(node);
        if (depth == null) {
            depth = findDepthImpl(node);
            depthMap.put(node, depth);
        }

        return depth;
    }

    private int findDepthImpl(Node node) {
        int depth = 0;
        for (Node current : block.getNodes()) {
            if (current.equals(node)) {
                return depth;
            }
            depth++;
        }

        return -1;
    }

    private boolean hasNoPath(Node shallow, Node deep) {
        return hasNoPath(shallow, deep, 0);
    }

    private boolean hasNoPath(Node shallow, Node deep, int iterationDepth) {
        if (iterationDepth >= 1000) {
            return false; // Stop infinite/deep recursion
        }

        final int shallowDepth = findDepth(shallow);

        for (Node pred : deep.inputs()) {
            if (notInBlock(pred)) { // ensure that the predecessor is in the block
                continue;
            }

            if (shallow == pred) {
                return false;
            }

            if (shallowDepth < findDepth(pred) && !hasNoPath(shallow, pred, iterationDepth + 1)) {
                return false;
            }
        }

        return true;
    }

    // Misc Block Utilities

    /**
     * Check whether the node is not in the current basic block.
     * @param node Node to check the block membership of.
     * @return True if the provided node is not in the current basic block.
     */
    boolean notInBlock(Node node) {
        return nodeToBlockMap.get(node) != block;
    }

    boolean inBlock(Node node) {
        return !notInBlock(node);
    }

    public Block getBlock() {
        return block;
    }
}

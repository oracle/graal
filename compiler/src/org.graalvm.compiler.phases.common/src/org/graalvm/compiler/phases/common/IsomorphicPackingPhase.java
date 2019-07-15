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
package org.graalvm.compiler.phases.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.VectorPrimitiveStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.VectorSupport.VectorExtractNode;
import org.graalvm.compiler.nodes.VectorSupport.VectorPackNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.FloatDivNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.LIRLowerableAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.VectorReadNode;
import org.graalvm.compiler.nodes.memory.VectorWriteNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.util.Pack;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.LowTierContext;

import jdk.vm.ci.meta.JavaKind;

/**
 * A phase to identify isomorphisms within basic blocks.
 * MIT (basis)  http://groups.csail.mit.edu/cag/slp/SLP-PLDI-2000.pdf
 * GCC          https://ols.fedoraproject.org/GCC/Reprints-2007/rosen-reprint.pdf
 * INTEL        https://people.apache.org/~xli/papers/npc10_java_vectorization.pdf
 */
public final class IsomorphicPackingPhase extends BasePhase<LowTierContext> {

    private static final int ALIGNMENT_BOTTOM = -666;
    private static final Set<Class<? extends Node>> supportedNodes = new HashSet<>(Stream.of(
                    WriteNode.class,
                    ReadNode.class,
                    AddNode.class,
                    SubNode.class,
                    MulNode.class,
                    AndNode.class,
                    OrNode.class,
                    XorNode.class,
                    NegateNode.class,
                    FloatDivNode.class).collect(Collectors.toSet()));

    // Class to encapsulate state used by functions in the algorithm
    private static final class Instance {
        private final LowTierContext context;
        private final NodeMap<Block> nodeToBlockMap;
        private final BlockMap<List<Node>> blockToNodesMap;
        private final Block currentBlock;
        private final NodeView view;

        private final Map<Node, Integer> alignmentMap = new HashMap<>();

        private Instance(LowTierContext context, NodeMap<Block> nodeToBlockMap, BlockMap<List<Node>> blockToNodesMap, Block currentBlock, NodeView view) {
            this.context = context;
            this.nodeToBlockMap = nodeToBlockMap;
            this.blockToNodesMap = blockToNodesMap;
            this.currentBlock = currentBlock;
            this.view = view;
        }

        // Utilities

        /**
         * Check whether the node is not in the current basic block.
         * @param node Node to check the block membership of.
         * @return True if the provided node is not in the current basic block.
         */
        private boolean notInBlock(Node node) {
            return nodeToBlockMap.get(node) != currentBlock;
        }

        private int dataSize(Node node) {
            if (node instanceof LIRLowerableAccess) {
                return dataSize((LIRLowerableAccess) node);
            }

            if (node instanceof ValueNode) {
                return dataSize((ValueNode) node);
            }

            return -1;
        }

        private int dataSize(ValueNode node) {
            return dataSize(node.stamp(view));
        }

        private int dataSize(LIRLowerableAccess node) {
            return dataSize(node.getAccessStamp());
        }

        private int dataSize(Stamp stamp) {
            return stamp.javaType(context.getMetaAccess()).getJavaKind().getByteCount();
        }

        /**
         * Get the alignment of a vector memory reference.
         */
        private <T extends FixedAccessNode & LIRLowerableAccess> int memoryAlignment(T access, int ivAdjust) {
            final Stamp stamp = access.getAccessStamp();
            final JavaKind accessJavaKind = stamp.javaType(context.getMetaAccess()).getJavaKind();
            final int byteCount = dataSize(stamp);
            // TODO: velt may be different to type at address
            final int vectorWidthInBytes = byteCount * context.getTargetProvider().getVectorDescription().maxVectorWidth(stamp);

            // If each vector element is less than 2 bytes, no need to vectorize
            if (vectorWidthInBytes < 2) {
                return ALIGNMENT_BOTTOM;
            }

            final int baseOffset = context.getMetaAccess().getArrayBaseOffset(accessJavaKind);
            final int offset = (int) access.getAddress().getMaxConstantDisplacement() + ivAdjust * byteCount;

            // Access is to the array header rather than elements of the array
            if (offset < baseOffset) {
                return ALIGNMENT_BOTTOM;
            }

            final int offsetRemainder = (offset - baseOffset) % vectorWidthInBytes;
            return offsetRemainder >= 0 ? offsetRemainder : offsetRemainder + vectorWidthInBytes;
        }

        private void setAlignment(Node s1, Node s2, int align) {
            setAlignment(s1, align);
            if (align == ALIGNMENT_BOTTOM) {
                setAlignment(s2, align);
            } else {
                setAlignment(s2, align + dataSize(s1));
            }
        }

        private void setAlignment(Node node, int alignment) {
            alignmentMap.put(node, alignment);
        }

        private Optional<Integer> getAlignment(Node node) {
            return Optional.ofNullable(alignmentMap.get(node));
        }

        /**
         * Predicate to determine whether a specific node is supported for
         * vectorization, based on its type.
         *
         * @param node Vectorization candidate.
         * @return Whether this is a supported node.
         */
        private boolean supported(Node node) {
            return supportedNodes.contains(node.getClass());
        }

        /**
         * Determine whether two nodes are accesses to the same array.
         * @param left Left access node
         * @param right Right access node
         * @return Boolean indicating whether same array.
         *         If nodes are not access nodes, this is false.
         */
        private static boolean sameArray(Node left, Node right) {
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

        /**
         * Check whether the left and right node of a potential pack are isomorphic.
         * "Isomorphic statements are those that contain the same operations in the same order."
         *
         * @param left Left node of the potential pack
         * @param right Right node of the potential pack
         * @return Boolean indicating whether the left and right node of a potential pack are
         *         isomorphic.
         */
        private boolean isomorphic(Node left, Node right) {
            // Trivial case, isomorphic if the same
            if (left == right || left.equals(right)) {
                return true;
            }

            // Are left & right the same action?
            if (!left.getNodeClass().equals(right.getNodeClass())) {
                return false; // TODO: support subclasses
            }

            // Is the input count the same? (accounts for inputs that are null)
            if (left.inputs().count() != right.inputs().count()) {
                return false;
            }

            // Conservatively bail if we have a FAN and non-FAN
            if (left instanceof FixedAccessNode != right instanceof FixedAccessNode) {
                return false;
            }

            // Ensure that both fixed access nodes are accessing the same array
            if (!sameArray(left, right)) {
                return false;
            }

            return true;
        }

        private int findDepth(Node node) {
            int depth = 0;
            for (Node current : currentBlock.getNodes()) {
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

            // TODO: pre-compute depth information
            // TODO: calculate depth information to avoid recursing too far, doing unnecessary calculations
            // TODO: verify that at this stage it's even possible to get dependency cycles

            for (Node pred : deep.inputs()) {
                if (notInBlock(pred)) { // ensure that the predecessor is in the block
                    continue;
                }

                if (shallow == pred) {
                    return false;
                }

                if (/* pred is below shallow && */ !hasNoPath(shallow, pred, iterationDepth + 1)) {
                    return false;
                }
            }

            return true;
        }

        /**
         * Ensure that there is no data path between left and right. Pre: left and right are
         * isomorphic
         *
         * @param left Left node of the potential pack
         * @param right Right node of the potential pack
         * @return Are the two statements independent? Only independent statements may be packed.
         */
        private boolean independent(Node left, Node right) {
            // Calculate depth from how far into block.getNodes() we are. TODO: this is a hack.
            final int leftDepth = findDepth(left);
            final int rightDepth = findDepth(right);

            if (leftDepth == rightDepth) {
                return !left.equals(right);
            }

            int shallowDepth = Math.min(leftDepth, rightDepth);
            Node deep = leftDepth == shallowDepth ? right : left;
            Node shallow = leftDepth == shallowDepth ? left : right;

            return hasNoPath(shallow, deep);
        }

        private Stamp getStamp(ValueNode node) {
            return (node instanceof WriteNode ? ((WriteNode) node).value() : node).stamp(view);
        }

        /**
         * Ensure that there is no data path between left and right. This version operates on Nodes,
         * avoiding the need to check for FAN at the callsite. Pre: left and right are isomorphic
         *
         * @param left Left node of the potential pack
         * @param right Right node of the potential pack
         * @return Are the two statements independent? Only independent statements may be packed.
         */
        private boolean adjacent(Node left, Node right) {
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
            return adjacent(s1, s2, getStamp(s1), getStamp(s2));
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
            // TODO: Evaluate whether graph guarantees that pointers for same collection have same base
            if (s1a.getBase() != null && s2a.getBase() != null && !s1a.getBase().equals(s2a.getBase())) {
                return false;
            }

            return s2a.getMaxConstantDisplacement() - s1a.getMaxConstantDisplacement() == s1k.getByteCount();
        }

        private boolean stmtsCanPack(Set<Pair<Node, Node>> packSet, Node s1, Node s2, int align) {
            // TODO: Also make sure that the platform supports vectors of the primitive type of this candidate pack

            if (supported(s1) && supported(s2) &&
                isomorphic(s1, s2) && independent(s1, s2) &&
                packSet.stream().noneMatch(p -> p.getLeft().equals(s1) || p.getRight().equals(s2))) {
                final Optional<Integer> alignS1 = getAlignment(s1);
                final Optional<Integer> alignS2 = getAlignment(s2);

                final int s2DataSize = dataSize(s2);
                boolean offset = false;
                if (s2DataSize >= 0) {
                    offset = alignS2.map(v -> v == s2DataSize + align).orElse(false);
                }

                return (!alignS1.isPresent() || alignS1.get().equals(align)) &&
                        (!alignS2.isPresent() || offset);
            }

            return false;
        }

        /**
         * Extend the packset by visiting operand definitions of nodes inside the provided pack.
         * @param packSet Pack set.
         * @param pack The pack to use for operand definitions.
         */
        private boolean followUseDefs(Set<Pair<Node, Node>> packSet, Pair<Node, Node> pack) {
            final Node left = pack.getLeft();
            final Node right = pack.getRight();
            final Optional<Integer> align = getAlignment(left);

            // TODO: bail if left is load (why?)

            boolean changed = false;

            outer: // labelled outer loop so that hasNext check is performed for left
            for (Iterator<Node> leftInputIt = left.inputs().iterator(); leftInputIt.hasNext();) {
                for (Iterator<Node> rightInputIt = right.inputs().iterator(); leftInputIt.hasNext() && rightInputIt.hasNext();) {
                    // Incrementing both iterators at the same time, so looking at the same input
                    // always
                    final Node leftInput = leftInputIt.next();
                    final Node rightInput = rightInputIt.next();

                    // Check block membership, bail if nodes not in block (prevent analysis beyond block)
                    if (notInBlock(leftInput) || notInBlock(rightInput)) {
                        continue outer;
                    }

                    // If the statements cannot be packed, bail
                    if (!align.isPresent() || !stmtsCanPack(packSet, leftInput, rightInput, align.get())) {
                        continue outer;
                    }

                    // If there are no savings to be gained, bail
                    // NB: here this is basically useless, as <s>our</s> the C2 formula does not allow for negative savings
                    if (estSavings(packSet, leftInput, rightInput) < 0) {
                        continue outer;
                    }

                    changed |= packSet.add(Pair.create(leftInput, rightInput));
                    setAlignment(left, right, align.get());
                }
            }

            return changed;
        }

        /**
         * Extend the packset by visiting uses of nodes in the provided pack.
         * @param packSet Pack set.
         * @param pack The pack to use for nodes to find usages of.
         */
        private boolean followDefUses(Set<Pair<Node, Node>> packSet, Pair<Node, Node> pack) {
            final Node left = pack.getLeft();
            final Node right = pack.getRight();
            final Optional<Integer> align = getAlignment(left);

            int savings = -1;
            Pair<Node, Node> bestPack = null;

            // TODO: bail if left is store (why?)

            for (Node leftUsage : left.usages()) {
                if (notInBlock(left)) {
                    continue;
                }

                for (Node rightUsage : right.usages()) {
                    if (leftUsage == rightUsage || notInBlock(right)) {
                        continue;
                    }

                    // TODO: Rather than adding the first, add the best
                    if (!align.isPresent() || stmtsCanPack(packSet, leftUsage, rightUsage, align.get())) {
                        final int currentSavings = estSavings(packSet, leftUsage, rightUsage);
                        if (currentSavings > savings) {
                            savings = currentSavings;
                            bestPack = Pair.create(leftUsage, rightUsage);
                        }
                    }
                }
            }

            if (savings >= 0) {
                if (align.isPresent()) {
                    setAlignment(bestPack.getLeft(), bestPack.getRight(), align.get());
                } else {
                    alignmentMap.remove(bestPack.getLeft());
                    alignmentMap.remove(bestPack.getRight());
                }

                return packSet.add(bestPack);
            }

            return false;
        }

        /**
         * Estimate the savings of executing the pack rather than two separate instructions.
         *
         * @param s1 Candidate left element of Pack
         * @param s2 Candidate right element of Pack
         * @param packSet PackSet, for membership checks
         * @return Savings in an arbitrary unit and can be negative.
         */
        private int estSavings(Set<Pair<Node, Node>> packSet, Node s1, Node s2) {
            // Savings originating from inputs
            int saveIn = 1; // Save 1 instruction as executing 2 in parallel.

            outer: // labelled outer loop so that hasNext check is performed for left
            for (Iterator<Node> leftInputIt = s1.inputs().iterator(); leftInputIt.hasNext();) {
                for (Iterator<Node> rightInputIt = s2.inputs().iterator(); leftInputIt.hasNext() && rightInputIt.hasNext();) {
                    final Node leftInput = leftInputIt.next();
                    final Node rightInput = rightInputIt.next();

                    if (leftInput == rightInput) {
                        continue outer;
                    }

                    if (adjacent(leftInput, rightInput)) {
                        // Inputs are adjacent in memory, this is good.
                        saveIn += 2; // Not necessarily packed, but good because packing is easy.
                    } else if (packSet.contains(Pair.create(leftInput, rightInput))) {
                        saveIn += 2; // Inputs already packed, so we don't need to pack these.
                    } else {
                        saveIn -= 2; // Not adjacent, not packed. Inputs need to be packed in a
                                     // vector for candidate.
                    }
                }
            }

            // Savings originating from result
            int ct = 0; // the number of usages that are packed
            int saveUse = 0;
            for (Node s1Usage : s1.usages()) {
                for (Pair<Node, Node> pack : packSet) {
                    if (pack.getLeft() != s1Usage) {
                        continue;
                    }

                    for (Node s2Usage : s2.usages()) {
                        if (pack.getRight() != s2Usage) {
                            continue;
                        }

                        ct++;

                        if (adjacent(s1Usage, s2Usage)) {
                            saveUse += 2;
                        }
                    }
                }
            }

            // idk, c2 does this though
            if (ct < s1.getUsageCount()) {
                saveUse += 1;
            }
            if (ct < s2.getUsageCount()) {
                saveUse += 1;
            }

            // TODO: investigate this formula - can't have negative savings
            return Math.max(saveIn, saveUse);
        }

        // Core

        /**
         * Create the initial seed packSet of operations that are adjacent.
         * TODO: CHECK THAT VECTOR ELEMENT TYPE IS THE SAME
         * @param packSet PackSet to populate.
         */
        private void findAdjRefs(Set<Pair<Node, Node>> packSet) {
            // Create initial seed set containing memory operations
            List<FixedAccessNode> memoryNodes = // Candidate list of memory nodes
                    StreamSupport.stream(currentBlock.getNodes().spliterator(), false).
                            filter(x -> x instanceof FixedAccessNode && x instanceof LIRLowerableAccess).
                            map(x -> (FixedAccessNode & LIRLowerableAccess) x).
                            // using stack kind here as this works with MetaspacePointerStamp and others
                            filter(x -> getStamp(x).getStackKind().isPrimitive() && memoryAlignment(x, 0) != ALIGNMENT_BOTTOM).
                            collect(Collectors.toList());

            // TODO: Align relative to best reference rather than setting alignment for all
            for (FixedAccessNode node : memoryNodes) {
                setAlignment(node, memoryAlignment((FixedAccessNode & LIRLowerableAccess) node, 0));
            }

            for (FixedAccessNode s1 : memoryNodes) {
                for (FixedAccessNode s2 : memoryNodes) {
                    if (adjacent(s1, s2)) {
                        final Optional<Integer> alignS1 = getAlignment(s1);
                        if (alignS1.isPresent() && stmtsCanPack(packSet, s1, s2, alignS1.get())) {
                            packSet.add(Pair.create(s1, s2));
                        }
                    }
                }
            }
        }

        /**
         * Extend the packset by following use->def and def->use links from pack members until the set does not change.
         * @param packSet PackSet to populate.
         */
        private void extendPacklist(Set<Pair<Node, Node>> packSet) {
            Set<Pair<Node, Node>> iterationPackSet;

            boolean changed;
            do {
                changed = false;
                iterationPackSet = new HashSet<>(packSet);

                for (Pair<Node, Node> pack : iterationPackSet) {
                    changed |= followUseDefs(packSet, pack);
                    changed |= followDefUses(packSet, pack);
                }
            } while (changed);
        }

        // Combine packs where right = left
        private void combinePacks(Set<Pair<Node, Node>> packSet, Set<Pack<Node>> combinedPackSet) {
            combinedPackSet.addAll(packSet.stream().map(Pack::create).collect(Collectors.toList()));
            packSet.clear();

            boolean changed;
            do {
                changed = false;

                Deque<Pack<Node>> remove = new ArrayDeque<>();
                Deque<Pack<Node>> add = new ArrayDeque<>();

                for (Pack<Node> leftPack : combinedPackSet) {
                    if (remove.contains(leftPack)) {
                        continue;
                    }

                    for (Pack<Node> rightPack : combinedPackSet) {
                        if (remove.contains(leftPack) || remove.contains(rightPack)) {
                            continue;
                        }


                        if (leftPack != rightPack && leftPack.getLast().equals(rightPack.getFirst())) {
                            remove.push(leftPack);
                            remove.push(rightPack);

                            add.push(Pack.combine(leftPack, rightPack));
                            changed = true;
                        }
                    }
                }

                combinedPackSet.removeAll(remove);
                combinedPackSet.addAll(add);
            } while (changed);
        }

        /**
         * Have all of the dependencies of node been scheduled? Filters inputs, removing blocks that
         * are not in the current block as well as Begin/End/Return.
         */
        private boolean depsScheduled(Node node, List<Node> scheduled, boolean considerControlFlow) {
            return StreamSupport.stream(node.inputs().spliterator(), false).
                    filter(n -> !nodeToBlockMap.isNew(n) && nodeToBlockMap.get(n) == currentBlock).
                    allMatch(scheduled::contains) &&
                    // AND have all the control flow dependencies been scheduled? (only if considering CF)
                    (!considerControlFlow || StreamSupport.stream(node.cfgPredecessors().spliterator(), false).
                            filter(n -> nodeToBlockMap.get(n) == currentBlock).
                            allMatch(scheduled::contains)
                    );
        }

        private Pack<Node> earliestUnscheduled(List<Node> unscheduled, Map<Node, Pack<Node>> nodeToPackMap) {
            for (Node node : unscheduled) {
                if (nodeToPackMap.containsKey(node)) {
                    return nodeToPackMap.get(node);
                }
            }

            return null;
        }

        private void schedule(List<Node> unscheduled, Set<Pack<Node>> packSet) {
            final List<Node> scheduled = new ArrayList<>();
            final Deque<FixedNode> lastFixed = new ArrayDeque<>();

            // Populate a nodeToPackMap
            final Map<Node, Pack<Node>> nodeToPackMap = packSet.stream().
                    flatMap(pack -> pack.getElements().stream().map(node -> Pair.create(node, pack))).
                    collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

            final List<Runnable> deferred = new ArrayList<>();

            // While unscheduled isn't empty
            outer: while (!unscheduled.isEmpty()) {
                for (Node node : unscheduled) {
                    // Node is in some pack p
                    if (nodeToPackMap.containsKey(node)) {
                        final Pack<Node> pack = nodeToPackMap.get(node);
                        // Have the dependencies of statements in the pack been scheduled?

                        // Use control flow of earliest node in chain
                        final Node firstInCFPack = unscheduled.stream().filter(x -> pack.getElements().contains(x)).findFirst().get();

                        if (pack.getElements().stream().allMatch(n -> depsScheduled(n, scheduled, n == firstInCFPack))) {
                            // Remove statements from unscheduled
                            scheduled.addAll(pack.getElements());
                            unscheduled.removeAll(pack.getElements());
                            deferred.add(() -> schedulePack(pack, lastFixed));
                            continue outer;
                        }
                    } else if (depsScheduled(node, scheduled, true)) {
                        // Remove statement from unscheduled and schedule
                        scheduled.add(node);
                        unscheduled.remove(node);
                        deferred.add(() -> scheduleStmt(node, lastFixed));
                        continue outer;
                    }
                }

                // We only reach here if there is a grouped statement dependency violation.
                // These are broken by removing one of the packs.
                Pack<Node> packToRemove = earliestUnscheduled(unscheduled, nodeToPackMap);
                assert packToRemove != null : "there no are unscheduled statements in packs";
                for (Node packNode : packToRemove.getElements()) {
                    nodeToPackMap.remove(packNode); // Remove all references for these statements to
                                                    // pack
                }
            }

            for (Runnable runnable : deferred) {
                runnable.run();
            }
        }

        private void schedulePack(Pack<Node> pack, Deque<FixedNode> lastFixed) {
            final Node first = pack.getElements().get(0);
            final Graph graph = first.graph();

            if (first instanceof ReadNode) {
                final List<ReadNode> nodes = pack.getElements().stream().map(x -> (ReadNode) x).collect(Collectors.toList());
                final VectorReadNode vectorRead = VectorReadNode.fromPackElements(nodes);
                graph.add(vectorRead);

                for (ReadNode node : nodes) {
                    node.setNext(null);
                }
                for (int i = 0; i < nodes.size(); i++) {
                    final ReadNode node = nodes.get(i);
                    final VectorExtractNode extractNode = new VectorExtractNode(node.getAccessStamp().unrestricted(), vectorRead, i);

                    if (node.predecessor() != null) {
                        node.predecessor().replaceFirstSuccessor(node, null);
                    }
                    node.replaceAtUsagesAndDelete(extractNode);

                    graph.addOrUnique(extractNode);
                }

                if (!lastFixed.isEmpty() && lastFixed.element() instanceof FixedWithNextNode) {
                    ((FixedWithNextNode) lastFixed.poll()).setNext(vectorRead);
                }

                lastFixed.add(vectorRead);
                return;
            }

            if (first instanceof WriteNode) {
                final List<WriteNode> nodes = pack.getElements().stream().map(x -> (WriteNode) x).collect(Collectors.toList());

                final VectorPrimitiveStamp vectorInputStamp = nodes.get(1).getAccessStamp().unrestricted().asVector(nodes.size());
                final VectorPackNode stv = new VectorPackNode(vectorInputStamp, nodes.stream().map(AbstractWriteNode::value).collect(Collectors.toList()));
                final VectorWriteNode vectorWrite = VectorWriteNode.fromPackElements(nodes, stv);

                for (WriteNode node : nodes) {
                    node.setNext(null);
                }
                for (WriteNode node : nodes) {
                    if (node.predecessor() != null) {
                      node.predecessor().replaceFirstSuccessor(node, null);
                    }
                    node.safeDelete();
                }

                graph.addWithoutUnique(stv);
                graph.add(vectorWrite);

                if (!lastFixed.isEmpty() && lastFixed.element() instanceof FixedWithNextNode) {
                    ((FixedWithNextNode) lastFixed.poll()).setNext(vectorWrite);
                }

                lastFixed.add(vectorWrite);
                return;
            }

            if (first instanceof BinaryArithmeticNode<?>) {
                final BinaryArithmeticNode<?> firstBAN = (BinaryArithmeticNode<?>) first;

                final List<BinaryArithmeticNode<?>> nodes = pack.getElements().stream().
                        map(x -> (BinaryArithmeticNode<?>) x).
                        collect(Collectors.toList());


                // Link up firstBAN
                final VectorPrimitiveStamp vectorInputStamp = firstBAN.getX().stamp(view).unrestricted().asVector(nodes.size());

                final VectorPackNode packX = new VectorPackNode(vectorInputStamp, nodes.stream().map(BinaryNode::getX).collect(Collectors.toList()));
                final VectorPackNode packY = new VectorPackNode(vectorInputStamp, nodes.stream().map(BinaryNode::getY).collect(Collectors.toList()));
                graph.addOrUnique(packX);
                graph.addOrUnique(packY);

                final VectorExtractNode firstBANExtractNode = new VectorExtractNode(firstBAN.stamp(view), firstBAN, 0);
                first.replaceAtUsages(firstBANExtractNode);
                graph.addOrUnique(firstBANExtractNode);

                firstBAN.setX(packX);
                firstBAN.setY(packY);
                firstBAN.inferStamp();

                // Link up the rest
                for (int i = 1; i < nodes.size(); i++) {
                    final BinaryArithmeticNode<?> node = nodes.get(i);
                    final VectorExtractNode extractNode = new VectorExtractNode(node.stamp(view), firstBAN, i);
                    node.replaceAtUsagesAndDelete(extractNode);

                    graph.addOrUnique(extractNode);
                }
                return;
            }

            throw GraalError.shouldNotReachHere("I don't know how to vectorize pack.");
        }

        private void scheduleStmt(Node node, Deque<FixedNode> lastFixed) {
            if (node instanceof FixedNode) {
                if (!lastFixed.isEmpty() && lastFixed.element() instanceof FixedWithNextNode) {
                    ((FixedWithNextNode) lastFixed.poll()).setNext((FixedNode) node);
                }

                lastFixed.add((FixedNode) node);
            }
        }

        // Main
        void slpExtract() {
            // TODO: don't operate on blocks that contain changes in control flow
            final Set<Pair<Node, Node>> packSet = new HashSet<>();
            final Set<Pack<Node>> combinedPackSet = new HashSet<>();

            findAdjRefs(packSet);
            extendPacklist(packSet);

            // after this it's not a packset anymore
            combinePacks(packSet, combinedPackSet);

            schedule(new ArrayList<>(blockToNodesMap.get(currentBlock)), combinedPackSet);
        }

    }

    private final SchedulePhase schedulePhase;
    private final NodeView view;

    public IsomorphicPackingPhase(SchedulePhase schedulePhase) {
        this(schedulePhase, NodeView.DEFAULT);
    }

    public IsomorphicPackingPhase(SchedulePhase schedulePhase, NodeView view) {
        this.schedulePhase = schedulePhase;
        this.view = view;
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        // Schedule phase is required so that lowered addresses are in the nodeToBlockMap
        schedulePhase.apply(graph);
        ControlFlowGraph cfg = graph.getLastSchedule().getCFG();
        for (Block block : cfg.reversePostOrder()) {
            new Instance(context, graph.getLastSchedule().getNodeToBlockMap(), graph.getLastSchedule().getBlockToNodesMap(), block, view).slpExtract();
        }
    }
}

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
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.VectorPrimitiveStamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeNode;
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
import org.graalvm.compiler.nodes.calc.UnaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
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
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.LowTierContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.graalvm.compiler.phases.common.vectorization.BlockInfo.sameBaseAddress;

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
        private final DebugContext debug;
        private final StructuredGraph graph;
        private final BlockInfo blockInfo;
        private final AutovectorizationPolicies policies;
        private final NodeView view;

        private final NodeMap<Integer> alignmentMap;

        private Instance(LowTierContext context, DebugContext debug, StructuredGraph graph, Block currentBlock, AutovectorizationPolicies policies, NodeView view) {
            this.context = context;
            this.debug = debug;
            this.graph = graph;
            this.blockInfo = new BlockInfo(graph, currentBlock, context, view);
            this.policies = policies;
            this.view = view;

            this.alignmentMap = new NodeMap<>(graph);
        }

        // Utilities

        private int dataSize(Node node) {
            if (node instanceof LIRLowerableAccess) {
                return dataSize(((LIRLowerableAccess) node).getAccessStamp());
            }

            if (node instanceof ValueNode) {
                return dataSize(((ValueNode) node).stamp(view));
            }

            return -1;
        }

        private int dataSize(Stamp stamp) {
            return stamp.javaType(context.getMetaAccess()).getJavaKind().getByteCount();
        }

        private int vectorWidth(ValueNode node) {
            final Stamp stamp = getStamp(node);
            return dataSize(stamp) * context.getTargetProvider().getVectorDescription().maxVectorWidth(stamp);
        }

        /**
         * Get the alignment of a vector memory reference.
         */
        private <T extends FixedAccessNode & LIRLowerableAccess> int memoryAlignment(T access, int ivAdjust) {
            final Stamp stamp = access.getAccessStamp();
            final JavaKind accessJavaKind = stamp.javaType(context.getMetaAccess()).getJavaKind();
            if (!accessJavaKind.isPrimitive()) {
                return ALIGNMENT_BOTTOM;
            }

            final int byteCount = dataSize(stamp);
            final int vectorWidthInBytes = byteCount * context.getTargetProvider().getVectorDescription().maxVectorWidth(stamp);

            // If each vector element is less than 2 bytes, no need to vectorize
            if (vectorWidthInBytes < 2) {
                return ALIGNMENT_BOTTOM;
            }

            final int offset = (int) access.getAddress().getMaxConstantDisplacement() + ivAdjust * byteCount;
            final int offsetRemainder = offset % vectorWidthInBytes;

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

        private Stamp getStamp(ValueNode node) {
            return Util.getStamp(node, view);
        }

        private boolean stmtsCanPack(Set<Pair<ValueNode, ValueNode>> packSet, ValueNode s1, ValueNode s2, Integer align) {
            // TODO: Also make sure that the platform supports vectors of the primitive type of this candidate pack

            if (supported(s1) && supported(s2) &&
                blockInfo.isomorphic(s1, s2) && blockInfo.independent(s1, s2) &&
                packSet.stream().noneMatch(p -> p.getLeft().equals(s1) || p.getRight().equals(s2))) {
                final Optional<Integer> alignS1 = getAlignment(s1);
                final Optional<Integer> alignS2 = getAlignment(s2);

                final int s2DataSize = dataSize(s2);
                boolean offset = false;
                if (s2DataSize >= 0 && align != null) {
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
        private boolean followUseDefs(Set<Pair<ValueNode, ValueNode>> packSet, Pair<ValueNode, ValueNode> pack) {
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

                    // If nodes not value nodes, bail
                    if (!(leftInput instanceof ValueNode) || !(rightInput instanceof ValueNode)) {
                        continue outer;
                    }

                    // Check block membership, bail if nodes not in block (prevent analysis beyond block)
                    if (blockInfo.notInBlock(leftInput) || blockInfo.notInBlock(rightInput)) {
                        continue outer;
                    }

                    // If the statements cannot be packed, bail
                    if (!align.isPresent() || !stmtsCanPack(packSet, (ValueNode) leftInput, (ValueNode) rightInput, align.get())) {
                        continue outer;
                    }

                    // If there are no savings to be gained, bail
                    if (policies.estSavings(blockInfo, packSet, leftInput, rightInput) < 0) {
                        continue outer;
                    }

                    changed |= packSet.add(Pair.create((ValueNode) leftInput, (ValueNode) rightInput));
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
        private boolean followDefUses(Set<Pair<ValueNode, ValueNode>> packSet, Pair<ValueNode, ValueNode> pack) {
            final Node left = pack.getLeft();
            final Node right = pack.getRight();
            final Optional<Integer> align = getAlignment(left);

            int savings = -1;
            Pair<ValueNode, ValueNode> bestPack = null;

            // TODO: bail if left is store (why?)

            for (Node leftUsage : left.usages()) {
                if (blockInfo.notInBlock(left)) {
                    continue;
                }

                for (Node rightUsage : right.usages()) {
                    if (leftUsage == rightUsage || blockInfo.notInBlock(right)) {
                        continue;
                    }

                    // TODO: Rather than adding the first, add the best
                    if (leftUsage instanceof ValueNode &&
                            rightUsage instanceof ValueNode &&
                            stmtsCanPack(packSet, (ValueNode) leftUsage, (ValueNode) rightUsage, align.orElse(null))) {
                        final int currentSavings = policies.estSavings(blockInfo, packSet, leftUsage, rightUsage);
                        if (currentSavings > savings) {
                            savings = currentSavings;
                            bestPack = Pair.create((ValueNode) leftUsage, (ValueNode) rightUsage);
                        }
                    }
                }
            }

            if (savings >= 0) {
                if (align.isPresent()) {
                    setAlignment(bestPack.getLeft(), bestPack.getRight(), align.get());
                } else {
                    alignmentMap.removeKey(bestPack.getLeft());
                    alignmentMap.removeKey(bestPack.getRight());
                }

                return packSet.add(bestPack);
            }

            return false;
        }

        private int getIvAdjustment(FixedAccessNode alignmentReference) {
            final int offset = (int) alignmentReference.getAddress().getMaxConstantDisplacement();
            final int elementSize = dataSize(getStamp(alignmentReference));
            final int vectorWidth = vectorWidth(alignmentReference);

            // TODO: support different scale factors

            return (vectorWidth - offset % vectorWidth) / elementSize;
        }

        /**
         * Find the memory reference to align other memory references to.
         * This reference should have the largest number of memory references similar to it.
         *
         * First we look at stores, then at loads.
         * This alignment analysis is largely inspired by C2's superword.cpp implementation.
         */
        private FixedAccessNode findAlignToRef(List<FixedAccessNode> memoryNodes) {
            // Count the number of comparable memory operations
            // How many memory operations can we compare another one to?
            // Comparable means isomorphic, part of the same array (which we handle as one)

            int[] accessCount = new int[memoryNodes.size()];

            for (int i = 0; i < memoryNodes.size(); i++) {
                final FixedAccessNode s1 = memoryNodes.get(i);
                for (int j = i + 1; j < memoryNodes.size(); j++) {
                    final FixedAccessNode s2 = memoryNodes.get(j);
                    if (blockInfo.isomorphic(s1, s2) && sameBaseAddress(s1, s2)) {
                        accessCount[i]++;
                        accessCount[j]++;
                    }
                }
            }

            int maxCount = 0;
            int maxVectorWidth = 0;
            int maxIndex = -1;
            int minSize = Integer.MAX_VALUE;
            int minIvOffset = Integer.MAX_VALUE;

            // Look at Writes
            for (int i = 0; i < memoryNodes.size(); i++) {
                final FixedAccessNode s = memoryNodes.get(i);
                if (!(s instanceof WriteNode)) {
                    continue;
                }

                final Stamp stamp = getStamp(s);
                final int dataSize = dataSize(stamp);
                final int vectorWidthInBytes = vectorWidth(s);
                final int offset = (int) s.getAddress().getMaxConstantDisplacement();

                // if the access count is greater than the max count
                // if the ac is the same as the max but the vector width is greater
                // if the ac & vw same but the data size is smaller than the min
                // if the ac & vw & ds same but the pointer offset smaller than min iv offset
                if (accessCount[i] > maxCount || (accessCount[i] == maxCount && (vectorWidthInBytes > maxVectorWidth || (vectorWidthInBytes == maxVectorWidth && (dataSize < minSize || (dataSize == minSize && offset < minIvOffset)))))) {
                    maxCount = accessCount[i];
                    maxVectorWidth = vectorWidthInBytes;
                    maxIndex = i;
                    minSize = dataSize;
                    minIvOffset = offset;
                }
            }

            // Look at Reads
            for (int i = 0; i < memoryNodes.size(); i++) {
                final FixedAccessNode s = memoryNodes.get(i);
                if (!(s instanceof ReadNode)) {
                    continue;
                }

                final Stamp stamp = getStamp(s);
                final int dataSize = dataSize(stamp);
                final int vectorWidthInBytes = vectorWidth(s);
                final int offset = (int) s.getAddress().getMaxConstantDisplacement();

                // if the access count is greater than the max count
                // if the ac is the same as the max but the vector width is greater
                // if the ac & vw same but the data size is smaller than the min
                // if the ac & vw & ds same but the pointer offset smaller than min iv offset
                if (accessCount[i] > maxCount || (accessCount[i] == maxCount && (vectorWidthInBytes > maxVectorWidth || (vectorWidthInBytes == maxVectorWidth && (dataSize < minSize || (dataSize == minSize && offset < minIvOffset)))))) {
                    maxCount = accessCount[i];
                    maxVectorWidth = vectorWidthInBytes;
                    maxIndex = i;
                    minSize = dataSize;
                    minIvOffset = offset;
                }
            }

            if (maxCount == 0) {
                return null;
            }

            return memoryNodes.get(maxIndex);
        }

        /**
         * Predicate to determine whether performing the IPP operation is valid for this block.
         * @return Boolean indicating whether we can proceed with packing.
         */
        private boolean validForBlock() {
            for (FixedNode node : blockInfo.getBlock().getNodes()) {
                if (node instanceof ControlSplitNode) {
                    return false;
                }
                if (node instanceof InvokeNode) {
                    return false;
                }
            }

            return true;
        }

        // Core

        /**
         * Create the initial seed packSet of operations that are adjacent.
         * @param packSet PackSet to populate.
         */
        private void findAdjRefs(Set<Pair<ValueNode, ValueNode>> packSet) {
            // Create initial seed set containing memory operations
            List<FixedAccessNode> memoryNodes = // Candidate list of memory nodes
                    StreamSupport.stream(blockInfo.getBlock().getNodes().spliterator(), false).
                            filter(x -> x instanceof FixedAccessNode && x instanceof LIRLowerableAccess).
                            map(x -> (FixedAccessNode & LIRLowerableAccess) x).
                            // using stack kind here as this works with MetaspacePointerStamp and others
                            filter(x -> getStamp(x).getStackKind().isPrimitive() && memoryAlignment(x, 0) != ALIGNMENT_BOTTOM).
                            collect(Collectors.toList());

            while (!memoryNodes.isEmpty()) {
                final FixedAccessNode alignmentReference = findAlignToRef(memoryNodes);
                if (alignmentReference == null) {
                    return; // we can't find a reference to align to, packSet is complete
                }
                final int ivAdjust = getIvAdjustment(alignmentReference);

                for (int i = memoryNodes.size() - 1; i >= 0; i--) {
                    final FixedAccessNode s = memoryNodes.get(i);
                    if (blockInfo.isomorphic(s, alignmentReference) && sameBaseAddress(s, alignmentReference)) {
                        setAlignment(s, memoryAlignment((FixedAccessNode & LIRLowerableAccess) s, ivAdjust));
                    }
                }

                // create a pack
                for (FixedAccessNode s1 : memoryNodes) {
                    final Optional<Integer> alignS1 = getAlignment(s1);
                    if (!alignS1.isPresent()) {
                        continue;
                    }

                    for (FixedAccessNode s2 : memoryNodes) {
                        if (!getAlignment(s2).isPresent()) {
                            continue;
                        }

                        if (s1 != s2 && blockInfo.adjacent(s1, s2) && stmtsCanPack(packSet, s1, s2, alignS1.orElse(null))) {
                            packSet.add(Pair.create(s1, s2));
                        }
                    }
                }

                // remove unused memory nodes
                for (int i = memoryNodes.size() - 1; i >= 0; i--) {
                    final FixedAccessNode s = memoryNodes.get(i);
                    if (getAlignment(s).isPresent()) {
                        memoryNodes.remove(i);
                    }
                }
            }
        }

        /**
         * Extend the packset by following use->def and def->use links from pack members until the set does not change.
         * @param packSet PackSet to populate.
         */
        private void extendPacklist(Set<Pair<ValueNode, ValueNode>> packSet) {
            Set<Pair<ValueNode, ValueNode>> iterationPackSet;

            boolean changed;
            do {
                changed = false;
                iterationPackSet = new HashSet<>(packSet);

                for (Pair<ValueNode, ValueNode> pack : iterationPackSet) {
                    changed |= followUseDefs(packSet, pack);
                    changed |= followDefUses(packSet, pack);
                }
            } while (changed);
        }

        // Combine packs where right = left
        private void combinePacks(Set<Pair<ValueNode, ValueNode>> packSet, Set<Pack> combinedPackSet) {
            combinedPackSet.addAll(packSet.stream().map(Pack::create).collect(Collectors.toList()));
            packSet.clear();

            boolean changed;
            do {
                changed = false;

                Deque<Pack> remove = new ArrayDeque<>();
                Deque<Pack> add = new ArrayDeque<>();

                for (Pack leftPack : combinedPackSet) {
                    if (remove.contains(leftPack)) {
                        continue;
                    }

                    for (Pack rightPack : combinedPackSet) {
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
            final NodeMap<Block> nodeToBlockMap = graph.getLastSchedule().getNodeToBlockMap();

            return StreamSupport.stream(node.inputs().spliterator(), false).
                    filter(n -> !nodeToBlockMap.isNew(n) && blockInfo.inBlock(n)).
                    allMatch(scheduled::contains) &&
                    // AND have all the control flow dependencies been scheduled? (only if considering CF)
                    (!considerControlFlow || StreamSupport.stream(node.cfgPredecessors().spliterator(), false).
                            filter(blockInfo::inBlock).
                            allMatch(scheduled::contains)
                    );
        }

        private Pack earliestUnscheduled(List<Node> unscheduled, Map<Node, Pack> nodeToPackMap) {
            for (Node node : unscheduled) {
                if (nodeToPackMap.containsKey(node)) {
                    return nodeToPackMap.get(node);
                }
            }

            return null;
        }

        private void schedule(List<Node> unscheduled, Set<Pack> packSet) {
            final List<Node> scheduled = new ArrayList<>();
            final Deque<FixedNode> lastFixed = new ArrayDeque<>();

            // Populate a nodeToPackMap
            final Map<Node, Pack> nodeToPackMap = packSet.stream().
                    flatMap(pack -> pack.getElements().stream().map(node -> Pair.create(node, pack))).
                    collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

            final List<Runnable> deferred = new ArrayList<>();

            // While unscheduled isn't empty
            outer: while (!unscheduled.isEmpty()) {
                for (Node node : unscheduled) {
                    // Node is in some pack p
                    if (nodeToPackMap.containsKey(node)) {
                        final Pack pack = nodeToPackMap.get(node);
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
                Pack packToRemove = earliestUnscheduled(unscheduled, nodeToPackMap);
                assert packToRemove != null : "there no are unscheduled statements in packs";
                debug.log("dependency violation, removing %s", packToRemove);
                for (Node packNode : packToRemove.getElements()) {
                    nodeToPackMap.remove(packNode); // Remove all references for these statements to pack
                }
            }

            for (Runnable runnable : deferred) {
                runnable.run();
            }
        }

        private void schedulePack(Pack pack, Deque<FixedNode> lastFixed) {
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
                    final VectorExtractNode extractNode =
                        graph.addOrUnique(new VectorExtractNode(node.getAccessStamp().unrestricted(), vectorRead, i));

                    if (node.predecessor() != null) {
                        node.predecessor().clearSuccessors();
                    }
                    node.replaceAtUsagesAndDelete(extractNode);
                }

                if (!lastFixed.isEmpty() && lastFixed.element() instanceof FixedWithNextNode) {
                    ((FixedWithNextNode) lastFixed.poll()).setNext(vectorRead);
                }

                lastFixed.add(vectorRead);
                return;
            }

            if (first instanceof WriteNode) {
                final List<WriteNode> nodes = pack.getElements().stream().map(x -> (WriteNode) x).collect(Collectors.toList());

                final VectorPackNode vectorPackNode =
                    graph.addOrUnique(new VectorPackNode(pack.stamp(view), nodes.stream().map(AbstractWriteNode::value).collect(Collectors.toList())));
                final VectorWriteNode vectorWrite = VectorWriteNode.fromPackElements(nodes, vectorPackNode);

                for (WriteNode node : nodes) {
                    if (node.predecessor() != null) {
                        node.predecessor().clearSuccessors();
                    }

                    node.safeDelete();
                }

                graph.add(vectorWrite);

                if (!lastFixed.isEmpty() && lastFixed.element() instanceof FixedWithNextNode) {
                    ((FixedWithNextNode) lastFixed.poll()).setNext(vectorWrite);
                }

                lastFixed.add(vectorWrite);
                return;
            }

            if (first instanceof UnaryArithmeticNode<?>) {
                final UnaryArithmeticNode<?> firstUAN = (UnaryArithmeticNode<?>) first;

                final List<UnaryArithmeticNode<?>> nodes = pack.getElements().stream().
                    map(x -> (UnaryArithmeticNode<?>) x).
                    collect(Collectors.toList());

                // Link up firstUAN
                final VectorPackNode packVal = graph.addOrUnique(new VectorPackNode(pack.stamp(view), nodes.stream().map(UnaryNode::getValue).collect(Collectors.toList())));

                final VectorExtractNode firstUANExtractNode = graph.addOrUnique(new VectorExtractNode(firstUAN.stamp(view), firstUAN, 0));
                first.replaceAtUsages(firstUANExtractNode, n -> n != firstUANExtractNode);

                firstUAN.setValue(packVal);
                firstUAN.inferStamp();

                // Link up the rest
                for (int i = 1; i < nodes.size(); i++) {
                    final UnaryArithmeticNode<?> node = nodes.get(i);
                    final VectorExtractNode extractNode = graph.addOrUnique(new VectorExtractNode(node.stamp(view), firstUAN, i));
                    node.replaceAtUsagesAndDelete(extractNode, n -> n != extractNode);
                }
                return;
            }

            if (first instanceof BinaryArithmeticNode<?>) {
                final BinaryArithmeticNode<?> firstBAN = (BinaryArithmeticNode<?>) first;

                final List<BinaryArithmeticNode<?>> nodes = pack.getElements().stream().
                        map(x -> (BinaryArithmeticNode<?>) x).
                        collect(Collectors.toList());


                // Link up firstBAN
                final VectorPrimitiveStamp vectorInputStamp = pack.stamp(view);

                final VectorPackNode packX = graph.addOrUnique(new VectorPackNode(vectorInputStamp, nodes.stream().map(BinaryNode::getX).collect(Collectors.toList())));
                final VectorPackNode packY = graph.addOrUnique(new VectorPackNode(vectorInputStamp, nodes.stream().map(BinaryNode::getY).collect(Collectors.toList())));

                final VectorExtractNode firstBANExtractNode = graph.addOrUnique(new VectorExtractNode(firstBAN.stamp(view), firstBAN, 0));
                first.replaceAtUsages(firstBANExtractNode, n -> n != firstBANExtractNode);

                firstBAN.setX(packX);
                firstBAN.setY(packY);
                firstBAN.inferStamp();

                // Link up the rest
                for (int i = 1; i < nodes.size(); i++) {
                    final BinaryArithmeticNode<?> node = nodes.get(i);
                    final VectorExtractNode extractNode = graph.addOrUnique(new VectorExtractNode(node.stamp(view), firstBAN, i));
                    node.replaceAtUsagesAndDelete(extractNode, n -> n != extractNode);
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

            // non-fixed nodes don't have control flow, so don't need to do anything else
        }

        // Main
        @SuppressWarnings("try")
        private void slpExtract() {
            if (!validForBlock()) {
                return;
            }

            final Set<Pair<ValueNode, ValueNode>> packSet = new HashSet<>();
            final Set<Pack> combinedPackSet = new HashSet<>();

            findAdjRefs(packSet);
            if (packSet.isEmpty()) {
                return;
            }

            debug.log(DebugContext.DETAILED_LEVEL, "%s seed packset has size %d", blockInfo.getBlock(), packSet.size());
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "%s", packSet);

            extendPacklist(packSet);
            if (packSet.isEmpty()) {
                return;
            }

            debug.log(DebugContext.DETAILED_LEVEL, "%s extended packset has size %d", blockInfo.getBlock(), packSet.size());
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "%s", packSet);

            combinePacks(packSet, combinedPackSet);

            debug.log(DebugContext.VERBOSE_LEVEL, "%s combined packset has size %d", blockInfo.getBlock(), combinedPackSet.size());
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "%s", combinedPackSet);

            policies.filterPacks(combinedPackSet);

            debug.log(DebugContext.VERBOSE_LEVEL, "%s filtered packset has size %d", blockInfo.getBlock(), combinedPackSet.size());
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "%s", combinedPackSet);

            try (DebugContext.Scope s = debug.scope("schedule")) {
                schedule(new ArrayList<>(graph.getLastSchedule().getBlockToNodesMap().get(blockInfo.getBlock())), combinedPackSet);
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }

    }

    private final SchedulePhase schedulePhase;
    private final AutovectorizationPolicies policies;
    private final MethodList methodList;
    private final NodeView view;

    public IsomorphicPackingPhase(SchedulePhase schedulePhase, AutovectorizationPolicies policies, MethodList methodList, NodeView view) {
        this.schedulePhase = schedulePhase;
        this.policies = policies;
        this.methodList = methodList;
        this.view = view;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, LowTierContext context) {
        // Schedule phase is required so that lowered addresses are in the nodeToBlockMap
        schedulePhase.apply(graph);
        final ControlFlowGraph cfg = graph.getLastSchedule().getCFG();
        final DebugContext debug = graph.getDebug();

        final ResolvedJavaMethod method = graph.method();
        if (method != null && methodList.shouldSkip(method)) {
            debug.log(DebugContext.DETAILED_LEVEL, "Skipping IPP for %s.%s", method.getDeclaringClass().toJavaName(), method.getName());
            return;
        }

        for (Block block : cfg.reversePostOrder()) {
            new Instance(context, debug, graph, block, policies, view).slpExtract();
        }
    }
}

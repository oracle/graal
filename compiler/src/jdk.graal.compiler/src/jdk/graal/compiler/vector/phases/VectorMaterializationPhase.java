/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.VectorAccess;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.LoweredMaterializeVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorSafepointNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorWriteNode;
import jdk.graal.compiler.vector.nodes.op.ConcatVectorNode;
import jdk.graal.compiler.vector.nodes.op.MapVectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorOperation;
import jdk.graal.compiler.vector.nodes.producer.VectorReadNode;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphNode;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractFixedGuardNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.DeoptimizingNode.DeoptBefore;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.graph.ReentrantNodeIterator;
import jdk.graal.compiler.phases.graph.ReentrantNodeIterator.LoopInfo;
import jdk.graal.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

/**
 * This phase combines array allocations and operations on arrays in a high-level vectorized form as
 * produced by {@link NodeVectorizationPhase} and {@link LoopVectorizationPhase}. It recognizes and
 * eliminates useless array initializations and operations on temporary arrays.
 * </p>
 *
 * Consider the following code as an example:
 *
 * <pre>
 * double[] temp = new double[a.length];
 * for (int i = 0; i < a.length; i++) {
 *     temp[i] = a[i] + 1;
 * }
 * double[] result = new double[a.length];
 * for (int i = 0; i < a.length; i++) {
 *     result[i] = temp[i] * 2;
 * }
 * // assume no further use of temp
 * </pre>
 *
 * Conceptually, both arrays are initialized to zero when they are allocated. Vector materialization
 * recognizes that the following loops overwrite all elements of the new arrays, so it eliminates
 * this useless zeroing. It also recognizes that the temporary array is not necessary. Its
 * allocation as well as all reads and writes are eliminated, and the operations from the two loops
 * are fused into one. The result is (a vectorized version of) the following code:
 *
 * <pre>
 * double[] result = [raw allocation without zeroing];
 * for (int i = 0; i < a.length; i++) {
 *     result[i] = (a[i] + 1) * 2;
 * }
 * </pre>
 *
 * @implNote This implementation is careful in its handling of frame states and side effects. It
 *           only eliminates zeroing when there are no side effects between the allocation and the
 *           initializing vector write. It updates the frame states of intervening deopts to the
 *           state before the materialize. We will therefore never deopt to a state with an
 *           uninitialized array: When we deopt, it will look as if the allocation hasn't been
 *           executed yet.
 */
public class VectorMaterializationPhase extends PostRunCanonicalizationPhase<MidTierContext> {

    private static final int MAX_VECTOR_OPERATION_DUPLICATIONS = 30;

    /**
     * Maximum number of nested concat operations to optimize. A materialization that is initialized
     * with a deeper nest of concats will not be merged with further writes that would add more
     * concats.
     */
    private static final int MAX_MATERIALIZED_CONCAT_DEPTH = 3;

    private final DeadCodeEliminationPhase deadCodeElimination = new DeadCodeEliminationPhase();

    public VectorMaterializationPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public float codeSizeIncrease() {
        return 10.0f;
    }

    @Override
    public void run(StructuredGraph graph, MidTierContext context) {
        if (!graph.hasNode(LoweredMaterializeVectorNode.TYPE)) {
            return;
        }
        EconomicSet<LoweredMaterializeVectorNode> excludedMaterializeNodes = computeExcludedMaterializeNodes(graph);
        if (!excludedMaterializeNodes.isEmpty() && excludedMaterializeNodes.size() == graph.getNodes(LoweredMaterializeVectorNode.TYPE).count()) {
            /* All nodes are excluded, nothing to do. */
            return;
        }
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeDominators(true).build();
        ArrayList<LoweredMaterializeVectorNode> materializeQueue = new ArrayList<>();
        graph.getNodes(LoweredMaterializeVectorNode.TYPE).snapshotTo(materializeQueue);
        materializeQueue.sort(new MaterializePriorityComparator(cfg));
        /*
         * For optimal optimization effect, we want to visit existing materialize nodes in program
         * order (as the queue is now sorted). Newly created materializations should only be visited
         * in a second pass after all the pre-existing ones, but still in program order within that
         * pass. As new materializations are built during one program-order visit, simply appending
         * newly created nodes to the queue does the right thing. New materializations are only
         * built when we merge one with an existing vector write, so we terminate when all eligible
         * vector writes have been used up.
         */
        int i = 0;
        while (i < materializeQueue.size()) {
            LoweredMaterializeVectorNode materialize = materializeQueue.get(i);
            if (!excludedMaterializeNodes.contains(materialize)) {
                propagateFramestate(materialize.stateBefore(), materialize.next());

                shortcutMaterializeConsumer(cfg, context, materialize);

                boolean canMergeWithAdjacentWrite = moveTowardsVectorWrite(cfg, context, materialize);
                if (canMergeWithAdjacentWrite) {
                    GraalError.guarantee(materialize.next() instanceof VectorWriteNode, "materialize %s should have been moved to a vector write, got: %s", materialize, materialize.next());
                    optimizeMaterializeWrite(graph, cfg, context, materialize, (VectorWriteNode) materialize.next(), materializeQueue);
                }

                graph.getOptimizationLog().report(getClass(), "MaterializeVectorOptimization", materialize);
            }
            i++;
        }

        deadCodeElimination.apply(graph);

        for (LoweredMaterializeVectorNode materialize : graph.getNodes(LoweredMaterializeVectorNode.TYPE)) {
            materializeVector(graph, context, materialize);
            graph.getOptimizationLog().report(getClass(), "VectorMaterialization", materialize);
        }
    }

    /**
     * Compares materialize nodes according to a priority that reflects program order. Priority
     * reflects dominance relationships between blocks as well as order within blocks. See
     * {@link #priority} for details.
     */
    private static class MaterializePriorityComparator implements Comparator<LoweredMaterializeVectorNode> {

        private ControlFlowGraph cfg;
        /** Caches priorities for materialize nodes. */
        private EconomicMap<LoweredMaterializeVectorNode, Integer> cache;

        MaterializePriorityComparator(ControlFlowGraph cfg) {
            this.cfg = cfg;
            this.cache = EconomicMap.create();
        }

        @Override
        public int compare(LoweredMaterializeVectorNode a, LoweredMaterializeVectorNode b) {
            HIRBlock blockA = cfg.blockFor(a);
            HIRBlock blockB = cfg.blockFor(b);
            if (blockA != blockB) {
                /* Block IDs are in reverse post order. */
                return blockA.getId() - blockB.getId();
            }

            return priority(a) - priority(b);
        }

        /**
         * Computes a priority for the given materialize node. We want to process materialize nodes
         * in program order, so a node's priority is its distance from the start of its block.
         */
        private int priority(LoweredMaterializeVectorNode materialize) {
            if (cache.containsKey(materialize)) {
                return cache.get(materialize);
            }
            /* Eagerly cache priorities for all materialize nodes in this block. */
            AbstractBeginNode blockBegin = cfg.blockFor(materialize).getBeginNode();
            FixedNode current = blockBegin.next();
            int distance = 1;
            while (current instanceof FixedWithNextNode currentWithNext) {
                if (current instanceof LoweredMaterializeVectorNode someMaterialize) {
                    cache.put(someMaterialize, distance);
                }
                current = currentWithNext.next();
                distance++;
            }
            GraalError.guarantee(cache.containsKey(materialize), "should have found %s after scanning its whole block", materialize);
            return cache.get(materialize);
        }
    }

    /**
     * Each {@link VectorWriteNode} that accesses a {@link LoweredMaterializeVectorNode} can result
     * in one {@link ConcatVectorNode} that potentially ends up as an argument to a
     * {@link MapVectorNode} or {@link FoldVectorNode}.
     *
     * Both {@link MapVectorNode} and {@link FoldVectorNode} handle {@link ConcatVectorNode} inputs
     * in a way that can result in an exponential duplication of map/fold operations. Therefore, we
     * need to limit the number of {@link ConcatVectorNode}s that can end up as such inputs.
     */
    private static EconomicSet<LoweredMaterializeVectorNode> computeExcludedMaterializeNodes(StructuredGraph graph) {
        EconomicSet<LoweredMaterializeVectorNode> excludedMaterializeNodes = EconomicSet.create();
        List<LoweredMaterializeVectorNode> materializeNodes = graph.getNodes(LoweredMaterializeVectorNode.TYPE).snapshot();
        if (materializeNodes.size() > 1) {
            int[] writesPerMaterialize = computeVectorWriteNodesPerMaterialize(graph, materializeNodes);
            for (Node node : graph.getNodes()) {
                if (node instanceof SubGraphNode) {
                    SubGraphNode subGraphNode = (SubGraphNode) node;
                    checkPotentialDuplicationOfVectorOperations(materializeNodes, subGraphNode.getVectorInputs(), writesPerMaterialize, excludedMaterializeNodes);
                }
            }
        }
        return excludedMaterializeNodes;
    }

    private static int[] computeVectorWriteNodesPerMaterialize(StructuredGraph graph, List<LoweredMaterializeVectorNode> materializeNodes) {
        int[] writesPerMaterialize = new int[materializeNodes.size()];
        // we are conservative, so we need to assume that at least a part of each allocated array is
        // initialized with default values, which is not directly visible as a vector write.
        Arrays.fill(writesPerMaterialize, 1);

        for (VectorWriteNode write : graph.getNodes(VectorWriteNode.TYPE)) {
            for (int i = 0; i < materializeNodes.size(); i++) {
                LoweredMaterializeVectorNode materialize = materializeNodes.get(i);
                if (isAccessTo(write, materialize)) {
                    writesPerMaterialize[i]++;
                }
            }
        }
        return writesPerMaterialize;
    }

    private static void checkPotentialDuplicationOfVectorOperations(List<LoweredMaterializeVectorNode> materializeNodes, List<ValueNode> vectorInputs, int[] writesPerMaterialize,
                    EconomicSet<LoweredMaterializeVectorNode> excludedMaterializeNodes) {
        int potentialVectorOperationDuplications = 1;
        for (int materializeIndex = 0; materializeIndex < materializeNodes.size(); materializeIndex++) {
            LoweredMaterializeVectorNode materialize = materializeNodes.get(materializeIndex);
            if (!excludedMaterializeNodes.contains(materialize)) {
                for (ValueNode vectorInput : vectorInputs) {
                    if (vectorInput instanceof VectorReadNode) {
                        VectorReadNode read = (VectorReadNode) vectorInput;
                        if (isAccessTo(read, materialize)) {
                            assert writesPerMaterialize[materializeIndex] > 0 : materializeNodes + " vectorInputs=" + vectorInput + " writePM=" + writesPerMaterialize;
                            potentialVectorOperationDuplications *= writesPerMaterialize[materializeIndex];
                            if (potentialVectorOperationDuplications > MAX_VECTOR_OPERATION_DUPLICATIONS) {
                                excludedMaterializeNodes.add(materialize);
                            }

                            // each materialize must only be counted once, even if it is used
                            // multiple times in the same vector operation
                            break;
                        }
                    }
                }
            }
        }
    }

    private static void shortcutMaterializeConsumer(ControlFlowGraph cfg, MidTierContext context, LoweredMaterializeVectorNode materialize) {
        // Try to propagate the initialization value to vector reads from this materialized array.
        // If the initializer involves a vector read and we would propagate its value to more than
        // one vector read, don't perform this optimization: It would duplicate reads in violation
        // of the Java memory model (GR-25127).
        int vectorReads = 0;
        for (Node usage : materialize.usages()) {
            if (usage instanceof AddressNode) {
                if (usage instanceof OffsetAddressNode) {
                    for (Node addressUsage : usage.usages()) {
                        if (addressUsage instanceof VectorReadNode) {
                            vectorReads++;
                        } else if (addressUsage instanceof VectorWriteNode || addressUsage instanceof FloatingReadNode) {
                            // benign usages, not vector reads
                        } else {
                            // unknown usage, don't risk optimizing
                            return;
                        }
                    }
                } else {
                    // unknown usage, don't risk optimizing
                    return;
                }
            }
        }
        if (vectorReads == 0) {
            // No vector reads to optimize.
            return;
        } else if (vectorReads > 1 && usesVectorReads(materialize.getVector())) {
            // Not allowed to duplicate reads.
            return;
        }

        FixedNode current = materialize.next();
        while (current != null) {
            if (current instanceof VectorConsumer) {
                VectorConsumer consumer = (VectorConsumer) current;
                optimizeMaterializeRead(cfg, context, materialize, consumer);
                // Optimize any further consumers in the same consumer group.
                if (consumer instanceof LowerableVectorConsumer lowerableConsumer && lowerableConsumer.isPartOfALoop()) {
                    FixedNode next = consumer.asFixedWithNextNode().next();
                    while (next instanceof VectorReadNode vectorRead) {
                        next = vectorRead.next();
                    }
                    GraalError.guarantee(next instanceof LoweredMaterializeVectorNode || next instanceof VectorLoopNode ||
                                    (next instanceof LowerableVectorConsumer nextConsumer && nextConsumer.vectorLoop() == lowerableConsumer.vectorLoop()),
                                    "consumers in a group must be adjacent; consumer = %s, next = %s", consumer, next);
                    current = next;
                    continue;
                }
            }

            if (current instanceof IfNode) {
                // skip deopt
                IfNode ifNode = (IfNode) current;
                if (getDeopt(ifNode.trueSuccessor()) != null) {
                    current = ifNode.falseSuccessor();
                } else if (getDeopt(ifNode.falseSuccessor()) != null) {
                    current = ifNode.trueSuccessor();
                } else {
                    return;
                }
            } else if (current instanceof FixedWithNextNode) {
                // skip nodes that have no side effect
                if (current instanceof StateSplit && ((StateSplit) current).hasSideEffect()) {
                    return;
                } else {
                    current = ((FixedWithNextNode) current).next();
                }
            } else {
                return;
            }
        }
    }

    // Determine whether the computation of the given vector value involves a vector read.
    private static boolean usesVectorReads(VectorNode value) {
        NodeFlood flood = new NodeFlood(value.asNode().graph());
        flood.add(value.asNode());
        for (Node n : flood) {
            if (n instanceof VectorReadNode) {
                return true;
            } else if (n instanceof VectorNode) {
                flood.addAll(n.inputs());
            }
        }
        return false;
    }

    /**
     * Propagate the given {@code state} along the control flow, setting it as the frame state in
     * {@link DeoptBefore} deopts. This means that, if such a deopt is taken, it will deopt to an
     * earlier state. The intention is to deopt to a state in which a vector materialization has not
     * yet taken place. Not having the materialization in the state allows us more freedom for
     * moving the materialization to a later point in the graph. Propagation stops if a side effect
     * is encountered, so side effects do not get lost when deoptimizing.
     */
    private static void propagateFramestate(FrameState state, FixedNode node) {
        ReentrantNodeIterator.apply(new PropagateFramestateClosure(state), node, PropagationState.PROPAGATE);
    }

    public enum PropagationState {
        PROPAGATE,
        FIND_SIDEEFFECT,
        EXIT
    }

    private static class PropagateFramestateClosure extends NodeIteratorClosure<PropagationState> {

        private final FrameState frameState;

        PropagateFramestateClosure(FrameState frameState) {
            this.frameState = frameState;
        }

        @Override
        protected PropagationState processNode(FixedNode node, PropagationState currentState) {
            if (node instanceof StateSplit && ((StateSplit) node).hasSideEffect()) {
                // stop propagation at first side effect
                return PropagationState.EXIT;
            }

            if (currentState == PropagationState.PROPAGATE && node instanceof DeoptBefore) {
                DeoptBefore deopt = (DeoptBefore) node;
                if (deopt.canDeoptimize()) {
                    FrameState deoptState = deopt.stateBefore();
                    if ((deopt instanceof VectorGuardNode || deopt instanceof VectorSafepointNode) && !deoptState.getMethod().equals(frameState.getMethod())) {
                        // Can't propagate to vector guard frame states across inlined method
                        // boundaries: These guard nodes must keep track of vectorized induction
                        // variables in the state.
                        return PropagationState.EXIT;
                    } else if (frameState != deoptState) {
                        deopt.setStateBefore(frameState);
                    }
                }
            }

            return currentState;
        }

        @Override
        protected PropagationState merge(AbstractMergeNode merge, List<PropagationState> states) {
            PropagationState ret = PropagationState.PROPAGATE;

            for (PropagationState state : states) {
                switch (state) {
                    case EXIT:
                        return PropagationState.EXIT;
                    case FIND_SIDEEFFECT:
                        ret = PropagationState.FIND_SIDEEFFECT;
                        break;
                }
            }

            return ret;
        }

        @Override
        protected PropagationState afterSplit(AbstractBeginNode node, PropagationState oldState) {
            return oldState;
        }

        @Override
        protected EconomicMap<LoopExitNode, PropagationState> processLoop(LoopBeginNode loop, PropagationState initialState) {
            LoopInfo<PropagationState> info = ReentrantNodeIterator.processLoop(this, loop, PropagationState.FIND_SIDEEFFECT);

            for (LoopEndNode end : loop.loopEnds()) {
                PropagationState endState = info.endStates.get(end);
                if (endState == null || endState == PropagationState.EXIT) {
                    // this loop contains a side effect
                    return EconomicMap.create(Equivalence.IDENTITY);
                }
            }

            EconomicMap<LoopExitNode, PropagationState> ret = EconomicMap.create(Equivalence.IDENTITY, info.exitStates.size());
            MapCursor<LoopExitNode, PropagationState> entry = info.exitStates.getEntries();
            while (entry.advance()) {
                if (entry.getValue() != PropagationState.EXIT) {
                    ret.put(entry.getKey(), initialState);
                }
            }
            return ret;
        }

        @Override
        protected boolean continueIteration(PropagationState currentState) {
            return currentState != PropagationState.EXIT;
        }
    }

    /**
     * Check if {@code node} is the start of a path to an unconditional deoptimization, with only
     * begin or end nodes but nothing else before the deopt. If so, return the deopt; return
     * {@code null} otherwise.
     */
    private static DeoptimizeNode getDeopt(FixedNode node) {
        FixedNode current = node;
        while (current != null) {
            if (current instanceof AbstractBeginNode) {
                current = ((AbstractBeginNode) current).next();
            } else if (current instanceof AbstractEndNode) {
                current = ((AbstractEndNode) current).merge();
            } else {
                break;
            }
        }
        return current instanceof DeoptimizeNode ? (DeoptimizeNode) current : null;
    }

    /**
     * Tries to move the {@code materialize} immediately before the first {@link VectorWriteNode}
     * that writes into the materialized array. On success, modifies the graph by moving the
     * {@code materialize} so that {@code materialize.next()} is the relevant write. Leaves the
     * graph unchanged otherwise.
     * <p/>
     * This method also checks if the write it found overwrites the entire contents of the
     * materialized array and can therefore be merged with the materialization itself. This check
     * determines the return value.
     *
     * @return {@code true} if {@code materialize.next()} is a {@link VectorWriteNode} that
     *         overwrites the entire materialized array and may therefore be merged into the
     *         materialization
     */
    private static boolean moveTowardsVectorWrite(ControlFlowGraph cfg, MidTierContext context, LoweredMaterializeVectorNode materialize) {
        /* See if there is any relevant vector write at all. */
        boolean usedByVectorWrite = false;
        for (AddressNode addressUsage : materialize.usages().filter(AddressNode.class)) {
            for (VectorWriteNode vectorWriteUsage : addressUsage.usages().filter(VectorWriteNode.class)) {
                if (isAccessTo(vectorWriteUsage, materialize)) {
                    usedByVectorWrite = true;
                    break;
                }
            }
        }
        if (!usedByVectorWrite) {
            /* We can give up right away. */
            return false;
        }

        /*
         * Follow a straight-line path from the materialize until we reach the first vector write to
         * it. Record this path for dependency checking.
         */
        VectorWriteNode initializingWrite = null;
        ArrayList<FixedNode> path = new ArrayList<>();
        FixedNode cursor = materialize.next();
        while (cursor != null) {
            path.add(cursor);
            if (cursor instanceof LoweredMaterializeVectorNode otherMaterialize && materialize.stateBefore() == otherMaterialize.stateBefore()) {
                cursor = otherMaterialize.next();
            } else if (cursor instanceof VectorWriteNode vectorWrite) {
                if (isAccessTo(vectorWrite, materialize)) {
                    /* We found the write of interest. */
                    path.remove(vectorWrite);
                    initializingWrite = vectorWrite;
                    break;
                }
                cursor = vectorWrite.next();
            } else if (cursor instanceof LoopBeginNode) {
                /* Don't try to enter loops. */
                return false;
            } else if (cursor instanceof AbstractBeginNode begin) {
                cursor = begin.next();
            } else if (cursor instanceof AbstractFixedGuardNode guard) {
                cursor = guard.next();
            } else if (cursor instanceof IfNode ifNode) {
                FixedNode deopt;
                if ((deopt = getDeopt(ifNode.trueSuccessor())) != null) {
                    cursor = ifNode.falseSuccessor();
                } else if ((deopt = getDeopt(ifNode.falseSuccessor())) != null) {
                    cursor = ifNode.trueSuccessor();
                } else {
                    return false;
                }
                /*
                 * The deopt is not on the straight-line path, but if its state refers to the
                 * materialize, then we cannot sink the materialize past the branch. Add the deopt
                 * to the path for dependency checking.
                 */
                path.add(deopt);
            } else if (cursor instanceof VectorReadNode vectorRead) {
                if (isAccessTo(vectorRead, materialize)) {
                    return false;
                }
                cursor = vectorRead.next();
            } else {
                /* Some other node that we don't want to skip over. */
                return false;
            }
        }

        /*
         * We have a path from the materialize to a vector write. Now we must check usages of the
         * materialize: If some node on the path is a transitive dependency of the materialize, we
         * can't move past it since that would create a cycle in the graph. We only do this check
         * now because collecting the transitive usages can be expensive, so we wanted to delay it
         * as much as possible.
         *
         * The transitive usages are only collected "up to fixed nodes". This is enough for our
         * purposes here. Assume the materialize has a usage path like the following:
         *
         * materialize -> ...floating... -> fixed usage A -> ...floating... -> fixed usage B
         *
         * The "usages up to fixed nodes" will include A but not B. This allows us to answer the
         * query "can the materialize move past A". The usages don't allow us to answer the query
         * "can the materialize move past B" directly. But if B is a transitive usage of A, it must
         * come after A, and since we can't move past A, we never need to consider B.
         */
        NodeBitMap materializeUsages = transitiveUsagesUpToFixed(materialize);
        for (FixedNode pathNode : path) {
            if (materializeUsages.isMarked(pathNode)) {
                /* Can't move the materialize past this node, so give up. */
                return false;
            }
        }

        if (mayMoveNodeBefore(cfg, initializingWrite, materialize, materializeUsages, context)) {
            if (materialize.next() != initializingWrite) {
                moveNodeBefore(cfg, initializingWrite, materialize);
            }
            return canOptimizeMaterializeWrite(cfg, materialize, initializingWrite, materializeUsages);
        }
        return false;
    }

    /**
     * Collect usages of the {@code initialNode} transitively, but stopping at fixed nodes. That is,
     * fixed nodes are included if they are a direct usage of the initial node, or if they use it
     * through a path consisting only of floating nodes. However, the usages of such fixed nodes are
     * not included.
     *
     * @return a bitmap that marks transitive usages of the initial node collected up to and
     *         including the first fixed node on each path
     */
    private static NodeBitMap transitiveUsagesUpToFixed(ValueNode initialNode) {
        NodeFlood flood = initialNode.graph().createNodeFlood();
        flood.addAll(initialNode.usages());
        for (Node n : flood) {
            if (!(n instanceof FixedNode)) {
                flood.addAll(n.usages());
            }
        }
        return flood.getVisited();
    }

    private static void moveNodeBefore(ControlFlowGraph cfg, FixedNode node, FixedWithNextNode nodeToMove) {
        FixedNode next = nodeToMove.next();
        nodeToMove.setNext(null);
        nodeToMove.replaceAtPredecessor(next);
        nodeToMove.graph().addBeforeFixed(node, nodeToMove);
        cfg.getNodeToBlock().put(nodeToMove, cfg.blockFor(node));
    }

    private static boolean mayMoveNodeBefore(ControlFlowGraph cfg, VectorWriteNode vectorWrite, LoweredMaterializeVectorNode materialize, NodeBitMap materializeUsagesUpToFixed,
                    MidTierContext context) {
        if (vectorWrite.isPartOfALoop() && vectorWrite.getBarrierType() != BarrierType.NONE && !canOptimizeMaterializeWrite(cfg, materialize, vectorWrite, materializeUsagesUpToFixed)) {
            BarrierSet barrierSet = ((VectorLoweringProvider) context.getLowerer()).getBasicLoweringProvider().getBarrierSet();
            if (vectorWrite.writesObjectArray() && barrierSet.mayNeedPreWriteBarrier(JavaKind.Object)) {
                /*
                 * This write is in a consumer group and cannot be merged with the materialization.
                 * It will probably need a pre-write barrier. That barrier will be placed before the
                 * whole consumer group, but it would need to consume the address from this
                 * materialization inside the group, leading to an unschedulable graph. In this case
                 * we must not move the materialization.
                 */
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("try")
    private static void materializeVector(StructuredGraph graph, MidTierContext context, LoweredMaterializeVectorNode materialize) {
        /*
         * There is no deoptimization reason for NegativeArraySizeException, so we have to use the
         * generic reason RuntimeConstraint. That requires the check whether speculative
         * optimizations are allowed, i.e., that an Assumptions object is present - even though
         * there is nothing speculative per se in this case.
         */
        NodeIterable<VectorLoopNode> vectorLoopUsages = materialize.usages().filter(VectorLoopNode.class);
        int realUsageCount = materialize.getUsageCount() - vectorLoopUsages.count();
        if (realUsageCount == 0 && graph.getAssumptions() != null) {
            try (DebugCloseable position = materialize.withNodeSourcePosition()) {
                ValueNode length = materialize.getLength();
                ValueNode zero = ConstantNode.forIntegerStamp(length.stamp(NodeView.DEFAULT), 0, graph);
                LogicNode lengthLowerZero = CompareNode.createCompareNode(graph, CanonicalCondition.LT, length, zero, context.getConstantReflection(), NodeView.DEFAULT);
                FixedGuardNode guard = graph.add(new FixedGuardNode(lengthLowerZero, DeoptimizationReason.RuntimeConstraint, DeoptimizationAction.None, true));
                guard.setStateBefore(materialize.stateBefore());
                if (vectorLoopUsages.isNotEmpty()) {
                    assert vectorLoopUsages.count() == 1 : vectorLoopUsages.snapshot();
                    VectorLoopNode group = vectorLoopUsages.first();
                    group.removeConsumer(materialize);
                }
                graph.replaceFixedWithFixed(materialize, guard);
            }
        } else {
            materialize.materialize(context.getMetaAccess());
        }
    }

    /**
     * Return {@code true} if the given {@code vectorAccess} accesses the given {@code materialize}
     * object.
     */
    private static boolean isAccessTo(VectorAccess vectorAccess, LoweredMaterializeVectorNode materialize) {
        AddressNode address = vectorAccess.getAddress();
        int elementStride = vectorAccess.getElementStride();
        if (address instanceof OffsetAddressNode && elementStride == materialize.getElementStride()) {
            return address.getBase() == materialize;
        } else {
            return false;
        }
    }

    /**
     * Find all transitive {@link VectorReadNode} inputs to the {@code consumer}. Replace any that
     * read from the {@code materialize} by the value that would be read.
     */
    private static void optimizeMaterializeRead(ControlFlowGraph cfg, MidTierContext context, LoweredMaterializeVectorNode materialize, VectorConsumer consumer) {
        NodeFlood inputs = consumer.asNode().graph().createNodeFlood();
        ArrayList<VectorReadNode> optimizableReads = null;

        inputs.add(consumer.asNode());
        for (Node input : inputs) {
            if (input instanceof VectorReadNode) {
                VectorReadNode read = (VectorReadNode) input;
                assert read.getLastLocationAccess() != null;
                // Make sure not to propagate concat values to consumers inside vector consumer
                // groups: This might cause us to split up different consumers in a group in
                // different ways, which would prevent us from generating common code for them.
                boolean allowPropagation = allowReadPropagation(read, materialize.getVector());
                if (allowPropagation && isReadFromMaterialization(cfg, read, materialize)) {
                    if (optimizableReads == null) {
                        optimizableReads = new ArrayList<>();
                    }
                    optimizableReads.add(read);
                }
            } else if (input instanceof VectorOperation vectorOp) {
                inputs.addAll(vectorOp.getVectorInputs());
            }
        }

        if (optimizableReads != null) {
            for (VectorReadNode read : optimizableReads) {
                ValueNode readIndex = context.getLowerer().reconstructArrayIndex(materialize.getAllocator().getArrayKind(), read.getAddress());
                VectorNode shifted = AbstractVectorNode.shift(materialize.getVector(), readIndex, consumer.asFixedNode(), context.getConstantReflection());
                /*
                 * Replace the read at all the usages that flow to the current consumer. These are
                 * all marked in the flood we collected during the traversal.
                 */
                for (Node readUsage : read.usages().snapshot()) {
                    if (inputs.isMarked(readUsage)) {
                        readUsage.replaceAllInputs(read, shifted.asNode());
                    }
                }
                if (read.hasNoUsages()) {
                    read.graph().removeFixed(read);
                }
            }
        }
    }

    /**
     * Checks if this {@code read} reads directly from the {@code materialize}, without any possible
     * intervening side effect.
     */
    private static boolean isReadFromMaterialization(ControlFlowGraph cfg, VectorReadNode read, LoweredMaterializeVectorNode materialize) {
        return isAccessTo(read, materialize) && lastLocationAccessBeforeMaterialize(cfg, read, materialize);
    }

    /**
     * Checks if the {@code readAccess}'s last location access precedes the {@code materialize} in
     * the graph. The read's base address is must be the materialize operation. The materialize is
     * an allocation and thus not part of the memory graph, so the read's last location access
     * cannot refer to the materialize itself. But if the read's last location access precedes the
     * materialize in the graph, this means that the read accesses the values that are written by
     * the materialize. Otherwise, the read accesses values written by some other side effect that
     * comes after the materialization.
     */
    private static boolean lastLocationAccessBeforeMaterialize(ControlFlowGraph cfg, AddressableMemoryAccess readAccess, LoweredMaterializeVectorNode materialize) {
        GraalError.guarantee(readAccess.getAddress().getBase() == materialize, "check only makes sense if the read is from the materialize");
        MemoryKill lla = readAccess.getLastLocationAccess();
        FixedNode llaFixed = lla instanceof FixedNode fixed ? fixed : lla instanceof MemoryPhiNode phi ? phi.merge() : null;
        if (llaFixed == null) {
            return false;
        }
        HIRBlock lastLocationAccessBlock = cfg.blockFor(llaFixed);
        HIRBlock materializeBlock = cfg.blockFor(materialize);
        if (lastLocationAccessBlock.strictlyDominates(materializeBlock)) {
            return true;
        } else if (lastLocationAccessBlock == materializeBlock) {
            FixedWithNextNode current = lastLocationAccessBlock.getBeginNode();
            boolean seenLastLocationAccess = false;
            while (current != null) {
                if (current == llaFixed) {
                    seenLastLocationAccess = true;
                } else if (current == materialize) {
                    return seenLastLocationAccess;
                }
                if (current.next() instanceof FixedWithNextNode next) {
                    current = next;
                } else {
                    current = null;
                }
            }
        }
        return false;
    }

    private static boolean allowReadPropagation(VectorReadNode read, VectorNode materializeVector) {
        if (!canSplitOutConcats(read) && hasConcatInput(materializeVector)) {
            return false;
        }
        return true;
    }

    /**
     * Return {@code false} if {@code read} has a transitive usage by a vector loop containing more
     * than one consumer or which doesn't allow splitting of concats for some other reason. Read
     * propagation must not split consumers out of such loops. Return {@code true} if splitting of
     * concats is possible.
     */
    private static boolean canSplitOutConcats(VectorReadNode read) {
        NodeFlood flood = read.graph().createNodeFlood();

        flood.add(read);
        for (Node node : flood) {
            if (node instanceof LowerableVectorConsumer) {
                LowerableVectorConsumer consumer = (LowerableVectorConsumer) node;
                if (consumer.isPartOfALoop() && (consumer.vectorLoop().getConsumers().size() > 1 || !consumer.vectorLoop().mayRemoveConcatsFromLoop())) {
                    return false;
                }
            }
            for (Node usage : node.usages()) {
                if (usage instanceof VectorOperation && !(usage instanceof PhiNode)) {
                    flood.add(usage);
                }
            }
        }
        return true;
    }

    /**
     * Return {@code true} if {@code root} has a transitive input that is a
     * {@link ConcatVectorNode}.
     */
    private static boolean hasConcatInput(VectorNode root) {
        NodeFlood flood = root.asNode().graph().createNodeFlood();

        flood.add(root.asNode());
        for (Node node : flood) {
            if (node instanceof ConcatVectorNode) {
                return true;
            }
            for (Node input : node.inputs()) {
                if (input instanceof VectorNode && !(input instanceof PhiNode)) {
                    flood.add(input);
                }
            }
        }
        return false;
    }

    private static boolean canOptimizeMaterializeWrite(ControlFlowGraph cfg, LoweredMaterializeVectorNode materialize, VectorWriteNode vectorWriteNode, NodeBitMap materializeUsagesUpToFixed) {
        for (AddressNode addressUsage : materialize.usages().filter(AddressNode.class)) {
            for (Node access : addressUsage.usages()) {
                if (access == vectorWriteNode) {
                    continue;
                }
                if (access instanceof FloatingReadNode floatingRead) {
                    if (lastLocationAccessBeforeMaterialize(cfg, floatingRead, materialize)) {
                        /*
                         * The floating read reads directly from the materialized value. We can't
                         * merge the materialize and the vector write, we would overwrite the value
                         * of this read.
                         */
                        return false;
                    }
                }
            }
        }
        boolean allowConcat = !vectorWriteNode.isPartOfALoop() || vectorWriteNode.vectorLoop().mayRemoveConcatsFromLoop();
        if (allowConcat && materialize.getLength() != vectorWriteNode.getLength() && materialize.getVector() instanceof ConcatVectorNode concat) {
            /*
             * As the lengths don't match, merging the materialize and the write would produce a
             * ConcatVector. The materialization already has a concat; adding more would blow up the
             * graph size. Only do this up to a limit.
             */
            int concatDepth = 1;
            while (concat.x() instanceof ConcatVectorNode nestedConcat) {
                concat = nestedConcat;
                concatDepth++;
                if (concatDepth > MAX_MATERIALIZED_CONCAT_DEPTH) {
                    return false;
                }
            }
        }
        return isAccessTo(vectorWriteNode, materialize) &&
                        !materializeUsagesUpToFixed.isMarked(vectorWriteNode.getVector().asNode()) &&
                        (materialize.getLength() == vectorWriteNode.getLength() || allowConcat) &&
                        materialize.getVector().getVectorStamp().isCompatible(vectorWriteNode.getVector().getVectorStamp());
    }

    @SuppressWarnings("try")
    private static void optimizeMaterializeWrite(StructuredGraph graph, ControlFlowGraph cfg, MidTierContext context, LoweredMaterializeVectorNode materialize, VectorWriteNode vectorWriteNode,
                    ArrayList<LoweredMaterializeVectorNode> materializeQueue) {
        try (DebugCloseable nsp = materialize.asNode().withNodeSourcePosition()) {
            LoweredMaterializeVectorNode newMaterialize;
            if (materialize.getLength() == vectorWriteNode.getLength()) {
                // the whole previous vector gets overwritten -> just materialize the new one
                newMaterialize = graph.add(new LoweredMaterializeVectorNode(materialize.getAllocator(), materialize.stamp(NodeView.DEFAULT), vectorWriteNode.getVector(),
                                vectorWriteNode.getLength(), materialize.getBaseOffset(), vectorWriteNode.getElementStride(), vectorWriteNode.getBarrierType(),
                                vectorWriteNode.trustedBodyIterations()));
            } else {
                assert NumUtil.assertNonNegativeInt(materialize.getElementStride());
                VectorNode oldVector = materialize.getVector();
                // This will be i64 if the loop counter is long.
                Stamp lengthStamp = vectorWriteNode.getLength().stamp(NodeView.DEFAULT);

                ValueNode writeIndex = context.getLowerer().reconstructArrayIndex(materialize.getAllocator().getArrayKind(), vectorWriteNode.getAddress());
                writeIndex = IntegerConvertNode.convert(writeIndex, lengthStamp, NodeView.DEFAULT);

                ValueNode suffixIndex = BinaryArithmeticNode.add(graph, writeIndex, vectorWriteNode.getLength(), NodeView.DEFAULT);
                VectorNode suffix = AbstractVectorNode.shift(oldVector, suffixIndex, vectorWriteNode, context.getConstantReflection());
                ValueNode materializeLength = IntegerConvertNode.convert(materialize.getLength(), lengthStamp, NodeView.DEFAULT);
                ValueNode suffixLength = BinaryArithmeticNode.sub(graph, materializeLength, suffixIndex, NodeView.DEFAULT);

                VectorNode concat = graph.unique(new ConcatVectorNode(vectorWriteNode.getVector(), vectorWriteNode.getLength(), suffix, suffixLength));
                ValueNode concatLength = BinaryArithmeticNode.sub(graph, materializeLength, writeIndex, NodeView.DEFAULT);

                VectorNode result = graph.unique(new ConcatVectorNode(oldVector, writeIndex, concat, concatLength));

                newMaterialize = graph.add(new LoweredMaterializeVectorNode(materialize.getAllocator(), materialize.stamp(NodeView.DEFAULT), result, materialize.getLength(),
                                materialize.getBaseOffset(), materialize.getElementStride(), vectorWriteNode.getBarrierType()));
            }

            newMaterialize.setStateBefore(materialize.stateBefore());
            newMaterialize.setEmitMemoryBarrier(materialize.emitMemoryBarrier());
            newMaterialize.setVectorLoopMarker(vectorWriteNode.vectorLoopMarker());
            /*
             * We can have vector writes produced by NodeVectorization in compilation pipelines that
             * have floating reads disabled. So in rare cases the lastLocationAccess can be null.
             */
            if (vectorWriteNode.getLastLocationAccess() != null) {
                vectorWriteNode.replaceAtUsages(vectorWriteNode.getLastLocationAccess().asNode(), InputType.Memory);
            }
            HIRBlock block = cfg.blockFor(vectorWriteNode);
            graph.replaceFixedWithFixed(vectorWriteNode, newMaterialize);
            cfg.getNodeToBlock().setAndGrow(newMaterialize, block);
            materialize.replaceAtUsages(newMaterialize);
            graph.removeFixed(materialize);
            materializeQueue.addLast(newMaterialize);
        }
    }
}

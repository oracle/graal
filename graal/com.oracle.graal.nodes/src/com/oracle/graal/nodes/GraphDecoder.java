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
package com.oracle.graal.nodes;

import static com.oracle.graal.compiler.common.GraalInternalError.*;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.util.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.util.*;

/**
 * Decoder for {@link EncodedGraph encoded graphs} produced by {@link GraphEncoder}. Support for
 * loop explosion during decoding is built into this class, because it requires many interactions
 * with the decoding process. Subclasses can provide canonicalization and simplification of nodes
 * during decoding, as well as method inlining during decoding.
 */
public class GraphDecoder {

    public enum LoopExplosionKind {
        /**
         * No loop explosion.
         */
        NONE,
        /**
         * Fully unroll all loops. The loops must have a known finite number of iterations. If a
         * loop has multiple loop ends, they are merged so that the subsequent loop iteration is
         * processed only once. For example, a loop with 4 iterations and 2 loop ends leads to
         * 1+1+1+1 = 4 copies of the loop body. The merge can introduce phi functions.
         */
        FULL_UNROLL,
        /**
         * Fully explode all loops. The loops must have a known finite number of iterations. If a
         * loop has multiple loop ends, they are not merged so that subsequent loop iterations are
         * processed multiple times. For example, a loop with 4 iterations and 2 loop ends leads to
         * 1+2+4+8 = 15 copies of the loop body.
         */
        FULL_EXPLODE,
        /**
         * like {@link #FULL_EXPLODE}, but copies of the loop body that have the exact same state
         * are merged. This reduces the number of copies necessary, but can introduce loops again.
         */
        MERGE_EXPLODE
    }

    /** Decoding state maintained for each encoded graph. */
    protected static class MethodScope {
        /** The target graph where decoded nodes are added to. */
        public final StructuredGraph graph;
        /** The state of the caller method. Only non-null during method inlining. */
        public final MethodScope caller;
        /** The encode graph that is decoded. */
        public final EncodedGraph encodedGraph;
        /** Access to the encoded graph. */
        public final TypeReader reader;
        /**
         * The "table of contents" of the encoded graph, i.e., the mapping from orderId numbers to
         * the offset in the encoded byte[] array.
         */
        public final long[] nodeStartOffsets;
        /** The kind of loop explosion to be performed during decoding. */
        public final LoopExplosionKind loopExplosion;

        /**
         * The start node of the decoded graph. This is a temporary node for inlined graphs that
         * needs to be deleted after inlining.
         */
        public StartNode startNode;
        /** All return nodes encountered during decoding. */
        public final List<ReturnNode> returnNodes;
        /** The exception unwind node encountered during decoding, or null. */
        public UnwindNode unwindNode;

        protected MethodScope(StructuredGraph graph, MethodScope caller, EncodedGraph encodedGraph, LoopExplosionKind loopExplosion) {
            this.graph = graph;
            this.caller = caller;
            this.encodedGraph = encodedGraph;
            this.loopExplosion = loopExplosion;
            this.returnNodes = new ArrayList<>();

            reader = new UnsafeArrayTypeReader(encodedGraph.getEncoding(), encodedGraph.getStartOffset());
            int nodeCount = reader.getUVInt();
            nodeStartOffsets = new long[nodeCount];
            for (int i = 0; i < nodeCount; i++) {
                nodeStartOffsets[i] = encodedGraph.getStartOffset() - reader.getUV();
            }
        }

        public boolean isInlinedMethod() {
            return caller != null;
        }
    }

    /** Decoding state maintained for each loop in the encoded graph. */
    protected static class LoopScope {
        public final LoopScope outer;
        public final int loopDepth;
        public final int loopIteration;
        /**
         * Upcoming loop iterations during loop explosions that have not been processed yet. Only
         * used when {@link MethodScope#loopExplosion} is not {@link LoopExplosionKind#NONE}.
         */
        public Deque<LoopScope> nextIterations;
        /**
         * Information about already processed loop iterations for state merging during loop
         * explosion. Only used when {@link MethodScope#loopExplosion} is
         * {@link LoopExplosionKind#MERGE_EXPLODE}.
         */
        public final Map<LoopExplosionState, LoopExplosionState> iterationStates;
        public final int loopBeginOrderId;
        /**
         * The worklist of fixed nodes to process. Since we already the correct processing order
         * from the orderId, we just set the orderId bit in the bitset when a node is ready for
         * processing. The lowest set bit is the next node to process.
         */
        public final BitSet nodesToProcess;
        /** Nodes that have been created, indexed by the orderId. */
        public final Node[] createdNodes;
        /**
         * Nodes that have been created in outer loop scopes and existed before starting to process
         * this loop, indexed by the orderId.
         */
        public final Node[] initialCreatedNodes;

        protected LoopScope(MethodScope methodScope) {
            this.outer = null;
            this.nextIterations = null;
            this.loopDepth = 0;
            this.loopIteration = 0;
            this.iterationStates = null;
            this.loopBeginOrderId = -1;

            int nodeCount = methodScope.nodeStartOffsets.length;
            this.nodesToProcess = new BitSet(nodeCount);
            this.initialCreatedNodes = new Node[nodeCount];
            this.createdNodes = new Node[nodeCount];
        }

        protected LoopScope(LoopScope outer, int loopDepth, int loopIteration, int loopBeginOrderId, Node[] initialCreatedNodes, Deque<LoopScope> nextIterations,
                        Map<LoopExplosionState, LoopExplosionState> iterationStates) {
            this.outer = outer;
            this.loopDepth = loopDepth;
            this.loopIteration = loopIteration;
            this.nextIterations = nextIterations;
            this.iterationStates = iterationStates;
            this.loopBeginOrderId = loopBeginOrderId;
            this.nodesToProcess = new BitSet(initialCreatedNodes.length);
            this.initialCreatedNodes = initialCreatedNodes;
            this.createdNodes = Arrays.copyOf(initialCreatedNodes, initialCreatedNodes.length);
        }

        @Override
        public String toString() {
            return loopDepth + "," + loopIteration + (loopBeginOrderId == -1 ? "" : "#" + loopBeginOrderId);
        }
    }

    protected static class LoopExplosionState {
        public final FrameState state;
        public final MergeNode merge;
        public final int hashCode;

        protected LoopExplosionState(FrameState state, MergeNode merge) {
            this.state = state;
            this.merge = merge;

            int h = 0;
            for (ValueNode value : state.values()) {
                if (value == null) {
                    h = h * 31 + 1234;
                } else {
                    h = h * 31 + value.hashCode();
                }
            }
            this.hashCode = h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof LoopExplosionState)) {
                return false;
            }

            FrameState otherState = ((LoopExplosionState) obj).state;
            FrameState thisState = state;
            assert thisState.outerFrameState() == otherState.outerFrameState();

            Iterator<ValueNode> thisIter = thisState.values().iterator();
            Iterator<ValueNode> otherIter = otherState.values().iterator();
            while (thisIter.hasNext() && otherIter.hasNext()) {
                ValueNode thisValue = thisIter.next();
                ValueNode otherValue = otherIter.next();
                if (thisValue != otherValue) {
                    return false;
                }
            }
            return thisIter.hasNext() == otherIter.hasNext();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    public final void decode(StructuredGraph graph, EncodedGraph encodedGraph) {
        MethodScope methodScope = new MethodScope(graph, null, encodedGraph, LoopExplosionKind.NONE);
        decode(methodScope);
        cleanupGraph(methodScope);
        methodScope.graph.verify();
    }

    protected final void decode(MethodScope methodScope) {
        LoopScope loopScope = new LoopScope(methodScope);
        if (methodScope.isInlinedMethod()) {
            methodScope.startNode = methodScope.graph.add(new StartNode());
            methodScope.startNode.setNext(makeStubNode(methodScope, loopScope, GraphEncoder.FIRST_NODE_ORDER_ID));
            loopScope.nodesToProcess.set(GraphEncoder.FIRST_NODE_ORDER_ID);
        } else {
            methodScope.startNode = methodScope.graph.start();
            registerNode(loopScope, GraphEncoder.START_NODE_ORDER_ID, methodScope.startNode, false, false);
            loopScope.nodesToProcess.set(GraphEncoder.START_NODE_ORDER_ID);
        }

        while (loopScope != null) {
            while (!loopScope.nodesToProcess.isEmpty()) {
                loopScope = processNextNode(methodScope, loopScope);
            }

            if (loopScope.nextIterations != null && !loopScope.nextIterations.isEmpty()) {
                /* Loop explosion: process the loop iteration. */
                assert loopScope.nextIterations.peekFirst().loopIteration == loopScope.loopIteration + 1;
                loopScope = loopScope.nextIterations.removeFirst();
            } else {
                loopScope = loopScope.outer;
            }
        }

        if (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE) {
            cleanupGraph(methodScope);
            Debug.dump(methodScope.graph, "Before loop detection");
            detectLoops(methodScope.graph, methodScope.startNode);
        }
    }

    protected LoopScope processNextNode(MethodScope methodScope, LoopScope loopScope) {
        int nodeOrderId = loopScope.nodesToProcess.nextSetBit(0);
        loopScope.nodesToProcess.clear(nodeOrderId);

        FixedNode node = (FixedNode) lookupNode(loopScope, nodeOrderId);
        if (node.isDeleted()) {
            return loopScope;
        }

        LoopScope successorAddScope = loopScope;
        boolean updatePredecessors = true;
        if (node instanceof LoopExitNode) {
            successorAddScope = loopScope.outer;
            updatePredecessors = methodScope.loopExplosion == LoopExplosionKind.NONE;
        }

        methodScope.reader.setByteIndex(methodScope.nodeStartOffsets[nodeOrderId]);
        int typeId = methodScope.reader.getUVInt();
        assert node.getNodeClass() == methodScope.encodedGraph.getNodeClasses()[typeId];
        readProperties(methodScope, node);
        makeSuccessorStubs(methodScope, successorAddScope, node, updatePredecessors);
        makeInputNodes(methodScope, loopScope, node, true);

        LoopScope resultScope = loopScope;
        if (node instanceof LoopBeginNode) {
            if (methodScope.loopExplosion != LoopExplosionKind.NONE) {
                handleLoopExplosionBegin(methodScope, loopScope, (LoopBeginNode) node);
            }

        } else if (node instanceof LoopExitNode) {
            if (methodScope.loopExplosion != LoopExplosionKind.NONE) {
                handleLoopExplosionProxyNodes(methodScope, loopScope, (LoopExitNode) node, nodeOrderId);
            } else {
                handleProxyNodes(methodScope, loopScope);
            }

        } else if (node instanceof AbstractEndNode) {
            LoopScope phiInputScope = loopScope;
            LoopScope phiNodeScope = loopScope;

            if (methodScope.loopExplosion != LoopExplosionKind.NONE && node instanceof LoopEndNode) {
                node = handleLoopExplosionEnd(methodScope, loopScope, (LoopEndNode) node);
                phiNodeScope = loopScope.nextIterations.getLast();
            }

            int mergeOrderId = methodScope.reader.getUVInt();
            AbstractMergeNode merge = (AbstractMergeNode) lookupNode(phiNodeScope, mergeOrderId);
            if (merge == null) {
                merge = (AbstractMergeNode) makeStubNode(methodScope, phiNodeScope, mergeOrderId);

                if (merge instanceof LoopBeginNode) {
                    assert phiNodeScope == phiInputScope && phiNodeScope == loopScope;
                    resultScope = new LoopScope(loopScope, loopScope.loopDepth + 1, 0, mergeOrderId, Arrays.copyOf(loopScope.createdNodes, loopScope.createdNodes.length), //
                                    methodScope.loopExplosion != LoopExplosionKind.NONE ? new ArrayDeque<>() : null, //
                                    methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE ? new HashMap<>() : null);
                    phiNodeScope = resultScope;

                    registerNode(phiInputScope, mergeOrderId, null, true, true);
                    phiInputScope.nodesToProcess.clear(mergeOrderId);
                    phiNodeScope.nodesToProcess.set(mergeOrderId);
                }
            }

            handlePhiFunctions(methodScope, phiInputScope, phiNodeScope, (AbstractEndNode) node, merge);

        } else if (node instanceof Invoke) {
            simplifyInvoke(methodScope, loopScope, nodeOrderId, (Invoke) node);

        } else if (node instanceof ReturnNode) {
            methodScope.returnNodes.add((ReturnNode) node);
        } else if (node instanceof UnwindNode) {
            assert methodScope.unwindNode == null : "graph can have only one UnwindNode";
            methodScope.unwindNode = (UnwindNode) node;

        } else {
            simplifyFixedNode(methodScope, loopScope, nodeOrderId, node);
        }

        return resultScope;
    }

    /**
     * Hook for subclasses.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param invokeOrderId The orderId of the method invocation node.
     * @param invoke The invocation node.
     */
    protected void simplifyInvoke(MethodScope methodScope, LoopScope loopScope, int invokeOrderId, Invoke invoke) {
    }

    protected void handleLoopExplosionBegin(MethodScope methodScope, LoopScope loopScope, LoopBeginNode loopBegin) {
        checkLoopExplosionIteration(methodScope, loopScope);

        List<EndNode> predecessors = loopBegin.forwardEnds().snapshot();
        FixedNode successor = loopBegin.next();
        FrameState frameState = loopBegin.stateAfter();

        if (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE) {
            LoopExplosionState queryState = new LoopExplosionState(frameState, null);
            LoopExplosionState existingState = loopScope.iterationStates.get(queryState);
            if (existingState != null) {
                loopBegin.replaceAtUsages(existingState.merge);
                loopBegin.safeDelete();
                successor.safeDelete();
                for (EndNode predecessor : predecessors) {
                    existingState.merge.addForwardEnd(predecessor);
                }
                return;
            }
        }

        MergeNode merge = methodScope.graph.add(new MergeNode());
        loopBegin.replaceAtUsages(merge);
        loopBegin.safeDelete();
        merge.setStateAfter(frameState);
        merge.setNext(successor);
        for (EndNode predecessor : predecessors) {
            merge.addForwardEnd(predecessor);
        }

        if (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE) {
            LoopExplosionState explosionState = new LoopExplosionState(frameState, merge);
            loopScope.iterationStates.put(explosionState, explosionState);
        }
    }

    /**
     * Hook for subclasses.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     */
    protected void checkLoopExplosionIteration(MethodScope methodScope, LoopScope loopScope) {
        throw shouldNotReachHere("when subclass uses loop explosion, it needs to implement this method");
    }

    protected FixedNode handleLoopExplosionEnd(MethodScope methodScope, LoopScope loopScope, LoopEndNode loopEnd) {
        EndNode replacementNode = methodScope.graph.add(new EndNode());
        loopEnd.replaceAtPredecessor(replacementNode);
        loopEnd.safeDelete();

        assert methodScope.loopExplosion != LoopExplosionKind.NONE;
        if (methodScope.loopExplosion != LoopExplosionKind.FULL_UNROLL || loopScope.nextIterations.isEmpty()) {
            int nextIterationNumber = loopScope.nextIterations.isEmpty() ? loopScope.loopIteration + 1 : loopScope.nextIterations.getLast().loopIteration + 1;
            LoopScope nextIterationScope = new LoopScope(loopScope.outer, loopScope.loopDepth, nextIterationNumber, loopScope.loopBeginOrderId, loopScope.initialCreatedNodes,
                            loopScope.nextIterations, loopScope.iterationStates);
            loopScope.nextIterations.addLast(nextIterationScope);
            registerNode(nextIterationScope, loopScope.loopBeginOrderId, null, true, true);
            makeStubNode(methodScope, nextIterationScope, loopScope.loopBeginOrderId);
        }
        return replacementNode;
    }

    /**
     * Hook for subclasses.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param nodeOrderId The orderId of the node.
     * @param node The node to be simplified.
     */
    protected void simplifyFixedNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId, FixedNode node) {
    }

    protected void handleProxyNodes(MethodScope methodScope, LoopScope loopScope) {
        int numProxies = methodScope.reader.getUVInt();
        for (int i = 0; i < numProxies; i++) {
            int proxyOrderId = methodScope.reader.getUVInt();
            ProxyNode proxy = (ProxyNode) ensureNodeCreated(methodScope, loopScope, proxyOrderId);
            /*
             * The ProxyNode transports a value from the loop to the outer scope. We therefore
             * register it in the outer scope.
             */
            registerNode(loopScope.outer, proxyOrderId, proxy, false, false);
        }
    }

    protected void handleLoopExplosionProxyNodes(MethodScope methodScope, LoopScope loopScope, LoopExitNode loopExit, int loopExitOrderId) {
        BeginNode begin = methodScope.graph.add(new BeginNode());

        FrameState loopExitState = loopExit.stateAfter();
        FixedNode loopExitSuccessor = loopExit.next();
        loopExit.replaceAtPredecessor(begin);

        /*
         * In the original graph, the loop exit is not a merge node. Multiple exploded loop
         * iterations now take the same loop exit, so we have to introduce a new merge node to
         * handle the merge.
         */
        MergeNode merge;
        if (lookupNode(loopScope.outer, loopExitOrderId) == null) {
            merge = methodScope.graph.add(new MergeNode());
            registerNode(loopScope.outer, loopExitOrderId, merge, false, false);
            merge.setNext(loopExitSuccessor);
        } else {
            merge = (MergeNode) loopScope.outer.createdNodes[loopExitOrderId];
        }

        FrameState oldStateAfter = merge.stateAfter();
        merge.setStateAfter(loopExitState);
        if (oldStateAfter != null) {
            oldStateAfter.safeDelete();
        }

        EndNode end = methodScope.graph.add(new EndNode());
        begin.setNext(end);
        merge.addForwardEnd(end);

        /*
         * Possibly create phi nodes for the original proxy nodes that flow out of the loop. Note
         * that we definitely do not need a proxy node itself anymore, since the loop was exploded
         * and is no longer present.
         */
        int numProxies = methodScope.reader.getUVInt();
        for (int i = 0; i < numProxies; i++) {
            int proxyOrderId = methodScope.reader.getUVInt();
            ProxyNode proxy = (ProxyNode) ensureNodeCreated(methodScope, loopScope, proxyOrderId);
            ValueNode phiInput = proxy.value();
            ValueNode replacement;

            ValueNode existing = (ValueNode) loopScope.outer.createdNodes[proxyOrderId];
            if (existing == null || existing == phiInput) {
                /*
                 * We are at the first loop exit, or the proxy carries the same value for all exits.
                 * We do not need a phi node yet.
                 */
                registerNode(loopScope.outer, proxyOrderId, phiInput, true, false);
                replacement = phiInput;

            } else if (!merge.isPhiAtMerge(existing)) {
                /* Now we have two different values, so we need to create a phi node. */
                PhiNode phi = methodScope.graph.addWithoutUnique(new ValuePhiNode(proxy.stamp(), merge));
                /* Add the inputs from all previous exits. */
                for (int j = 0; j < merge.phiPredecessorCount() - 1; j++) {
                    phi.addInput(existing);
                }
                /* Add the input from this exit. */
                phi.addInput(phiInput);
                registerNode(loopScope.outer, proxyOrderId, phi, true, false);
                replacement = phi;

            } else {
                /* Phi node has been created before, so just add the new input. */
                PhiNode phi = (PhiNode) existing;
                phi.addInput(phiInput);
                replacement = phi;
            }

            methodScope.graph.replaceFloating(proxy, replacement);
        }

        loopExit.safeDelete();
        assert loopExitSuccessor.predecessor() == null;
        merge.getNodeClass().getSuccessorEdges().update(merge, null, loopExitSuccessor);
    }

    protected void handlePhiFunctions(MethodScope methodScope, LoopScope phiInputScope, LoopScope phiNodeScope, AbstractEndNode end, AbstractMergeNode merge) {

        if (end instanceof LoopEndNode) {
            /*
             * Fix the loop end index and the number of loop ends. When we do canonicalization
             * during decoding, we can end up with fewer ends than the encoded graph had. And the
             * order of loop ends can be different.
             */
            int numEnds = ((LoopBeginNode) merge).loopEnds().count();
            ((LoopBeginNode) merge).nextEndIndex = numEnds;
            ((LoopEndNode) end).endIndex = numEnds - 1;

        } else {
            if (merge.ends == null) {
                merge.ends = new NodeInputList<>(merge);
            }
            merge.addForwardEnd((EndNode) end);
        }

        /*
         * We create most phi functions lazily. Canonicalization and simplification during decoding
         * can lead to dead branches that are not decoded, so we might not need all phi functions
         * that the original graph contained. Since we process all predecessors before actually
         * processing the merge node, we have the final phi function when processing the merge node.
         * The only exception are loop headers of non-exploded loops: since backward branches are
         * not processed yet when processing the loop body, we need to create all phi functions
         * upfront.
         */
        boolean lazyPhi = !(merge instanceof LoopBeginNode) || methodScope.loopExplosion != LoopExplosionKind.NONE;
        int numPhis = methodScope.reader.getUVInt();
        for (int i = 0; i < numPhis; i++) {
            int phiInputOrderId = methodScope.reader.getUVInt();
            int phiNodeOrderId = methodScope.reader.getUVInt();

            ValueNode phiInput = (ValueNode) ensureNodeCreated(methodScope, phiInputScope, phiInputOrderId);

            ValueNode existing = (ValueNode) lookupNode(phiNodeScope, phiNodeOrderId);
            if (lazyPhi && (existing == null || existing == phiInput)) {
                /* Phi function not yet necessary. */
                registerNode(phiNodeScope, phiNodeOrderId, phiInput, true, false);

            } else if (!merge.isPhiAtMerge(existing)) {
                /*
                 * Phi function is necessary. Create it and fill it with existing inputs as well as
                 * the new input.
                 */
                registerNode(phiNodeScope, phiNodeOrderId, null, true, true);
                PhiNode phi = (PhiNode) ensureNodeCreated(methodScope, phiNodeScope, phiNodeOrderId);

                phi.setMerge(merge);
                for (int j = 0; j < merge.phiPredecessorCount() - 1; j++) {
                    phi.addInput(existing);
                }
                phi.addInput(phiInput);

            } else {
                /* Phi node has been created before, so just add the new input. */
                PhiNode phi = (PhiNode) existing;
                phi.addInput(phiInput);
            }
        }
    }

    protected Node instantiateNode(MethodScope methodScope, int nodeOrderId) {
        methodScope.reader.setByteIndex(methodScope.nodeStartOffsets[nodeOrderId]);
        NodeClass<?> nodeClass = methodScope.encodedGraph.getNodeClasses()[methodScope.reader.getUVInt()];
        return nodeClass.allocateInstance();
    }

    protected void readProperties(MethodScope methodScope, Node node) {
        Fields fields = node.getNodeClass().getData();
        for (int pos = 0; pos < fields.getCount(); pos++) {
            if (fields.getType(pos).isPrimitive()) {
                long primitive = methodScope.reader.getSV();
                fields.setRawPrimitive(node, pos, primitive);
            } else {
                int objectId = methodScope.reader.getUVInt();
                Object value = methodScope.encodedGraph.getObjects()[objectId];
                fields.set(node, pos, value);
            }
        }
    }

    /**
     * Process the input edges of a node. Input nodes that have not yet been created must be
     * non-fixed nodes (because fixed nodes are processed in reverse postorder. Such non-fixed nodes
     * are created on demand (recursively since they can themselves reference not yet created
     * nodes).
     */
    protected void makeInputNodes(MethodScope methodScope, LoopScope loopScope, Node node, boolean updateUsages) {
        Edges edges = node.getNodeClass().getEdges(Edges.Type.Inputs);
        for (int index = 0; index < edges.getDirectCount(); index++) {
            if (skipEdge(node, edges, index, true, true)) {
                continue;
            }
            int orderId = methodScope.reader.getUVInt();
            Node value = ensureNodeCreated(methodScope, loopScope, orderId);
            Edges.initializeNode(node, edges.getOffsets(), index, value);
            if (updateUsages && value != null && !value.isDeleted()) {
                edges.update(node, null, value);

            }
        }
        for (int index = edges.getDirectCount(); index < edges.getCount(); index++) {
            if (skipEdge(node, edges, index, false, true)) {
                continue;
            }
            int size = methodScope.reader.getSVInt();
            if (size != -1) {
                NodeList<Node> nodeList = new NodeInputList<>(node, size);
                Edges.initializeList(node, edges.getOffsets(), index, nodeList);
                for (int idx = 0; idx < size; idx++) {
                    int orderId = methodScope.reader.getUVInt();
                    Node value = ensureNodeCreated(methodScope, loopScope, orderId);
                    nodeList.initialize(idx, value);
                    if (updateUsages && value != null && !value.isDeleted()) {
                        edges.update(node, null, value);
                    }
                }
            }
        }
    }

    protected Node ensureNodeCreated(MethodScope methodScope, LoopScope loopScope, int nodeOrderId) {
        if (nodeOrderId == GraphEncoder.NULL_ORDER_ID) {
            return null;
        }
        Node node = lookupNode(loopScope, nodeOrderId);
        if (node != null) {
            return node;
        }

        long readerByteIndex = methodScope.reader.getByteIndex();
        node = instantiateNode(methodScope, nodeOrderId);
        assert !(node instanceof FixedNode);

        /* Read the properties of the node. */
        readProperties(methodScope, node);
        /* There must not be any successors to read, since it is a non-fixed node. */
        assert node.getNodeClass().getEdges(Edges.Type.Successors).getCount() == 0;
        /* Read the inputs of the node, possibly creating them recursively. */
        makeInputNodes(methodScope, loopScope, node, false);
        methodScope.reader.setByteIndex(readerByteIndex);

        if (node instanceof ProxyNode || node instanceof PhiNode) {
            /*
             * We need these nodes as they were in the original graph, without any canonicalization
             * or value numbering.
             */
            node = methodScope.graph.addWithoutUnique(node);

        } else {
            /* Allow subclasses to canonicalize and intercept nodes. */
            node = handleFloatingNodeBeforeAdd(methodScope, loopScope, node);
            if (!node.isAlive()) {
                node = methodScope.graph.addOrUnique(node);
            }
            node = handleFloatingNodeAfterAdd(methodScope, loopScope, node);
        }
        registerNode(loopScope, nodeOrderId, node, false, false);
        return node;
    }

    /**
     * Hook for subclasses to process a non-fixed node before it is added to the graph.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param node The node to be canonicalized.
     * @return The replacement for the node, or the node itself.
     */
    protected Node handleFloatingNodeBeforeAdd(MethodScope methodScope, LoopScope loopScope, Node node) {
        return node;
    }

    /**
     * Hook for subclasses to process a non-fixed node after it is added to the graph.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param node The node to be canonicalized.
     * @return The replacement for the node, or the node itself.
     */
    protected Node handleFloatingNodeAfterAdd(MethodScope methodScope, LoopScope loopScope, Node node) {
        return node;
    }

    /**
     * Process successor edges of a node. We create the successor nodes so that we can fill the
     * successor list, but no properties or edges are loaded yet. That is done when the successor is
     * on top of the worklist in {@link #processNextNode}.
     */
    protected void makeSuccessorStubs(MethodScope methodScope, LoopScope loopScope, Node node, boolean updatePredecessors) {
        Edges edges = node.getNodeClass().getEdges(Edges.Type.Successors);
        for (int index = 0; index < edges.getDirectCount(); index++) {
            if (skipEdge(node, edges, index, true, true)) {
                continue;
            }
            int orderId = methodScope.reader.getUVInt();
            Node value = makeStubNode(methodScope, loopScope, orderId);
            Edges.initializeNode(node, edges.getOffsets(), index, value);
            if (updatePredecessors && value != null) {
                edges.update(node, null, value);
            }
        }
        for (int index = edges.getDirectCount(); index < edges.getCount(); index++) {
            if (skipEdge(node, edges, index, false, true)) {
                continue;
            }
            int size = methodScope.reader.getSVInt();
            if (size != -1) {
                NodeList<Node> nodeList = new NodeSuccessorList<>(node, size);
                Edges.initializeList(node, edges.getOffsets(), index, nodeList);
                for (int idx = 0; idx < size; idx++) {
                    int orderId = methodScope.reader.getUVInt();
                    Node value = makeStubNode(methodScope, loopScope, orderId);
                    nodeList.initialize(idx, value);
                    if (updatePredecessors && value != null) {
                        edges.update(node, null, value);
                    }
                }
            }
        }
    }

    protected FixedNode makeStubNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId) {
        if (nodeOrderId == GraphEncoder.NULL_ORDER_ID) {
            return null;
        }
        FixedNode node = (FixedNode) lookupNode(loopScope, nodeOrderId);
        if (node != null) {
            return node;
        }

        long readerByteIndex = methodScope.reader.getByteIndex();
        node = (FixedNode) methodScope.graph.add(instantiateNode(methodScope, nodeOrderId));
        /* Properties and edges are not filled yet, the node remains uninitialized. */
        methodScope.reader.setByteIndex(readerByteIndex);

        registerNode(loopScope, nodeOrderId, node, false, false);
        loopScope.nodesToProcess.set(nodeOrderId);
        return node;
    }

    /**
     * Returns false for {@link Edges} that are not necessary in the encoded graph because they are
     * reconstructed using other sources of information.
     */
    protected static boolean skipEdge(Node node, Edges edges, int index, boolean direct, boolean decode) {
        if (node instanceof PhiNode) {
            /* The inputs of phi functions are filled manually when the end nodes are processed. */
            assert edges.type() == Edges.Type.Inputs;
            if (direct) {
                assert index == edges.getDirectCount() - 1 : "PhiNode has one direct input (the MergeNode)";
            } else {
                assert index == edges.getCount() - 1 : "PhiNode has one variable size input (the values)";
                if (decode) {
                    /* The values must not be null, so initialize with an empty list. */
                    Edges.initializeList(node, edges.getOffsets(), index, new NodeInputList<>(node));
                }
            }
            return true;

        } else if (node instanceof AbstractMergeNode && edges.type() == Edges.Type.Inputs && !direct) {
            /* The ends of merge nodes are filled manually when the ends are processed. */
            assert index == edges.getCount() - 1 : "MergeNode has one variable size input (the ends)";
            assert Edges.getNodeList(node, edges.getOffsets(), index) != null : "Input list must have been already created";
            return true;
        }
        return false;
    }

    protected Node lookupNode(LoopScope loopScope, int nodeOrderId) {
        return loopScope.createdNodes[nodeOrderId];
    }

    protected void registerNode(LoopScope loopScope, int nodeOrderId, Node node, boolean allowOverwrite, boolean allowNull) {
        assert node == null || node.isAlive();
        assert allowNull || node != null;
        assert allowOverwrite || lookupNode(loopScope, nodeOrderId) == null;
        loopScope.createdNodes[nodeOrderId] = node;
    }

    /*
     * The following methods are a literal copy from GraphBuilderPhase.
     */

    protected void detectLoops(StructuredGraph currentGraph, FixedNode startInstruction) {
        NodeBitMap visited = currentGraph.createNodeBitMap();
        NodeBitMap active = currentGraph.createNodeBitMap();
        Deque<Node> stack = new ArrayDeque<>();
        stack.add(startInstruction);
        visited.mark(startInstruction);
        while (!stack.isEmpty()) {
            Node next = stack.peek();
            assert next.isDeleted() || visited.isMarked(next);
            if (next.isDeleted() || active.isMarked(next)) {
                stack.pop();
                if (!next.isDeleted()) {
                    active.clear(next);
                }
            } else {
                active.mark(next);
                for (Node n : next.cfgSuccessors()) {
                    if (active.contains(n)) {
                        // Detected cycle.
                        assert n instanceof MergeNode;
                        assert next instanceof EndNode;
                        MergeNode merge = (MergeNode) n;
                        EndNode endNode = (EndNode) next;
                        merge.removeEnd(endNode);
                        FixedNode afterMerge = merge.next();
                        if (!(afterMerge instanceof EndNode) || !(((EndNode) afterMerge).merge() instanceof LoopBeginNode)) {
                            FrameState stateAfter = merge.stateAfter();
                            merge.setNext(null);
                            merge.setStateAfter(null);
                            LoopBeginNode newLoopBegin = appendLoopBegin(currentGraph, merge);
                            newLoopBegin.setNext(afterMerge);
                            newLoopBegin.setStateAfter(stateAfter);
                        }
                        LoopBeginNode loopBegin = (LoopBeginNode) ((EndNode) merge.next()).merge();
                        LoopEndNode loopEnd = currentGraph.add(new LoopEndNode(loopBegin));
                        endNode.replaceAndDelete(loopEnd);
                    } else if (visited.contains(n)) {
                        // Normal merge into a branch we are already exploring.
                    } else {
                        visited.mark(n);
                        stack.push(n);
                    }
                }
            }
        }

        Debug.dump(currentGraph, "After loops detected");
        insertLoopEnds(currentGraph, startInstruction);
    }

    private static LoopBeginNode appendLoopBegin(StructuredGraph currentGraph, FixedWithNextNode fixedWithNext) {
        EndNode preLoopEnd = currentGraph.add(new EndNode());
        LoopBeginNode loopBegin = currentGraph.add(new LoopBeginNode());
        fixedWithNext.setNext(preLoopEnd);
        // Add the single non-loop predecessor of the loop header.
        loopBegin.addForwardEnd(preLoopEnd);
        return loopBegin;
    }

    private static void insertLoopEnds(StructuredGraph currentGraph, FixedNode startInstruction) {
        NodeBitMap visited = currentGraph.createNodeBitMap();
        Deque<Node> stack = new ArrayDeque<>();
        stack.add(startInstruction);
        visited.mark(startInstruction);
        List<LoopBeginNode> loopBegins = new ArrayList<>();
        while (!stack.isEmpty()) {
            Node next = stack.pop();
            assert visited.isMarked(next);
            if (next instanceof LoopBeginNode) {
                loopBegins.add((LoopBeginNode) next);
            }
            for (Node n : next.cfgSuccessors()) {
                if (visited.contains(n)) {
                    // Nothing to do.
                } else {
                    visited.mark(n);
                    stack.push(n);
                }
            }
        }

        IdentityHashMap<LoopBeginNode, List<LoopBeginNode>> innerLoopsMap = new IdentityHashMap<>();
        for (int i = loopBegins.size() - 1; i >= 0; --i) {
            LoopBeginNode loopBegin = loopBegins.get(i);
            insertLoopExits(currentGraph, loopBegin, innerLoopsMap);
        }

        // Remove degenerated merges with only one predecessor.
        for (LoopBeginNode loopBegin : loopBegins) {
            Node pred = loopBegin.forwardEnd().predecessor();
            if (pred instanceof MergeNode) {
                MergeNode.removeMergeIfDegenerated((MergeNode) pred);
            }
        }
    }

    private static void insertLoopExits(StructuredGraph currentGraph, LoopBeginNode loopBegin, IdentityHashMap<LoopBeginNode, List<LoopBeginNode>> innerLoopsMap) {
        NodeBitMap visited = currentGraph.createNodeBitMap();
        Deque<Node> stack = new ArrayDeque<>();
        for (LoopEndNode loopEnd : loopBegin.loopEnds()) {
            stack.push(loopEnd);
            visited.mark(loopEnd);
        }

        List<ControlSplitNode> controlSplits = new ArrayList<>();
        List<LoopBeginNode> innerLoopBegins = new ArrayList<>();

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if (current == loopBegin) {
                continue;
            }
            for (Node pred : current.cfgPredecessors()) {
                if (!visited.isMarked(pred)) {
                    visited.mark(pred);
                    if (pred instanceof LoopExitNode) {
                        // Inner loop
                        LoopExitNode loopExitNode = (LoopExitNode) pred;
                        LoopBeginNode innerLoopBegin = loopExitNode.loopBegin();
                        if (!visited.isMarked(innerLoopBegin)) {
                            stack.push(innerLoopBegin);
                            visited.mark(innerLoopBegin);
                            innerLoopBegins.add(innerLoopBegin);
                        }
                    } else {
                        if (pred instanceof ControlSplitNode) {
                            ControlSplitNode controlSplitNode = (ControlSplitNode) pred;
                            controlSplits.add(controlSplitNode);
                        }
                        stack.push(pred);
                    }
                }
            }
        }

        for (ControlSplitNode controlSplit : controlSplits) {
            for (Node succ : controlSplit.cfgSuccessors()) {
                if (!visited.isMarked(succ)) {
                    LoopExitNode loopExit = currentGraph.add(new LoopExitNode(loopBegin));
                    FixedNode next = ((FixedWithNextNode) succ).next();
                    next.replaceAtPredecessor(loopExit);
                    loopExit.setNext(next);
                }
            }
        }

        for (LoopBeginNode inner : innerLoopBegins) {
            addLoopExits(currentGraph, loopBegin, inner, innerLoopsMap, visited);
        }

        innerLoopsMap.put(loopBegin, innerLoopBegins);
    }

    private static void addLoopExits(StructuredGraph currentGraph, LoopBeginNode loopBegin, LoopBeginNode inner, IdentityHashMap<LoopBeginNode, List<LoopBeginNode>> innerLoopsMap, NodeBitMap visited) {
        for (LoopExitNode exit : inner.loopExits()) {
            if (!visited.isMarked(exit)) {
                LoopExitNode newLoopExit = currentGraph.add(new LoopExitNode(loopBegin));
                FixedNode next = exit.next();
                next.replaceAtPredecessor(newLoopExit);
                newLoopExit.setNext(next);
            }
        }

        for (LoopBeginNode innerInner : innerLoopsMap.get(inner)) {
            addLoopExits(currentGraph, loopBegin, innerInner, innerLoopsMap, visited);
        }
    }

    protected void cleanupGraph(MethodScope methodScope) {
        assert verifyEdges(methodScope);

        Debug.dump(methodScope.graph, "Before removing redundant merges");
        for (MergeNode mergeNode : methodScope.graph.getNodes(MergeNode.TYPE)) {
            if (mergeNode.forwardEndCount() == 1) {
                methodScope.graph.reduceTrivialMerge(mergeNode);
            }
        }

        Debug.dump(methodScope.graph, "Before removing redundant begins");
        for (Node node : methodScope.graph.getNodes()) {
            if (node instanceof BeginNode || node instanceof KillingBeginNode) {
                if (!(node.predecessor() instanceof ControlSplitNode) && node.hasNoUsages()) {
                    GraphUtil.unlinkFixedNode((AbstractBeginNode) node);
                    node.safeDelete();
                }
            }
        }

        Debug.dump(methodScope.graph, "Before removing unused non-fixed nodes");
        for (Node node : methodScope.graph.getNodes()) {
            if (!(node instanceof FixedNode) && node.hasNoUsages()) {
                GraphUtil.killCFG(node);
            }
        }
    }

    protected boolean verifyEdges(MethodScope methodScope) {
        for (Node node : methodScope.graph.getNodes()) {
            assert node.isAlive();
            node.acceptInputs((n, i) -> {
                assert i.isAlive();
                assert i.usages().contains(n);
            });
            node.acceptSuccessors((n, s) -> {
                assert s.isAlive();
                assert s.predecessor() == n;
            });

            for (Node usage : node.usages()) {
                assert usage.isAlive();
                assert usage.inputs().contains(node);
            }
            if (node.predecessor() != null) {
                assert node.predecessor().isAlive();
                assert node.predecessor().successors().contains(node);
            }
        }
        return true;
    }
}

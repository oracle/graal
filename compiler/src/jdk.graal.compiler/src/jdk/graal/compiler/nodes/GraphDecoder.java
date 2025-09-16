/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.debug.GraalError.shouldNotReachHere;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.util.TypeReader;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeReader;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Edges;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeList;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.NodeSuccessorList;
import jdk.graal.compiler.nodes.GraphDecoder.MethodScope;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.MethodHandleWithExceptionNode;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Decoder for {@link EncodedGraph encoded graphs} produced by {@link GraphEncoder}. Support for
 * loop explosion during decoding is built into this class, because it requires many interactions
 * with the decoding process. Subclasses can provide canonicalization and simplification of nodes
 * during decoding, as well as method inlining during decoding.
 */
public class GraphDecoder {

    /** Decoding state maintained for each encoded graph. */
    protected class MethodScope {
        /** The loop that contains the call. Only non-null during method inlining. */
        public final LoopScope callerLoopScope;
        /**
         * Mark for nodes that were present before the decoding of this method started. Note that
         * nodes that were decoded after the mark can still be part of an outer method, since
         * floating nodes of outer methods are decoded lazily.
         */
        public final Graph.Mark methodStartMark;
        /** The encode graph that is decoded. */
        public final EncodedGraph encodedGraph;
        /** The highest node order id that a fixed node has in the EncodedGraph. */
        public final int maxFixedNodeOrderId;
        /**
         * Number of bytes needed to encode an order id (order ids have a per-encoded-graph fixed
         * size).
         */
        public final int orderIdWidth;
        /** Access to the encoded graph. */
        public final TypeReader reader;
        /** The kind of loop explosion to be performed during decoding. */
        public final LoopExplosionPlugin.LoopExplosionKind loopExplosion;

        /** All return nodes encountered during decoding. */
        public final List<ControlSinkNode> returnAndUnwindNodes;

        /** All merges created during loop explosion. */
        public final EconomicSet<Node> loopExplosionMerges;

        /**
         * The start of explosion, and the merge point for when irreducible loops are detected. Only
         * used when {@link MethodScope#loopExplosion} is
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#MERGE_EXPLODE}.
         */
        public MergeNode loopExplosionHead;

        /**
         * The decoded inlining log. If this is the root method scope, it
         * {@link StructuredGraph#setInliningLog replaces} the inlining log of the graph. Otherwise,
         * the inlining log is {@link InliningLog#inlineByTransfer transferred} to the caller.
         */
        public InliningLog inliningLog;

        /**
         * The decoded optimization log. If this is the root method scope, it
         * {@link StructuredGraph#setOptimizationLog replaces} the optimization log of the graph.
         * Otherwise, the optimization log is {@link OptimizationLog#inline inlined} in the caller.
         */
        public OptimizationLog optimizationLog;

        /**
         * The stateful decoder for the {@link #inliningLog}, which is needed to map order IDs back
         * to decoded graph nodes. The decoder is also responsible for tracking new callsites in the
         * inlining log. {@code null} if the inlining log is not being decoded.
         */
        public InliningLogCodec.InliningLogDecoder inliningLogDecoder;

        @SuppressWarnings("unchecked")
        protected MethodScope(LoopScope callerLoopScope, StructuredGraph graph, EncodedGraph encodedGraph, LoopExplosionPlugin.LoopExplosionKind loopExplosion) {
            this.callerLoopScope = callerLoopScope;
            this.methodStartMark = graph.getMark();
            this.encodedGraph = encodedGraph;
            this.loopExplosion = loopExplosion;
            this.returnAndUnwindNodes = new ArrayList<>(2);

            if (encodedGraph != null) {
                reader = UnsafeArrayTypeReader.create(encodedGraph.getEncoding(), encodedGraph.getStartOffset(), architecture.supportsUnalignedMemoryAccess());
                maxFixedNodeOrderId = reader.getUVInt();
                GraphState.GuardsStage guardsStage = (GraphState.GuardsStage) readObject(this);
                EnumSet<GraphState.StageFlag> stageFlags = (EnumSet<GraphState.StageFlag>) readObject(this);
                if (callerLoopScope == null) {
                    /**
                     * Only propagate stage flags in non-inlining scenarios. If the caller scope has
                     * not been guard lowered yet (or is a runtime compilation) while we inline
                     * something that has been guard lowered already (or is an encoded hosted graph
                     * like a snippet) do not advance stage flags or guards stage.
                     */
                    graph.getGraphState().setGuardsStage(guardsStage);
                    graph.getGraphState().getStageFlags().addAll(stageFlags);
                }

                var decoderPair = InliningLogCodec.maybeDecode(graph, readObject(this));
                if (decoderPair != null) {
                    inliningLogDecoder = decoderPair.getLeft();
                    inliningLog = decoderPair.getRight();
                }
                optimizationLog = OptimizationLogCodec.maybeDecode(graph, readObject(this));

                int nodeCount = reader.getUVInt();
                if (encodedGraph.nodeStartOffsets == null) {
                    int[] nodeStartOffsets = new int[nodeCount];
                    for (int i = 0; i < nodeCount; i++) {
                        nodeStartOffsets[i] = encodedGraph.getStartOffset() - reader.getUVInt();
                    }
                    encodedGraph.nodeStartOffsets = nodeStartOffsets;
                }

                if (nodeCount <= GraphEncoder.MAX_INDEX_1_BYTE) {
                    orderIdWidth = 1;
                } else if (nodeCount <= GraphEncoder.MAX_INDEX_2_BYTES) {
                    orderIdWidth = 2;
                } else {
                    orderIdWidth = 4;
                }
            } else {
                reader = null;
                maxFixedNodeOrderId = 0;
                orderIdWidth = 0;
            }

            if (loopExplosion.useExplosion()) {
                loopExplosionMerges = EconomicSet.create(Equivalence.IDENTITY);
            } else {
                loopExplosionMerges = null;
            }
        }

        public boolean isInlinedMethod() {
            return false;
        }

        public NodeSourcePosition getCallerNodeSourcePosition() {
            return null;
        }

        public NodeSourcePosition getNodeSourcePosition(NodeSourcePosition position) {
            return position;
        }

        /**
         * Sets the {@link #inliningLog} and {@link #optimizationLog} as the logs of the
         * {@link #graph} if they are non-null.
         */
        public void replaceLogsForDecodedGraph() {
            if (inliningLog != null) {
                graph.setInliningLog(inliningLog);
            }
            if (optimizationLog != null) {
                graph.setOptimizationLog(optimizationLog);
            }
        }
    }

    /**
     * Marker to distinguish the reasons for the creation of a loop scope during partial evaluation.
     */
    public enum LoopScopeTrigger {
        /**
         * Start loop scope: creation triggered manually at the beginning of partial evaluation.
         */
        START,

        /**
         * Loop scope created for the next iteration of a loop if unrolling is enabled in the loop
         * explosion mode. See
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#unrollLoops()}
         * for details. Loop unrolling will merge loop end nodes for each iteration of the original
         * loop.
         */
        LOOP_BEGIN_UNROLLING,

        /**
         * Loop scope created for the next iteration of a loop along a particular loop end node if
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#duplicateLoopEnds()}
         * is enabled and loops are exploded. This means for every loop end we duplicate the next
         * loop iteration of the original loop.
         */
        LOOP_END_DUPLICATION,

        /**
         * Loop scope created for a loop exit node if
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#duplicateLoopExits()}
         * is enabled, i.e., code after a loop exit is duplicated per loop exit node.
         *
         * Special case nested loops: For compilation units with nested loops where inner loops
         * continue loops at a level n -1 the partial evaluation algorithm will merge outer loops to
         * avoid loop explosion along loop end nodes (which would be the same as
         * {@link #LOOP_END_DUPLICATION}.
         */
        LOOP_EXIT_DUPLICATION
    }

    /** Decoding state maintained for each loop in the encoded graph. */
    protected static class LoopScope {
        public final MethodScope methodScope;
        public final LoopScope outer;
        public final int loopDepth;
        public final int loopIteration;

        /**
         * Creation trigger of this particular loop scope, i.e., the reason it was created.
         */
        final LoopScopeTrigger trigger;
        /**
         * Upcoming, not yet processed, loop iterations created in the context of code duplication
         * along loop exits. Only used when {@link MethodScope#loopExplosion} has
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#duplicateLoopExits()}
         * enabled.
         */
        public Deque<LoopScope> nextIterationFromLoopExitDuplication;
        /**
         * Same as {@link #nextIterationFromLoopExitDuplication} except that upcoming iterations
         * have been created because the duplication of loop ends
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#duplicateLoopEnds()}
         * is enabled.
         */
        public Deque<LoopScope> nextIterationFromLoopEndDuplication;
        /**
         * Same as {@link #nextIterationFromLoopExitDuplication} except that upcoming iterations
         * have been created because the unrolling of a loop with constant iteration count
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#unrollLoops()}
         * is enabled.
         */
        public Deque<LoopScope> nextIterationsFromUnrolling;
        /**
         * Information about already processed loop iterations for state merging during loop
         * explosion. Only used when {@link MethodScope#loopExplosion} is
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#MERGE_EXPLODE}.
         */
        public final EconomicMap<LoopExplosionState, LoopExplosionState> iterationStates;
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
         * this loop, indexed by the orderId. Only used when {@link MethodScope#loopExplosion} is
         * not
         * {@link jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind#NONE}.
         */
        public final Node[] initialCreatedNodes;

        /**
         * The written set of all nodes in this loop scope. Each bit represents a node orderId. If
         * the bit is set, the node orderId has been written in this scope and needs to be read from
         * the {@link LoopScope#createdNodes}. If the bit is not set, the node orderId has not been
         * written in this scope and can be read from the {@link LoopScope#initialCreatedNodes}. The
         * written set is null if it is not used in this loop scope.
         */
        public final BitSet writtenNodes;

        protected LoopScope(MethodScope methodScope) {
            this.methodScope = methodScope;
            this.outer = null;
            this.nextIterationFromLoopExitDuplication = methodScope.loopExplosion.duplicateLoopExits() || methodScope.loopExplosion.mergeLoops() ? new ArrayDeque<>(2) : null;
            this.nextIterationFromLoopEndDuplication = methodScope.loopExplosion.duplicateLoopEnds() ? new ArrayDeque<>(2) : null;
            this.nextIterationsFromUnrolling = methodScope.loopExplosion.unrollLoops() ? new ArrayDeque<>(2) : null;
            this.loopDepth = 0;
            this.loopIteration = 0;
            this.iterationStates = null;
            this.loopBeginOrderId = -1;
            int nodeCount = methodScope.encodedGraph.nodeStartOffsets.length;
            this.nodesToProcess = new BitSet(methodScope.maxFixedNodeOrderId);
            this.createdNodes = new Node[nodeCount];
            this.initialCreatedNodes = null;
            this.trigger = LoopScopeTrigger.START;
            this.writtenNodes = null;
        }

        /**
         * Creates a new loop scope that uses a written set. As long as node orderIds are not
         * written in this scope, the nodes are read from the initial nodes
         * ({@link LoopScope#initialCreatedNodes}). As soon as a node orderId is written, its node
         * is stored in the created nodes ({@link LoopScope#createdNodes}) and reads access the
         * created nodes instead of the initial nodes.
         */
        protected LoopScope(MethodScope methodScope, LoopScope outer, int loopDepth, int loopIteration, int loopBeginOrderId, LoopScopeTrigger trigger, Node[] initialCreatedNodes, Node[] createdNodes,
                        Deque<LoopScope> nextIterationFromLoopExitDuplication,
                        Deque<LoopScope> nextIterationFromLoopEndDuplication,
                        Deque<LoopScope> nextIterationsFromUnrolling, EconomicMap<LoopExplosionState, LoopExplosionState> iterationStates) {
            this(methodScope, outer, loopDepth, loopIteration, loopBeginOrderId, trigger, initialCreatedNodes, createdNodes, nextIterationFromLoopExitDuplication, nextIterationFromLoopEndDuplication,
                            nextIterationsFromUnrolling, iterationStates, true);
        }

        /**
         * Creates a new loop scope that uses a written set based on {@code reuseInitialNodes}. If
         * {@code reuseInitialNodes} is {@code true}, as long as node orderIds are not written in
         * this scope, the nodes are read from the initial nodes
         * ({@link LoopScope#initialCreatedNodes}). As soon as a node orderId is written, its node
         * is stored in the created nodes ({@link LoopScope#createdNodes}) and reads access the
         * created nodes instead of the initial nodes. If {@code reuseInitialNodes} is
         * {@code false}, all read accesses use the created nodes.
         */
        protected LoopScope(MethodScope methodScope, LoopScope outer, int loopDepth, int loopIteration, int loopBeginOrderId, LoopScopeTrigger trigger, Node[] initialCreatedNodes, Node[] createdNodes,
                        Deque<LoopScope> nextIterationFromLoopExitDuplication,
                        Deque<LoopScope> nextIterationFromLoopEndDuplication,
                        Deque<LoopScope> nextIterationsFromUnrolling, EconomicMap<LoopExplosionState, LoopExplosionState> iterationStates,
                        boolean reuseInitialNodes) {
            this.methodScope = methodScope;
            this.outer = outer;
            this.loopDepth = loopDepth;
            this.loopIteration = loopIteration;
            this.trigger = trigger;
            this.nextIterationFromLoopExitDuplication = nextIterationFromLoopExitDuplication;
            this.nextIterationFromLoopEndDuplication = nextIterationFromLoopEndDuplication;
            this.nextIterationsFromUnrolling = nextIterationsFromUnrolling;
            this.iterationStates = iterationStates;
            this.loopBeginOrderId = loopBeginOrderId;
            this.nodesToProcess = new BitSet(methodScope.maxFixedNodeOrderId);
            this.initialCreatedNodes = initialCreatedNodes;
            this.createdNodes = createdNodes;
            if (reuseInitialNodes && initialCreatedNodes != null) {
                this.writtenNodes = new BitSet(createdNodes.length);
            } else {
                this.writtenNodes = null;
            }
        }

        /**
         * Sets a node in the initial nodes of this scope and sets the written status of the node
         * orderId to {@code false}. Reads of the node orderId after calling this method will access
         * the initial nodes instead of the created nodes.
         *
         * @param nodeOrderId The orderId of the node
         * @param node The node to be set in the initial nodes
         */
        public void clearNode(int nodeOrderId, Node node) {
            if (writtenNodes != null) {
                writtenNodes.clear(nodeOrderId);
            }
            initialCreatedNodes[nodeOrderId] = node;
        }

        /**
         * Sets a node in the created nodes of this scope and sets the written status of the node
         * orderId to {@code true}. Reads of the node orderId after calling this method will access
         * the created nodes instead of the initial nodes.
         *
         * @param nodeOrderId The orderId of the node
         * @param node The node to be set in the created nodes
         */
        public void setNode(int nodeOrderId, Node node) {
            if (writtenNodes != null) {
                writtenNodes.set(nodeOrderId);
            }
            createdNodes[nodeOrderId] = node;
        }

        /**
         * Copies the nodes in the given array into the created nodes and sets the written status
         * for all node orderIds to {@code true}. The nodes are copied to the node orderIds in
         * consecutive ascending order, starting with the {@code firstNodeOrderId}.
         *
         * @param firstNodeOrderId The first node orderId (Node orderId of the first element in the
         *            array)
         * @param nodes The nodes to be copied into the created nodes
         */
        public void setNodes(int firstNodeOrderId, Node[] nodes) {
            final int length = nodes.length;
            if (writtenNodes != null) {
                writtenNodes.set(firstNodeOrderId, firstNodeOrderId + length);
            }
            System.arraycopy(nodes, 0, createdNodes, firstNodeOrderId, length);
        }

        /**
         * Gets the node for the given node orderId.
         *
         * @param nodeOrderId The node orderId to retrieve
         * @return The node with the given node orderId. If the written status of the node orderId
         *         is {@code true} or the scope does not use a written set, the node is read from
         *         the created nodes. Otherwise, it is read form the initial nodes.
         */
        public Node getNode(int nodeOrderId) {
            if (writtenNodes == null || writtenNodes.get(nodeOrderId)) {
                return createdNodes[nodeOrderId];
            } else {
                assert initialCreatedNodes != null : "Initial nodes not initialized when using written set inside loop scope";
                return initialCreatedNodes[nodeOrderId];
            }
        }

        /**
         * Since the created nodes are only updated when written in this scope, this method combines
         * the nodes of the initial nodes and created nodes into a single array that contains the
         * latest written node for each node orderId. If this scope does not use a written set, this
         * method returns the current created nodes without copying them into a new array.
         * Otherwise, a new array combining initial and created nodes is created.
         *
         * @return The combination of initial and created nodes.
         */
        public Node[] materializeCreatedNodes() {
            if (initialCreatedNodes == null || writtenNodes == null) {
                return createdNodes;
            } else {
                final Node[] nodes = Arrays.copyOf(initialCreatedNodes, createdNodes.length);
                int i = writtenNodes.nextSetBit(0);
                while (i != -1) {
                    nodes[i] = createdNodes[i];
                    i = writtenNodes.nextSetBit(i + 1);
                }
                return nodes;
            }
        }

        @Override
        public String toString() {
            return loopDepth + "," + loopIteration + (loopBeginOrderId == -1 ? "" : "#" + loopBeginOrderId) + " triggered by " + trigger;
        }

        /**
         * Determines if iterations generated when decoding this loop have yet to be processed.
         *
         * @return {@code true} if there are iterations to be decoded, {@code false} else
         */
        public boolean hasIterationsToProcess() {
            return nextIterationFromLoopEndDuplication != null && !nextIterationFromLoopEndDuplication.isEmpty() ||
                            nextIterationFromLoopExitDuplication != null && !nextIterationFromLoopExitDuplication.isEmpty() ||
                            nextIterationsFromUnrolling != null && !nextIterationsFromUnrolling.isEmpty();
        }

        /**
         * Return the next iteration yet to be processed that has been created in the context of
         * decoding this loop scope.
         *
         * @param remove determines if the query of the next iteration should remove it from the
         *            list of iterations to be processed
         * @return the next {@link LoopScope} to be processed that has been created in the context
         *         of decoding this loop scope. Note that the order is not necessarily reflecting
         *         the number of loop iterations.
         */
        public LoopScope getNextIterationToProcess(boolean remove) {
            if (nextIterationFromLoopEndDuplication != null && !nextIterationFromLoopEndDuplication.isEmpty()) {
                return remove ? nextIterationFromLoopEndDuplication.removeFirst() : nextIterationFromLoopEndDuplication.peekFirst();
            }
            if (nextIterationFromLoopExitDuplication != null && !nextIterationFromLoopExitDuplication.isEmpty()) {
                return remove ? nextIterationFromLoopExitDuplication.removeFirst() : nextIterationFromLoopExitDuplication.peekFirst();
            }
            if (nextIterationsFromUnrolling != null && !nextIterationsFromUnrolling.isEmpty()) {
                return remove ? nextIterationsFromUnrolling.removeFirst() : nextIterationsFromUnrolling.peekFirst();
            }
            return null;
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
            if (!(obj instanceof LoopExplosionState other)) {
                return false;
            }

            // Check the hash code first to avoid iterating the frame states.
            if (hashCode != other.hashCode) {
                return false;
            }

            final FrameState thisState = state;
            final FrameState otherState = other.state;
            assert thisState.outerFrameState() == otherState.outerFrameState() : Assertions.errorMessage(thisState, thisState.outerFrameState(), otherState, otherState.outerFrameState());

            final NodeInputList<ValueNode> thisValues = thisState.values();
            final NodeInputList<ValueNode> otherValues = otherState.values();

            final int size = thisValues.size();
            if (size != otherValues.size()) {
                return false;
            }

            for (int i = 0; i < size; i++) {
                final ValueNode thisValue = thisValues.get(i);
                final ValueNode otherValue = otherValues.get(i);

                if (thisValue != otherValue) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    protected static class InvokableData<T extends Invokable> {
        public final T invoke;
        public final ResolvedJavaType contextType;
        public final int orderId;
        public final int stateAfterOrderId;
        public final int nextOrderId;
        public final int exceptionOrderId;
        public final int exceptionStateOrderId;
        public final int exceptionNextOrderId;

        protected InvokableData(T invoke, ResolvedJavaType contextType, int orderId, int stateAfterOrderId, int nextOrderId,
                        int exceptionOrderId, int exceptionStateOrderId, int exceptionNextOrderId) {
            this.invoke = invoke;
            this.contextType = contextType;
            this.orderId = orderId;
            this.stateAfterOrderId = stateAfterOrderId;
            this.nextOrderId = nextOrderId;
            this.exceptionOrderId = exceptionOrderId;
            this.exceptionStateOrderId = exceptionStateOrderId;
            this.exceptionNextOrderId = exceptionNextOrderId;
        }
    }

    /**
     * Additional information encoded for {@link Invoke} nodes to allow method inlining without
     * decoding the frame state and successors beforehand.
     */
    protected static class InvokeData extends InvokableData<Invoke> {
        static InvokeData createFrom(InvokableData<? extends Invoke> from, int callTargetOrderId, boolean intrinsifiedMethodHandle) {
            return new InvokeData(from.invoke, from.contextType, from.orderId, callTargetOrderId, intrinsifiedMethodHandle,
                            from.stateAfterOrderId, from.nextOrderId, from.exceptionOrderId, from.exceptionStateOrderId, from.exceptionNextOrderId);
        }

        public final int callTargetOrderId;
        public final boolean intrinsifiedMethodHandle;

        public JavaConstant constantReceiver;
        public CallTargetNode callTarget;
        public FixedWithNextNode invokePredecessor;

        public InvokeData(Invoke invoke, ResolvedJavaType contextType, int invokeOrderId, int callTargetOrderId, boolean intrinsifiedMethodHandle,
                        int stateAfterOrderId, int nextOrderId, int exceptionOrderId, int exceptionStateOrderId, int exceptionNextOrderId) {
            super(invoke, contextType, invokeOrderId, stateAfterOrderId, nextOrderId, exceptionOrderId, exceptionStateOrderId, exceptionNextOrderId);
            this.callTargetOrderId = callTargetOrderId;
            this.intrinsifiedMethodHandle = intrinsifiedMethodHandle;
        }
    }

    private static final TimerKey MakeSuccessorStubsTimer = DebugContext.timer("PartialEvaluation-MakeSuccessorStubs").doc("Time spent in making successor stubs for the PE.");
    private static final TimerKey ReadPropertiesTimer = DebugContext.timer("PartialEvaluation-ReadProperties").doc("Time spent in reading node properties in the PE.");

    protected final Architecture architecture;
    /** The target graph where decoded nodes are added to. */
    protected final StructuredGraph graph;
    protected final OptionValues options;
    protected final DebugContext debug;

    private final EconomicMap<NodeClass<?>, ArrayDeque<Node>> reusableFloatingNodes;

    public GraphDecoder(Architecture architecture, StructuredGraph graph) {
        this.architecture = architecture;
        this.graph = graph;
        this.options = graph.getOptions();
        this.debug = graph.getDebug();
        reusableFloatingNodes = EconomicMap.create(Equivalence.IDENTITY);
    }

    public final void decode(EncodedGraph encodedGraph) {
        decode(encodedGraph, null);
    }

    @SuppressWarnings("try")
    public final void decode(EncodedGraph encodedGraph, Iterable<EncodedGraph.EncodedNodeReference> nodeReferences) {
        try (DebugContext.Scope scope = debug.scope("GraphDecoder", graph)) {
            recordGraphElements(encodedGraph);
            MethodScope methodScope = new MethodScope(null, graph, encodedGraph, LoopExplosionPlugin.LoopExplosionKind.NONE);
            LoopScope loopScope = createInitialLoopScope(methodScope, null);
            decode(loopScope);
            cleanupGraph(methodScope);
            assert graph.verify();

            if (nodeReferences != null) {
                for (var nodeReference : nodeReferences) {
                    if (nodeReference.orderId < 0) {
                        throw GraalError.shouldNotReachHere("EncodeNodeReference is not in 'encoded' state"); // ExcludeFromJacocoGeneratedReport
                    }
                    nodeReference.node = loopScope.getNode(nodeReference.orderId);
                    if (nodeReference.node == null || !nodeReference.node.isAlive()) {
                        throw GraalError.shouldNotReachHere("Could not decode the EncodedNodeReference"); // ExcludeFromJacocoGeneratedReport
                    }
                    nodeReference.orderId = EncodedGraph.EncodedNodeReference.DECODED;
                }
            }

            graph.maybeMarkUnsafeAccess(encodedGraph);
        } catch (Throwable ex) {
            debug.handle(ex);
        }
    }

    protected void recordGraphElements(EncodedGraph encodedGraph) {
        List<ResolvedJavaMethod> inlinedMethods = encodedGraph.getInlinedMethods();
        if (inlinedMethods != null) {
            for (ResolvedJavaMethod other : inlinedMethods) {
                graph.recordMethod(other);
            }
        }
        Assumptions assumptions = graph.getAssumptions();
        Assumptions inlinedAssumptions = encodedGraph.getAssumptions();
        if (assumptions != null) {
            if (inlinedAssumptions != null) {
                assumptions.record(inlinedAssumptions);
            }
        } else {
            assert inlinedAssumptions == null : String.format("cannot inline graph (%s) which makes assumptions into a graph (%s) that doesn't", encodedGraph, graph);
        }
        graph.maybeMarkUnsafeAccess(encodedGraph);
    }

    protected final LoopScope createInitialLoopScope(MethodScope methodScope, FixedWithNextNode startNode) {
        LoopScope loopScope = new LoopScope(methodScope);
        FixedNode firstNode;
        if (startNode != null) {
            /*
             * The start node of a graph can be referenced as the guard for a GuardedNode. We
             * register the previous block node, so that such guards are correctly anchored when
             * doing inlining during graph decoding.
             */
            registerNode(loopScope, GraphEncoder.START_NODE_ORDER_ID, AbstractBeginNode.prevBegin(startNode), false, false);

            firstNode = makeStubNode(methodScope, loopScope, GraphEncoder.FIRST_NODE_ORDER_ID);
            startNode.setNext(firstNode);
            loopScope.nodesToProcess.set(GraphEncoder.FIRST_NODE_ORDER_ID);
        } else {
            firstNode = graph.start();
            registerNode(loopScope, GraphEncoder.START_NODE_ORDER_ID, firstNode, false, false);
            loopScope.nodesToProcess.set(GraphEncoder.START_NODE_ORDER_ID);
        }
        return loopScope;
    }

    @SuppressWarnings("try")
    protected final void decode(LoopScope initialLoopScope) {
        initialLoopScope.methodScope.replaceLogsForDecodedGraph();
        try (InliningLog.UpdateScope updateScope = InliningLog.openDefaultUpdateScope(graph.getInliningLog())) {
            LoopScope loopScope = initialLoopScope;
            /* Process (inlined) methods. */
            while (loopScope != null) {
                MethodScope methodScope = loopScope.methodScope;

                /* Process loops of method. */
                while (loopScope != null) {
                    /* Process nodes of loop. */
                    while (!loopScope.nodesToProcess.isEmpty()) {
                        loopScope = processNextNode(methodScope, loopScope);
                        methodScope = loopScope.methodScope;
                        /*
                         * We can have entered a new loop, and we can have entered a new inlined
                         * method.
                         */
                    }

                    /* Finished with a loop. */
                    if (loopScope.hasIterationsToProcess()) {
                        loopScope = loopScope.getNextIterationToProcess(true);
                    } else {
                        propagateCreatedNodes(loopScope);
                        loopScope = loopScope.outer;

                        if (loopScope == null) {
                            // finished all loops of a method
                            afterMethodScope(methodScope);
                        }
                    }
                }

                /*
                 * Finished with an inlined method. Perform end-of-method cleanup tasks.
                 */
                if (methodScope.loopExplosion.mergeLoops()) {
                    LoopDetector loopDetector = new LoopDetector(graph, methodScope);
                    loopDetector.run();
                }
                if (methodScope.isInlinedMethod()) {
                    finishInlining(methodScope);
                }

                /* continue with the caller */
                loopScope = methodScope.callerLoopScope;
            }
        }
    }

    protected void afterMethodScope(@SuppressWarnings("unused") MethodScope methodScope) {
    }

    protected void finishInlining(@SuppressWarnings("unused") MethodScope inlineScope) {
    }

    private static void propagateCreatedNodes(LoopScope loopScope) {
        if (loopScope.outer == null || loopScope.createdNodes != loopScope.outer.createdNodes) {
            return;
        }

        /* Register nodes that were created while decoding the loop to the outside scope. */
        for (int i = 0; i < loopScope.createdNodes.length; i++) {
            if (loopScope.outer.getNode(i) == null) {
                loopScope.outer.setNode(i, loopScope.getNode(i));
            }
        }
    }

    public static final boolean DUMP_DURING_FIXED_NODE_PROCESSING = false;

    protected LoopScope processNextNode(MethodScope methodScope, LoopScope loopScope) {
        int nodeOrderId = loopScope.nodesToProcess.nextSetBit(0);
        loopScope.nodesToProcess.clear(nodeOrderId);

        FixedNode node = (FixedNode) lookupNode(loopScope, nodeOrderId);

        if (node.isDeleted()) {
            return loopScope;
        }
        graph.beforeDecodingFields(node);
        try {
            if (DUMP_DURING_FIXED_NODE_PROCESSING) {
                if (node != null) {
                    try {
                        debug.dump(DebugContext.DETAILED_LEVEL, graph, "Before processing node %s", node);
                    } catch (Throwable t) {
                        // swallow here, dumping uninitialized nodes can cause problems
                    }
                }
            }
            if ((node instanceof MergeNode ||
                            (node instanceof LoopBeginNode && (methodScope.loopExplosion.unrollLoops() &&
                                            !methodScope.loopExplosion.mergeLoops()))) &&
                            ((AbstractMergeNode) node).forwardEndCount() == 1) {
                /*
                 * In case node is a loop begin and we are unrolling loops we remove the loop begin
                 * since the loop will be gone after PE.
                 */
                AbstractMergeNode merge = (AbstractMergeNode) node;
                EndNode singleEnd = merge.forwardEndAt(0);

                /* Nodes that would use this merge as the guard need to use the previous block. */
                registerNode(loopScope, nodeOrderId, AbstractBeginNode.prevBegin(singleEnd), true, false);

                FixedNode next = makeStubNode(methodScope, loopScope, nodeOrderId + GraphEncoder.BEGIN_NEXT_ORDER_ID_OFFSET);
                singleEnd.replaceAtPredecessor(next);

                merge.safeDelete();
                singleEnd.safeDelete();
                return loopScope;
            }

            LoopScope successorAddScope = loopScope;
            boolean updatePredecessors = true;
            if (node instanceof LoopExitNode) {
                if (methodScope.loopExplosion.duplicateLoopExits() || (methodScope.loopExplosion.mergeLoops() && loopScope.loopDepth > 1)) {
                    /*
                     * We do not want to merge loop exits of inner loops. Instead, we want to keep
                     * exploding the outer loop separately for every loop exit and then merge the
                     * outer loop. Therefore, we create a new LoopScope of the outer loop for every
                     * loop exit of the inner loop.
                     */
                    LoopScope outerScope = loopScope.outer;
                    int nextIterationNumber = outerScope.nextIterationFromLoopExitDuplication.isEmpty() ? outerScope.loopIteration + 1
                                    : outerScope.nextIterationFromLoopExitDuplication.getLast().loopIteration + 1;
                    Node[] initialCreatedNodes = outerScope.initialCreatedNodes == null ? null
                                    : (methodScope.loopExplosion.mergeLoops()
                                                    ? Arrays.copyOf(outerScope.initialCreatedNodes, outerScope.initialCreatedNodes.length)
                                                    : outerScope.initialCreatedNodes);
                    /*
                     * Since the initial nodes and created nodes are from two different loop scopes,
                     * we cannot use the written set in the new loop scope and set reuseInitialNodes
                     * to false.
                     */
                    successorAddScope = new LoopScope(methodScope, outerScope.outer, outerScope.loopDepth, nextIterationNumber, outerScope.loopBeginOrderId, LoopScopeTrigger.LOOP_EXIT_DUPLICATION,
                                    initialCreatedNodes,
                                    Arrays.copyOf(loopScope.initialCreatedNodes, loopScope.initialCreatedNodes.length),
                                    outerScope.nextIterationFromLoopExitDuplication,
                                    outerScope.nextIterationFromLoopEndDuplication,
                                    outerScope.nextIterationsFromUnrolling,
                                    outerScope.iterationStates,
                                    false);
                    checkLoopExplosionIteration(methodScope, successorAddScope);

                    /*
                     * Nodes that are still unprocessed in the outer scope might be merge nodes that
                     * are also reachable from the new exploded scope. Clearing them ensures that we
                     * do not merge, but instead keep exploding.
                     */
                    for (int id = outerScope.nodesToProcess.nextSetBit(0); id >= 0; id = outerScope.nodesToProcess.nextSetBit(id + 1)) {
                        successorAddScope.setNode(id, null);
                    }

                    outerScope.nextIterationFromLoopExitDuplication.addLast(successorAddScope);
                } else {
                    successorAddScope = loopScope.outer;
                }
                updatePredecessors = methodScope.loopExplosion.isNoExplosion();
            }

            methodScope.reader.setByteIndex(methodScope.encodedGraph.nodeStartOffsets[nodeOrderId]);
            int typeId = methodScope.reader.getUVInt();
            assert node.getNodeClass() == methodScope.encodedGraph.getNodeClass(typeId) : Assertions.errorMessage(node, methodScope.encodedGraph.getNodeClass(typeId));
            makeFixedNodeInputs(methodScope, loopScope, node);
            readProperties(methodScope, node);

            if ((node instanceof IfNode || node instanceof SwitchNode) &&
                            earlyCanonicalization(methodScope, successorAddScope, nodeOrderId, node)) {
                return loopScope;
            }

            makeSuccessorStubs(methodScope, successorAddScope, node, updatePredecessors);

            LoopScope resultScope = loopScope;
            if (node instanceof LoopBeginNode) {
                if (methodScope.loopExplosion.useExplosion()) {
                    handleLoopExplosionBegin(methodScope, loopScope, (LoopBeginNode) node);
                }

            } else if (node instanceof LoopExitNode) {
                if (methodScope.loopExplosion.useExplosion()) {
                    handleLoopExplosionProxyNodes(methodScope, loopScope, successorAddScope, (LoopExitNode) node, nodeOrderId);
                } else {
                    handleProxyNodes(methodScope, loopScope, (LoopExitNode) node);
                }

            } else if (node instanceof MergeNode) {
                handleMergeNode(((MergeNode) node));
            } else if (node instanceof AbstractEndNode) {
                LoopScope phiInputScope = loopScope;
                LoopScope phiNodeScope = loopScope;
                int mergeOrderId = readOrderId(methodScope);

                boolean requiresMergeOfOuterLoop = methodScope.loopExplosion.unrollLoops() &&
                                methodScope.loopExplosion.duplicateLoopExits() &&
                                (!methodScope.loopExplosion.duplicateLoopEnds()) &&
                                (!methodScope.loopExplosion.mergeLoops()) &&
                                node instanceof LoopEndNode &&
                                loopScope.trigger == LoopScopeTrigger.LOOP_EXIT_DUPLICATION;

                if (requiresMergeOfOuterLoop) {
                    EndNode replacementNode = graph.add(new EndNode());
                    node.replaceAtPredecessor(replacementNode);
                    node.safeDelete();
                    node = replacementNode;
                    /*
                     * We are in a loop exit duplicated loop scope and see a loop end node, this can
                     * only happen if we have a loop end to an outer loop. When duplicating over
                     * loop exits we have to merge outer loops for nested inner loops.
                     *
                     * Therefore, we create a correct outer loop iteration and check if there is
                     * already one, if not we create it else we re-use it.
                     */
                    if (loopScope.nextIterationsFromUnrolling.isEmpty()) {
                        // create it
                        int nextIterationNumber = loopScope.nextIterationsFromUnrolling.isEmpty() ? loopScope.loopIteration + 1 : loopScope.nextIterationsFromUnrolling.getLast().loopIteration + 1;
                        LoopScope outerLoopMergeScope = new LoopScope(methodScope, loopScope.outer, loopScope.loopDepth, nextIterationNumber, loopScope.loopBeginOrderId,
                                        LoopScopeTrigger.LOOP_BEGIN_UNROLLING,
                                        methodScope.loopExplosion.mergeLoops() ? Arrays.copyOf(loopScope.initialCreatedNodes, loopScope.initialCreatedNodes.length) : loopScope.initialCreatedNodes,
                                        Arrays.copyOf(loopScope.initialCreatedNodes, loopScope.initialCreatedNodes.length),
                                        loopScope.nextIterationFromLoopExitDuplication,
                                        loopScope.nextIterationFromLoopEndDuplication,
                                        loopScope.nextIterationsFromUnrolling,
                                        loopScope.iterationStates);
                        checkLoopExplosionIteration(methodScope, outerLoopMergeScope);
                        loopScope.nextIterationsFromUnrolling.addLast(outerLoopMergeScope);
                        registerNode(outerLoopMergeScope, loopScope.loopBeginOrderId, null, true, true);
                        makeStubNode(methodScope, outerLoopMergeScope, loopScope.loopBeginOrderId);
                        phiNodeScope = outerLoopMergeScope;
                    } else {
                        // re-use it
                        phiNodeScope = loopScope.nextIterationsFromUnrolling.getLast();
                    }

                } else if (methodScope.loopExplosion.useExplosion() && node instanceof LoopEndNode) {
                    EndNode replacementNode = graph.add(new EndNode());
                    node.replaceAtPredecessor(replacementNode);
                    node.safeDelete();
                    node = replacementNode;
                    LoopScopeTrigger trigger = handleLoopExplosionEnd(methodScope, loopScope);
                    Deque<LoopScope> phiScope = loopScope.nextIterationsFromUnrolling;
                    if (trigger == LoopScopeTrigger.LOOP_END_DUPLICATION) {
                        phiScope = loopScope.nextIterationFromLoopEndDuplication;
                    }
                    phiNodeScope = phiScope.getLast();
                }
                AbstractMergeNode merge = (AbstractMergeNode) lookupNode(phiNodeScope, mergeOrderId);
                if (merge == null) {
                    merge = (AbstractMergeNode) makeStubNode(methodScope, phiNodeScope, mergeOrderId);
                    if (merge instanceof LoopBeginNode) {
                        /*
                         * In contrast to the LoopScopeTrigger.START created at the beginning of
                         * every PE, we see a real loop here and create the first real loop scope
                         * associated with a loop.
                         *
                         * Creation of a loop scope if we reach a loop begin node. We process a loop
                         * begin node (always before encountering a loop end associated with the
                         * loop begin) and simply create a normal loop scope. This does not imply an
                         * advanced unrolling strategy (however it can later if we see duplicate
                         * over loop end or exits). Therefore, we still use the start marker here,
                         * we could also use the unrolling marker.
                         *
                         * If we unroll loops we will later remove the loop begin node and replace
                         * it with its forward end (since we do not need to create a loop begin node
                         * if we unroll the entire loop and it has a constant trip count).
                         */
                        assert phiNodeScope == phiInputScope : phiInputScope + "!=" + phiInputScope;
                        assert phiNodeScope == loopScope : phiNodeScope + "!=" + loopScope;
                        /*
                         * Since we use the created nodes as either initial nodes or created nodes
                         * of the next loop scope, we need to materialize the created nodes of the
                         * current loop scope (i.e., combine its initial and created nodes into a
                         * single array).
                         */
                        final Node[] createdNodes = loopScope.materializeCreatedNodes();
                        resultScope = new LoopScope(methodScope, loopScope, loopScope.loopDepth + 1, 0, mergeOrderId, LoopScopeTrigger.START,
                                        methodScope.loopExplosion.useExplosion() ? Arrays.copyOf(createdNodes, createdNodes.length) : null,
                                        methodScope.loopExplosion.useExplosion() ? new Node[createdNodes.length] : createdNodes, //
                                        methodScope.loopExplosion.duplicateLoopExits() || methodScope.loopExplosion.mergeLoops() ? new ArrayDeque<>(2) : null,
                                        methodScope.loopExplosion.duplicateLoopEnds() ? new ArrayDeque<>(2) : null,
                                        methodScope.loopExplosion.unrollLoops() ? new ArrayDeque<>(2) : null, //
                                        methodScope.loopExplosion.mergeLoops() ? EconomicMap.create(Equivalence.DEFAULT) : null);
                        phiInputScope = resultScope;
                        phiNodeScope = resultScope;

                        if (methodScope.loopExplosion.useExplosion()) {
                            registerNode(loopScope, mergeOrderId, null, true, true);
                        }
                        loopScope.nodesToProcess.clear(mergeOrderId);
                        resultScope.nodesToProcess.set(mergeOrderId);
                    }
                }
                handlePhiFunctions(methodScope, phiInputScope, phiNodeScope, (AbstractEndNode) node, merge);
            } else if (node instanceof Invoke) {
                InvokeData invokeData = readInvokeData(methodScope, nodeOrderId, (Invoke) node);
                resultScope = handleInvoke(methodScope, loopScope, invokeData);
            } else if (node instanceof MethodHandleWithExceptionNode methodHandle) {
                InvokableData<MethodHandleWithExceptionNode> invokableData = readInvokableData(methodScope, nodeOrderId, methodHandle);
                resultScope = handleMethodHandle(methodScope, loopScope, invokableData);
            } else if (node instanceof ReturnNode || node instanceof UnwindNode) {
                methodScope.returnAndUnwindNodes.add((ControlSinkNode) node);
            } else {
                handleFixedNode(methodScope, loopScope, nodeOrderId, node);
            }
            if (DUMP_DURING_FIXED_NODE_PROCESSING) {
                if (node != null) {
                    try {
                        debug.dump(DebugContext.DETAILED_LEVEL, graph, "After processing node %s", node);
                    } catch (Throwable t) {
                        // swallow here, dumping uninitialized nodes can cause problems
                    }
                }
            }
            return resultScope;
        } finally {
            graph.afterDecodingFields(node);
        }
    }

    protected LoopScope handleMethodHandle(MethodScope methodScope, LoopScope loopScope, InvokableData<MethodHandleWithExceptionNode> invokableData) {
        appendInvokable(methodScope, loopScope, invokableData);
        return loopScope;
    }

    protected <T extends Invokable> InvokableData<T> readInvokableData(MethodScope methodScope, int orderId, T node) {
        ResolvedJavaType contextType = (ResolvedJavaType) readObject(methodScope);
        int stateAfterOrderId = readOrderId(methodScope);
        int nextOrderId = readOrderId(methodScope);

        if (node instanceof WithExceptionNode) {
            int exceptionOrderId = readOrderId(methodScope);
            int exceptionStateOrderId = readOrderId(methodScope);
            int exceptionNextOrderId = readOrderId(methodScope);
            return new InvokableData<>(node, contextType, orderId, stateAfterOrderId, nextOrderId, exceptionOrderId, exceptionStateOrderId, exceptionNextOrderId);
        } else {
            return new InvokableData<>(node, contextType, orderId, stateAfterOrderId, nextOrderId, -1, -1, -1);
        }
    }

    protected InvokeData readInvokeData(MethodScope methodScope, int invokeOrderId, Invoke invoke) {
        int callTargetOrderId = readOrderId(methodScope);
        InvokableData<Invoke> invokableData = readInvokableData(methodScope, invokeOrderId, invoke);
        return InvokeData.createFrom(invokableData, callTargetOrderId, false);
    }

    /**
     * {@link Invoke} nodes do not have the {@link CallTargetNode}, {@link FrameState}, and
     * successors encoded. Instead, this information is provided separately to allow method inlining
     * without decoding and adding them to the graph upfront. For non-inlined methods, this method
     * restores the normal state. Subclasses can override it to perform method inlining.
     *
     * The return value is the loop scope where decoding should continue. When method inlining
     * should be performed, the returned loop scope must be a new loop scope for the inlined method.
     * Without inlining, the original loop scope must be returned.
     */
    protected LoopScope handleInvoke(MethodScope methodScope, LoopScope loopScope, InvokeData invokeData) {
        assert invokeData.invoke.callTarget() == null : "callTarget edge is ignored during decoding of Invoke";
        CallTargetNode callTarget = (CallTargetNode) ensureNodeCreated(methodScope, loopScope, invokeData.callTargetOrderId);
        appendInvoke(methodScope, loopScope, invokeData, callTarget);
        return loopScope;
    }

    protected void appendInvoke(MethodScope methodScope, LoopScope loopScope, InvokeData invokeData, CallTargetNode callTarget) {
        if (invokeData.invoke instanceof InvokeWithExceptionNode) {
            ((InvokeWithExceptionNode) invokeData.invoke).setCallTarget(callTarget);
        } else {
            ((InvokeNode) invokeData.invoke).setCallTarget(callTarget);
        }
        appendInvokable(methodScope, loopScope, invokeData);
    }

    private <T extends Invokable & StateSplit> void appendInvokable(MethodScope methodScope, LoopScope loopScope, InvokableData<T> invokeData) {
        if (invokeData.invoke.stateAfter() == null) {
            invokeData.invoke.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, invokeData.stateAfterOrderId));
        }
        assert !(invokeData.invoke instanceof Invoke inv && inv.stateDuring() != null) : "stateDuring is not used in high tier graphs";

        FixedNode next = makeStubNode(methodScope, loopScope, invokeData.nextOrderId);
        if (invokeData.invoke instanceof WithExceptionNode withException) {
            withException.setNext((AbstractBeginNode) next);
            withException.setExceptionEdge((AbstractBeginNode) makeStubNode(methodScope, loopScope, invokeData.exceptionOrderId));
        } else {
            ((FixedWithNextNode) invokeData.invoke).setNext(next);
        }
    }

    /**
     * Hook for subclasses to perform simplifications for a non-loop-header control flow merge.
     *
     * @param merge The control flow merge.
     */
    protected void handleMergeNode(MergeNode merge) {
    }

    protected void handleLoopExplosionBegin(MethodScope methodScope, LoopScope loopScope, LoopBeginNode loopBegin) {
        checkLoopExplosionIteration(methodScope, loopScope);

        List<EndNode> predecessors = loopBegin.forwardEnds().snapshot();
        FixedNode successor = loopBegin.next();
        FrameState frameState = loopBegin.stateAfter();

        if (methodScope.loopExplosion.mergeLoops()) {
            LoopExplosionState queryState = new LoopExplosionState(frameState, null);
            LoopExplosionState existingState = loopScope.iterationStates.get(queryState);
            if (existingState != null) {
                loopBegin.replaceAtUsagesAndDelete(existingState.merge);
                successor.safeDelete();
                for (EndNode predecessor : predecessors) {
                    existingState.merge.addForwardEnd(predecessor);
                }
                return;
            }
        }

        /*
         * Merge explosion: When doing merge-explode PE loops are detected after partial evaluation
         * in a dedicated steps. Therefore, we create merge nodes instead of loop begins and loop
         * exits and later replace them with the detected loop begin and loop exit nodes.
         */
        MergeNode merge = graph.add(new MergeNode());
        methodScope.loopExplosionMerges.add(merge);

        if (methodScope.loopExplosion.mergeLoops()) {
            if (loopScope.iterationStates.size() == 0 && loopScope.loopDepth == 1) {
                if (methodScope.loopExplosionHead != null) {
                    throw new PermanentBailoutException("Graal implementation restriction: Method with %s loop explosion must not have more than one top-level loop",
                                    LoopExplosionPlugin.LoopExplosionKind.MERGE_EXPLODE);
                }
                methodScope.loopExplosionHead = merge;
            }
        }

        loopBegin.replaceAtUsagesAndDelete(merge);
        merge.setStateAfter(frameState);
        merge.setNext(successor);
        for (EndNode predecessor : predecessors) {
            merge.addForwardEnd(predecessor);
        }

        if (methodScope.loopExplosion.mergeLoops()) {
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
        throw shouldNotReachHere("when subclass uses loop explosion, it needs to implement this method"); // ExcludeFromJacocoGeneratedReport
    }

    protected LoopScopeTrigger handleLoopExplosionEnd(MethodScope methodScope, LoopScope loopScope) {
        /*
         * This method is only called if we reach a loop end and we use some kind of loop explosion,
         * i.e., we unroll loops or explode along loop ends.
         */
        LoopScopeTrigger trigger = null;
        Deque<LoopScope> nextIterations = null;
        if (methodScope.loopExplosion.duplicateLoopEnds()) {
            /*
             * Loop explosion along loop ends: We see a loop end, however we do not merge all loop
             * ends at a common merge node but rather duplicate the rest of the loop for every loop
             * end.
             */
            trigger = LoopScopeTrigger.LOOP_END_DUPLICATION;
            nextIterations = loopScope.nextIterationFromLoopEndDuplication;
        } else if (loopScope.nextIterationsFromUnrolling.isEmpty()) {
            /*
             * Regular loop unrolling, i.e., we reach a loop end node of a loop that should be
             * unrolled: We create a new successor scope.
             */
            trigger = LoopScopeTrigger.LOOP_BEGIN_UNROLLING;
            nextIterations = loopScope.nextIterationsFromUnrolling;
        }
        if (trigger != null) {
            final LoopScope nextIterationScope = createNextLoopIterationScope(methodScope, loopScope, trigger, nextIterations);
            checkLoopExplosionIteration(methodScope, nextIterationScope);
            nextIterations.addLast(nextIterationScope);
            registerNode(nextIterationScope, loopScope.loopBeginOrderId, null, true, true);
            makeStubNode(methodScope, nextIterationScope, loopScope.loopBeginOrderId);
        }
        return trigger;
    }

    /**
     * Creates the next loop iteration of a loop that is currently unrolled through loop explosion.
     *
     * @param methodScope The current method scope.
     * @param loopScope The current loop scope.
     * @param trigger The trigger for unrolling the next loop iteration.
     * @param nextIterations Already existing next loop iterations.
     * @return The loop scope for the next loop iteration that should be unrolled.
     */
    private static LoopScope createNextLoopIterationScope(MethodScope methodScope, LoopScope loopScope, LoopScopeTrigger trigger, Deque<LoopScope> nextIterations) {
        int nextIterationNumber = nextIterations.isEmpty() ? loopScope.loopIteration + 1 : nextIterations.getLast().loopIteration + 1;
        /*
         * We try to reuse existing initial and created nodes as often as possible. Therefore, if
         * there are no more nodes to process in the current loop scope, we reuse both arrays to
         * eliminate allocation and copying overhead. Only if there are still nodes to process, for
         * example, if the current loop scope contains a control split and both branches continue in
         * independent new loop scopes, we copy the initial nodes and create a new array for the
         * created nodes.
         */
        final Node[] initialCreatedNodes;
        final Node[] createdNodes;
        if (loopScope.nodesToProcess.isEmpty()) {
            initialCreatedNodes = loopScope.initialCreatedNodes;
            createdNodes = loopScope.createdNodes;
        } else {
            initialCreatedNodes = Arrays.copyOf(loopScope.initialCreatedNodes, loopScope.initialCreatedNodes.length);
            createdNodes = new Node[loopScope.createdNodes.length];
        }

        return new LoopScope(methodScope, loopScope.outer, loopScope.loopDepth, nextIterationNumber, loopScope.loopBeginOrderId, trigger,
                        initialCreatedNodes,
                        createdNodes,
                        loopScope.nextIterationFromLoopExitDuplication,
                        loopScope.nextIterationFromLoopEndDuplication,
                        loopScope.nextIterationsFromUnrolling,
                        loopScope.iterationStates);
    }

    /**
     * Hook for subclasses.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param nodeOrderId The orderId of the node.
     * @param node The node to be simplified.
     */
    protected void handleFixedNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId, FixedNode node) {
    }

    /**
     * Hook for subclasses for early canonicalization of IfNodes and IntegerSwitchNodes.
     *
     * "Early" means that this is called before successor stubs creation. Therefore, all successors
     * are null at this point, and calling any method using them without prior manual initialization
     * will fail.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param nodeOrderId The orderId of the node.
     * @param node The node to be simplified.
     * @return true if canonicalization happened, false otherwise.
     */
    protected boolean earlyCanonicalization(MethodScope methodScope, LoopScope loopScope, int nodeOrderId, FixedNode node) {
        return false;
    }

    protected void handleProxyNodes(MethodScope methodScope, LoopScope loopScope, LoopExitNode loopExit) {
        assert loopExit.stateAfter() == null;
        int stateAfterOrderId = readOrderId(methodScope);
        loopExit.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, stateAfterOrderId));

        int numProxies = methodScope.reader.getUVInt();
        for (int i = 0; i < numProxies; i++) {
            int proxyOrderId = readOrderId(methodScope);
            ProxyNode proxy = (ProxyNode) ensureNodeCreated(methodScope, loopScope, proxyOrderId);
            /*
             * The ProxyNode transports a value from the loop to the outer scope. We therefore
             * register it in the outer scope.
             */
            if (loopScope.outer.createdNodes != loopScope.createdNodes) {
                registerNode(loopScope.outer, proxyOrderId, proxy, false, false);
            }
        }
    }

    protected void handleLoopExplosionProxyNodes(MethodScope methodScope, LoopScope loopScope, LoopScope outerScope, LoopExitNode loopExit, int loopExitOrderId) {
        assert loopExit.stateAfter() == null;
        int stateAfterOrderId = readOrderId(methodScope);

        FixedNode loopExitSuccessor = loopExit.next();

        BeginNode begin;
        if (loopExit.predecessor() instanceof BeginNode) {
            // Optimization: Reuse an existing BeginNode rather than creating a new one.
            begin = (BeginNode) loopExit.predecessor();
            loopExit.replaceAtPredecessor(null);
        } else {
            begin = graph.add(new BeginNode());
            loopExit.replaceAtPredecessor(begin);
        }

        MergeNode loopExitPlaceholder = null;
        if (methodScope.loopExplosion.mergeLoops() && loopScope.loopDepth == 1) {
            /*
             * This exit might end up as a loop exit of a loop detected after partial evaluation. We
             * need to be able to create a FrameState and the necessary proxy nodes in this case.
             */
            loopExitPlaceholder = graph.add(new MergeNode());
            methodScope.loopExplosionMerges.add(loopExitPlaceholder);

            EndNode end = graph.add(new EndNode());
            begin.setNext(end);
            loopExitPlaceholder.addForwardEnd(end);

            begin = graph.add(new BeginNode());
            loopExitPlaceholder.setNext(begin);
        }

        /*
         * In the original graph, the loop exit is not a merge node. Multiple exploded loop
         * iterations now take the same loop exit, so we have to introduce a new merge node to
         * handle the merge.
         */
        MergeNode merge = null;
        Node existingExit = lookupNode(outerScope, loopExitOrderId);
        if (existingExit == null) {
            /* First loop iteration that exits. No merge necessary yet. */
            registerNode(outerScope, loopExitOrderId, begin, false, false);
            begin.setNext(loopExitSuccessor);

        } else if (existingExit instanceof BeginNode) {
            /* Second loop iteration that exits. Create the merge. */
            merge = graph.add(new MergeNode());
            registerNode(outerScope, loopExitOrderId, merge, true, false);
            /* Add the first iteration. */
            EndNode firstEnd = graph.add(new EndNode());
            ((BeginNode) existingExit).setNext(firstEnd);
            merge.addForwardEnd(firstEnd);
            merge.setNext(loopExitSuccessor);

        } else {
            /* Subsequent loop iteration. Merge already created. */
            merge = (MergeNode) existingExit;
        }

        if (merge != null) {
            EndNode end = graph.add(new EndNode());
            begin.setNext(end);
            merge.addForwardEnd(end);
        }

        /*
         * Possibly create phi nodes for the original proxy nodes that flow out of the loop. Note
         * that we definitely do not need a proxy node itself anymore, since the loop was exploded
         * and is no longer present.
         */
        int numProxies = methodScope.reader.getUVInt();
        boolean phiCreated = false;
        for (int i = 0; i < numProxies; i++) {
            int proxyOrderId = readOrderId(methodScope);
            ProxyNode proxy = (ProxyNode) ensureNodeCreated(methodScope, loopScope, proxyOrderId);
            ValueNode phiInput = proxy.value();

            if (loopExitPlaceholder != null) {
                registerNode(loopScope, proxyOrderId, phiInput, true, false);
            }

            ValueNode replacement;
            ValueNode existing = (ValueNode) outerScope.getNode(proxyOrderId);
            if (existing == null || existing == phiInput) {
                /*
                 * We are at the first loop exit, or the proxy carries the same value for all exits.
                 * We do not need a phi node yet.
                 */
                registerNode(outerScope, proxyOrderId, phiInput, true, false);
                replacement = phiInput;

            } else {
                // Fortify: Suppress Null Dereference false positive
                assert merge != null;

                if (!merge.isPhiAtMerge(existing)) {
                    /* Now we have two different values, so we need to create a phi node. */
                    PhiNode phi = proxy.createPhi(merge);
                    /* Add the inputs from all previous exits. */
                    for (int j = 0; j < merge.phiPredecessorCount() - 1; j++) {
                        phi.addInput(existing);
                    }
                    /* Add the input from this exit. */
                    phi.addInput(phiInput);
                    registerNode(outerScope, proxyOrderId, phi, true, false);
                    replacement = phi;
                    phiCreated = true;

                } else {
                    /* Phi node has been created before, so just add the new input. */
                    PhiNode phi = (PhiNode) existing;
                    phi.addInput(phiInput);
                    replacement = phi;
                }
            }
            proxy.replaceAtUsagesAndDelete(replacement);
        }

        if (loopExitPlaceholder != null) {
            registerNode(loopScope, stateAfterOrderId, null, true, true);
            loopExitPlaceholder.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, stateAfterOrderId));
        }

        if (merge != null && (merge.stateAfter() == null || phiCreated)) {
            FrameState oldStateAfter = merge.stateAfter();
            registerNode(outerScope, stateAfterOrderId, null, true, true);
            merge.setStateAfter((FrameState) ensureNodeCreated(methodScope, outerScope, stateAfterOrderId));
            if (oldStateAfter != null) {
                oldStateAfter.safeDelete();
            }
        }
        loopExit.safeDelete();
        assert loopExitSuccessor.predecessor() == null;
        if (merge != null) {
            merge.getNodeClass().getSuccessorEdges().update(merge, null, loopExitSuccessor);
        } else {
            begin.getNodeClass().getSuccessorEdges().update(begin, null, loopExitSuccessor);
        }
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
        boolean lazyPhi = allowLazyPhis() && (!(merge instanceof LoopBeginNode) || methodScope.loopExplosion.useExplosion());
        int numPhis = methodScope.reader.getUVInt();

        if (numPhis > 1 && merge instanceof LoopBeginNode) {
            /*
             * Since we try to reuse the initial and created nodes of loop scopes as often as
             * possible, it can happen that the input scope (phiInputScope) and node scope
             * (phiNodeScope) use the same arrays. Since phis are created into the node scope based
             * on the orderId of nodes in the input scope, it could happen that creating a new node
             * in the node scope overwrites a node that is required by another phi value in the
             * input scope. For example, if the first phi reads from orderId [1] in the input scope
             * and writes to orderId [2] in the node scope, and the second phi reads from orderId
             * [2] in the input scope, the original value would no longer exist in the array.
             * Therefore, we first extract all phi values from the input scope and then create the
             * new phi values in the node scope.
             */
            final ValueNode[] phiInputs = new ValueNode[numPhis];
            final int[] phiNodeOrderIds = new int[numPhis];
            for (int i = 0; i < numPhis; i++) {
                final int phiInputOrderId = readOrderId(methodScope);
                phiNodeOrderIds[i] = readOrderId(methodScope);
                phiInputs[i] = (ValueNode) ensureNodeCreated(methodScope, phiInputScope, phiInputOrderId);
            }
            for (int i = 0; i < numPhis; i++) {
                final int phiNodeOrderId = phiNodeOrderIds[i];
                final ValueNode phiInput = phiInputs[i];
                handlePhi(methodScope, phiNodeScope, merge, phiInput, phiNodeOrderId, lazyPhi);
            }
        } else {
            for (int i = 0; i < numPhis; i++) {
                int phiInputOrderId = readOrderId(methodScope);
                int phiNodeOrderId = readOrderId(methodScope);
                final ValueNode phiInput = (ValueNode) ensureNodeCreated(methodScope, phiInputScope, phiInputOrderId);
                handlePhi(methodScope, phiNodeScope, merge, phiInput, phiNodeOrderId, lazyPhi);
            }
        }
    }

    private void handlePhi(MethodScope methodScope, LoopScope phiNodeScope, AbstractMergeNode merge, ValueNode phiInput, int phiNodeOrderId, boolean lazyPhi) {
        ValueNode existing = (ValueNode) lookupNode(phiNodeScope, phiNodeOrderId);

        if (existing != null && merge.phiPredecessorCount() == 1) {
            /*
             * When exploding loops and the code after the loop (FULL_EXPLODE_UNTIL_RETURN), then an
             * existing value can already be registered: Parsing of the code before the loop
             * registers it when preparing for the later merge. The code after the loop, which
             * starts with a clone of the values that were created before the loop, sees the stale
             * value when processing the merge the first time. We can safely ignore the stale value
             * because it will never be needed to be merged (we are exploding until we hit a
             * return).
             */
            assert methodScope.loopExplosion.duplicateLoopExits();
            assert phiNodeScope.loopIteration > 0 : Assertions.errorMessageContext("phiNodeScope.loopIteration", phiNodeScope.loopIteration);
            existing = null;
        }

        if (lazyPhi && (existing == null || existing == phiInput)) {
            /* Phi function not yet necessary. */
            registerNode(phiNodeScope, phiNodeOrderId, phiInput, true, false);

        } else if (!merge.isPhiAtMerge(existing)) {
            /*
             * Phi function is necessary. Create it and fill it with existing inputs as well as the
             * new input.
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

    protected boolean allowLazyPhis() {
        /* We need to exactly reproduce the encoded graph, including unnecessary phi functions. */
        return false;
    }

    @SuppressWarnings("try")
    protected void readProperties(MethodScope methodScope, Node node) {
        try (DebugCloseable a = ReadPropertiesTimer.start(debug)) {
            NodeSourcePosition position = (NodeSourcePosition) readObject(methodScope);
            Fields fields = node.getNodeClass().getData();
            for (int pos = 0; pos < fields.getCount(); pos++) {
                if (fields.getType(pos).isPrimitive()) {
                    long primitive = methodScope.reader.getSV();
                    fields.setRawPrimitive(node, pos, primitive);
                } else {
                    Object value = readObject(methodScope);
                    fields.putObject(node, pos, value);
                }
            }
            if (graph.trackNodeSourcePosition() && position != null) {
                NodeSourcePosition newPosition = methodScope.getNodeSourcePosition(position);
                node.setNodeSourcePosition(newPosition);
                if (node instanceof DeoptimizingGuard deoptGuard && !newPosition.equals(position)) {
                    deoptGuard.addCallerToNoDeoptSuccessorPosition(newPosition.getCaller());
                }
            }
        }
    }

    /**
     * Process the input edges of a node. Input nodes that have not yet been created must be
     * non-fixed nodes (because fixed nodes are processed in reverse postorder. Such non-fixed nodes
     * are created on demand (recursively since they can themselves reference not yet created
     * nodes).
     */
    protected void makeFixedNodeInputs(MethodScope methodScope, LoopScope loopScope, Node node) {
        Edges edges = node.getNodeClass().getInputEdges();
        for (int index = 0; index < edges.getDirectCount(); index++) {
            if (skipDirectEdge(node, edges, index)) {
                continue;
            }
            int orderId = readOrderId(methodScope);
            Node value = ensureNodeCreated(methodScope, loopScope, orderId);
            edges.initializeNode(node, index, value);
            if (value != null && !value.isDeleted()) {
                edges.update(node, null, value);

            }
        }

        if (node instanceof AbstractMergeNode) {
            /* The ends of merge nodes are filled manually when the ends are processed. */
            assert edges.getCount() - edges.getDirectCount() == 1 : "MergeNode has one variable size input (the ends)";
            assert Edges.getNodeList(node, edges.getOffsets(), edges.getDirectCount()) != null : "Input list must have been already created";
        } else {
            for (int index = edges.getDirectCount(); index < edges.getCount(); index++) {
                int size = methodScope.reader.getSVInt();
                if (size != -1) {
                    NodeList<Node> nodeList = new NodeInputList<>(node, size);
                    edges.initializeList(node, index, nodeList);
                    for (int idx = 0; idx < size; idx++) {
                        int orderId = readOrderId(methodScope);
                        Node value = ensureNodeCreated(methodScope, loopScope, orderId);
                        nodeList.initialize(idx, value);
                        if (value != null && !value.isDeleted()) {
                            edges.update(node, null, value);
                        }
                    }
                }
            }
        }
    }

    protected void makeFloatingNodeInputs(MethodScope methodScope, LoopScope loopScope, Node node) {
        Edges edges = node.getNodeClass().getInputEdges();
        if (node instanceof PhiNode) {
            /*
             * The inputs of phi functions are filled manually when the end nodes are processed.
             * However, the values must not be null, so initialize them with an empty list.
             */
            assert edges.getDirectCount() == 1 : "PhiNode has one direct input (the MergeNode)";
            assert edges.getCount() - edges.getDirectCount() == 1 : "PhiNode has one variable size input (the values)";
            edges.initializeList(node, edges.getDirectCount(), new NodeInputList<>(node));
        } else {
            for (int index = 0; index < edges.getDirectCount(); index++) {
                int orderId = readOrderId(methodScope);
                Node value = ensureNodeCreated(methodScope, loopScope, orderId);
                edges.initializeNode(node, index, value);
            }
            for (int index = edges.getDirectCount(); index < edges.getCount(); index++) {
                int size = methodScope.reader.getSVInt();
                if (size != -1) {
                    NodeList<Node> nodeList = new NodeInputList<>(node, size);
                    edges.initializeList(node, index, nodeList);
                    for (int idx = 0; idx < size; idx++) {
                        int orderId = readOrderId(methodScope);
                        Node value = ensureNodeCreated(methodScope, loopScope, orderId);
                        nodeList.initialize(idx, value);
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

        node = decodeFloatingNode(methodScope, loopScope, nodeOrderId);
        if (node instanceof ProxyNode || node instanceof PhiNode) {
            /*
             * We need these nodes as they were in the original graph, without any canonicalization
             * or value numbering.
             */
            node = graph.addWithoutUnique(node);
        } else {
            /* Allow subclasses to canonicalize and intercept nodes. */
            Node newNode = handleFloatingNodeBeforeAdd(methodScope, loopScope, node);
            if (newNode != node) {
                releaseFloatingNode(node);
            }

            if (!newNode.isAlive()) {
                newNode = addFloatingNode(methodScope, loopScope, newNode);
            }
            node = handleFloatingNodeAfterAdd(methodScope, loopScope, newNode);
        }
        registerNode(loopScope, nodeOrderId, node, false, false);
        return node;
    }

    protected Node addFloatingNode(@SuppressWarnings("unused") MethodScope methodScope, @SuppressWarnings("unused") LoopScope loopScope, Node node) {
        /*
         * We want to exactly reproduce the encoded graph. Even though nodes should be unique in the
         * encoded graph, this is not always guaranteed.
         */
        return graph.addWithoutUnique(node);
    }

    /**
     * Decodes a non-fixed node, but does not do any post-processing and does not register it.
     */
    protected Node decodeFloatingNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId) {
        long readerByteIndex = methodScope.reader.getByteIndex();

        methodScope.reader.setByteIndex(methodScope.encodedGraph.nodeStartOffsets[nodeOrderId]);
        NodeClass<?> nodeClass = methodScope.encodedGraph.getNodeClass(methodScope.reader.getUVInt());
        Node node = allocateFloatingNode(nodeClass);
        if (node instanceof FixedNode) {
            /*
             * This is a severe error that will lead to a corrupted graph, so it is better not to
             * continue decoding at all.
             */
            throw shouldNotReachHere("Not a floating node: " + node.getClass().getName()); // ExcludeFromJacocoGeneratedReport
        }

        /* Read the inputs of the node, possibly creating them recursively. */
        makeFloatingNodeInputs(methodScope, loopScope, node);

        /* Read the properties of the node. */
        readProperties(methodScope, node);
        /* There must not be any successors to read, since it is a non-fixed node. */
        assert node.getNodeClass().getEdges(Edges.Type.Successors).getCount() == 0 : Assertions.errorMessage(node);

        methodScope.reader.setByteIndex(readerByteIndex);
        return node;
    }

    private Node allocateFloatingNode(NodeClass<?> nodeClass) {
        ArrayDeque<? extends Node> cachedNodes = reusableFloatingNodes.get(nodeClass);
        if (cachedNodes != null) {
            Node node = cachedNodes.poll();
            if (node != null) {
                return node;
            }
        }
        return nodeClass.allocateInstance();
    }

    private void releaseFloatingNode(Node node) {
        ArrayDeque<Node> cachedNodes = reusableFloatingNodes.get(node.getNodeClass());
        if (cachedNodes == null) {
            cachedNodes = new ArrayDeque<>(2);
            reusableFloatingNodes.put(node.getNodeClass(), cachedNodes);
        }
        cachedNodes.push(node);
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
     * If this method replaces a node with another node, it must update its source position if the
     * original node has the source position set.
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
    @SuppressWarnings("try")
    protected void makeSuccessorStubs(MethodScope methodScope, LoopScope loopScope, Node node, boolean updatePredecessors) {
        try (DebugCloseable a = MakeSuccessorStubsTimer.start(debug)) {
            Edges edges = node.getNodeClass().getSuccessorEdges();
            for (int index = 0; index < edges.getDirectCount(); index++) {
                if (skipDirectEdge(node, edges, index)) {
                    continue;
                }
                int orderId = readOrderId(methodScope);
                Node value = makeStubNode(methodScope, loopScope, orderId);
                edges.initializeNode(node, index, value);
                if (updatePredecessors && value != null) {
                    edges.update(node, null, value);
                }
            }
            for (int index = edges.getDirectCount(); index < edges.getCount(); index++) {
                int size = methodScope.reader.getSVInt();
                if (size != -1) {
                    NodeList<Node> nodeList = new NodeSuccessorList<>(node, size);
                    edges.initializeList(node, index, nodeList);
                    for (int idx = 0; idx < size; idx++) {
                        int orderId = readOrderId(methodScope);
                        Node value = makeStubNode(methodScope, loopScope, orderId);
                        nodeList.initialize(idx, value);
                        if (updatePredecessors && value != null) {
                            edges.update(node, null, value);
                        }
                    }
                }
            }
        }
    }

    protected NodeClass<?> getNodeClass(MethodScope methodScope, LoopScope loopScope, int nodeOrderId) {
        if (nodeOrderId == GraphEncoder.NULL_ORDER_ID) {
            return null;
        }
        FixedNode node = (FixedNode) lookupNode(loopScope, nodeOrderId);
        if (node != null) {
            return node.getNodeClass();
        }

        long readerByteIndex = methodScope.reader.getByteIndex();
        methodScope.reader.setByteIndex(methodScope.encodedGraph.nodeStartOffsets[nodeOrderId]);
        NodeClass<?> nodeClass = methodScope.encodedGraph.getNodeClass(methodScope.reader.getUVInt());
        methodScope.reader.setByteIndex(readerByteIndex);
        return nodeClass;
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
        methodScope.reader.setByteIndex(methodScope.encodedGraph.nodeStartOffsets[nodeOrderId]);
        NodeClass<?> nodeClass = methodScope.encodedGraph.getNodeClass(methodScope.reader.getUVInt());
        Node stubNode = nodeClass.allocateInstance();
        if (graph.trackNodeSourcePosition()) {
            stubNode.setNodeSourcePosition(NodeSourcePosition.placeholder(graph.method()));
        }
        node = (FixedNode) graph.add(stubNode);
        /* Properties and edges are not filled yet, the node remains uninitialized. */
        methodScope.reader.setByteIndex(readerByteIndex);

        registerNode(loopScope, nodeOrderId, node, false, false);
        loopScope.nodesToProcess.set(nodeOrderId);
        return node;
    }

    protected static boolean skipDirectEdge(Node node, Edges edges, int index) {
        if (node instanceof Invoke || node instanceof MethodHandleWithExceptionNode) {
            assert node instanceof InvokeNode || node instanceof InvokeWithExceptionNode || node instanceof MethodHandleWithExceptionNode : "The only supported node classes. Got " + node.getClass();
            if (edges.type() == Edges.Type.Successors) {
                assert edges.getCount() == (node instanceof WithExceptionNode ? 2 : 1) : "Base Invokable has one successor (next); WithExceptionNode has two successors (next, exceptionEdge)";
                return true;
            } else {
                assert edges.type() == Edges.Type.Inputs : edges.type();
                if (edges.getType(index) == CallTargetNode.class) {
                    return true;
                } else if (edges.getType(index) == FrameState.class) {
                    assert edges.get(node, index) == null || edges.get(node, index) == ((StateSplit) node).stateAfter() : "Only stateAfter can be a FrameState during encoding";
                    return true;
                }
            }
        } else if (node instanceof LoopExitNode && edges.type() == Edges.Type.Inputs && edges.getType(index) == FrameState.class) {
            /* The stateAfter of the loop exit is filled manually. */
            return true;

        }
        return false;
    }

    protected Node lookupNode(LoopScope loopScope, int nodeOrderId) {
        return loopScope.getNode(nodeOrderId);
    }

    protected void registerNode(LoopScope loopScope, int nodeOrderId, Node node, boolean allowOverwrite, boolean allowNull) {
        assert node == null || node.isAlive();
        assert allowNull || node != null;
        assert allowOverwrite || lookupNode(loopScope, nodeOrderId) == null;

        loopScope.setNode(nodeOrderId, node);
        if (loopScope.methodScope.inliningLogDecoder != null) {
            loopScope.methodScope.inliningLogDecoder.registerNode(loopScope.methodScope.inliningLog, node, nodeOrderId);
        }
    }

    protected int readOrderId(MethodScope methodScope) {
        switch (methodScope.orderIdWidth) {
            case 1:
                return methodScope.reader.getU1();
            case 2:
                return methodScope.reader.getU2();
            case 4:
                return methodScope.reader.getS4();
        }
        throw GraalError.shouldNotReachHere("Invalid orderIdWidth: " + methodScope.orderIdWidth); // ExcludeFromJacocoGeneratedReport
    }

    protected Object readObject(MethodScope methodScope) {
        return methodScope.encodedGraph.getObject(methodScope.reader.getUVInt());
    }

    /**
     * Removes unnecessary nodes from the graph after decoding.
     *
     * @param rootMethodScope The current method.
     */
    protected void cleanupGraph(MethodScope rootMethodScope) {
        assert verifyEdges();
    }

    protected boolean verifyEdges() {
        for (Node node : graph.getNodes()) {
            assert node.isAlive();
            for (Node i : node.inputs()) {
                assert i.isAlive();
                assert i.usages().contains(node);
            }
            for (Node s : node.successors()) {
                assert s.isAlive();
                assert s.predecessor() == node : Assertions.errorMessage(s.predecessor(), node);
            }

            for (Node usage : node.usages()) {
                assert usage.isAlive();
                assert usage.inputs().contains(node) : node + " / " + usage + " / " + usage.inputs().count();
            }
            if (node.predecessor() != null) {
                assert node.predecessor().isAlive();
                assert node.predecessor().successors().contains(node);
            }
        }
        return true;
    }
}

class LoopDetector implements Runnable {

    /**
     * Information about loops before the actual loop nodes are inserted.
     */
    static class Loop {
        /**
         * The header, i.e., the target of backward branches.
         */
        MergeNode header;
        /**
         * The ends, i.e., the source of backward branches. The {@link EndNode#successors successor}
         * is the {@link #header loop header}.
         */
        List<EndNode> ends = new ArrayList<>(2);
        /**
         * Exits of the loop. The successor is a {@link MergeNode} marked in
         * {@link MethodScope#loopExplosionMerges}.
         */
        List<AbstractEndNode> exits = new ArrayList<>();
        /**
         * Set to true when the loop is irreducible, i.e., has multiple entries. See
         * {@link #handleIrreducibleLoop} for details on the handling.
         */
        boolean irreducible;
    }

    private final StructuredGraph graph;
    private final MethodScope methodScope;

    private Loop irreducibleLoopHandler;
    private IntegerSwitchNode irreducibleLoopSwitch;

    protected LoopDetector(StructuredGraph graph, MethodScope methodScope) {
        this.graph = graph;
        this.methodScope = methodScope;
    }

    @Override
    public void run() {
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "Before loop detection");

        if (methodScope.loopExplosionHead == null) {
            /*
             * The to-be-exploded loop was not reached during partial evaluation (e.g., because
             * there was a deoptimization beforehand), or the method might not even contain a loop.
             * This is an uncommon case, but not an error.
             */
            return;
        }

        List<Loop> orderedLoops = findLoops();
        assert orderedLoops.get(orderedLoops.size() - 1) == irreducibleLoopHandler : "outermost loop must be the last element in the list";

        for (Loop loop : orderedLoops) {
            if (loop.ends.isEmpty()) {
                assert loop == irreducibleLoopHandler : Assertions.errorMessage(loop, irreducibleLoopHandler);
                continue;
            }

            /*
             * The algorithm to find loop exits requires that inner loops have already been
             * processed. Therefore, we need to iterate the loops in order (inner loops before outer
             * loops), and we cannot find the exits for all loops before we start inserting nodes.
             */
            findLoopExits(loop);

            if (loop.irreducible) {
                handleIrreducibleLoop(loop);
            } else {
                insertLoopNodes(loop);
            }
            debug.dump(DebugContext.DETAILED_LEVEL, graph, "After handling of loop %s", loop.header);
        }

        logIrreducibleLoops();
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "After loop detection");
    }

    private List<Loop> findLoops() {
        /* Mapping from the loop header node to additional loop information. */
        EconomicMap<MergeNode, Loop> unorderedLoops = EconomicMap.create(Equivalence.IDENTITY);
        /* Loops in reverse order of, i.e., inner loops before outer loops. */
        List<Loop> orderedLoops = new ArrayList<>();

        /*
         * Ensure we have an outermost loop that we can use to eliminate irreducible loops. This
         * loop can remain empty (no ends), in which case it is ignored.
         */
        irreducibleLoopHandler = findOrCreateLoop(unorderedLoops, methodScope.loopExplosionHead);

        NodeBitMap visited = graph.createNodeBitMap();
        NodeBitMap active = graph.createNodeBitMap();
        Deque<Node> stack = new ArrayDeque<>();
        visited.mark(methodScope.loopExplosionHead);
        stack.push(methodScope.loopExplosionHead);

        while (!stack.isEmpty()) {
            Node current = stack.peek();
            assert visited.isMarked(current);

            if (active.isMarked(current)) {
                /* We are back-tracking, i.e., all successor nodes have been processed. */
                stack.pop();
                active.clear(current);

                if (current instanceof MergeNode) {
                    Loop loop = unorderedLoops.get((MergeNode) current);
                    if (loop != null) {
                        /*
                         * Since nodes are popped in reverse order that they were pushed, we add
                         * inner loops before outer loops here.
                         */
                        assert !orderedLoops.contains(loop);
                        orderedLoops.add(loop);
                    }
                }

            } else {
                /*
                 * Process the node. Note that we do not remove the node from the stack, i.e., we
                 * will peek it again. But the next time the node is marked as active, so we do not
                 * execute this code again.
                 */
                active.mark(current);
                for (Node successor : current.cfgSuccessors()) {
                    if (active.isMarked(successor)) {
                        /* Detected a cycle, i.e., a backward branch of a loop. */
                        Loop loop = findOrCreateLoop(unorderedLoops, (MergeNode) successor);
                        assert !loop.ends.contains(current);
                        loop.ends.add((EndNode) current);

                    } else if (visited.isMarked(successor)) {
                        /* Forward merge into a branch we are already exploring. */

                    } else {
                        /* Forward branch to a node we have not seen yet. */
                        visited.mark(successor);
                        stack.push(successor);
                    }
                }
            }
        }
        return orderedLoops;
    }

    private Loop findOrCreateLoop(EconomicMap<MergeNode, Loop> unorderedLoops, MergeNode loopHeader) {
        assert methodScope.loopExplosionMerges.contains(loopHeader) : loopHeader;
        Loop loop = unorderedLoops.get(loopHeader);
        if (loop == null) {
            loop = new Loop();
            loop.header = loopHeader;
            unorderedLoops.put(loopHeader, loop);
        }
        return loop;
    }

    private void findLoopExits(Loop loop) {
        /*
         * Backward marking of loop nodes: Starting with the known loop ends, we mark all nodes that
         * are reachable until we hit the loop begin. All successors of loop nodes that are not
         * marked as loop nodes themselves are exits of the loop. We mark all successors, and then
         * subtract the loop nodes, to find the exits.
         */

        List<Node> possibleExits = new ArrayList<>();
        NodeBitMap visited = graph.createNodeBitMap();
        Deque<Node> stack = new ArrayDeque<>();
        for (EndNode loopEnd : loop.ends) {
            stack.push(loopEnd);
            visited.mark(loopEnd);
        }

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if (current == loop.header) {
                continue;
            }
            if (!graph.isNew(methodScope.methodStartMark, current)) {
                /*
                 * The current node is before the method that contains the exploded loop. The loop
                 * must have a second entry point, i.e., it is an irreducible loop.
                 */
                loop.irreducible = true;
                return;
            }

            for (Node predecessor : current.cfgPredecessors()) {
                if (predecessor instanceof LoopExitNode) {
                    /*
                     * Inner loop. We do not need to mark every node of it, instead we just continue
                     * marking at the loop header.
                     */
                    LoopBeginNode innerLoopBegin = ((LoopExitNode) predecessor).loopBegin();
                    if (!visited.isMarked(innerLoopBegin)) {
                        stack.push(innerLoopBegin);
                        visited.mark(innerLoopBegin);

                        /*
                         * All loop exits of the inner loop possibly need a LoopExit of our loop.
                         * Because we are processing inner loops first, we are guaranteed to already
                         * have all exits of the inner loop.
                         */
                        for (LoopExitNode exit : innerLoopBegin.loopExits()) {
                            possibleExits.add(exit);
                        }
                    }

                } else if (!visited.isMarked(predecessor)) {
                    stack.push(predecessor);
                    visited.mark(predecessor);

                    if (predecessor instanceof ControlSplitNode) {
                        for (Node succ : predecessor.cfgSuccessors()) {
                            /*
                             * We would not need to mark the current node, and would not need to
                             * mark visited nodes. But it is easier to just mark everything, since
                             * we subtract all visited nodes in the end anyway. Note that at this
                             * point we do not have the complete visited information, so we would
                             * always mark too many possible exits.
                             */
                            possibleExits.add(succ);
                        }
                    }
                }
            }
        }

        /*
         * Now we know all the actual loop exits. Ideally, we would insert LoopExit nodes for them.
         * However, a LoopExit needs a valid FrameState that captures the state at the point where
         * we exit the loop. During graph decoding, we create a FrameState for every exploded loop
         * iteration. We need to do a forward marking until we hit the next such point. This puts
         * some nodes into the loop that are actually not part of the loop.
         *
         * In some cases, we did not create a FrameState during graph decoding: when there was no
         * LoopExit in the original loop that we exploded. This happens for code paths that lead
         * immediately to a DeoptimizeNode.
         *
         * Both cases mimic the behavior of the BytecodeParser, which also puts more nodes than
         * necessary into a loop because it computes loop information based on bytecodes, before the
         * actual parsing.
         */
        for (Node succ : possibleExits) {
            if (!visited.contains(succ)) {
                stack.push(succ);
                visited.mark(succ);
                assert !methodScope.loopExplosionMerges.contains(succ);
            }
        }

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            assert visited.isMarked(current);
            assert current instanceof ControlSinkNode || current instanceof LoopEndNode || current.cfgSuccessors().iterator().hasNext() : "Must not reach a node that has not been decoded yet";

            for (Node successor : current.cfgSuccessors()) {
                if (visited.isMarked(successor)) {
                    /* Already processed this successor. */

                } else if (methodScope.loopExplosionMerges.contains(successor)) {
                    /*
                     * We have a FrameState for the successor. The LoopExit will be inserted between
                     * the current node and the successor node. Since the successor node is a
                     * MergeNode, the current node mus be a AbstractEndNode with only that MergeNode
                     * as the successor.
                     */
                    assert successor instanceof MergeNode : Assertions.errorMessage(successor);
                    assert !loop.exits.contains(current);
                    loop.exits.add((AbstractEndNode) current);

                } else {
                    /* Node we have not seen yet. */
                    visited.mark(successor);
                    stack.push(successor);
                }
            }
        }
        /*-
         * Special case loop exits that merge on a common merge node. If the original, merge
         * exploded loop, contains loop exit paths, where a loop exit path (a path already exiting
         * the loop see loop exits vs natural loop exits) is already a natural one merge on a loop
         * explosion merge, we run into troubles with phi nodes and proxy nodes.
         *
         * Consider the following piece of code outlining a loop exit path of a merge explode annotated loop
         *
         * <pre>
         *      // merge exploded loop
         *      mergeExplodedLoop: while(....)
         *          ...
         *          if(condition effectively exiting the loop) // natural loop exit
         *
         *              break mergeExplodedLoop;
         *          ...
         *
         *      // outerLoopContinueCode that uses values proxied inside the loop
         * </pre>
         *
         *
         * However, once the exit path contains control flow like below
         * <pre>
         *      // merge exploded loop
         *      mergeExplodedLoop: while(....)
         *          ...
         *          if(condition effectively exiting the loop) // natural loop exit
         *              if(some unrelated condition) {
         *                 ...
         *              } else {
         *                  ...
         *              }
         *              break mergeExplodedLoop;
         *          ...
         *
         *      // outerLoopContinueCode that uses values proxied inside the loop
         * </pre>
         *
         * We would produce two loop exits that merge booth on the outerLoopContinueCode.
         * This would require the generation of complex phi and proxy constructs, thus we include the merge inside the
         * loop if we find a subsequent loop explosion merge.
         */
        EconomicSet<MergeNode> merges = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        EconomicSet<MergeNode> mergesToRemove = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

        for (AbstractEndNode end : loop.exits) {
            AbstractMergeNode merge = end.merge();
            assert merge instanceof MergeNode : Assertions.errorMessage(merge);
            if (merges.contains((MergeNode) merge)) {
                mergesToRemove.add((MergeNode) merge);
            } else {
                merges.add((MergeNode) merge);
            }
        }
        merges.clear();
        merges.addAll(mergesToRemove);
        mergesToRemove.clear();
        outer: for (MergeNode merge : merges) {
            for (EndNode end : merge.ends) {
                if (!loop.exits.contains(end)) {
                    continue outer;
                }
            }
            mergesToRemove.add(merge);
        }
        // we found a shared merge as outlined above
        if (mergesToRemove.size() > 0) {
            assert merges.size() < loop.exits.size() : Assertions.errorMessage(merges, loop, loop.exits);
            outer: for (MergeNode merge : mergesToRemove) {
                FixedNode current = merge;
                while (current != null) {
                    if (current instanceof FixedWithNextNode) {
                        current = ((FixedWithNextNode) current).next();
                        continue;
                    }
                    if (current instanceof EndNode && methodScope.loopExplosionMerges.contains(((EndNode) current).merge())) {
                        // we found the place for the loop exit introduction since the subsequent
                        // merge has a frame state
                        loop.exits.removeIf(x -> x.merge() == merge);
                        loop.exits.add((EndNode) current);
                        break;
                    }
                    /*
                     * No next merge was found, this can only mean no immediate unroll happend next,
                     * i.e., there is no subsequent iteration of any loop exploded directly after,
                     * thus no loop exit possible.
                     */
                    continue outer;
                }
            }
        }
    }

    private void insertLoopNodes(Loop loop) {
        MergeNode merge = loop.header;
        FrameState stateAfter = merge.stateAfter().duplicate();
        FixedNode afterMerge = merge.next();
        merge.setNext(null);
        EndNode preLoopEnd = graph.add(new EndNode());
        LoopBeginNode loopBegin = graph.add(new LoopBeginNode());

        merge.setNext(preLoopEnd);
        /* Add the single non-loop predecessor of the loop header. */
        loopBegin.addForwardEnd(preLoopEnd);
        loopBegin.setNext(afterMerge);
        loopBegin.setStateAfter(stateAfter);

        /*
         * Phi functions of the original merge need to be split: inputs that come from forward edges
         * remain with the original phi function; inputs that come from backward edges are added to
         * new phi functions.
         */
        List<PhiNode> mergePhis = merge.phis().snapshot();
        List<PhiNode> loopBeginPhis = new ArrayList<>(mergePhis.size());
        for (int i = 0; i < mergePhis.size(); i++) {
            PhiNode mergePhi = mergePhis.get(i);
            PhiNode loopBeginPhi = graph.addWithoutUnique(new ValuePhiNode(mergePhi.stamp(NodeView.DEFAULT), loopBegin));
            mergePhi.replaceAtUsages(loopBeginPhi);
            /*
             * The first input of the new phi function is the original phi function, for the one
             * forward edge of the LoopBeginNode.
             */
            loopBeginPhi.addInput(mergePhi);
            loopBeginPhis.add(loopBeginPhi);
        }

        for (EndNode endNode : loop.ends) {
            for (int i = 0; i < mergePhis.size(); i++) {
                PhiNode mergePhi = mergePhis.get(i);
                PhiNode loopBeginPhi = loopBeginPhis.get(i);
                loopBeginPhi.addInput(mergePhi.valueAt(endNode));
            }

            merge.removeEnd(endNode);
            LoopEndNode loopEnd = graph.add(new LoopEndNode(loopBegin));
            endNode.replaceAndDelete(loopEnd);
        }

        /*
         * Insert the LoopExit nodes (the easy part) and compute the FrameState for the new exits
         * (the difficult part).
         */
        for (AbstractEndNode exit : loop.exits) {
            AbstractMergeNode loopExplosionMerge = exit.merge();
            assert methodScope.loopExplosionMerges.contains(loopExplosionMerge);

            LoopExitNode loopExit = graph.add(new LoopExitNode(loopBegin));
            exit.replaceAtPredecessor(loopExit);
            loopExit.setNext(exit);
            assignLoopExitState(loopExit, loopExplosionMerge, exit);
        }
    }

    /**
     * During graph decoding, we create a FrameState for every exploded loop iteration. This is
     * mostly the state that we want, we only need to tweak it a little bit to account for phis.
     */
    private void assignLoopExitState(LoopExitNode loopExit, AbstractMergeNode loopExplosionMerge, AbstractEndNode loopExplosionEnd) {
        FrameState oldState = loopExplosionMerge.stateAfter();

        FrameState.ValueFunction valueFunction = new FrameState.ValueFunction() {
            @Override
            public ValueNode apply(int index, ValueNode value) {
                /*
                 * The LoopExit is inserted before the existing merge, i.e., separately for every
                 * branch that leads to the merge. So for phi functions of the merge, we need to
                 * take the input that corresponds to our branch.
                 */
                if (loopExplosionMerge.isPhiAtMerge(value)) {
                    assert value instanceof PhiNode : "isPhiAtMerge should guarantee that value is a PhiNode";
                    return ((PhiNode) value).valueAt(loopExplosionEnd);
                }
                return value;
            }
        };

        FrameState newState = oldState.duplicate(valueFunction);

        assert loopExit.stateAfter() == null;
        loopExit.setStateAfter(graph.add(newState));
    }

    /**
     * Graal does not support irreducible loops (loops with more than one entry point). There are
     * two ways to make them reducible: 1) duplicate nodes (peel a loop iteration starting at the
     * second entry point until we reach the first entry point), or 2) insert a big outer loop
     * covering the whole method and build a state machine for the different loop entry points.
     * Since node duplication can lead to an exponential explosion of nodes in the worst case, we
     * use the second approach.
     *
     * We already did some preparations to insert a big outer loop:
     * {@link MethodScope#loopExplosionHead} is the loop header for the outer loop, and we ensured
     * that we have a {@link Loop} data object for it in {@link #irreducibleLoopHandler}.
     *
     * Now we need to insert the state machine. We have several implementation restrictions to make
     * that efficient:
     * <ul>
     * <li>There must be only one loop variable, i.e., one value that is different in the
     * {@link FrameState} of the different loop headers.</li>
     * <li>The loop variable must use the primitive {@code int} type, because Graal only has a
     * {@link IntegerSwitchNode switch node} for {@code int}.</li>
     * <li>The values of the loop variable that are merged are {@link PrimitiveConstant compile time
     * constants}.</li>
     * </ul>
     */
    private void handleIrreducibleLoop(Loop loop) {
        assert loop != irreducibleLoopHandler : Assertions.errorMessageContext("loop", loop, "irreducibleLoop", irreducibleLoopHandler);

        FrameState loopState = loop.header.stateAfter();
        FrameState explosionHeadState = irreducibleLoopHandler.header.stateAfter();
        assert loopState.outerFrameState() == explosionHeadState.outerFrameState() : Assertions.errorMessage(loopState, loopState.outerFrameState(), explosionHeadState,
                        explosionHeadState.outerFrameState());
        NodeInputList<ValueNode> loopValues = loopState.values();
        NodeInputList<ValueNode> explosionHeadValues = explosionHeadState.values();
        assert loopValues.size() == explosionHeadValues.size() : Assertions.errorMessage(loopValues, explosionHeadValues);

        /*
         * Find the loop variable, and the value of the loop variable for our loop and the outermost
         * loop. There must be exactly one loop variable.
         */
        int loopVariableIndex = -1;
        ValueNode loopValue = null;
        ValueNode explosionHeadValue = null;
        for (int i = 0; i < loopValues.size(); i++) {
            ValueNode curLoopValue = loopValues.get(i);
            ValueNode curExplosionHeadValue = explosionHeadValues.get(i);

            if (curLoopValue != curExplosionHeadValue) {
                if (loopVariableIndex != -1) {
                    throw bailout("must have only one variable that is changed in loop. " + loopValue + " != " + explosionHeadValue + " and " + curLoopValue + " != " + curExplosionHeadValue);
                }

                loopVariableIndex = i;
                loopValue = curLoopValue;
                explosionHeadValue = curExplosionHeadValue;
            }
        }
        assert loopVariableIndex != -1;
        assert explosionHeadValue != null;

        ValuePhiNode loopVariablePhi;
        SortedMap<Integer, AbstractBeginNode> dispatchTable = new TreeMap<>();
        AbstractBeginNode unreachableDefaultSuccessor;
        if (irreducibleLoopSwitch == null) {
            /*
             * This is the first irreducible loop. We need to build the initial state machine
             * (dispatch for the loop header of the outermost loop).
             */
            assert !irreducibleLoopHandler.header.isPhiAtMerge(explosionHeadValue);
            assert irreducibleLoopHandler.header.phis().isEmpty();

            /* The new phi function for the loop variable. */
            loopVariablePhi = graph.addWithoutUnique(new ValuePhiNode(explosionHeadValue.stamp(NodeView.DEFAULT).unrestricted(), irreducibleLoopHandler.header));
            for (int i = 0; i < irreducibleLoopHandler.header.phiPredecessorCount(); i++) {
                loopVariablePhi.addInput(explosionHeadValue);
            }

            /*
             * Build the new FrameState for the loop header. There is only one change in comparison
             * to the old FrameState: the loop variable is replaced with the phi function.
             */
            int loopVariableIndexCopy = loopVariableIndex;
            FrameState.ValueFunction valueFunction = new FrameState.ValueFunction() {
                @Override
                public ValueNode apply(int index, ValueNode node) {
                    if (index == loopVariableIndexCopy) {
                        return loopVariablePhi;
                    } else {
                        return node;
                    }
                }
            };
            FrameState newFrameState = graph.add(explosionHeadState.duplicate(valueFunction));
            explosionHeadState.replaceAtUsages(newFrameState);

            /*
             * Disconnect the outermost loop header from its loop body, so that we can later on
             * insert the switch node. Collect dispatch information for the outermost loop.
             */
            FixedNode handlerNext = irreducibleLoopHandler.header.next();
            irreducibleLoopHandler.header.setNext(null);
            BeginNode handlerBegin = graph.add(new BeginNode());
            handlerBegin.setNext(handlerNext);
            dispatchTable.put(asInt(explosionHeadValue), handlerBegin);

            /*
             * We know that there will always be a matching key in the switch. But Graal always
             * wants a default successor, so we build a dummy block that just deoptimizes.
             */
            unreachableDefaultSuccessor = graph.add(new BeginNode());
            DeoptimizeNode deopt = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.UnreachedCode));
            unreachableDefaultSuccessor.setNext(deopt);

        } else {
            /*
             * This is the second or a subsequent irreducible loop, i.e., we already inserted a
             * switch node before. We re-create the dispatch state machine of that switch, so that
             * we can extend it with one more branch.
             */
            assert irreducibleLoopHandler.header.isPhiAtMerge(explosionHeadValue);
            assert irreducibleLoopHandler.header.phis().count() == 1 : Assertions.errorMessageContext("irrHeader.phis", irreducibleLoopHandler.header.phis());
            assert irreducibleLoopHandler.header.phis().first() == explosionHeadValue : Assertions.errorMessageContext("irrHeader.phis", irreducibleLoopHandler.header.phis());
            assert irreducibleLoopSwitch.value() == explosionHeadValue : Assertions.errorMessageContext("irrLoopSwitch", irreducibleLoopSwitch.value(), "explosionHead", explosionHeadValue);

            /* We can modify the phi function used by the old switch node. */
            loopVariablePhi = (ValuePhiNode) explosionHeadValue;

            /*
             * We cannot modify the old switch node. Insert all information from the old switch node
             * into our temporary data structures for the new, larger, switch node.
             */
            for (int i = 0; i < irreducibleLoopSwitch.keyCount(); i++) {
                int key = irreducibleLoopSwitch.keyAt(i).asInt();
                dispatchTable.put(key, irreducibleLoopSwitch.successorAtKey(key));
            }
            unreachableDefaultSuccessor = irreducibleLoopSwitch.defaultSuccessor();

            /* Unlink and delete the old switch node, we do not need it anymore. */
            assert irreducibleLoopHandler.header.next() == irreducibleLoopSwitch : Assertions.errorMessage(irreducibleLoopHandler.header.next(), irreducibleLoopSwitch);
            irreducibleLoopHandler.header.setNext(null);
            irreducibleLoopSwitch.clearSuccessors();
            irreducibleLoopSwitch.safeDelete();
        }

        /* Insert our loop into the dispatch state machine. */
        assert loop.header.phis().isEmpty();
        BeginNode dispatchBegin = graph.add(new BeginNode());
        EndNode dispatchEnd = graph.add(new EndNode());
        dispatchBegin.setNext(dispatchEnd);
        loop.header.addForwardEnd(dispatchEnd);
        int intLoopValue = asInt(loopValue);
        assert !dispatchTable.containsKey(intLoopValue);
        dispatchTable.put(intLoopValue, dispatchBegin);

        /* Disconnect the ends of our loop and re-connect them to the outermost loop header. */
        for (EndNode end : loop.ends) {
            loop.header.removeEnd(end);
            irreducibleLoopHandler.ends.add(end);
            irreducibleLoopHandler.header.addForwardEnd(end);
            loopVariablePhi.addInput(loopValue);
        }

        /* Build and insert the switch node. */
        irreducibleLoopSwitch = graph.add(createSwitch(loopVariablePhi, dispatchTable, unreachableDefaultSuccessor));
        irreducibleLoopHandler.header.setNext(irreducibleLoopSwitch);
    }

    private static int asInt(ValueNode node) {
        if (!node.isConstant() || node.asJavaConstant().getJavaKind() != JavaKind.Int) {
            throw bailout("must have a loop variable of type int. " + node);
        }
        return node.asJavaConstant().asInt();
    }

    private static RuntimeException bailout(String msg) {
        throw new PermanentBailoutException("Graal implementation restriction: Method with %s loop explosion %s", LoopExplosionPlugin.LoopExplosionKind.MERGE_EXPLODE, msg);
    }

    private static IntegerSwitchNode createSwitch(ValuePhiNode switchedValue, SortedMap<Integer, AbstractBeginNode> dispatchTable, AbstractBeginNode defaultSuccessor) {
        int numKeys = dispatchTable.size();
        int numSuccessors = numKeys + 1;

        AbstractBeginNode[] switchSuccessors = new AbstractBeginNode[numSuccessors];
        int[] switchKeys = new int[numKeys];
        double[] switchKeyProbabilities = new double[numSuccessors];
        int[] switchKeySuccessors = new int[numSuccessors];

        int idx = 0;
        for (Map.Entry<Integer, AbstractBeginNode> entry : dispatchTable.entrySet()) {
            switchSuccessors[idx] = entry.getValue();
            switchKeys[idx] = entry.getKey();
            switchKeyProbabilities[idx] = 1d / numKeys;
            switchKeySuccessors[idx] = idx;
            idx++;
        }
        switchSuccessors[idx] = defaultSuccessor;
        /* We know the default branch is never going to be executed. */
        switchKeyProbabilities[idx] = 0;
        switchKeySuccessors[idx] = idx;

        return new IntegerSwitchNode(switchedValue, switchSuccessors, switchKeys, switchKeySuccessors, ProfileData.SwitchProbabilityData.unknown(switchKeyProbabilities));
    }

    /**
     * Print information about irreducible loops, when enabled with
     * -Djdk.graal.Log=IrreducibleLoops.
     */
    @SuppressWarnings("try")
    private void logIrreducibleLoops() {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("IrreducibleLoops")) {
            if (debug.isLogEnabled(DebugContext.BASIC_LEVEL) && irreducibleLoopSwitch != null) {
                StringBuilder msg = new StringBuilder("Inserted state machine to remove irreducible loops. Dispatching to the following states: ");
                String sep = "";
                for (int i = 0; i < irreducibleLoopSwitch.keyCount(); i++) {
                    msg.append(sep).append(irreducibleLoopSwitch.keyAt(i).asInt());
                    sep = ", ";
                }
                debug.log(DebugContext.BASIC_LEVEL, "%s", msg);
            }
        }
    }
}

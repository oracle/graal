/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.duplication.phases;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.EscapeObjectState;
import jdk.graal.compiler.nodes.virtual.MaterializedObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.graal.compiler.util.CollectionsUtil;
import org.graalvm.word.LocationIdentity;

/// Creates shared nodes after a merge from equivalent nodes on each incoming branch.
public final class NodeMergeProcessor {

    private NodeMergeProcessor() {
    }

    /// Tries to merge, or "pull through the merge", the nodes in `nodes`.
    ///
    /// Returns a node that can be inserted after `merge` to replace the nodes before the merge, or
    /// `null` when the nodes cannot be merged.
    public static <T extends Node> T merge(T[] nodes, AbstractMergeNode merge) {

        // NodeClass of the node being moved below the merge.
        NodeClass<? extends Node> nodeClass = nodes[0].getNodeClass();

        // The flat comparison code below does not work for input lists.
        if (nodeClass.getInputEdges().getDirectCount() != nodeClass.getInputEdges().getCount()) {
            return null;
        }

        // Check that all non-input fields are equal.
        for (int i = 1; i < nodes.length; i++) {
            assert nodes[i].getClass() == nodes[0].getClass() : "only nodes of the same type can be merged";
            if (!nodeClass.dataEquals(nodes[0], nodes[i])) {
                return null;
            }
        }

        T firstNode = nodes[0];

        if (firstNode instanceof FloatingReadNode) {
            FloatingReadNode floatingReadNode = (FloatingReadNode) firstNode;
            if (floatingReadNode.getLocationIdentity().isMutable()) {
                // We don't know whether the floating read may be pushed through the phi or whether
                // this is violating read/write anti-dependencies.
                return null;
            }
        }

        T newNode = null;
        Iterator<Position> inputIterator = firstNode.inputPositions().iterator();
        while (inputIterator.hasNext()) {
            Position position = inputIterator.next();
            InputType inputType = position.getInputType();

            /*
             * Check for each input if (a) it's equal in all nodes, (b) can be merged by a phi or
             * (c) cannot be merged.
             */
            Node[] nodesAtPosition = getInputNodesAtPosition(position, nodes);
            if (!allEqual(nodesAtPosition)) {

                Node value = null;
                if (nodesAtPosition instanceof ValueNode[]) {
                    ValueNode[] values = (ValueNode[]) nodesAtPosition;
                    if (inputType == InputType.Value) {
                        value = mergeValues(values, merge);
                    } else if (inputType == InputType.Memory) {
                        LocationIdentity loc = null;
                        /*
                         * Certain nodes are both a memory access and a memory kill and they do not
                         * necessarily access and kill the same location, however for a correct
                         * memory graph we create a phi with a location of the access part, i.e.,
                         * the getLocationIdentity can return a different accessed location before
                         * the killed location
                         */
                        if (MemoryKill.isMemoryKill(firstNode)) {
                            if (MemoryKill.isSingleMemoryKill(firstNode)) {
                                loc = ((SingleMemoryKill) firstNode).getKilledLocationIdentity();
                            } else if (MemoryKill.isMultiMemoryKill(firstNode)) {
                                return null;
                            }
                        }
                        if (firstNode instanceof MemoryAccess) {
                            loc = ((MemoryAccess) firstNode).getLocationIdentity();
                        }
                        assert loc != null : "Must have a location to produce a phi for a node in the memory graph " + firstNode;
                        value = new MemoryPhiNode(merge, loc, values);
                    } else if (inputType == InputType.Guard) {
                        value = new GuardPhiNode(merge, values);
                    } else {
                        assert inputType == InputType.Extension : inputType;
                        Class<?> clazz = values[0] == null ? null : values[0].getClass();
                        if (clazz == null || CollectionsUtil.anyMatch(values, node -> node == null || node.getClass() != clazz)) {
                            return null;
                        }
                        value = merge(values, merge);
                    }
                } else if (nodesAtPosition instanceof FrameState[]) {
                    FrameState[] frameStates = (FrameState[]) nodesAtPosition;
                    value = mergeStates(frameStates, merge);
                }

                if (value == null) {
                    return null;
                }
                if (newNode == null) {
                    newNode = cloneNode(firstNode);
                }
                position.initialize(newNode, value);
            }
        }
        return newNode == null ? firstNode : newNode;
    }

    private static Node[] getInputNodesAtPosition(Position position, Node[] nodes) {
        InputType inputType = position.getInputType();
        if (inputType == InputType.State) {
            FrameState[] frameStates = new FrameState[nodes.length];
            for (int i = 0; i < nodes.length; ++i) {
                frameStates[i] = (FrameState) position.get(nodes[i]);
            }
            return frameStates;
        } else if (inputType == InputType.Value || inputType == InputType.Memory || inputType == InputType.Guard || inputType == InputType.Extension) {
            ValueNode[] values = new ValueNode[nodes.length];
            for (int i = 0; i < nodes.length; ++i) {
                values[i] = (ValueNode) position.get(nodes[i]);
            }
            return values;
        } else {
            Node[] values = new Node[nodes.length];
            for (int i = 0; i < nodes.length; ++i) {
                values[i] = position.get(nodes[i]);
            }
            return values;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T cloneNode(T firstNode) {
        return (T) firstNode.copyWithInputs(false);
    }

    private static FrameState mergeStates(FrameState[] frameStates, AbstractMergeNode merge) {
        FrameState firstState = frameStates[0];
        if (allEqual(frameStates)) {
            return firstState;
        }
        // check for compatible frame states (bci, method, ...)
        if (!CollectionsUtil.allMatch(frameStates, state -> isCompatible(firstState, state))) {
            return null;
        }
        // merge valid for deoptimization
        boolean validForDeoptimization = CollectionsUtil.allMatch(frameStates, FrameState::isValidForDeoptimization);
        // merge the outer frame states
        FrameState outerFrameState;
        if (firstState.outerFrameState() == null) {
            if (CollectionsUtil.anyMatch(frameStates, state -> state.outerFrameState() != null)) {
                return null;
            }
            outerFrameState = null;
        } else {
            if (CollectionsUtil.anyMatch(frameStates, state -> state.outerFrameState() == null)) {
                return null;
            }
            outerFrameState = mergeStates(CollectionsUtil.mapToArray(frameStates, state -> state.outerFrameState(), FrameState[]::new), merge);
            if (outerFrameState == null) {
                return null;
            }
        }
        // merge locals, stack and locked objects
        List<ValueNode> values = mergeValueLists(frameStates, merge, FrameState::values, NodeMergeProcessor::mergeValues, ValueNode[]::new);
        if (values == null) {
            return null;
        }
        // merge virtual object mappings
        List<EscapeObjectState> virtualObjectMappings = mergeValueLists(frameStates, merge, FrameState::virtualObjectMappings, NodeMergeProcessor::mergeEscapeObjectStates, EscapeObjectState[]::new);
        if (virtualObjectMappings == null && firstState.virtualObjectMappings() != null) {
            return null;
        }

        if (values == firstState.values() && virtualObjectMappings == firstState.virtualObjectMappings() && outerFrameState == firstState.outerFrameState()) {
            return firstState;
        } else {
            List<MonitorIdNode> monitorIds = new ArrayList<>(firstState.monitorIdCount());
            for (int i = 0; i < firstState.monitorIdCount(); ++i) {
                monitorIds.add(firstState.monitorIdAt(i));
            }

            return new FrameState(outerFrameState,
                            firstState.getCode(),
                            firstState.bci,
                            values,
                            firstState.localsSize(),
                            firstState.stackSize(),
                            firstState.locksSize(),
                            firstState.getStackState(),
                            validForDeoptimization,
                            monitorIds,
                            virtualObjectMappings, null);
        }
    }

    private static EscapeObjectState mergeEscapeObjectStates(EscapeObjectState[] objectStates, AbstractMergeNode merge) {
        EscapeObjectState firstObjectState = objectStates[0];
        if (allEqual(objectStates)) {
            return firstObjectState;
        }
        if (CollectionsUtil.anyMatch(objectStates, value -> value.getClass() != firstObjectState.getClass() || value.object() != firstObjectState.object())) {
            return null;
        }
        if (firstObjectState instanceof MaterializedObjectState) {
            MaterializedObjectState materializedState = (MaterializedObjectState) firstObjectState;
            ValueNode[] array = CollectionsUtil.mapToArray(objectStates, objectState -> ((MaterializedObjectState) objectState).materializedValue(), ValueNode[]::new);
            if (allEqual(array)) {
                return materializedState;
            } else {
                ValueNode materializedValue = mergeValues(array, merge);
                if (materializedValue == null) {
                    return null;
                }
                return new MaterializedObjectState(materializedState.object(), materializedValue);
            }
        } else {
            VirtualObjectState virtualState = (VirtualObjectState) firstObjectState;
            List<ValueNode> values = mergeValueLists(objectStates, merge, objectState -> ((VirtualObjectState) objectState).values(), NodeMergeProcessor::mergeValues, ValueNode[]::new);
            if (values == null) {
                return null;
            }
            if (values == virtualState.values()) {
                return virtualState;
            } else {
                return new VirtualObjectState(virtualState.object(), values);
            }
        }
    }

    private static boolean allEqual(Object[] nodes) {
        Object first = nodes[0];
        for (int i = 1; i < nodes.length; i++) {
            if (nodes[i] != first) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompatible(FrameState state, FrameState other) {
        return other.bci == state.bci &&
                        Objects.equals(other.getCode(), state.getCode()) &&
                        other.values().size() == state.values().size() &&
                        other.virtualObjectMappingCount() == state.virtualObjectMappingCount();
    }

    private static <T extends Node, V extends Node> List<V> mergeValueLists(T[] nodes,
                    AbstractMergeNode merge,
                    Function<T, NodeInputList<V>> listFunc,
                    BiFunction<V[], AbstractMergeNode, V> mergeFunc,
                    IntFunction<V[]> arrayFunc) {

        List<V> firstList = listFunc.apply(nodes[0]);
        List<V> values = null;
        if (firstList != null) {
            for (int i = 0; i < firstList.size(); i++) {
                final int finalI = i;
                V[] array = CollectionsUtil.mapToArray(nodes, state -> listFunc.apply(state).get(finalI), arrayFunc);
                if (!allEqual(array)) {
                    V value = mergeFunc.apply(array, merge);
                    if (value == null) {
                        return null;
                    }
                    if (values == null) {
                        values = new ArrayList<>(firstList);
                    }
                    values.set(i, value);
                }
            }
        }
        return values == null ? firstList : values;
    }

    private static ValueNode mergeValues(ValueNode[] values, AbstractMergeNode merge) {
        assert !allEqual(values) : "Values are not all equal at merge=" + merge + " values=" + values;
        if (CollectionsUtil.anyMatch(values, value -> value == null || value instanceof VirtualObjectNode)) {
            return null;
        } else {
            // check for compatibility before calling "meet" on the stamps
            Stamp firstStamp = values[0].stamp(NodeView.DEFAULT);
            for (int i = 1; i < values.length; i++) {
                if (!firstStamp.isCompatible(values[i].stamp(NodeView.DEFAULT))) {
                    return null;
                }
            }
            Stamp phiStamp = StampTool.meet(Arrays.asList(values));
            if (phiStamp.hasValues()) {
                return new ValuePhiNode(phiStamp, merge, values);
            } else {
                return null;
            }
        }
    }
}

/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.virtual.phases.ea;

import java.util.ArrayList;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.EscapeObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;

public final class GraphEffectList extends EffectList {

    public GraphEffectList(DebugContext debug) {
        super(debug);
    }

    /**
     * Determines how many objects are virtualized (positive) or materialized (negative) by this
     * effect.
     */
    private int virtualizationDelta;

    /**
     * Determines how many nodes are virtualized (positive) or materialized (negative) by this
     * effect. Note that this is different than {@link #virtualizationDelta} as it considers actual
     * nodes in the IR. This can be relevant if the only effect of a PEA is to fold two commit
     * allocation nodes into one. While this may look like no progress the actual allocation path
     * can common out barriers, tlab reads etc.
     */
    private int allocationNodeDelta;

    @Override
    public void clear() {
        super.clear();
        virtualizationDelta = 0;
        allocationNodeDelta = 0;
    }

    /**
     * Adds the given fixed node to the graph's control flow, before position (so that the original
     * predecessor of position will then be node's predecessor). The node must not yet be part of
     * the graph.
     *
     * @param node The fixed node to be added to the graph.
     * @param position The fixed node before which the node should be added.
     */
    public void addFixedNodeBefore(FixedWithNextNode node, FixedNode position) {
        add(new SimpleEffect("add fixed node") {
            @Override
            public void apply(StructuredGraph graph) {
                assert !node.isAlive() && !node.isDeleted() && position.isAlive() : Assertions.errorMessageContext("node", node, "position", position);
                graph.addBeforeFixed(position, graph.add(node));
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node", "position"}, new Object[]{node, position});
            }
        });
    }

    /**
     * Add {@code node} to the graph if it is not yet part of the graph. {@code node} must be a
     * {@link FixedNode}. If it is not yet part of the graph, it is added before {@code position}.
     *
     * @param node The fixed node to be added to the graph if not yet present.
     * @param position The fixed node before which the node should be added if not yet present.
     */
    void ensureAdded(ValueNode node, FixedNode position) {
        assert !(node instanceof FixedWithNextNode) || position != null;
        add(new SimpleEffect("ensure added") {
            @Override
            public void apply(StructuredGraph graph) {
                assert position == null || position.isAlive();
                if (!node.isAlive()) {
                    graph.addWithoutUniqueWithInputs(node);
                }
                if (node instanceof FixedWithNextNode fixedWithNextNode && fixedWithNextNode.next() == null) {
                    graph.addBeforeFixed(position, fixedWithNextNode);
                }
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node", "position"}, new Object[]{node, position});
            }
        });
    }

    public void addVirtualizationDelta(int delta) {
        virtualizationDelta += delta;
    }

    public int getVirtualizationDelta() {
        return virtualizationDelta;
    }

    public void addAllocationDelta(int delta) {
        allocationNodeDelta += delta;
    }

    public int getAllocationDelta() {
        return allocationNodeDelta;
    }

    /**
     * Add the given floating node to the graph. The node must not yet be part of the graph.
     *
     * @param node The floating node to be added.
     */
    public void addFloatingNode(ValueNode node, @SuppressWarnings("unused") String cause) {
        add(new SimpleEffect("add floating node") {
            @Override
            public void apply(StructuredGraph graph) {
                GraalError.guarantee(node.isUnregistered(), cause);
                assert !node.isAlive() && !node.isDeleted() : node;
                graph.addWithoutUniqueWithInputs(node);
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node", "cause"}, new Object[]{node, cause});
            }
        });
    }

    /**
     * Add {@code node} to the graph if it is not yet part of the graph. {@code node} must be a
     * {@link FloatingNode}.
     *
     * @param node The floating node to be added to the graph if not yet present.
     */
    void ensureFloatingAdded(ValueNode node) {
        ensureAdded(node, null);
    }

    /**
     * Sets the phi node's input at the given index to the given value, adding new phi inputs as
     * needed.
     *
     * @param node The phi node whose input should be changed.
     * @param index The index of the phi input to be changed.
     * @param value The new value for the phi input.
     */
    public void initializePhiInput(PhiNode node, int index, ValueNode value) {
        add(new Effect("set phi input") {
            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && index >= 0 : node;
                node.initializeValueAt(index, graph.addOrUniqueWithInputs(value));
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node", "index", "value"}, new Object[]{node, index, value});
            }
        });
    }

    /**
     * Adds a virtual object's state to the given frame state. If the given frame state contains the
     * virtual object then old states for this object will be removed.
     *
     * @param node The frame state to which the state should be added.
     * @param state The virtual object state to add.
     */
    public void addVirtualMapping(FrameState node, EscapeObjectState state) {
        add(new Effect("add virtual mapping") {
            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                if (node.isAlive()) {
                    assert !state.isDeleted();
                    FrameState stateAfter = node;
                    for (int i = 0; i < stateAfter.virtualObjectMappingCount(); i++) {
                        if (stateAfter.virtualObjectMappingAt(i).object() == state.object()) {
                            stateAfter.virtualObjectMappings().remove(i);
                        }
                    }
                    stateAfter.addVirtualObjectMapping(graph.addOrUniqueWithInputs(state));
                }
            }

            @Override
            public boolean isVisible() {
                return false;
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node", "state"}, new Object[]{node, state});
            }
        });
    }

    /**
     * Update a virtual object mapping for the given {@link VirtualObjectState} with a new value.
     *
     * @param state The virtual state previously constructed.
     * @param field The field to update its value.
     * @param newValue The new value of the field.
     */
    public void updateVirtualMapping(VirtualObjectState state, int field, ValueNode newValue) {
        add(new Effect("add virtual mapping") {
            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                if (state.isAlive()) {
                    state.values().set(field, graph.addOrUniqueWithInputs(newValue));
                }
            }

            @Override
            public boolean isVisible() {
                return false;
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"state", "field", "newValue"}, new Object[]{state, field, newValue});
            }
        });
    }

    /**
     * Removes the given fixed node from the control flow and deletes it.
     *
     * @param node The fixed node that should be deleted.
     */
    public void deleteNode(Node node) {
        add(new Effect("delete fixed node") {
            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                if (node instanceof FixedWithNextNode) {
                    GraphUtil.unlinkFixedNode((FixedWithNextNode) node);
                } else if (node instanceof WithExceptionNode && node.isAlive()) {
                    WithExceptionNode withExceptionNode = (WithExceptionNode) node;
                    AbstractBeginNode next = withExceptionNode.next();
                    GraphUtil.unlinkAndKillExceptionEdge(withExceptionNode);
                    if (next.hasNoUsages() && MemoryKill.isMemoryKill(next)) {
                        // This is a killing begin which is no longer needed.
                        graph.replaceFixedWithFixed(next, graph.add(new BeginNode()));
                    }
                    obsoleteNodes.add(withExceptionNode);
                }
                obsoleteNodes.add(node);
            }

            @Override
            public boolean isCfgKill() {
                return node instanceof WithExceptionNode;
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node"}, new Object[]{node});
            }
        });
    }

    public void killIfBranch(IfNode ifNode, boolean constantCondition) {
        add(new Effect("kill if branch") {
            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                if (ifNode.isAlive()) {
                    graph.removeSplitPropagate(ifNode, ifNode.getSuccessor(constantCondition));
                }
            }

            @Override
            public boolean isCfgKill() {
                return true;
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"ifNode", "constantCondition"}, new Object[]{ifNode,
                                constantCondition});
            }
        });
    }

    public void replaceWithSink(FixedWithNextNode node, ControlSinkNode sink) {
        add(new Effect("replace with sink") {
            @SuppressWarnings("try")
            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                try (DebugCloseable position = graph.withNodeSourcePosition(node)) {
                    graph.addWithoutUnique(sink);
                    node.replaceAtPredecessor(sink);
                    GraphUtil.killCFG(node);
                }
            }

            @Override
            public boolean isCfgKill() {
                return true;
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node", "sink"}, new Object[]{node, sink});
            }
        });
    }

    /**
     * Replaces the given node at its usages without deleting it. If the current node is a fixed
     * node it will be disconnected from the control flow, so that it will be deleted by a
     * subsequent {@link DeadCodeEliminationPhase}
     *
     * @param node The node to be replaced.
     * @param replacement The node that should replace the original value. If the replacement is a
     *            non-connected {@link FixedWithNextNode} it will be added to the control flow.
     * @param insertBefore
     *
     */
    @SuppressWarnings("try")
    public void replaceAtUsages(ValueNode node, ValueNode replacement, FixedNode insertBefore) {
        assert node != null && replacement != null : node + " " + replacement;
        assert !node.hasUsages() || node.stamp(NodeView.DEFAULT).isCompatible(replacement.stamp(NodeView.DEFAULT)) : "Replacement node stamp not compatible " + node.stamp(NodeView.DEFAULT) + " vs " +
                        replacement.stamp(NodeView.DEFAULT);
        add(new Effect("replace at usages") {
            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                try (DebugCloseable position = graph.withNodeSourcePosition(node)) {
                    assert node.isAlive();
                    ValueNode replacementNode = graph.addOrUniqueWithInputs(replacement);
                    assert replacementNode.isAlive();
                    assert insertBefore != null;
                    if (replacementNode instanceof FixedWithNextNode && ((FixedWithNextNode) replacementNode).next() == null) {
                        graph.addBeforeFixed(insertBefore, (FixedWithNextNode) replacementNode);
                    }
                    /*
                     * Keep the (better) stamp information when replacing a node with another one if
                     * the replacement has a less precise stamp than the original node. This can
                     * happen for example in the context of read nodes and unguarded pi nodes where
                     * the pi will be used to improve the stamp information of the read. Such a read
                     * might later be replaced with a read with a less precise stamp.
                     */
                    if (node.hasUsages() && (replacementNode.stamp(NodeView.DEFAULT).tryImproveWith(node.stamp(NodeView.DEFAULT)) != null)) {
                        replacementNode = graph.unique(new PiNode(replacementNode, node.stamp(NodeView.DEFAULT)));
                    }
                    if (node != replacementNode) {
                        node.replaceAtUsages(replacementNode);
                        if (node instanceof WithExceptionNode) {
                            GraphUtil.unlinkAndKillExceptionEdge((WithExceptionNode) node);
                        } else if (node instanceof FixedWithNextNode) {
                            GraphUtil.unlinkFixedNode((FixedWithNextNode) node);
                        }
                        obsoleteNodes.add(node);
                    }
                }
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node", "replacement", "insertBefore"}, new Object[]{node, replacement, insertBefore});
            }
        });
    }

    /**
     * Replaces the first occurrence of oldInput in node with newInput.
     *
     * @param node The node whose input should be changed.
     * @param oldInput The value to look for.
     * @param newInput The value to replace with.
     */
    public void replaceFirstInput(Node node, Node oldInput, Node newInput) {
        assert node.isAlive() && oldInput.isAlive() && !newInput.isDeleted() : Assertions.errorMessageContext("node", node, "newInput", newInput);
        add(new Effect("replace first input") {
            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                if (node.isAlive()) {
                    assert oldInput.isAlive() && newInput.isAlive() : "Both must be alive " + Assertions.errorMessageContext("oldInput", oldInput, "newInput", newInput);
                    node.replaceFirstInput(oldInput, newInput);
                }
            }

            @Override
            public boolean isVisible() {
                return !(node instanceof FrameState);
            }

            @Override
            void format(StringBuilder str) {
                format(str, new String[]{"node", "oldInput", "newInput"}, new Object[]{node, oldInput, newInput});
            }
        });
    }
}

/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.virtual.phases.ea;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.virtual.nodes.*;

public class GraphEffectList extends EffectList {

    public void addCounterBefore(final String name, final int increment, final boolean addContext, final FixedNode position) {
        if (!DynamicCounterNode.enabled) {
            return;
        }
        add(new Effect() {

            @Override
            public String name() {
                return "addCounterBefore";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert position.isAlive();
                DynamicCounterNode node = graph.add(new DynamicCounterNode(name, increment, addContext));
                graph.addBeforeFixed(position, node);
                node.setProbability(position.probability());
            }
        });
    }

    public void addSurvivingCounterBefore(final String name, final int increment, final boolean addContext, final ValueNode checkedValue, final FixedNode position) {
        if (!DynamicCounterNode.enabled) {
            return;
        }
        add(new Effect() {

            @Override
            public String name() {
                return "addSurvivingCounterBefore";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert position.isAlive();
                DynamicCounterNode node = graph.add(new SurvivingCounterNode(name, increment, addContext, checkedValue));
                graph.addBeforeFixed(position, node);
                node.setProbability(position.probability());
            }
        });
    }

    /**
     * Adds the given fixed node to the graph's control flow, before position (so that the original
     * predecessor of position will then be node's predecessor).
     * 
     * @param node The fixed node to be added to the graph.
     * @param position The fixed node before which the node should be added.
     */
    public void addFixedNodeBefore(final FixedWithNextNode node, final FixedNode position) {
        add(new Effect() {

            @Override
            public String name() {
                return "addFixedNodeBefore";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert !node.isAlive() && !node.isDeleted() && position.isAlive();
                graph.addBeforeFixed(position, graph.add(node));
                node.setProbability(position.probability());
            }
        });
    }

    /**
     * Add the given floating node to the graph.
     * 
     * @param node The floating node to be added.
     */
    public void addFloatingNode(final ValueNode node, final String cause) {
        add(new Effect() {

            @Override
            public String name() {
                return "addFloatingNode " + cause;
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert !node.isAlive() && !node.isDeleted() : node + " " + cause;
                graph.add(node);
            }
        });
    }

    /**
     * Add the materialization node to the graph's control flow at the given position, and then sets
     * its values.
     * 
     * @param node The materialization node that should be added.
     * @param position The fixed node before which the materialization node should be added.
     * @param values The values for the materialization node's entries.
     */
    public void addMaterialization(final MaterializeObjectNode node, final FixedNode position, final ValueNode[] values) {
        add(new Effect() {

            @Override
            public String name() {
                return "addMaterialization";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert !node.isAlive() && !node.isDeleted() && position.isAlive();
                graph.addBeforeFixed(position, graph.add(node));
                node.setProbability(position.probability());
                for (int i = 0; i < values.length; i++) {
                    node.getValues().set(i, values[i]);
                }
            }
        });
    }

    /**
     * Adds an value to the given phi node.
     * 
     * @param node The phi node to which the value should be added.
     * @param value The value that will be added to the phi node.
     */
    public void addPhiInput(final PhiNode node, final ValueNode value) {
        add(new Effect() {

            @Override
            public String name() {
                return "addPhiInput";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && value.isAlive() : node + " " + value;
                node.addInput(value);
            }
        });
    }

    /**
     * Sets the phi node's input at the given index to the given value.
     * 
     * @param node The phi node whose input should be changed.
     * @param index The index of the phi input to be changed.
     * @param value The new value for the phi input.
     */
    public void setPhiInput(final PhiNode node, final int index, final ValueNode value) {
        add(new Effect() {

            @Override
            public String name() {
                return "setPhiInput";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && value.isAlive() && index >= 0;
                node.setValueAt(index, value);
            }
        });
    }

    /**
     * Adds a virtual object's state to the given frame state. If the given reusedVirtualObjects set
     * contains the virtual object then old states for this object will be removed.
     * 
     * @param node The frame state to which the state should be added.
     * @param state The virtual object state to add.
     */
    public void addVirtualMapping(final FrameState node, final EscapeObjectState state) {
        add(new Effect() {

            @Override
            public String name() {
                return "addVirtualMapping";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && !state.isAlive() && !state.isDeleted();
                FrameState stateAfter = node;
                for (int i = 0; i < stateAfter.virtualObjectMappingCount(); i++) {
                    if (stateAfter.virtualObjectMappingAt(i).object() == state.object()) {
                        stateAfter.virtualObjectMappings().remove(i);
                    }
                }
                stateAfter.addVirtualObjectMapping(graph.add(state));
            }

            @Override
            public boolean isVisible() {
                return false;
            }
        });
    }

    /**
     * Removes the given fixed node from the control flow and deletes it.
     * 
     * @param node The fixed node that should be deleted.
     */
    public void deleteFixedNode(final FixedWithNextNode node) {
        add(new Effect() {

            @Override
            public String name() {
                return "deleteFixedNode";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive();
                FixedNode next = node.next();
                node.setNext(null);
                node.replaceAtPredecessor(next);
                obsoleteNodes.add(node);
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
     * 
     */
    public void replaceAtUsages(final ValueNode node, final ValueNode replacement) {
        add(new Effect() {

            @Override
            public String name() {
                return "replaceAtUsages";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && replacement.isAlive();
                if (replacement instanceof FixedWithNextNode && ((FixedWithNextNode) replacement).next() == null) {
                    assert node instanceof FixedNode;
                    graph.addBeforeFixed((FixedNode) node, (FixedWithNextNode) replacement);
                }
                node.replaceAtUsages(replacement);
                if (node instanceof FixedWithNextNode) {
                    FixedNode next = ((FixedWithNextNode) node).next();
                    ((FixedWithNextNode) node).setNext(null);
                    node.replaceAtPredecessor(next);
                    obsoleteNodes.add(node);
                }
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
    public void replaceFirstInput(final Node node, final Node oldInput, final Node newInput) {
        add(new Effect() {

            @Override
            public String name() {
                return "replaceFirstInput";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && oldInput.isAlive() && newInput.isAlive();
                node.replaceFirstInput(oldInput, newInput);
            }

            @Override
            public boolean isVisible() {
                return !(node instanceof FrameState);
            }
        });
    }

    /**
     * Performs a custom action.
     * 
     * @param action The action that should be performed when the effects are applied.
     */
    public void customAction(final Runnable action) {
        add(new Effect() {

            @Override
            public String name() {
                return "customAction";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                action.run();
            }
        });
    }
}

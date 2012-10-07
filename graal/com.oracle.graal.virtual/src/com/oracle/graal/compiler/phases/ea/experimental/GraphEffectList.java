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
package com.oracle.graal.compiler.phases.ea.experimental;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.virtual.*;

public class GraphEffectList extends EffectList {

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

    public void addFloatingNode(final FloatingNode node) {
        add(new Effect() {

            @Override
            public String name() {
                return "addFloatingNode";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert !node.isAlive() && !node.isDeleted();
                graph.add(node);
            }
        });
    }

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
                    node.values().set(i, values[i]);
                }
            }
        });
    }

    public void addPhiInput(final PhiNode node, final ValueNode value) {
        add(new Effect() {

            @Override
            public String name() {
                return "addPhiInput";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && value.isAlive();
                node.addInput(value);
            }
        });
    }

    public void addVirtualMapping(final FrameState node, final EscapeObjectState state, final HashSet<VirtualObjectNode> reusedVirtualObjects) {
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
                        if (reusedVirtualObjects.contains(state.object())) {
                            stateAfter.virtualObjectMappings().remove(i);
                        } else {
                            throw new GraalInternalError("unexpected duplicate virtual state at: %s for %s", stateAfter, state.object());
                        }
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

    public void eliminateMonitor(final AccessMonitorNode node) {
        add(new Effect() {

            @Override
            public String name() {
                return "eliminateMonitor";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && node.object().isAlive() && (node.object() instanceof VirtualObjectNode);
                node.eliminate();
            }
        });
    }

    public void replaceAtUsages(final ValueNode node, final ValueNode replacement) {
        add(new Effect() {

            @Override
            public String name() {
                return "replaceAtUsages";
            }

            @Override
            public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
                assert node.isAlive() && replacement.isAlive();
                node.replaceAtUsages(replacement);
            }
        });
    }

    public void replaceFirstInput(final Node node, final ValueNode oldInput, final ValueNode newInput) {
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

    public void setPhiInput(final PhiNode node, final ValueNode value, final int index) {
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
}

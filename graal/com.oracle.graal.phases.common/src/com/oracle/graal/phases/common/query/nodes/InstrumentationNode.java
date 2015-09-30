/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.query.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.EndNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.MergeNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.StartNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.virtual.EscapeObjectState;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;

@NodeInfo
public class InstrumentationNode extends FixedWithNextNode implements Virtualizable {

    public static final NodeClass<InstrumentationNode> TYPE = NodeClass.create(InstrumentationNode.class);

    @OptionalInput(value = InputType.Association) protected ValueNode target;
    @OptionalInput protected NodeInputList<ValueNode> weakDependencies;

    protected StructuredGraph icg;
    protected final int offset;
    protected final int type;

    public InstrumentationNode(ValueNode target, int offset, int type) {
        super(TYPE, StampFactory.forVoid());

        this.target = target;
        this.icg = new StructuredGraph(AllowAssumptions.YES);
        this.offset = offset;
        this.type = type;

        this.weakDependencies = new NodeInputList<>(this);
    }

    public boolean addInput(Node node) {
        return weakDependencies.add(node);
    }

    public ValueNode target() {
        return target;
    }

    public StructuredGraph icg() {
        return icg;
    }

    public int offset() {
        return offset;
    }

    public int type() {
        return type;
    }

    public NodeInputList<ValueNode> getWeakDependencies() {
        return weakDependencies;
    }

    public void virtualize(VirtualizerTool tool) {
        // InstrumentationNode allows non-materialized inputs. During the inlining of the
        // InstrumentationNode, non-materialized inputs will be replaced by null.
        if (target != null) {
            ValueNode alias = tool.getAlias(target);
            if (alias instanceof VirtualObjectNode) {
                tool.replaceFirstInput(target, alias);
            }
        }
        for (ValueNode input : weakDependencies) {
            ValueNode alias = tool.getAlias(input);
            if (alias instanceof VirtualObjectNode) {
                tool.replaceFirstInput(input, alias);
            }
        }
    }

    public void inlineAt(FixedNode position) {
        ArrayList<Node> nodes = new ArrayList<>(icg.getNodes().count());
        final StartNode entryPointNode = icg.start();
        FixedNode firstCFGNode = entryPointNode.next();
        ArrayList<ReturnNode> returnNodes = new ArrayList<>(4);

        for (Node icgnode : icg.getNodes()) {
            if (icgnode == entryPointNode || icgnode == entryPointNode.stateAfter() || icgnode instanceof ParameterNode) {
                // Do nothing.
            } else {
                nodes.add(icgnode);
                if (icgnode instanceof ReturnNode) {
                    returnNodes.add((ReturnNode) icgnode);
                }
            }
        }

        final AbstractBeginNode prevBegin = AbstractBeginNode.prevBegin(position);
        DuplicationReplacement localReplacement = new DuplicationReplacement() {

            public Node replacement(Node replacement) {
                if (replacement instanceof ParameterNode) {
                    ValueNode value = getWeakDependencies().get(((ParameterNode) replacement).index());
                    if (value == null || value.isDeleted() || value instanceof VirtualObjectNode || value.stamp().getStackKind() != JavaKind.Object) {
                        return graph().unique(new ConstantNode(JavaConstant.NULL_POINTER, ((ParameterNode) replacement).stamp()));
                    } else {
                        return value;
                    }
                } else if (replacement == entryPointNode) {
                    return prevBegin;
                }
                return replacement;
            }

        };

        Map<Node, Node> duplicates = graph().addDuplicates(nodes, icg, icg.getNodeCount(), localReplacement);
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        position.replaceAtPredecessor(firstCFGNodeDuplicate);

        if (!returnNodes.isEmpty()) {
            if (returnNodes.size() == 1) {
                ReturnNode returnNode = (ReturnNode) duplicates.get(returnNodes.get(0));
                returnNode.replaceAndDelete(position);
            } else {
                ArrayList<ReturnNode> returnDuplicates = new ArrayList<>(returnNodes.size());
                for (ReturnNode returnNode : returnNodes) {
                    returnDuplicates.add((ReturnNode) duplicates.get(returnNode));
                }
                AbstractMergeNode merge = graph().add(new MergeNode());

                for (ReturnNode returnNode : returnDuplicates) {
                    EndNode endNode = graph().add(new EndNode());
                    merge.addForwardEnd(endNode);
                    returnNode.replaceAndDelete(endNode);
                }

                merge.setNext(position);
            }
        }

        // since we may relocate InstrumentationNodes, the FrameState can be invalid
        for (Node replacee : duplicates.values()) {
            if (replacee instanceof FrameState) {
                FrameState oldState = (FrameState) replacee;
                FrameState newState = new FrameState(null, oldState.method(), oldState.bci, 0, 0, 0, oldState.rethrowException(), oldState.duringCall(), null,
                                Collections.<EscapeObjectState> emptyList());
                graph().addWithoutUnique(newState);
                oldState.replaceAtUsages(newState);
            }
        }
    }

}

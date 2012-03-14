/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.types;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.graal.compiler.graph.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.schedule.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.NodeClass.Position;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public class PropagateTypesPhase extends Phase {

    private final CiTarget target;
    private final RiRuntime runtime;
    private final CiAssumptions assumptions;

    private NodeWorkList changedNodes;

    public PropagateTypesPhase(CiTarget target, RiRuntime runtime, CiAssumptions assumptions) {
        this.target = target;
        this.runtime = runtime;
        this.assumptions = assumptions;
    }

    @Override
    protected void run(StructuredGraph graph) {

        new DeadCodeEliminationPhase().apply(graph);

        changedNodes = graph.createNodeWorkList(false, 10);

        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph);

        schedule.scheduleGraph();
        Debug.dump(graph, "scheduled");

        new PropagateTypes(graph.start()).apply();
        Debug.dump(graph, "after propagation");

        new UnscheduleNodes(graph.start()).apply();

        CanonicalizerPhase.canonicalize(graph, changedNodes, runtime, target, assumptions);
    }

    private class PiNodeList {

        public final PiNodeList last;
        public final ValueNode replacement;
        public final int depth;

        public PiNodeList(ValueNode replacement, PiNodeList last) {
            this.last = last;
            this.replacement = replacement;
            this.depth = last != null ? last.depth + 1 : 1;
        }

        public PiNodeList merge(PiNodeList other) {
            PiNodeList thisList = this;
            PiNodeList otherList = other;
            while (thisList.depth > otherList.depth) {
                thisList = thisList.last;
            }
            while (otherList.depth > thisList.depth) {
                otherList = otherList.last;
            }
            while (thisList != otherList) {
                thisList = thisList.last;
                otherList = otherList.last;
            }
            return thisList;
        }
    }

    private class TypeInfo implements MergeableState<TypeInfo> {

        private HashMap<ValueNode, PiNodeList> piNodes = new HashMap<>();

        public TypeInfo(HashMap<ValueNode, PiNodeList> piNodes) {
            this.piNodes.putAll(piNodes);
        }

        @Override
        public TypeInfo clone() {
            return new TypeInfo(piNodes);
        }

        @Override
        public boolean merge(MergeNode merge, List<TypeInfo> withStates) {
            if (merge.forwardEndCount() > 1) {
                HashMap<ValueNode, PiNodeList> newPiNodes = new HashMap<>();
                for (Entry<ValueNode, PiNodeList> entry : piNodes.entrySet()) {
                    PiNodeList list = entry.getValue();
                    for (TypeInfo info : withStates) {
                        PiNodeList other = info.piNodes.get(entry.getKey());
                        if (other == null) {
                            list = null;
                        } else {
                            list = list.merge(other);
                        }
                        if (list == null) {
                            break;
                        }
                    }
                    if (list != null) {
                        newPiNodes.put(entry.getKey(), list);
                    }
                }
                piNodes = newPiNodes;
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loop) {
        }

        @Override
        public void loopEnds(LoopBeginNode loop, List<TypeInfo> loopEndStates) {
        }

        @Override
        public void afterSplit(FixedNode node) {
            assert node.predecessor() != null;
            assert node.predecessor() instanceof ControlSplitNode;
//            TTY.println("after split: %s", node);
            if (node.predecessor() instanceof IfNode) {
                IfNode ifNode = (IfNode) node.predecessor();
                if (ifNode.compare() instanceof InstanceOfNode) {
                    InstanceOfNode instanceOf = (InstanceOfNode) ifNode.compare();
                    assert node == ifNode.trueSuccessor() || node == ifNode.falseSuccessor();
                    if ((node == ifNode.trueSuccessor() && !instanceOf.negated()) || (node == ifNode.falseSuccessor() && instanceOf.negated())) {
                        ValueNode value = instanceOf.object();
                        if (value.declaredType() != instanceOf.targetClass() || !value.stamp().nonNull()) {
                            PiNode piNode = node.graph().unique(new PiNode(value, (BeginNode) node, StampFactory.declaredNonNull(instanceOf.targetClass())));
                            PiNodeList list = piNodes.get(value);
                            piNodes.put(value, new PiNodeList(piNode, list));
                        }
                    }
                } else if (ifNode.compare() instanceof CompareNode) {
                    CompareNode compare = (CompareNode) ifNode.compare();
                    assert node == ifNode.trueSuccessor() || node == ifNode.falseSuccessor();
                    if ((node == ifNode.trueSuccessor() && compare.condition() == Condition.EQ) || (node == ifNode.falseSuccessor() && compare.condition() == Condition.NE)) {
                        if (compare.y().isConstant()) {
                            ValueNode value = compare.x();
                            PiNodeList list = piNodes.get(value);
                            piNodes.put(value, new PiNodeList(compare.y(), list));
                        }
                    } else if ((node == ifNode.trueSuccessor() && compare.condition() == Condition.NE) || (node == ifNode.falseSuccessor() && compare.condition() == Condition.EQ)) {
                        if (!compare.x().isConstant() && compare.y().isNullConstant() && !compare.x().stamp().nonNull()) {
                            ValueNode value = compare.x();
                            PiNode piNode;
                            if (value.exactType() != null) {
                                piNode = node.graph().unique(new PiNode(value, (BeginNode) node, StampFactory.declaredNonNull(value.exactType())));
                            } else if (value.declaredType() != null) {
                                piNode = node.graph().unique(new PiNode(value, (BeginNode) node, StampFactory.declaredNonNull(value.declaredType())));
                            } else {
                                piNode = node.graph().unique(new PiNode(value, (BeginNode) node, StampFactory.objectNonNull()));
                            }
                            PiNodeList list = piNodes.get(value);
                            piNodes.put(value, new PiNodeList(piNode, list));
                        }
                    }
                }
            }
        }
    }

    private class Tool implements CanonicalizerTool {
        @Override
        public CiTarget target() {
            return target;
        }

        @Override
        public CiAssumptions assumptions() {
            return assumptions;
        }

        @Override
        public RiRuntime runtime() {
            return runtime;
        }
    }

    private final Tool tool = new Tool();

    private class PropagateTypes extends ScheduledNodeIterator<TypeInfo> {

        public PropagateTypes(FixedNode start) {
            super(start, new TypeInfo(new HashMap<ValueNode, PiNodeList>()));
        }

        @Override
        protected void node(ScheduledNode node) {
            if (node instanceof Canonicalizable || node instanceof Invoke) {
                NodeClassIterator iter = node.inputs().iterator();
                ArrayList<Node> changedInputs = new ArrayList<>();
                while (iter.hasNext()) {
                    Position pos = iter.nextPosition();
                    Node value = pos.get(node);
                    PiNodeList list = state.piNodes.get(value);
                    if (list != null) {
                        changedInputs.add(list.replacement instanceof PiNode ? value : null);
                        pos.set(node, list.replacement);
                    } else {
                        changedInputs.add(null);
                    }
                }

                ValueNode canonical = null;
                if (node instanceof Canonicalizable) {
                    canonical = ((Canonicalizable) node).canonical(tool);
                }

                if (canonical == node) {
                    iter = node.inputs().iterator();
                    int i = 0;
                    while (iter.hasNext()) {
                        Position pos = iter.nextPosition();
                        if (changedInputs.get(i) != null) {
                            pos.set(node, changedInputs.get(i));
                        }
                        i++;
                    }
                } else {
                    changedNodes.add(node);
                }
            }
        }
    }
}

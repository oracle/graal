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
package com.oracle.graal.phases.common;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;

public class ConditionalEliminationPhase extends Phase {

    private static final DebugMetric metricInstanceOfRegistered = Debug.metric("InstanceOfRegistered");
    private static final DebugMetric metricNullCheckRegistered = Debug.metric("NullCheckRegistered");
    private static final DebugMetric metricCheckCastRemoved = Debug.metric("CheckCastRemoved");
    private static final DebugMetric metricInstanceOfRemoved = Debug.metric("InstanceOfRemoved");
    private static final DebugMetric metricNullCheckRemoved = Debug.metric("NullCheckRemoved");
    private static final DebugMetric metricNullCheckGuardRemoved = Debug.metric("NullCheckGuardRemoved");
    private static final DebugMetric metricGuardsReplaced = Debug.metric("GuardsReplaced");

    private StructuredGraph graph;

    @Override
    protected void run(StructuredGraph inputGraph) {
        graph = inputGraph;
        new ConditionalElimination(graph.start(), new State()).apply();
    }

    public static class State implements MergeableState<State> {

        private IdentityHashMap<ValueNode, ResolvedJavaType> knownTypes;
        private HashSet<ValueNode> knownNotNull;
        private HashSet<ValueNode> knownNull;
        private IdentityHashMap<BooleanNode, ValueNode> trueConditions;
        private IdentityHashMap<BooleanNode, ValueNode> falseConditions;

        public State() {
            this.knownTypes = new IdentityHashMap<>();
            this.knownNotNull = new HashSet<>();
            this.knownNull = new HashSet<>();
            this.trueConditions = new IdentityHashMap<>();
            this.falseConditions = new IdentityHashMap<>();
        }

        public State(State other) {
            this.knownTypes = new IdentityHashMap<>(other.knownTypes);
            this.knownNotNull = new HashSet<>(other.knownNotNull);
            this.knownNull = new HashSet<>(other.knownNull);
            this.trueConditions = new IdentityHashMap<>(other.trueConditions);
            this.falseConditions = new IdentityHashMap<>(other.falseConditions);
        }

        @Override
        public boolean merge(MergeNode merge, List<State> withStates) {
            IdentityHashMap<ValueNode, ResolvedJavaType> newKnownTypes = new IdentityHashMap<>();
            HashSet<ValueNode> newKnownNotNull = new HashSet<>();
            HashSet<ValueNode> newKnownNull = new HashSet<>();
            IdentityHashMap<BooleanNode, ValueNode> newTrueConditions = new IdentityHashMap<>();
            IdentityHashMap<BooleanNode, ValueNode> newFalseConditions = new IdentityHashMap<>();

            for (Map.Entry<ValueNode, ResolvedJavaType> entry : knownTypes.entrySet()) {
                ValueNode node = entry.getKey();
                ResolvedJavaType type = entry.getValue();

                for (State other : withStates) {
                    ResolvedJavaType otherType = other.getNodeType(node);
                    type = widen(type, otherType);
                    if (type == null) {
                        break;
                    }
                }
                if (type == null && type != node.objectStamp().type()) {
                    newKnownTypes.put(node, type);
                }
            }
            for (ValueNode node : knownNotNull) {
                boolean notNull = true;
                for (State other : withStates) {
                    if (!other.knownNotNull.contains(node)) {
                        notNull = false;
                        break;
                    }
                }
                if (notNull) {
                    newKnownNotNull.add(node);
                }
            }
            for (ValueNode node : knownNull) {
                boolean isNull = true;
                for (State other : withStates) {
                    if (!other.knownNull.contains(node)) {
                        isNull = false;
                        break;
                    }
                }
                if (isNull) {
                    newKnownNull.add(node);
                }
            }
            for (Map.Entry<BooleanNode, ValueNode> entry : trueConditions.entrySet()) {
                BooleanNode check = entry.getKey();
                ValueNode guard = entry.getValue();

                for (State other : withStates) {
                    ValueNode otherGuard = other.trueConditions.get(check);
                    if (otherGuard == null) {
                        guard = null;
                        break;
                    }
                    if (otherGuard != guard) {
                        guard = merge;
                    }
                }
                if (guard != null) {
                    newTrueConditions.put(check, guard);
                }
            }
            for (Map.Entry<BooleanNode, ValueNode> entry : falseConditions.entrySet()) {
                BooleanNode check = entry.getKey();
                ValueNode guard = entry.getValue();

                for (State other : withStates) {
                    ValueNode otherGuard = other.falseConditions.get(check);
                    if (otherGuard == null) {
                        guard = null;
                        break;
                    }
                    if (otherGuard != guard) {
                        guard = merge;
                    }
                }
                if (guard != null) {
                    newFalseConditions.put(check, guard);
                }
            }

            // this piece of code handles phis (merges the types and knownNull/knownNotNull of the values)
            if (!(merge instanceof LoopBeginNode)) {
                for (PhiNode phi : merge.phis()) {
                    if (phi.type() == PhiType.Value && phi.kind() == Kind.Object) {
                        ValueNode firstValue = phi.valueAt(0);
                        ResolvedJavaType type = getNodeType(firstValue);
                        boolean notNull = knownNotNull.contains(firstValue);
                        boolean isNull = knownNull.contains(firstValue);

                        for (int i = 0; i < withStates.size(); i++) {
                            State otherState = withStates.get(i);
                            ValueNode value = phi.valueAt(i + 1);
                            ResolvedJavaType otherType = otherState.getNodeType(value);
                            type = widen(type, otherType);
                            notNull &= otherState.knownNotNull.contains(value);
                            isNull &= otherState.knownNull.contains(value);
                        }
                        if (type != null) {
                            newKnownTypes.put(phi, type);
                        }
                        if (notNull) {
                            newKnownNotNull.add(phi);
                        }
                        if (isNull) {
                            newKnownNull.add(phi);
                        }
                    }
                }
            }

            this.knownTypes = newKnownTypes;
            this.knownNotNull = newKnownNotNull;
            this.knownNull = newKnownNull;
            this.trueConditions = newTrueConditions;
            this.falseConditions = newFalseConditions;
            return true;
        }

        public ResolvedJavaType getNodeType(ValueNode node) {
            ResolvedJavaType result = knownTypes.get(node);
            return result == null ? node.objectStamp().type() : result;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<State> loopEndStates) {
        }

        @Override
        public void afterSplit(FixedNode node) {
        }

        @Override
        public State clone() {
            return new State(this);
        }
    }

    public static ResolvedJavaType widen(ResolvedJavaType a, ResolvedJavaType b) {
        if (a == null || b == null) {
            return null;
        } else if (a == b) {
            return a;
        } else {
            return a.findLeastCommonAncestor(b);
        }
    }

    public static ResolvedJavaType tighten(ResolvedJavaType a, ResolvedJavaType b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else if (a == b) {
            return a;
        } else if (a.isSubtypeOf(b)) {
            return a;
        } else if (b.isSubtypeOf(a)) {
            return b;
        } else {
            return a;
        }
    }

    public class ConditionalElimination extends PostOrderNodeIterator<State> {
        private BeginNode lastBegin = null;

        public ConditionalElimination(FixedNode start, State initialState) {
            super(start, initialState);
        }

        @Override
        protected void node(FixedNode node) {
            if (node instanceof BeginNode) {
                BeginNode begin = (BeginNode) node;
                lastBegin = begin;
                Node pred = node.predecessor();
                if (pred != null && pred instanceof IfNode) {
                    IfNode ifNode = (IfNode) pred;
                    if (!(ifNode.condition() instanceof ConstantNode)) {
                        boolean isTrue = (node == ifNode.trueSuccessor());
                        if (isTrue) {
                            state.trueConditions.put(ifNode.condition(), begin);
                        } else {
                            state.falseConditions.put(ifNode.condition(), begin);
                        }
                    }
                    if (ifNode.condition() instanceof InstanceOfNode) {
                        InstanceOfNode instanceOf = (InstanceOfNode) ifNode.condition();
                        if ((node == ifNode.trueSuccessor())) {
                            ValueNode object = instanceOf.object();
                            state.knownNotNull.add(object);
                            state.knownTypes.put(object, tighten(instanceOf.type(), state.getNodeType(object)));
                            metricInstanceOfRegistered.increment();
                        }
                    } else if (ifNode.condition() instanceof IsNullNode) {
                        IsNullNode nullCheck = (IsNullNode) ifNode.condition();
                        boolean isNull = (node == ifNode.trueSuccessor());
                        if (isNull) {
                            state.knownNull.add(nullCheck.object());
                        } else {
                            state.knownNotNull.add(nullCheck.object());
                        }
                        metricNullCheckRegistered.increment();
                    }
                }
                for (GuardNode guard : begin.guards().snapshot()) {
                    BooleanNode condition = guard.condition();
                    ValueNode existingGuards = guard.negated() ? state.falseConditions.get(condition) : state.trueConditions.get(condition);
                    if (existingGuards != null) {
                        guard.replaceAtUsages(existingGuards);
                        GraphUtil.killWithUnusedFloatingInputs(guard);
                        metricGuardsReplaced.increment();
                    } else {
                        boolean removeCheck = false;
                        if (condition instanceof IsNullNode) {
                            IsNullNode isNull = (IsNullNode) condition;
                            if (guard.negated() && state.knownNotNull.contains(isNull.object())) {
                                removeCheck = true;
                            } else if (!guard.negated() && state.knownNull.contains(isNull.object())) {
                                removeCheck = true;
                            }
                            if (removeCheck) {
                                metricNullCheckGuardRemoved.increment();
                            }
                        }
                        if (removeCheck) {
                            guard.replaceAtUsages(begin);
                            GraphUtil.killWithUnusedFloatingInputs(guard);
                        } else {
                            if (guard.negated()) {
                                state.falseConditions.put(condition, guard);
                            } else {
                                state.trueConditions.put(condition, guard);
                            }
                        }
                    }
                }
            } else if (node instanceof CheckCastNode) {
                CheckCastNode checkCast = (CheckCastNode) node;
                ResolvedJavaType type = state.getNodeType(checkCast.object());
                if (type != null && type.isSubtypeOf(checkCast.type())) {
                    PiNode piNode;
                    boolean nonNull = state.knownNotNull.contains(checkCast.object());
                    piNode = graph.unique(new PiNode(checkCast.object(), lastBegin, nonNull ? StampFactory.declaredNonNull(type) : StampFactory.declared(type)));
                    checkCast.replaceAtUsages(piNode);
                    graph.removeFixed(checkCast);
                    metricCheckCastRemoved.increment();
                }
            } else if (node instanceof IfNode) {
                IfNode ifNode = (IfNode) node;
                BooleanNode replaceWith = null;
                BooleanNode compare = ifNode.condition();

                if (state.trueConditions.containsKey(compare)) {
                    replaceWith = ConstantNode.forBoolean(true, graph);
                } else if (state.falseConditions.containsKey(compare)) {
                    replaceWith = ConstantNode.forBoolean(false, graph);
                } else {
                    if (compare instanceof InstanceOfNode) {
                        InstanceOfNode instanceOf = (InstanceOfNode) compare;
                        ValueNode object = instanceOf.object();
                        if (state.knownNull.contains(object)) {
                            replaceWith = ConstantNode.forBoolean(false, graph);
                        } else if (state.knownNotNull.contains(object)) {
                            ResolvedJavaType type = state.getNodeType(object);
                            if (type != null && type.isSubtypeOf(instanceOf.type())) {
                                replaceWith = ConstantNode.forBoolean(true, graph);
                            }
                        }
                        if (replaceWith != null) {
                            metricInstanceOfRemoved.increment();
                        }
                    } else if (compare instanceof IsNullNode) {
                        IsNullNode isNull = (IsNullNode) compare;
                        ValueNode object = isNull.object();
                        if (state.knownNull.contains(object)) {
                            replaceWith = ConstantNode.forBoolean(true, graph);
                        } else if (state.knownNotNull.contains(object)) {
                            replaceWith = ConstantNode.forBoolean(false, graph);
                        }
                        if (replaceWith != null) {
                            metricNullCheckRemoved.increment();
                        }
                    }
                }
                if (replaceWith != null) {
                    ifNode.setCondition(replaceWith);
                    if (compare.usages().isEmpty()) {
                        GraphUtil.killWithUnusedFloatingInputs(compare);
                    }
                }
            }
        }
    }

}

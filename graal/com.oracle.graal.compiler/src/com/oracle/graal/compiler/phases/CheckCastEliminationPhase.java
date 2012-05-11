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
package com.oracle.graal.compiler.phases;

import java.io.*;
import java.util.*;

import com.oracle.graal.compiler.graph.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

public class CheckCastEliminationPhase extends Phase {

    private static final DebugMetric metricInstanceOfRegistered = Debug.metric("InstanceOfRegistered");
    private static final DebugMetric metricIsTypeRegistered = Debug.metric("IsTypeRegistered");
    private static final DebugMetric metricNullCheckRegistered = Debug.metric("NullCheckRegistered");
    private static final DebugMetric metricCheckCastRemoved = Debug.metric("CheckCastRemoved");
    private static final DebugMetric metricInstanceOfRemoved = Debug.metric("InstanceOfRemoved");
    private static final DebugMetric metricNullCheckRemoved = Debug.metric("NullCheckRemoved");
    private static final DebugMetric metricNullCheckGuardRemoved = Debug.metric("NullCheckGuardRemoved");

    private StructuredGraph graph;

    static PrintStream out = System.out;

    @Override
    protected void run(StructuredGraph inputGraph) {
        graph = inputGraph;
//        if (!graph.method().holder().name().contains("DiskIndex")) {
//            return;
//        }
//        if (!graph.method().name().equals("writeCategoryTable")) {
//            return;
//        }
//        out.println("checkcast " + graph.method());

        new EliminateCheckCasts(graph.start(), new State()).apply();
    }

    public static class State implements MergeableState<State> {

        private IdentityHashMap<ValueNode, RiResolvedType> knownTypes;
        private HashSet<ValueNode> knownNotNull;
        private HashSet<ValueNode> knownNull;

        public State() {
            this.knownTypes = new IdentityHashMap<>();
            this.knownNotNull = new HashSet<>();
            this.knownNull = new HashSet<>();
        }

        public State(IdentityHashMap<ValueNode, RiResolvedType> knownTypes, HashSet<ValueNode> knownNotNull, HashSet<ValueNode> knownNull) {
            this.knownTypes = new IdentityHashMap<>(knownTypes);
            this.knownNotNull = new HashSet<>(knownNotNull);
            this.knownNull = new HashSet<>(knownNull);
        }

        @Override
        public boolean merge(MergeNode merge, List<State> withStates) {
            IdentityHashMap<ValueNode, RiResolvedType> newKnownTypes = new IdentityHashMap<>();
            HashSet<ValueNode> newKnownNotNull = new HashSet<>();
            HashSet<ValueNode> newKnownNull = new HashSet<>();

            for (Map.Entry<ValueNode, RiResolvedType> entry : knownTypes.entrySet()) {
                ValueNode node = entry.getKey();
                RiResolvedType type = entry.getValue();

                for (State other : withStates) {
                    RiResolvedType otherType = other.getNodeType(node);
                    type = widen(type, otherType);
                    if (type == null) {
                        break;
                    }
                }
                if (type == null && type != node.declaredType()) {
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
                boolean nul = true;
                for (State other : withStates) {
                    if (!other.knownNull.contains(node)) {
                        nul = false;
                        break;
                    }
                }
                if (nul) {
                    newKnownNull.add(node);
                }
            }
            /*
            // this piece of code handles phis (merges the types and knownNull/knownNotNull of the values)
            if (!(merge instanceof LoopBeginNode)) {
                for (PhiNode phi : merge.phis()) {
                    if (phi.type() == PhiType.Value && phi.kind() == CiKind.Object) {
                        ValueNode firstValue = phi.valueAt(0);
                        RiResolvedType type = getNodeType(firstValue);
                        boolean notNull = knownNotNull.contains(firstValue);
                        boolean nul = knownNull.contains(firstValue);

                        for (int i = 0; i < withStates.size(); i++) {
                            State otherState = withStates.get(i);
                            ValueNode value = phi.valueAt(i + 1);
                            RiResolvedType otherType = otherState.getNodeType(value);
                            type = widen(type, otherType);
                            notNull &= otherState.knownNotNull.contains(value);
                            nul &= otherState.knownNull.contains(value);
                        }
                        if (type == null && type != phi.declaredType()) {
                            newKnownTypes.put(phi, type);
                        }
                        if (notNull) {
                            newKnownNotNull.add(phi);
                        }
                        if (nul) {
                            newKnownNull.add(phi);
                        }
                    }
                }
            }
            */
            this.knownTypes = newKnownTypes;
            this.knownNotNull = newKnownNotNull;
            this.knownNull = newKnownNull;
            return true;
        }

        public RiResolvedType getNodeType(ValueNode node) {
            RiResolvedType result = knownTypes.get(node);
            return result == null ? node.declaredType() : result;
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
            return new State(knownTypes, knownNotNull, knownNull);
        }
    }

    public static RiResolvedType widen(RiResolvedType a, RiResolvedType b) {
        if (a == null || b == null) {
            return null;
        } else if (a == b) {
            return a;
        } else {
            return a.leastCommonAncestor(b);
        }
    }

    public static RiResolvedType tighten(RiResolvedType a, RiResolvedType b) {
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

    public class EliminateCheckCasts extends PostOrderNodeIterator<State> {
        private BeginNode lastBegin = null;

        public EliminateCheckCasts(FixedNode start, State initialState) {
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
                    if (ifNode.compare() instanceof InstanceOfNode) {
                        InstanceOfNode instanceOf = (InstanceOfNode) ifNode.compare();
                        if ((node == ifNode.trueSuccessor()) != instanceOf.negated()) {
                            ValueNode object = instanceOf.object();
                            state.knownNotNull.add(object);
                            state.knownTypes.put(object, tighten(instanceOf.targetClass(), state.getNodeType(object)));
                            metricInstanceOfRegistered.increment();
                        }
                    } else if (ifNode.compare() instanceof NullCheckNode) {
                        NullCheckNode nullCheck = (NullCheckNode) ifNode.compare();
                        boolean isNotNull = (node == ifNode.trueSuccessor()) != nullCheck.expectedNull;
                        if (isNotNull) {
                            state.knownNotNull.add(nullCheck.object());
                        } else {
                            state.knownNull.add(nullCheck.object());
                        }
                        metricNullCheckRegistered.increment();
                    } else if (ifNode.compare() instanceof IsTypeNode) {
                        IsTypeNode isType = (IsTypeNode) ifNode.compare();
                        if (isType.objectClass() instanceof ReadHubNode && (node == ifNode.trueSuccessor())) {
                            ReadHubNode readHub = (ReadHubNode) isType.objectClass();
                            ValueNode object = readHub.object();
                            state.knownNotNull.add(object);
                            state.knownTypes.put(object, tighten(isType.type(), state.getNodeType(object)));
                            metricIsTypeRegistered.increment();
                        }
                    }
                }
                for (GuardNode guard : begin.guards().snapshot()) {
                    boolean removeCheck = false;
                    if (guard.condition() instanceof NullCheckNode) {
                        NullCheckNode nullCheck = (NullCheckNode) guard.condition();
                        if (state.knownNotNull.contains(nullCheck.object()) && !nullCheck.expectedNull) {
                            removeCheck = true;
                        } else if (state.knownNull.contains(nullCheck.object()) && nullCheck.expectedNull) {
                            removeCheck = true;
                        }
                        if (removeCheck) {
                            metricNullCheckGuardRemoved.increment();
                        }
                    }
                    if (removeCheck) {
                        guard.replaceAtUsages(begin);
                        GraphUtil.killWithUnusedFloatingInputs(guard);
                    }
                }
            } else if (node instanceof CheckCastNode) {
                CheckCastNode checkCast = (CheckCastNode) node;
                RiResolvedType type = state.getNodeType(checkCast.object());
                if (checkCast.targetClass() != null && type != null && type.isSubtypeOf(checkCast.targetClass())) {
                    PiNode piNode;
                    if (state.knownNotNull.contains(checkCast.object())) {
                        piNode = graph.unique(new PiNode(checkCast.object(), lastBegin, StampFactory.declaredNonNull(type)));
                    } else {
                        piNode = graph.unique(new PiNode(checkCast.object(), lastBegin, StampFactory.declared(type)));
                    }
                    checkCast.replaceAtUsages(piNode);
                    graph.removeFixed(checkCast);
                    metricCheckCastRemoved.increment();
                }
            } else if (node instanceof IfNode) {
                IfNode ifNode = (IfNode) node;
                BooleanNode replaceWith = null;
                BooleanNode compare = ifNode.compare();
                if (compare instanceof InstanceOfNode) {
                    InstanceOfNode instanceOf = (InstanceOfNode) compare;
                    ValueNode object = instanceOf.object();
                    if (state.knownNull.contains(object)) {
                        replaceWith = ConstantNode.forBoolean(instanceOf.negated(), graph);
                    } else if (state.knownNotNull.contains(object)) {
                        RiResolvedType type = state.getNodeType(object);
                        if (type != null && type.isSubtypeOf(instanceOf.targetClass())) {
                            replaceWith = ConstantNode.forBoolean(!instanceOf.negated(), graph);
                        }
                    }
                    if (replaceWith != null) {
                        metricInstanceOfRemoved.increment();
                    }
                } else if (compare instanceof NullCheckNode) {
                    NullCheckNode nullCheck = (NullCheckNode) compare;
                    ValueNode object = nullCheck.object();
                    if (state.knownNull.contains(object)) {
                        replaceWith = ConstantNode.forBoolean(nullCheck.expectedNull, graph);
                    } else if (state.knownNotNull.contains(object)) {
                        replaceWith = ConstantNode.forBoolean(!nullCheck.expectedNull, graph);
                    }
                    if (replaceWith != null) {
                        metricNullCheckRemoved.increment();
                    }
                }
                if (replaceWith != null) {
                    ifNode.setCompare(replaceWith);
                    if (compare.usages().isEmpty()) {
                        GraphUtil.killWithUnusedFloatingInputs(compare);
                    }
                }
            }
        }
    }

}

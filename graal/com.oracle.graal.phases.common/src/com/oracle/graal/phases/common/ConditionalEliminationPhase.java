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

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;

public class ConditionalEliminationPhase extends Phase {

    private static final DebugMetric metricConditionRegistered = Debug.metric("ConditionRegistered");
    private static final DebugMetric metricTypeRegistered = Debug.metric("TypeRegistered");
    private static final DebugMetric metricNullnessRegistered = Debug.metric("NullnessRegistered");
    private static final DebugMetric metricObjectEqualsRegistered = Debug.metric("ObjectEqualsRegistered");
    private static final DebugMetric metricCheckCastRemoved = Debug.metric("CheckCastRemoved");
    private static final DebugMetric metricInstanceOfRemoved = Debug.metric("InstanceOfRemoved");
    private static final DebugMetric metricNullCheckRemoved = Debug.metric("NullCheckRemoved");
    private static final DebugMetric metricObjectEqualsRemoved = Debug.metric("ObjectEqualsRemoved");
    private static final DebugMetric metricGuardsRemoved = Debug.metric("GuardsRemoved");

    private final MetaAccessProvider metaAccess;

    private StructuredGraph graph;

    public ConditionalEliminationPhase(MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
    }

    @Override
    protected void run(StructuredGraph inputGraph) {
        graph = inputGraph;
        new ConditionalElimination(graph.start(), new State()).apply();
    }

    public static class State extends MergeableState<State> {

        private IdentityHashMap<ValueNode, ResolvedJavaType> knownTypes;
        private HashSet<ValueNode> knownNonNull;
        private HashSet<ValueNode> knownNull;
        private IdentityHashMap<LogicNode, ValueNode> trueConditions;
        private IdentityHashMap<LogicNode, ValueNode> falseConditions;

        public State() {
            this.knownTypes = new IdentityHashMap<>();
            this.knownNonNull = new HashSet<>();
            this.knownNull = new HashSet<>();
            this.trueConditions = new IdentityHashMap<>();
            this.falseConditions = new IdentityHashMap<>();
        }

        public State(State other) {
            this.knownTypes = new IdentityHashMap<>(other.knownTypes);
            this.knownNonNull = new HashSet<>(other.knownNonNull);
            this.knownNull = new HashSet<>(other.knownNull);
            this.trueConditions = new IdentityHashMap<>(other.trueConditions);
            this.falseConditions = new IdentityHashMap<>(other.falseConditions);
        }

        @Override
        public boolean merge(MergeNode merge, List<State> withStates) {
            IdentityHashMap<ValueNode, ResolvedJavaType> newKnownTypes = new IdentityHashMap<>();
            IdentityHashMap<LogicNode, ValueNode> newTrueConditions = new IdentityHashMap<>();
            IdentityHashMap<LogicNode, ValueNode> newFalseConditions = new IdentityHashMap<>();

            HashSet<ValueNode> newKnownNull = new HashSet<>(knownNull);
            HashSet<ValueNode> newKnownNonNull = new HashSet<>(knownNonNull);
            for (State state : withStates) {
                newKnownNull.retainAll(state.knownNull);
                newKnownNonNull.retainAll(state.knownNonNull);
            }

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
                if (type != null && type != ObjectStamp.typeOrNull(node)) {
                    newKnownTypes.put(node, type);
                }
            }

            for (Map.Entry<LogicNode, ValueNode> entry : trueConditions.entrySet()) {
                LogicNode check = entry.getKey();
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
            for (Map.Entry<LogicNode, ValueNode> entry : falseConditions.entrySet()) {
                LogicNode check = entry.getKey();
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

            // this piece of code handles phis
            if (!(merge instanceof LoopBeginNode)) {
                for (PhiNode phi : merge.phis()) {
                    if (phi.type() == PhiType.Value && phi.kind() == Kind.Object) {
                        ValueNode firstValue = phi.valueAt(0);
                        ResolvedJavaType type = getNodeType(firstValue);
                        boolean nonNull = knownNonNull.contains(firstValue);
                        boolean isNull = knownNull.contains(firstValue);

                        for (int i = 0; i < withStates.size(); i++) {
                            State otherState = withStates.get(i);
                            ValueNode value = phi.valueAt(i + 1);
                            ResolvedJavaType otherType = otherState.getNodeType(value);
                            type = widen(type, otherType);
                            nonNull &= otherState.knownNonNull.contains(value);
                            isNull &= otherState.knownNull.contains(value);
                        }
                        if (type != null) {
                            newKnownTypes.put(phi, type);
                        }
                        if (nonNull) {
                            newKnownNonNull.add(phi);
                        }
                        if (isNull) {
                            newKnownNull.add(phi);
                        }
                    }
                }
            }

            this.knownTypes = newKnownTypes;
            this.knownNonNull = newKnownNonNull;
            this.knownNull = newKnownNull;
            this.trueConditions = newTrueConditions;
            this.falseConditions = newFalseConditions;
            return true;
        }

        public ResolvedJavaType getNodeType(ValueNode node) {
            ResolvedJavaType result = knownTypes.get(GraphUtil.unproxify(node));
            return result == null ? ObjectStamp.typeOrNull(node) : result;
        }

        public boolean isNull(ValueNode value) {
            return ObjectStamp.isObjectAlwaysNull(value) || knownNull.contains(GraphUtil.unproxify(value));
        }

        public boolean isNonNull(ValueNode value) {
            return ObjectStamp.isObjectNonNull(value) || knownNonNull.contains(GraphUtil.unproxify(value));
        }

        @Override
        public State clone() {
            return new State(this);
        }

        /**
         * Adds information about a condition. If isTrue is true then the condition is known to
         * hold, otherwise the condition is known not to hold.
         */
        public void addCondition(boolean isTrue, LogicNode condition, ValueNode anchor) {
            if (isTrue) {
                if (!trueConditions.containsKey(condition)) {
                    trueConditions.put(condition, anchor);
                    metricConditionRegistered.increment();
                }
            } else {
                if (!falseConditions.containsKey(condition)) {
                    falseConditions.put(condition, anchor);
                    metricConditionRegistered.increment();
                }
            }
        }

        /**
         * Adds information about the nullness of a value. If isNull is true then the value is known
         * to be null, otherwise the value is known to be non-null.
         */
        public void addNullness(boolean isNull, ValueNode value) {
            if (isNull) {
                if (!isNull(value)) {
                    metricNullnessRegistered.increment();
                    knownNull.add(GraphUtil.unproxify(value));
                }
            } else {
                if (!isNonNull(value)) {
                    metricNullnessRegistered.increment();
                    knownNonNull.add(GraphUtil.unproxify(value));
                }
            }
        }

        public void addType(ResolvedJavaType type, ValueNode value) {
            ValueNode original = GraphUtil.unproxify(value);
            ResolvedJavaType knownType = getNodeType(original);
            ResolvedJavaType newType = tighten(type, knownType);

            if (newType != knownType) {
                knownTypes.put(original, newType);
                metricTypeRegistered.increment();
            }
        }

        public void clear() {
            knownTypes.clear();
            knownNonNull.clear();
            knownNull.clear();
            trueConditions.clear();
            falseConditions.clear();
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
        } else if (a.isAssignableFrom(b)) {
            return b;
        } else {
            return a;
        }
    }

    public class ConditionalElimination extends PostOrderNodeIterator<State> {

        private final LogicNode trueConstant;
        private final LogicNode falseConstant;

        public ConditionalElimination(FixedNode start, State initialState) {
            super(start, initialState);
            trueConstant = LogicConstantNode.tautology(graph);
            falseConstant = LogicConstantNode.contradiction(graph);
        }

        @Override
        public void finished() {
            if (trueConstant.usages().isEmpty()) {
                graph.removeFloating(trueConstant);
            }
            if (falseConstant.usages().isEmpty()) {
                graph.removeFloating(falseConstant);
            }
        }

        private void registerCondition(boolean isTrue, LogicNode condition, ValueNode anchor) {
            if (!isTrue && condition instanceof ShortCircuitOrNode) {
                /*
                 * We can only do this for fixed nodes, because floating guards will be registered
                 * at a BeginNode but might be "valid" only later due to data flow dependencies.
                 * Therefore, registering both conditions of a ShortCircuitOrNode for a floating
                 * guard could lead to cycles in data flow, because the guard will be used as anchor
                 * for both conditions, and one condition could be depending on the other.
                 */
                if (anchor instanceof FixedNode) {
                    ShortCircuitOrNode disjunction = (ShortCircuitOrNode) condition;
                    registerCondition(disjunction.isXNegated(), disjunction.getX(), anchor);
                    registerCondition(disjunction.isYNegated(), disjunction.getY(), anchor);
                }
            }
            state.addCondition(isTrue, condition, anchor);

            if (isTrue && condition instanceof InstanceOfNode) {
                InstanceOfNode instanceOf = (InstanceOfNode) condition;
                ValueNode object = instanceOf.object();
                state.addNullness(false, object);
                state.addType(instanceOf.type(), object);
            } else if (condition instanceof IsNullNode) {
                IsNullNode nullCheck = (IsNullNode) condition;
                state.addNullness(isTrue, nullCheck.object());
            } else if (condition instanceof ObjectEqualsNode) {
                ObjectEqualsNode equals = (ObjectEqualsNode) condition;
                ValueNode x = equals.x();
                ValueNode y = equals.y();
                if (isTrue) {
                    if (state.isNull(x) && !state.isNull(y)) {
                        metricObjectEqualsRegistered.increment();
                        state.addNullness(true, y);
                    } else if (!state.isNull(x) && state.isNull(y)) {
                        metricObjectEqualsRegistered.increment();
                        state.addNullness(true, x);
                    }
                    if (state.isNonNull(x) && !state.isNonNull(y)) {
                        metricObjectEqualsRegistered.increment();
                        state.addNullness(false, y);
                    } else if (!state.isNonNull(x) && state.isNonNull(y)) {
                        metricObjectEqualsRegistered.increment();
                        state.addNullness(false, x);
                    }
                } else {
                    if (state.isNull(x) && !state.isNonNull(y)) {
                        metricObjectEqualsRegistered.increment();
                        state.addNullness(false, y);
                    } else if (!state.isNonNull(x) && state.isNull(y)) {
                        metricObjectEqualsRegistered.increment();
                        state.addNullness(false, x);
                    }
                }
            }
        }

        private void registerControlSplitInfo(Node pred, AbstractBeginNode begin) {
            assert pred != null && begin != null;
            if (begin instanceof LoopExitNode) {
                state.clear();
            }

            if (pred instanceof IfNode) {
                IfNode ifNode = (IfNode) pred;

                if (!(ifNode.condition() instanceof LogicConstantNode)) {
                    registerCondition(begin == ifNode.trueSuccessor(), ifNode.condition(), begin);
                }
            } else if (pred instanceof TypeSwitchNode) {
                TypeSwitchNode typeSwitch = (TypeSwitchNode) pred;

                if (typeSwitch.value() instanceof LoadHubNode) {
                    LoadHubNode loadHub = (LoadHubNode) typeSwitch.value();
                    ResolvedJavaType type = null;
                    for (int i = 0; i < typeSwitch.keyCount(); i++) {
                        if (typeSwitch.keySuccessor(i) == begin) {
                            if (type == null) {
                                type = typeSwitch.typeAt(i);
                            } else {
                                type = widen(type, typeSwitch.typeAt(i));
                            }
                        }
                    }
                    if (type != null) {
                        state.addNullness(false, loadHub.object());
                        state.addType(type, loadHub.object());
                    }
                }
            }
        }

        private void registerGuard(GuardNode guard) {
            LogicNode condition = guard.condition();

            ValueNode existingGuards = guard.negated() ? state.falseConditions.get(condition) : state.trueConditions.get(condition);
            if (existingGuards != null) {
                guard.replaceAtUsages(existingGuards);
                GraphUtil.killWithUnusedFloatingInputs(guard);
                metricGuardsRemoved.increment();
            } else {
                ValueNode anchor = state.trueConditions.get(condition);
                if (anchor != null) {
                    if (!guard.negated()) {
                        guard.replaceAtUsages(anchor);
                        metricGuardsRemoved.increment();
                        GraphUtil.killWithUnusedFloatingInputs(guard);
                    }
                } else {
                    anchor = state.falseConditions.get(condition);
                    if (anchor != null) {
                        if (guard.negated()) {
                            guard.replaceAtUsages(anchor);
                            metricGuardsRemoved.increment();
                            GraphUtil.killWithUnusedFloatingInputs(guard);
                        }
                    } else {
                        registerCondition(!guard.negated(), condition, guard);
                    }
                }
            }
        }

        /**
         * Determines if, at the current point in the control flow, the condition is known to be
         * true, false or unknown. In case of true or false the corresponding value is returned,
         * otherwise null.
         */
        private <T extends ValueNode> T evaluateCondition(LogicNode condition, T trueValue, T falseValue) {
            if (state.trueConditions.containsKey(condition)) {
                return trueValue;
            } else if (state.falseConditions.containsKey(condition)) {
                return falseValue;
            } else {
                if (condition instanceof InstanceOfNode) {
                    InstanceOfNode instanceOf = (InstanceOfNode) condition;
                    ValueNode object = instanceOf.object();
                    if (state.isNull(object)) {
                        metricInstanceOfRemoved.increment();
                        return falseValue;
                    } else if (state.isNonNull(object)) {
                        ResolvedJavaType type = state.getNodeType(object);
                        if (type != null && instanceOf.type().isAssignableFrom(type)) {
                            metricInstanceOfRemoved.increment();
                            return trueValue;
                        }
                    }
                } else if (condition instanceof IsNullNode) {
                    IsNullNode isNull = (IsNullNode) condition;
                    ValueNode object = isNull.object();
                    if (state.isNull(object)) {
                        metricNullCheckRemoved.increment();
                        return trueValue;
                    } else if (state.isNonNull(object)) {
                        metricNullCheckRemoved.increment();
                        return falseValue;
                    }
                } else if (condition instanceof ObjectEqualsNode) {
                    ObjectEqualsNode equals = (ObjectEqualsNode) condition;
                    ValueNode x = equals.x();
                    ValueNode y = equals.y();
                    if (state.isNull(x) && state.isNonNull(y) || state.isNonNull(x) && state.isNull(y)) {
                        metricObjectEqualsRemoved.increment();
                        return falseValue;
                    } else if (state.isNull(x) && state.isNull(y)) {
                        metricObjectEqualsRemoved.increment();
                        return trueValue;
                    }
                }
            }
            return null;
        }

        @Override
        protected void node(FixedNode node) {
            if (node instanceof AbstractBeginNode) {
                AbstractBeginNode begin = (AbstractBeginNode) node;
                Node pred = node.predecessor();

                if (pred != null) {
                    registerControlSplitInfo(pred, begin);
                }
                for (GuardNode guard : begin.guards().snapshot()) {
                    registerGuard(guard);
                }
            } else if (node instanceof FixedGuardNode) {
                FixedGuardNode guard = (FixedGuardNode) node;
                registerCondition(!guard.isNegated(), guard.condition(), guard);
            } else if (node instanceof CheckCastNode) {
                CheckCastNode checkCast = (CheckCastNode) node;
                ValueNode object = checkCast.object();
                boolean isNull = state.isNull(object);
                ResolvedJavaType type = state.getNodeType(object);
                if (isNull || (type != null && checkCast.type().isAssignableFrom(type))) {
                    boolean nonNull = state.isNonNull(object);
                    GuardingNode replacementAnchor = null;
                    if (nonNull) {
                        replacementAnchor = searchAnchor(GraphUtil.unproxify(object), type);
                    }
                    ValueAnchorNode anchor = null;
                    if (replacementAnchor == null) {
                        anchor = graph.add(new ValueAnchorNode(null));
                        replacementAnchor = anchor;
                    }
                    PiNode piNode;
                    if (isNull) {
                        ConstantNode nullObject = ConstantNode.forObject(null, metaAccess, graph);
                        piNode = graph.unique(new PiNode(nullObject, StampFactory.forConstant(nullObject.getValue(), metaAccess), replacementAnchor.asNode()));
                    } else {
                        piNode = graph.unique(new PiNode(object, StampFactory.declared(type, nonNull), replacementAnchor.asNode()));
                    }
                    checkCast.replaceAtUsages(piNode);
                    if (anchor != null) {
                        graph.replaceFixedWithFixed(checkCast, anchor);
                    } else {
                        graph.removeFixed(checkCast);
                    }
                    metricCheckCastRemoved.increment();
                }
            } else if (node instanceof ConditionAnchorNode) {
                ConditionAnchorNode conditionAnchorNode = (ConditionAnchorNode) node;
                LogicNode condition = conditionAnchorNode.condition();
                ValueNode replacementAnchor = null;
                if (conditionAnchorNode.isNegated()) {
                    if (state.falseConditions.containsKey(condition)) {
                        replacementAnchor = state.falseConditions.get(condition);
                    }
                } else {
                    if (state.trueConditions.containsKey(condition)) {
                        replacementAnchor = state.trueConditions.get(condition);
                    }
                }
                if (replacementAnchor != null) {
                    conditionAnchorNode.replaceAtUsages(replacementAnchor);
                    conditionAnchorNode.graph().removeFixed(conditionAnchorNode);
                }
            } else if (node instanceof IfNode) {
                IfNode ifNode = (IfNode) node;
                LogicNode compare = ifNode.condition();

                LogicNode replacement = null;
                ValueNode replacementAnchor = null;
                AbstractBeginNode survivingSuccessor = null;
                if (state.trueConditions.containsKey(compare)) {
                    replacement = trueConstant;
                    replacementAnchor = state.trueConditions.get(compare);
                    survivingSuccessor = ifNode.trueSuccessor();
                } else if (state.falseConditions.containsKey(compare)) {
                    replacement = falseConstant;
                    replacementAnchor = state.falseConditions.get(compare);
                    survivingSuccessor = ifNode.falseSuccessor();
                } else {
                    replacement = evaluateCondition(compare, trueConstant, falseConstant);
                    if (replacement != null) {
                        if (replacement == trueConstant) {
                            survivingSuccessor = ifNode.trueSuccessor();
                        } else {
                            assert replacement == falseConstant;
                            survivingSuccessor = ifNode.falseSuccessor();
                        }
                    }
                }

                if (replacement != null) {
                    if (!(replacementAnchor instanceof AbstractBeginNode)) {
                        ValueAnchorNode anchor = graph.add(new ValueAnchorNode(replacementAnchor));
                        graph.addBeforeFixed(ifNode, anchor);
                    }
                    for (Node n : survivingSuccessor.usages().snapshot()) {
                        if (n instanceof GuardNode || n instanceof ProxyNode) {
                            // Keep wired to the begin node.
                        } else {
                            if (replacementAnchor == null) {
                                // Cannot simplify this IfNode as there is no anchor.
                                return;
                            }
                            // Rewire to the replacement anchor.
                            n.replaceFirstInput(survivingSuccessor, replacementAnchor);
                        }
                    }

                    ifNode.setCondition(replacement);
                    if (compare.usages().isEmpty()) {
                        GraphUtil.killWithUnusedFloatingInputs(compare);
                    }
                }
            } else if (node instanceof AbstractEndNode) {
                AbstractEndNode endNode = (AbstractEndNode) node;
                for (PhiNode phi : endNode.merge().phis()) {
                    int index = endNode.merge().phiPredecessorIndex(endNode);
                    ValueNode value = phi.valueAt(index);
                    if (value instanceof ConditionalNode) {
                        ConditionalNode materialize = (ConditionalNode) value;
                        LogicNode compare = materialize.condition();
                        ValueNode replacement = evaluateCondition(compare, materialize.trueValue(), materialize.falseValue());

                        if (replacement != null) {
                            phi.setValueAt(index, replacement);
                            if (materialize.usages().isEmpty()) {
                                GraphUtil.killWithUnusedFloatingInputs(materialize);
                            }
                        }
                    }
                }
            } else if (node instanceof Invoke) {
                Invoke invoke = (Invoke) node;
                if (invoke.callTarget() instanceof MethodCallTargetNode) {
                    MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
                    ValueNode receiver = callTarget.receiver();
                    if (receiver != null && (callTarget.invokeKind() == InvokeKind.Interface || callTarget.invokeKind() == InvokeKind.Virtual)) {
                        ResolvedJavaType type = state.getNodeType(receiver);
                        if (type != ObjectStamp.typeOrNull(receiver)) {
                            ResolvedJavaMethod method = type.resolveMethod(callTarget.targetMethod());
                            if (method != null) {
                                if (Modifier.isFinal(method.getModifiers()) || Modifier.isFinal(type.getModifiers())) {
                                    callTarget.setInvokeKind(InvokeKind.Special);
                                    callTarget.setTargetMethod(method);
                                }
                            }
                        }
                    }
                }

            }
        }

        private GuardingNode searchAnchor(ValueNode value, ResolvedJavaType type) {
            if (!value.recordsUsages()) {
                return null;
            }
            for (Node n : value.usages()) {
                if (n instanceof InstanceOfNode) {
                    InstanceOfNode instanceOfNode = (InstanceOfNode) n;
                    if (instanceOfNode.type() == type && state.trueConditions.containsKey(instanceOfNode)) {
                        ValueNode v = state.trueConditions.get(instanceOfNode);
                        if (v instanceof GuardingNode) {
                            return (GuardingNode) v;
                        }
                    }
                }
            }

            for (Node n : value.usages()) {
                if (n instanceof ValueProxy) {
                    ValueProxy proxyNode = (ValueProxy) n;
                    if (proxyNode.getOriginalValue() == value) {
                        GuardingNode result = searchAnchor((ValueNode) n, type);
                        if (result != null) {
                            return result;
                        }
                    }

                }
            }

            return null;
        }
    }
}

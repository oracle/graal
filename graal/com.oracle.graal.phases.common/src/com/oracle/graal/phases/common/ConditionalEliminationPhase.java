/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.debug.Debug.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
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

    private StructuredGraph graph;

    public ConditionalEliminationPhase() {
    }

    @Override
    protected void run(StructuredGraph inputGraph) {
        graph = inputGraph;
        try (Scope s = Debug.scope("ConditionalElimination")) {
            new ConditionalElimination(graph.start(), new State()).apply();
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    /**
     * Type information about a {@code value} that it produced by a {@code guard}. Usage of the
     * stamp information requires adopting the guard. Usually this means replacing an existing guard
     * with this guard.
     */
    static class GuardedStamp {
        private final ValueNode value;
        private final Stamp stamp;
        private final GuardNode guard;

        GuardedStamp(ValueNode value, Stamp stamp, GuardNode guard) {
            this.value = value;
            this.stamp = stamp;
            this.guard = guard;
        }

        public Stamp getStamp() {
            return stamp;
        }

        public GuardNode getGuard() {
            return guard;
        }

        public ValueNode getValue() {
            return value;
        }
    }

    public static class State extends MergeableState<State> implements Cloneable {

        private Map<ValueNode, ResolvedJavaType> knownTypes;
        private Set<ValueNode> knownNonNull;
        private Set<ValueNode> knownNull;
        private Map<LogicNode, GuardingNode> trueConditions;
        private Map<LogicNode, GuardingNode> falseConditions;
        private Map<ValueNode, GuardedStamp> valueConstraints;

        public State() {
            this.knownTypes = Node.newIdentityMap();
            this.knownNonNull = Node.newSet();
            this.knownNull = Node.newSet();
            this.trueConditions = Node.newIdentityMap();
            this.falseConditions = Node.newIdentityMap();
            this.valueConstraints = Node.newIdentityMap();
        }

        public State(State other) {
            this.knownTypes = Node.newIdentityMap(other.knownTypes);
            this.knownNonNull = Node.newSet(other.knownNonNull);
            this.knownNull = Node.newSet(other.knownNull);
            this.trueConditions = Node.newIdentityMap(other.trueConditions);
            this.falseConditions = Node.newIdentityMap(other.falseConditions);
            this.valueConstraints = Node.newIdentityMap(other.valueConstraints);
        }

        @Override
        public boolean merge(AbstractMergeNode merge, List<State> withStates) {
            Map<ValueNode, ResolvedJavaType> newKnownTypes = Node.newIdentityMap();
            Map<LogicNode, GuardingNode> newTrueConditions = Node.newIdentityMap();
            Map<LogicNode, GuardingNode> newFalseConditions = Node.newIdentityMap();
            Map<ValueNode, GuardedStamp> newValueConstraints = Node.newIdentityMap();

            Set<ValueNode> newKnownNull = Node.newSet(knownNull);
            Set<ValueNode> newKnownNonNull = Node.newSet(knownNonNull);
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
                if (type != null && !type.equals(StampTool.typeOrNull(node))) {
                    newKnownTypes.put(node, type);
                }
            }

            for (Map.Entry<LogicNode, GuardingNode> entry : trueConditions.entrySet()) {
                LogicNode check = entry.getKey();
                GuardingNode guard = entry.getValue();

                for (State other : withStates) {
                    GuardingNode otherGuard = other.trueConditions.get(check);
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
            for (Map.Entry<LogicNode, GuardingNode> entry : falseConditions.entrySet()) {
                LogicNode check = entry.getKey();
                GuardingNode guard = entry.getValue();

                for (State other : withStates) {
                    GuardingNode otherGuard = other.falseConditions.get(check);
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
                    if (phi instanceof ValuePhiNode && phi.getKind() == Kind.Object) {
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
            this.valueConstraints = newValueConstraints;
            return true;
        }

        public ResolvedJavaType getNodeType(ValueNode node) {
            ResolvedJavaType result = knownTypes.get(GraphUtil.unproxify(node));
            return result == null ? StampTool.typeOrNull(node) : result;
        }

        public boolean isNull(ValueNode value) {
            return StampTool.isPointerAlwaysNull(value) || knownNull.contains(GraphUtil.unproxify(value));
        }

        public boolean isNonNull(ValueNode value) {
            return StampTool.isPointerNonNull(value) || knownNonNull.contains(GraphUtil.unproxify(value));
        }

        @Override
        public State clone() {
            return new State(this);
        }

        /**
         * Adds information about a condition. If isTrue is true then the condition is known to
         * hold, otherwise the condition is known not to hold.
         */
        public void addCondition(boolean isTrue, LogicNode condition, GuardingNode anchor) {
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

            if (!newType.equals(knownType)) {
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
        } else if (a.equals(b)) {
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
        } else if (a.equals(b)) {
            return a;
        } else if (a.isAssignableFrom(b)) {
            return b;
        } else {
            return a;
        }
    }

    public class ConditionalElimination extends SinglePassNodeIterator<State> {

        private final LogicNode trueConstant;
        private final LogicNode falseConstant;

        public ConditionalElimination(StartNode start, State initialState) {
            super(start, initialState);
            trueConstant = LogicConstantNode.tautology(graph);
            falseConstant = LogicConstantNode.contradiction(graph);
        }

        @Override
        public void finished() {
            if (trueConstant.hasNoUsages()) {
                graph.removeFloating(trueConstant);
            }
            if (falseConstant.hasNoUsages()) {
                graph.removeFloating(falseConstant);
            }
            super.finished();
        }

        private void registerCondition(boolean isTrue, LogicNode condition, GuardingNode anchor) {
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
                ValueNode object = instanceOf.getValue();
                state.addNullness(false, object);
                state.addType(instanceOf.type(), object);
            } else if (condition instanceof IsNullNode) {
                IsNullNode nullCheck = (IsNullNode) condition;
                state.addNullness(isTrue, nullCheck.getValue());
            } else if (condition instanceof ObjectEqualsNode) {
                ObjectEqualsNode equals = (ObjectEqualsNode) condition;
                ValueNode x = equals.getX();
                ValueNode y = equals.getY();
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
            /*
             * We does not create value proxies for values it may connect accross loop exit node so
             * we have to clear the state at loop exits if the graph needs value proxies
             */
            if (begin instanceof LoopExitNode && begin.graph().hasValueProxies()) {
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
                        state.addNullness(false, loadHub.getValue());
                        state.addType(type, loadHub.getValue());
                    }
                }
            }
        }

        private GuardedStamp computeGuardedStamp(GuardNode guard) {
            if (guard.condition() instanceof IntegerBelowNode) {
                if (guard.isNegated()) {
                    // Not sure how to reason about negated guards
                    return null;
                }
                IntegerBelowNode below = (IntegerBelowNode) guard.condition();
                if (below.getX().getKind() == Kind.Int && below.getX().isConstant() && !below.getY().isConstant()) {
                    Stamp stamp = StampTool.unsignedCompare(below.getX().stamp(), below.getY().stamp());
                    if (stamp != null) {
                        return new GuardedStamp(below.getY(), stamp, guard);
                    }
                }
                if (below.getY().getKind() == Kind.Int && below.getY().isConstant() && !below.getX().isConstant()) {
                    Stamp stamp = StampTool.unsignedCompare(below.getX().stamp(), below.getY().stamp());
                    if (stamp != null) {
                        return new GuardedStamp(below.getX(), stamp, guard);
                    }
                }
            }
            return null;
        }

        private boolean eliminateTrivialGuardOrRegisterStamp(GuardNode guard) {
            if (tryReplaceWithExistingGuard(guard)) {
                return true;
            }
            // Can't be eliminated so accumulate any type information from the guard
            registerConditionalStamp(guard);
            return false;
        }

        /**
         * Replace {@code guard} with {@code anchor} .
         *
         * @param guard The guard to eliminate.
         * @param anchor Node to replace the guard.
         */
        private void eliminateGuard(GuardNode guard, GuardingNode anchor) {
            guard.replaceAtUsages(anchor.asNode());
            metricGuardsRemoved.increment();
            GraphUtil.killWithUnusedFloatingInputs(guard);
        }

        /**
         * See if a conditional type constraint can prove this guard.
         *
         * @param guard
         * @return true if the guard was eliminated.
         */
        private boolean testImpliedGuard(GuardNode guard) {
            if (state.valueConstraints.size() == 0) {
                // Nothing to do.
                return false;
            }

            GuardNode existingGuard = null;
            if (guard.condition() instanceof IntegerBelowNode) {
                IntegerBelowNode below = (IntegerBelowNode) guard.condition();
                IntegerStamp xStamp = (IntegerStamp) below.getX().stamp();
                IntegerStamp yStamp = (IntegerStamp) below.getY().stamp();
                GuardedStamp cstamp = state.valueConstraints.get(below.getX());
                if (cstamp != null) {
                    xStamp = (IntegerStamp) cstamp.getStamp();
                } else {
                    cstamp = state.valueConstraints.get(below.getY());
                    if (cstamp != null) {
                        yStamp = (IntegerStamp) cstamp.getStamp();
                    }
                }
                if (cstamp != null) {
                    if (cstamp.getGuard() == guard) {
                        // found ourselves
                        return false;
                    }
                    // See if we can use the other guard
                    if (!guard.isNegated() && !cstamp.getGuard().isNegated() && yStamp.isPositive()) {
                        if (xStamp.isPositive() && xStamp.upperBound() < yStamp.lowerBound()) {
                            // Proven true
                            existingGuard = cstamp.getGuard();
                            Debug.log("existing guard %s %1s proves %1s", existingGuard, existingGuard.condition(), guard.condition());
                        } else if (xStamp.isStrictlyNegative() || xStamp.lowerBound() >= yStamp.upperBound()) {
                            // An earlier guard proves that this will always fail but it's probably
                            // not worth trying to use it.
                        }
                    }
                }
            } else if (guard.condition() instanceof IntegerEqualsNode && guard.isNegated()) {
                IntegerEqualsNode equals = (IntegerEqualsNode) guard.condition();
                GuardedStamp cstamp = state.valueConstraints.get(equals.getY());
                if (cstamp != null && equals.getX().isConstant()) {
                    IntegerStamp stamp = (IntegerStamp) cstamp.getStamp();
                    if (!stamp.contains(equals.getX().asJavaConstant().asLong())) {
                        // x != n is true if n is outside the range of the stamp
                        existingGuard = cstamp.getGuard();
                        Debug.log("existing guard %s %1s proves !%1s", existingGuard, existingGuard.condition(), guard.condition());
                    }
                }
            }

            if (existingGuard != null) {
                // Found a guard which proves this guard to be true, so replace it.
                eliminateGuard(guard, existingGuard);
                return true;
            }
            return false;
        }

        private boolean tryReplaceWithExistingGuard(GuardNode guard) {
            GuardingNode existingGuard = guard.isNegated() ? state.falseConditions.get(guard.condition()) : state.trueConditions.get(guard.condition());
            if (existingGuard != null && existingGuard != guard) {
                eliminateGuard(guard, existingGuard);
                return true;
            }
            return false;
        }

        private void registerConditionalStamp(GuardNode guard) {
            GuardedStamp conditional = computeGuardedStamp(guard);
            if (conditional != null) {
                GuardedStamp other = state.valueConstraints.get(conditional.getValue());
                if (other == null) {
                    state.valueConstraints.put(conditional.getValue(), conditional);
                } else if (guard.isNegated() != other.getGuard().isNegated()) {
                    // This seems impossible
                    // Debug.log("negated and !negated guards %1s %1s", guard, other.getGuard());
                } else if (!guard.isNegated()) {
                    // two different constraints, pick the one with the tightest type
                    // information
                    Stamp result = conditional.getStamp().join(other.getStamp());
                    if (result == conditional.getStamp()) {
                        Debug.log("%1s overrides existing value %1s", guard.condition(), other.getGuard().condition());
                        state.valueConstraints.put(conditional.getValue(), conditional);
                    } else if (result == other.getStamp()) {
                        // existing type constraint is best
                        Debug.log("existing value is best %s", other.getGuard());
                    } else {
                        // The merger produced some combination of values
                        Debug.log("type merge produced new type %s", result);
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
                    ValueNode object = instanceOf.getValue();
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
                    ValueNode object = isNull.getValue();
                    if (state.isNull(object)) {
                        metricNullCheckRemoved.increment();
                        return trueValue;
                    } else if (state.isNonNull(object)) {
                        metricNullCheckRemoved.increment();
                        return falseValue;
                    }
                } else if (condition instanceof ObjectEqualsNode) {
                    ObjectEqualsNode equals = (ObjectEqualsNode) condition;
                    ValueNode x = equals.getX();
                    ValueNode y = equals.getY();
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
                processAbstractBegin((AbstractBeginNode) node);
            } else if (node instanceof FixedGuardNode) {
                processFixedGuard((FixedGuardNode) node);
            } else if (node instanceof CheckCastNode) {
                processCheckCast((CheckCastNode) node);
            } else if (node instanceof ConditionAnchorNode) {
                processConditionAnchor((ConditionAnchorNode) node);
            } else if (node instanceof IfNode) {
                processIf((IfNode) node);
            } else if (node instanceof AbstractEndNode) {
                processAbstractEnd((AbstractEndNode) node);
            } else if (node instanceof Invoke) {
                processInvoke((Invoke) node);
            }
        }

        private void processIf(IfNode ifNode) {
            LogicNode compare = ifNode.condition();

            LogicNode replacement = null;
            GuardingNode replacementAnchor = null;
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
                trySimplify(ifNode, compare, replacement, replacementAnchor, survivingSuccessor);
            }
        }

        private void trySimplify(IfNode ifNode, LogicNode compare, LogicNode replacement, GuardingNode replacementAnchor, AbstractBeginNode survivingSuccessor) {
            if (replacementAnchor != null && !(replacementAnchor instanceof AbstractBeginNode)) {
                ValueAnchorNode anchor = graph.add(new ValueAnchorNode(replacementAnchor.asNode()));
                graph.addBeforeFixed(ifNode, anchor);
            }
            boolean canSimplify = true;
            for (Node n : survivingSuccessor.usages().snapshot()) {
                if (n instanceof GuardNode || n instanceof ProxyNode) {
                    // Keep wired to the begin node.
                } else {
                    if (replacementAnchor == null) {
                        // Cannot simplify this IfNode as there is no anchor.
                        canSimplify = false;
                        break;
                    }
                    // Rewire to the replacement anchor.
                    n.replaceFirstInput(survivingSuccessor, replacementAnchor.asNode());
                }
            }

            if (canSimplify) {
                ifNode.setCondition(replacement);
                if (compare.hasNoUsages()) {
                    GraphUtil.killWithUnusedFloatingInputs(compare);
                }
            }
        }

        private void processInvoke(Invoke invoke) {
            if (invoke.callTarget() instanceof MethodCallTargetNode) {
                MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
                ValueNode receiver = callTarget.receiver();
                if (receiver != null && callTarget.invokeKind().isIndirect()) {
                    ResolvedJavaType type = state.getNodeType(receiver);
                    if (!Objects.equals(type, StampTool.typeOrNull(receiver))) {
                        ResolvedJavaMethod method = type.resolveConcreteMethod(callTarget.targetMethod(), invoke.getContextType());
                        if (method != null) {
                            if (method.canBeStaticallyBound() || type.isLeaf() || type.isArray()) {
                                callTarget.setInvokeKind(InvokeKind.Special);
                                callTarget.setTargetMethod(method);
                            }
                        }
                    }
                }
            }
        }

        private void processAbstractEnd(AbstractEndNode endNode) {
            for (PhiNode phi : endNode.merge().phis()) {
                int index = endNode.merge().phiPredecessorIndex(endNode);
                ValueNode value = phi.valueAt(index);
                if (value instanceof ConditionalNode) {
                    ConditionalNode materialize = (ConditionalNode) value;
                    LogicNode compare = materialize.condition();
                    ValueNode replacement = evaluateCondition(compare, materialize.trueValue(), materialize.falseValue());

                    if (replacement != null) {
                        phi.setValueAt(index, replacement);
                        if (materialize.hasNoUsages()) {
                            GraphUtil.killWithUnusedFloatingInputs(materialize);
                        }
                    }
                }
            }
        }

        private void processConditionAnchor(ConditionAnchorNode conditionAnchorNode) {
            LogicNode condition = conditionAnchorNode.condition();
            GuardingNode replacementAnchor = null;
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
                conditionAnchorNode.replaceAtUsages(replacementAnchor.asNode());
                conditionAnchorNode.graph().removeFixed(conditionAnchorNode);
            }
        }

        private void processCheckCast(CheckCastNode checkCast) {
            ValueNode object = checkCast.object();
            boolean isNull = state.isNull(object);
            ResolvedJavaType type = state.getNodeType(object);
            if (isNull || (type != null && checkCast.type().isAssignableFrom(type))) {
                boolean nonNull = state.isNonNull(object);
                // if (true)
                // throw new RuntimeException(checkCast.toString());
                GuardingNode replacementAnchor = null;
                if (nonNull) {
                    replacementAnchor = searchAnchor(GraphUtil.unproxify(object), type);
                }
                if (replacementAnchor == null) {
                    replacementAnchor = AbstractBeginNode.prevBegin(checkCast);
                }
                assert !(replacementAnchor instanceof FloatingNode) : "unsafe to mix unlowered Checkcast with floating guards";
                PiNode piNode;
                if (isNull) {
                    ConstantNode nullObject = ConstantNode.defaultForKind(Kind.Object, graph);
                    piNode = graph.unique(new PiNode(nullObject, nullObject.stamp(), replacementAnchor.asNode()));
                } else {
                    piNode = graph.unique(new PiNode(object, StampFactory.declaredTrusted(type, nonNull), replacementAnchor.asNode()));
                }
                checkCast.replaceAtUsages(piNode);
                graph.removeFixed(checkCast);
                metricCheckCastRemoved.increment();
            }
        }

        private void processFixedGuard(FixedGuardNode guard) {
            GuardingNode existingGuard = guard.isNegated() ? state.falseConditions.get(guard.condition()) : state.trueConditions.get(guard.condition());
            if (existingGuard != null && existingGuard instanceof FixedGuardNode) {
                guard.replaceAtUsages(existingGuard.asNode());
                guard.graph().removeFixed(guard);
            } else {
                registerCondition(!guard.isNegated(), guard.condition(), guard);
            }
        }

        private void processAbstractBegin(AbstractBeginNode begin) {
            Node pred = begin.predecessor();

            if (pred != null) {
                registerControlSplitInfo(pred, begin);
            }

            // First eliminate any guards which can be trivially removed and register any
            // type constraints the guards produce.
            for (GuardNode guard : begin.guards().snapshot()) {
                eliminateTrivialGuardOrRegisterStamp(guard);
            }

            // Collect the guards which have produced conditional stamps.
            // XXX (gd) IdentityHashMap.values().contains performs a linear search
            // so we prefer to build a set
            Set<GuardNode> provers = Node.newSet();
            for (GuardedStamp e : state.valueConstraints.values()) {
                provers.add(e.getGuard());
            }

            // Process the remaining guards. Guards which produced some type constraint should
            // just be registered since they aren't trivially deleteable. Test the other guards
            // to see if they can be deleted using type constraints.
            for (GuardNode guard : begin.guards().snapshot()) {
                if (provers.contains(guard) || !(tryReplaceWithExistingGuard(guard) || testImpliedGuard(guard))) {
                    registerCondition(!guard.isNegated(), guard.condition(), guard);
                }
            }
            assert assertImpliedGuard(provers);
        }

        private boolean assertImpliedGuard(Set<GuardNode> provers) {
            for (GuardNode guard : provers) {
                assert !testImpliedGuard(guard) : "provers shouldn't be trivially eliminatable";
            }
            return true;
        }

        private GuardingNode searchAnchor(ValueNode value, ResolvedJavaType type) {
            for (Node n : value.usages()) {
                if (n instanceof InstanceOfNode) {
                    InstanceOfNode instanceOfNode = (InstanceOfNode) n;
                    if (instanceOfNode.type().equals(type) && state.trueConditions.containsKey(instanceOfNode)) {
                        GuardingNode v = state.trueConditions.get(instanceOfNode);
                        if (v != null) {
                            return v;
                        }
                    }
                }
            }

            for (Node n : value.usages()) {
                if (n instanceof ValueProxy) {
                    ValueProxy proxyNode = (ValueProxy) n;
                    if (proxyNode.getOriginalNode() == value) {
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

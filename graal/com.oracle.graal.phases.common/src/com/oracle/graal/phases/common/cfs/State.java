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
package com.oracle.graal.phases.common.cfs;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.calc.ObjectEqualsNode;
import com.oracle.graal.nodes.extended.GuardedNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.java.InstanceOfNode;
import com.oracle.graal.nodes.spi.ValueProxy;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.graph.MergeableState;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * A State instance is mutated in place as each FixedNode is visited in a basic block of
 * instructions. Basic block: starts with a {@link com.oracle.graal.nodes.BeginNode BeginNode}, ends
 * at an {@link com.oracle.graal.nodes.EndNode EndNode} or
 * {@link com.oracle.graal.nodes.ControlSinkNode ControlSinkNode} and lacks intervening control
 * splits or merges.
 */
public final class State extends MergeableState<State> implements Cloneable {

    private static final DebugMetric metricTypeRegistered = Debug.metric("TypeRegistered");
    private static final DebugMetric metricNullnessRegistered = Debug.metric("NullnessRegistered");
    private static final DebugMetric metricObjectEqualsRegistered = Debug.metric("ObjectEqualsRegistered");
    private static final DebugMetric metricImpossiblePathDetected = Debug.metric("ImpossiblePathDetected");

    /**
     * <p>
     * Each state update results in a higher {@link State#versionNr versionNr}. The
     * {@link State#versionNr versionNr} of different State instances can't be meaningfully compared
     * (ie, same {@link State#versionNr versionNr} just indicates they've gone through the same
     * number of updates). In particular, the {@link State#versionNr versionNr} of a merged state
     * doesn't guarantee any more than being different from those of the states being merged.
     * </p>
     *
     * <p>
     * Still, {@link State#versionNr versionNr} proves useful in two cases:
     *
     * <ul>
     * <li>recording the {@link State#versionNr versionNr} right after {@link State State} cloning,
     * allows finding out afterwards whether (a) both states have diverged, (b) just one of them, or
     * (c) none of them.</li>
     * <li>a {@link State State} may become {@link State#isUnreachable isUnreachable}. In such case,
     * it may make a difference whether any updates were performed on the state from the time it was
     * cloned. Those updates indicate information not available in the state is was cloned from. For
     * the purposes of {@link FlowSensitiveReduction FlowSensitiveReduction} an unreachable state
     * need not be merged with any other (because control-flow won't reach the merge point over the
     * path of the unreachable state).</li>
     * </ul>
     * </p>
     *
     */
    int versionNr = 0;

    boolean isUnreachable = false;

    /**
     * Getting here implies an opportunity was detected for dead-code-elimination. A counterpoint
     * argument goes as follows: perhaps we don't get here that often, in which case the effort to
     * detect an "impossible path" could be shaved off.
     *
     * @see com.oracle.graal.phases.common.cfs.BaseReduction.PostponedDeopt
     */
    void impossiblePath() {
        isUnreachable = true;
        metricImpossiblePathDetected.increment();
    }

    /**
     * <p>
     * This map semantically tracks "facts" (ie, properties valid for the program-point the state
     * refers to) as opposed to floating-guard-dependent properties. The
     * {@link com.oracle.graal.nodes.extended.GuardingNode} being tracked comes handy at
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction#visitFixedGuardNode(com.oracle.graal.nodes.FixedGuardNode)}
     * .
     * </p>
     *
     * <p>
     * On a related note, {@link #typeRefinements} also captures information the way
     * {@link #trueFacts} and {@link #falseFacts} do, including "witnessing" guards. Why not just
     * standardize on one of them, and drop the other? Because the {@link #typeRefinements} eagerly
     * aggregates information for easier querying afterwards, e.g. when producing a "downcasted"
     * value (which involves building a {@link com.oracle.graal.nodes.PiNode}, see
     * {@link EquationalReasoner#downcast(com.oracle.graal.nodes.ValueNode) downcast()}
     * </p>
     *
     */
    private IdentityHashMap<ValueNode, Witness> typeRefinements;

    IdentityHashMap<ValueNode, GuardingNode> knownNull;
    IdentityHashMap<LogicNode, GuardingNode> trueFacts;
    IdentityHashMap<LogicNode, GuardingNode> falseFacts;

    public State() {
        this.typeRefinements = new IdentityHashMap<>();
        this.knownNull = new IdentityHashMap<>();
        this.trueFacts = new IdentityHashMap<>();
        this.falseFacts = new IdentityHashMap<>();
    }

    public State(State other) {
        this.isUnreachable = other.isUnreachable;
        this.versionNr = other.versionNr;
        this.typeRefinements = new IdentityHashMap<>();
        for (Map.Entry<ValueNode, Witness> entry : other.typeRefinements.entrySet()) {
            this.typeRefinements.put(entry.getKey(), new Witness(entry.getValue()));
        }
        this.knownNull = new IdentityHashMap<>(other.knownNull);
        this.trueFacts = new IdentityHashMap<>(other.trueFacts);
        this.falseFacts = new IdentityHashMap<>(other.falseFacts);
    }

    /**
     * @return A new list containing only those states that are reachable.
     */
    private static ArrayList<State> reachableStates(List<State> states) {
        ArrayList<State> result = new ArrayList<>(states);
        Iterator<State> iter = result.iterator();
        while (iter.hasNext()) {
            if (iter.next().isUnreachable) {
                iter.remove();
            }
        }
        return result;
    }

    private IdentityHashMap<ValueNode, Witness> mergeKnownTypes(MergeNode merge, ArrayList<State> withReachableStates) {
        IdentityHashMap<ValueNode, Witness> newKnownTypes = new IdentityHashMap<>();

        for (Map.Entry<ValueNode, Witness> entry : typeRefinements.entrySet()) {
            ValueNode node = entry.getKey();
            Witness type = new Witness(entry.getValue());

            for (State other : withReachableStates) {
                Witness otherType = other.typeInfo(node);
                if (otherType == null) {
                    type = null;
                    break;
                }
                type.merge(otherType, merge);
            }
            if (type != null && type.knowsBetterThan(node)) {
                assert node == GraphUtil.unproxify(node);
                newKnownTypes.put(node, type);
            }
        }

        return newKnownTypes;
    }

    private IdentityHashMap<ValueNode, GuardingNode> mergeKnownNull(MergeNode merge, ArrayList<State> withReachableStates) {
        // newKnownNull starts empty
        IdentityHashMap<ValueNode, GuardingNode> newKnownNull = new IdentityHashMap<>();
        for (Map.Entry<ValueNode, GuardingNode> entry : knownNull.entrySet()) {
            ValueNode key = entry.getKey();
            GuardingNode newGN = entry.getValue();
            boolean missing = false;

            for (State other : withReachableStates) {
                GuardingNode otherGuard = other.knownNull.get(key);
                if (otherGuard == null) {
                    missing = true;
                    break;
                }
                if (otherGuard != newGN) {
                    newGN = merge;
                }
            }
            if (!missing) {
                newKnownNull.put(key, newGN);
            }
        }
        return newKnownNull;
    }

    /**
     * <p>
     * This method handles phis, by adding to the resulting state any information that can be gained
     * (about type-refinement and nullness) based on the data available at each of the incoming
     * branches.
     * </p>
     *
     * <p>
     * In more detail, <code>FlowSensitiveReduction#visitAbstractEndNode()</code> has already
     * deverbosified the phi-values contributed by each reachable branch. The paths that
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction} determined to be
     * unreachable will be eliminated by canonicalization and dead code elimination. For now they
     * still exist, thus polluting the result of
     * {@link com.oracle.graal.nodes.ValuePhiNode#inferPhiStamp()} but we are careful to skip them
     * when merging type-witnesses and known-null maps.
     * </p>
     */
    private void mergePhis(MergeNode merge, List<State> withStates, IdentityHashMap<ValueNode, Witness> newKnownPhiTypes, IdentityHashMap<ValueNode, GuardingNode> newKnownNullPhis) {

        if (merge instanceof LoopBeginNode) {
            return;
        }

        for (PhiNode phi : merge.phis()) {
            assert phi == GraphUtil.unproxify(phi);
            if (phi instanceof ValuePhiNode && phi.getKind() == Kind.Object) {
                ArrayList<ValueNode> reachingValues = new ArrayList<>();
                if (!isUnreachable) {
                    reachingValues.add(phi.valueAt(0));
                }
                for (int i = 0; i < withStates.size(); i++) {
                    State otherState = withStates.get(i);
                    if (!otherState.isUnreachable) {
                        reachingValues.add(phi.valueAt(i + 1));
                    }
                }
                assert !reachingValues.isEmpty();
                ObjectStamp phiStamp = (ObjectStamp) phi.stamp();
                ObjectStamp nonPollutedStamp = (ObjectStamp) StampTool.meet(reachingValues);
                Witness w = new Witness(nonPollutedStamp, merge);
                if (FlowUtil.isMorePrecise(w.type(), phiStamp.type())) {
                    // precision gain regarding type
                    newKnownPhiTypes.put(phi, w);
                    // confirm no precision loss regarding nullness
                    assert implies(phiStamp.nonNull(), w.isNonNull());
                } else if (w.isNonNull() && !phiStamp.nonNull()) {
                    // precision gain regarding nullness
                    newKnownPhiTypes.put(phi, w);
                    // confirm no precision loss regarding type
                    assert !FlowUtil.isMorePrecise(phiStamp.type(), w.type());
                }
                if (nonPollutedStamp.alwaysNull()) {
                    newKnownNullPhis.put(phi, merge);
                }
            }
        }

    }

    private static boolean implies(boolean a, boolean b) {
        return !a || b;
    }

    @Override
    public boolean merge(MergeNode merge, List<State> withStates) {

        ArrayList<State> withReachableStates = reachableStates(withStates);
        if (withReachableStates.isEmpty()) {
            return true;
        }

        for (State other : withReachableStates) {
            versionNr = Math.max(versionNr, other.versionNr) + 1;
            if (!other.isUnreachable) {
                isUnreachable = false;
            }
        }

        if (isUnreachable) {
            typeRefinements.clear();
            knownNull.clear();
            trueFacts.clear();
            falseFacts.clear();
            return true;
        }

        // may also get updated in a moment, during processing of phi nodes.
        IdentityHashMap<ValueNode, Witness> newKnownTypes = mergeKnownTypes(merge, withReachableStates);
        // may also get updated in a moment, during processing of phi nodes.
        IdentityHashMap<ValueNode, GuardingNode> newKnownNull = mergeKnownNull(merge, withReachableStates);
        mergePhis(merge, withStates, newKnownTypes, newKnownNull);
        this.typeRefinements = newKnownTypes;
        this.knownNull = newKnownNull;

        this.trueFacts = mergeTrueFacts(withReachableStates, merge);
        this.falseFacts = mergeFalseFacts(withReachableStates, merge);
        return true;
    }

    private IdentityHashMap<LogicNode, GuardingNode> mergeTrueFacts(ArrayList<State> withReachableStates, GuardingNode merge) {
        IdentityHashMap<LogicNode, GuardingNode> newTrueConditions = new IdentityHashMap<>();
        for (Map.Entry<LogicNode, GuardingNode> entry : trueFacts.entrySet()) {
            LogicNode check = entry.getKey();
            GuardingNode guard = entry.getValue();

            for (State other : withReachableStates) {
                GuardingNode otherGuard = other.trueFacts.get(check);
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
        return newTrueConditions;
    }

    private IdentityHashMap<LogicNode, GuardingNode> mergeFalseFacts(ArrayList<State> withReachableStates, GuardingNode merge) {
        IdentityHashMap<LogicNode, GuardingNode> newFalseConditions = new IdentityHashMap<>();
        for (Map.Entry<LogicNode, GuardingNode> entry : falseFacts.entrySet()) {
            LogicNode check = entry.getKey();
            GuardingNode guard = entry.getValue();

            for (State other : withReachableStates) {
                GuardingNode otherGuard = other.falseFacts.get(check);
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
        return newFalseConditions;
    }

    /**
     * @return null if no type-witness available for the argument, the witness otherwise.
     */
    public Witness typeInfo(ValueNode object) {
        assert FlowUtil.hasLegalObjectStamp(object);
        return typeRefinements.get(GraphUtil.unproxify(object));
    }

    /**
     * @return true iff the argument is known to stand for null.
     */
    public boolean isNull(ValueNode object) {
        assert FlowUtil.hasLegalObjectStamp(object);
        return StampTool.isObjectAlwaysNull(object) || knownNull.containsKey(GraphUtil.unproxify(object));
    }

    /**
     * <p>
     * It makes a difference calling {@link Witness#isNonNull()} as opposed to
     * {@link State#isNonNull(com.oracle.graal.nodes.ValueNode)}. The former guarantees the witness
     * provides a guard certifying non-nullness. The latter just tells us there exists some guard
     * that certifies the property we asked about.
     * </p>
     *
     * <p>
     * TODO improvement: isKnownNonNull could be made smarter by noticing some nodes always denote a
     * non-null value (eg, ObjectGetClassNode). Similarly for isKnownNull. Code that looks at the
     * stamp as well as code that looks for a non-null-witness would benefit from also checking such
     * extended isKnownNonNull. Alternatively, the stamp of those nodes should always have
     * is-non-null set.
     * </p>
     *
     * @return true iff the argument is known to stand for non-null.
     */
    public boolean isNonNull(ValueNode object) {
        assert FlowUtil.hasLegalObjectStamp(object);
        if (StampTool.isObjectNonNull(object)) {
            return true;
        }
        Witness w = typeInfo(object);
        return w == null ? false : w.isNonNull();
    }

    /**
     * @return true iff the argument is known to stand for an object conforming to the given type.
     */
    public boolean knownToConform(ValueNode object, ResolvedJavaType to) {
        assert FlowUtil.hasLegalObjectStamp(object);
        assert !to.isPrimitive();
        ResolvedJavaType stampType = StampTool.typeOrNull(object);
        if (stampType != null && to.isAssignableFrom(stampType)) {
            return true;
        }
        final ValueNode scrutinee = GraphUtil.unproxify(object);
        if (isNull(scrutinee)) {
            return true;
        }
        Witness w = typeInfo(scrutinee);
        boolean witnessAnswer = w != null && w.type() != null && to.isAssignableFrom(w.type());
        if (witnessAnswer) {
            return true;
        }
        return false;
    }

    /**
     * @return true iff the argument is known to stand for an object that definitely does not
     *         conform to the given type.
     */
    public boolean knownNotToConform(ValueNode object, ResolvedJavaType to) {
        assert FlowUtil.hasLegalObjectStamp(object);
        assert !to.isPrimitive();
        final ValueNode scrutinee = GraphUtil.unproxify(object);
        if (isNull(scrutinee)) {
            return false;
        }
        ResolvedJavaType stampType = StampTool.typeOrNull(object);
        if (stampType != null && knownNotToConform(stampType, to)) {
            return true;
        }
        Witness w = typeInfo(scrutinee);
        boolean witnessAnswer = w != null && !w.cluelessAboutType() && knownNotToConform(w.type(), to);
        if (witnessAnswer) {
            return true;
        }
        return false;
    }

    // @formatter:off
    /**
     *   \   |     |     |     |
     *    \ b|     |     |     |
     *   a \ |     |     |     |
     *      \|iface|final|non-f|
     *  -----+-----------------|
     *  iface|  F  |  F  |  F  |
     *  -----+-----------------|
     *  final|  C  |  C  |  C  |
     *  -----+-----------------|
     *  non-f|  F  |  C  |  C  |
     *  -----------------------+
     *
     *  where:
     *    F:     false
     *    C:     check
     *    iface: interface
     *    final: exact non-interface reference-type
     *    non-f: non-exact non-interface reference-type
     *
     * @return true iff the first argument is known not to conform to the second argument.
     */
    // @formatter:on
    public static boolean knownNotToConform(ResolvedJavaType a, ResolvedJavaType b) {
        assert !a.isPrimitive();
        assert !b.isPrimitive();
        if (b.isAssignableFrom(a)) {
            return false;
        }
        if (a.isInterface()) {
            return false;
        }
        boolean aFinal = Modifier.isFinal(a.getModifiers());
        if (b.isInterface() && !aFinal) {
            return false;
        }
        if (a.isAssignableFrom(b)) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public State clone() {
        return new State(this);
    }

    /**
     * Porcelain method.
     */
    private Witness getOrElseAddTypeInfo(ValueNode object) {
        ValueNode scrutinee = GraphUtil.unproxify(object);
        Witness w = typeRefinements.get(scrutinee);
        if (w == null) {
            w = new Witness();
            typeRefinements.put(scrutinee, w);
        }
        return w;
    }

    /**
     * <p>
     * Updates this {@link State State} to account for an observation about the scrutinee being
     * non-null. In case instanceof was observed,
     * {@link #trackIO(com.oracle.graal.nodes.ValueNode, com.oracle.graal.api.meta.ResolvedJavaType, com.oracle.graal.nodes.extended.GuardingNode)
     * <code>trackIO(ResolvedJavaType, GuardingNode)</code>} should be invoked instead.
     * </p>
     *
     * <p>
     * No check is made on whether a contradiction would be introduced into the factbase (in which
     * case the state should be marked unreachable), the caller takes care of that.
     * </p>
     *
     * @return whether state was updated (iff the observation added any new information)
     */
    public boolean trackNN(ValueNode object, GuardingNode anchor) {
        if (isDependencyTainted(object, anchor)) {
            return false;
        }
        assert anchor instanceof FixedNode;
        ResolvedJavaType stampType = StampTool.typeOrNull(object);
        if (stampType != null && !stampType.isInterface()) {
            return trackIO(object, stampType, anchor);
        }
        Witness w = getOrElseAddTypeInfo(object);
        if (w.trackNN(anchor)) {
            versionNr++;
            return true;
        }
        return false;
    }

    /**
     * Updates this {@link State State} to account for an observation about the scrutinee conforming
     * to a type. In case instanceof was observed,
     * {@link #trackIO(com.oracle.graal.nodes.ValueNode, com.oracle.graal.api.meta.ResolvedJavaType, com.oracle.graal.nodes.extended.GuardingNode)
     * <code>trackIO(ResolvedJavaType, GuardingNode)</code>} should be invoked instead.
     *
     * <p>
     * No check is made on whether a contradiction would be introduced into the factbase (in which
     * case the state should be marked unreachable), the caller must take care of that.
     * </p>
     *
     * @return false iff the observed type is an interface, or doesn't provide any new information
     *         not already known. Ie, this method returns true iff the observation resulted in
     *         information gain.
     */
    public boolean trackCC(ValueNode object, ResolvedJavaType observed, GuardingNode anchor) {
        if (observed.isInterface()) {
            return false;
        }
        if (isDependencyTainted(object, anchor)) {
            return false;
        }
        assert anchor instanceof FixedNode;
        Witness w = getOrElseAddTypeInfo(object);
        if (w.trackCC(observed, anchor)) {
            versionNr++;
            metricTypeRegistered.increment();
            return true;
        }
        return false;
    }

    /**
     * Updates this {@link State State} to account for an observation about the non-null scrutinee
     * conforming to a type.
     *
     * <p>
     * No check is made on whether a contradiction would be introduced into the factbase (in which
     * case the state should be marked unreachable), the caller must take care of that.
     * </p>
     *
     * @return whether state was updated (iff the observation added any new information)
     */
    public boolean trackIO(ValueNode object, ResolvedJavaType observed, GuardingNode anchor) {
        assert !observed.isInterface() : "no infrastructure yet in State.Witness to support interfaces in general";
        if (isDependencyTainted(object, anchor)) {
            return false;
        }
        assert anchor instanceof FixedNode;
        Witness w = getOrElseAddTypeInfo(object);
        if (w.trackIO(observed, anchor)) {
            versionNr++;
            metricTypeRegistered.increment();
            return true;
        }
        return false;
    }

    /**
     * This method increases {@link #versionNr} (thus potentially invalidating
     * {@link EquationalReasoner EquationalReasoner}'s caches) only if the fact wasn't known
     * already.
     *
     * <p>
     * No check is made on whether a contradiction would be introduced into the factbase (in which
     * case the state should be marked unreachable), the caller must take care of that.
     * </p>
     *
     */
    private void addFactPrimordial(LogicNode condition, IdentityHashMap<LogicNode, GuardingNode> to, GuardingNode anchor) {
        assert condition != null;
        if (!to.containsKey(condition)) {
            versionNr++;
            to.put(condition, anchor);
        }
    }

    /**
     * Ideas for the future:
     * <ul>
     * <li>track inferred less-than edges from (accumulated) CompareNode-s</li>
     * <li>track set-representative for equality classes determined by (chained) IntegerTestNode</li>
     * </ul>
     *
     */
    public void addFact(boolean isTrue, LogicNode condition, GuardingNode anchor) {
        assert anchor != null;
        assert anchor instanceof FixedNode;
        assert !isUnreachable;

        if (condition instanceof LogicConstantNode) {
            if (((LogicConstantNode) condition).getValue() != isTrue) {
                impossiblePath();
            }
            return;
        }

        if (condition instanceof LogicNegationNode) {
            addFact(!isTrue, ((LogicNegationNode) condition).getInput(), anchor);
        } else if (condition instanceof ShortCircuitOrNode) {
            /*
             * We can register the conditions being or-ed as long as the anchor is a fixed node,
             * because floating guards will be registered at a BeginNode but might be "valid" only
             * later due to data flow dependencies. Therefore, registering both conditions of a
             * ShortCircuitOrNode for a floating guard could lead to cycles in data flow, because
             * the guard will be used as anchor for both conditions, and one condition could be
             * depending on the other.
             */
            if (isTrue) {
                CastCheckExtractor cce = CastCheckExtractor.extract(condition);
                if (cce == null || isDependencyTainted(cce.subject, anchor)) {
                    addFactPrimordial(condition, isTrue ? trueFacts : falseFacts, anchor);
                } else {
                    trackCC(cce.subject, cce.type, anchor);
                }
            } else {
                ShortCircuitOrNode disjunction = (ShortCircuitOrNode) condition;
                addFact(disjunction.isXNegated(), disjunction.getX(), anchor);
                // previous addFact might have resulted in impossiblePath()
                if (isUnreachable) {
                    return;
                }
                addFact(disjunction.isYNegated(), disjunction.getY(), anchor);
            }
        } else if (condition instanceof InstanceOfNode) {
            addFactInstanceOf(isTrue, (InstanceOfNode) condition, anchor);
        } else if (condition instanceof IsNullNode) {
            IsNullNode nullCheck = (IsNullNode) condition;
            addNullness(isTrue, nullCheck.object(), anchor);
        } else if (condition instanceof ObjectEqualsNode) {
            addFactObjectEqualsNode(isTrue, (ObjectEqualsNode) condition, anchor);
        } else {
            addFactPrimordial(condition, isTrue ? trueFacts : falseFacts, anchor);
        }
    }

    /**
     * An instanceof hint is tracked differently depending on whether it's an interface-test or not
     * (because type-refinements currently lacks the ability to track interface types).
     *
     */
    private void addFactInstanceOf(boolean isTrue, InstanceOfNode instanceOf, GuardingNode anchor) {
        ValueNode object = instanceOf.object();
        if (isTrue) {
            if (knownNotToConform(object, instanceOf.type())) {
                impossiblePath();
                return;
            }
            addNullness(false, object, anchor);
            if (instanceOf.type().isInterface()) {
                if (!knownToConform(object, instanceOf.type())) {
                    addFactPrimordial(instanceOf, trueFacts, anchor);
                }
            } else {
                trackIO(object, instanceOf.type(), anchor);
            }
        }
    }

    private void addFactObjectEqualsNode(boolean isTrue, ObjectEqualsNode equals, GuardingNode anchor) {
        if (isDependencyTainted(equals.x(), anchor)) {
            return;
        }
        if (isDependencyTainted(equals.y(), anchor)) {
            return;
        }
        assert anchor instanceof FixedNode;
        ValueNode x = GraphUtil.unproxify(equals.x());
        ValueNode y = GraphUtil.unproxify(equals.y());
        if (isTrue) {
            if (isNull(x) && isNonNull(y)) {
                impossiblePath();
                return;
            }
            if (isNonNull(x) && isNull(y)) {
                impossiblePath();
                return;
            }
            if (isNull(x) || isNull(y)) {
                metricObjectEqualsRegistered.increment();
                addNullness(true, equals.x(), anchor);
                addNullness(true, equals.y(), anchor);
            } else if (isNonNull(x) || isNonNull(y)) {
                metricObjectEqualsRegistered.increment();
                addNullness(false, equals.x(), anchor);
                addNullness(false, equals.y(), anchor);
            }
            Witness wx = typeInfo(x);
            Witness wy = typeInfo(y);
            if (wx == null && wy == null) {
                return;
            } else if (wx != null && wy != null) {
                // tighten their type-hints, provided at least one available
                // both witnesses may have seen == null, ie they may be NN witnesses
                ResolvedJavaType best = FlowUtil.tighten(wx.type(), wy.type());
                if (best != null) {
                    assert !best.isInterface();
                    // type tightening is enough, nullness already taken care of
                    trackCC(equals.x(), best, anchor);
                    trackCC(equals.y(), best, anchor);
                }
            } else if (wx == null) {
                typeRefinements.put(x, new Witness(wy));
            } else if (wy == null) {
                typeRefinements.put(y, new Witness(wx));
            }
        } else {
            if (isNull(x) && !isNonNull(y)) {
                metricObjectEqualsRegistered.increment();
                addNullness(false, equals.y(), anchor);
            } else if (!isNonNull(x) && isNull(y)) {
                metricObjectEqualsRegistered.increment();
                addNullness(false, equals.x(), anchor);
            }
        }
    }

    /**
     * Adds information about the nullness of a value. If isNull is true then the value is known to
     * be null, otherwise the value is known to be non-null.
     */
    public void addNullness(boolean isNull, ValueNode value, GuardingNode anchor) {
        if (isDependencyTainted(value, anchor)) {
            return;
        }
        assert anchor instanceof FixedNode;
        ValueNode original = GraphUtil.unproxify(value);
        boolean wasNull = isNull(original);
        boolean wasNonNull = isNonNull(original);
        if (isNull) {
            if (wasNonNull) {
                impossiblePath();
            } else {
                metricNullnessRegistered.increment();
                versionNr++;
                knownNull.put(original, anchor);
            }
        } else {
            if (wasNull) {
                impossiblePath();
            } else {
                metricNullnessRegistered.increment();
                trackNN(original, anchor);
            }
        }
    }

    /**
     *
     * @return true iff `value` may lose dependency not covered by `anchor`.
     */
    public static boolean isDependencyTainted(ValueNode value, GuardingNode anchor) {
        assert anchor instanceof FixedNode;
        if (value instanceof ValueProxy) {
            if (value instanceof GuardedNode) {
                GuardedNode gn = (GuardedNode) value;
                GuardingNode guardian = gn.getGuard();
                if (guardian != null) {
                    boolean isGuardedByFixed = guardian instanceof FixedNode;
                    if (!isGuardedByFixed) {
                        return true;
                    }
                }
            }
            // if (value instanceof GuardingNode) {
            // return true;
            // }
            ValueProxy proxy = (ValueProxy) value;
            return isDependencyTainted(proxy.getOriginalNode(), anchor);
        }
        return false;
    }

    public void clear() {
        versionNr = 0;
        isUnreachable = false;
        typeRefinements.clear();
        knownNull.clear();
        trueFacts.clear();
        falseFacts.clear();
    }

}

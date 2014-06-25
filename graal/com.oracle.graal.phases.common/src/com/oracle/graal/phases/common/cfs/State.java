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

import static com.oracle.graal.graph.util.CollectionsAccess.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.graph.*;

/**
 * A State instance is mutated in place as each FixedNode is visited in a basic block of
 * instructions. Basic block: starts with a {@link com.oracle.graal.nodes.BeginNode BeginNode}, ends
 * at an {@link com.oracle.graal.nodes.EndNode EndNode} or
 * {@link com.oracle.graal.nodes.ControlSinkNode ControlSinkNode} and lacks intervening control
 * splits or merges.
 */
public final class State extends MergeableState<State> implements Cloneable {

    private static final DebugMetric metricTypeRegistered = Debug.metric("FSR-TypeRegistered");
    private static final DebugMetric metricNullnessRegistered = Debug.metric("FSR-NullnessRegistered");
    private static final DebugMetric metricObjectEqualsRegistered = Debug.metric("FSR-ObjectEqualsRegistered");
    private static final DebugMetric metricImpossiblePathDetected = Debug.metric("FSR-ImpossiblePathDetected");

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
     * This map tracks properties about reference-values, ie combinations of: definitely-null,
     * known-to-be-non-null, seen-type.
     * </p>
     *
     * <p>
     * In contrast to {@link #trueFacts} and {@link #falseFacts}, this map can answer queries even
     * though the exact {@link LogicNode} standing for such query hasn't been tracked. For example,
     * queries about subtyping. Additionally, a {@link Witness} can combine separate pieces of
     * information more flexibly, eg, two separate observations about non-null and check-cast are
     * promoted to an instance-of witness.
     * </p>
     *
     */
    private Map<ValueNode, Witness> typeRefinements;

    Map<LogicNode, GuardingNode> trueFacts;
    Map<LogicNode, GuardingNode> falseFacts;

    public State() {
        this.typeRefinements = newNodeIdentityMap();
        this.trueFacts = newNodeIdentityMap();
        this.falseFacts = newNodeIdentityMap();
    }

    public State(State other) {
        this.isUnreachable = other.isUnreachable;
        this.versionNr = other.versionNr;
        this.typeRefinements = newNodeIdentityMap();
        for (Map.Entry<ValueNode, Witness> entry : other.typeRefinements.entrySet()) {
            this.typeRefinements.put(entry.getKey(), new Witness(entry.getValue()));
        }
        this.trueFacts = newNodeIdentityMap(other.trueFacts);
        this.falseFacts = newNodeIdentityMap(other.falseFacts);
    }

    public boolean repOK() {
        // trueFacts and falseFacts disjoint
        for (LogicNode trueFact : trueFacts.keySet()) {
            assert !falseFacts.containsKey(trueFact) : trueFact + " tracked as both true and false fact.";
        }
        return true;
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

    private Map<ValueNode, Witness> mergeKnownTypes(MergeNode merge, ArrayList<State> withReachableStates) {
        Map<ValueNode, Witness> newKnownTypes = newNodeIdentityMap();

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
     * {@link com.oracle.graal.nodes.ValuePhiNode#inferStamp()} but we are careful to skip them when
     * merging type-witnesses and known-null maps.
     * </p>
     */
    private void mergePhis(MergeNode merge, List<State> withStates, Map<ValueNode, Witness> newKnownPhiTypes) {

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
                if (w.isNull() && !phiStamp.alwaysNull()) {
                    // precision gain: null
                    newKnownPhiTypes.put(phi, w);
                } else if (FlowUtil.isMorePrecise(w.type(), phiStamp.type())) {
                    // precision gain regarding type
                    newKnownPhiTypes.put(phi, w);
                    // confirm no precision loss regarding nullness
                    assert implies(phiStamp.nonNull(), w.isNonNull());
                    assert implies(phiStamp.alwaysNull(), w.isNull());
                } else if (w.isNonNull() && !phiStamp.nonNull()) {
                    // precision gain: non-null
                    newKnownPhiTypes.put(phi, w);
                    // confirm no precision loss regarding type
                    assert !FlowUtil.isMorePrecise(phiStamp.type(), w.type());
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
            trueFacts.clear();
            falseFacts.clear();
            return true;
        }

        // may also get updated in a moment, during processing of phi nodes.
        Map<ValueNode, Witness> newKnownTypes = mergeKnownTypes(merge, withReachableStates);
        // may also get updated in a moment, during processing of phi nodes.
        mergePhis(merge, withStates, newKnownTypes);
        this.typeRefinements = newKnownTypes;

        this.trueFacts = mergeTrueFacts(withReachableStates, merge);
        this.falseFacts = mergeFalseFacts(withReachableStates, merge);

        assert repOK();

        return true;
    }

    private Map<LogicNode, GuardingNode> mergeTrueFacts(ArrayList<State> withReachableStates, GuardingNode merge) {
        Map<LogicNode, GuardingNode> newTrueConditions = newNodeIdentityMap();
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

    private Map<LogicNode, GuardingNode> mergeFalseFacts(ArrayList<State> withReachableStates, GuardingNode merge) {
        Map<LogicNode, GuardingNode> newFalseConditions = newNodeIdentityMap();
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
        if (StampTool.isObjectAlwaysNull(object)) {
            return true;
        }
        ValueNode scrutinee = GraphUtil.unproxify(object);
        Witness w = typeRefinements.get(scrutinee);
        return (w != null) && w.isNull();
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
     * @return true iff the argument definitely stands for an object-value that conforms to the
     *         given type.
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
     * @return true iff the argument is known to stand for an object that is definitely non-null and
     *         moreover does not conform to the given type.
     */
    public boolean knownNotToPassCheckCast(ValueNode object, ResolvedJavaType to) {
        assert FlowUtil.hasLegalObjectStamp(object);
        assert !to.isPrimitive();
        final ValueNode scrutinee = GraphUtil.unproxify(object);
        if (isNull(scrutinee)) {
            // known-null means it conforms to whatever `to`
            // and thus passes the check-cast
            return false;
        }
        if (!isNonNull(scrutinee)) {
            // unless `null` can be ruled out, a positive answer isn't safe
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

    /**
     * @return true iff the argument is known to stand for an object that definitely does not
     *         conform to the given type (no matter whether the object turns out to be null or
     *         non-null).
     */
    public boolean knownNotToPassInstanceOf(ValueNode object, ResolvedJavaType to) {
        assert FlowUtil.hasLegalObjectStamp(object);
        assert !to.isPrimitive();
        final ValueNode scrutinee = GraphUtil.unproxify(object);
        if (isNull(scrutinee)) {
            return true;
        }
        ResolvedJavaType stampType = StampTool.typeOrNull(object);
        if (stampType != null && knownNotToConform(stampType, to)) {
            // object turns out to be null, positive answer is correct
            // object turns out non-null, positive answer is also correct
            return true;
        }
        Witness w = typeInfo(scrutinee);
        boolean witnessAnswer = w != null && !w.cluelessAboutType() && knownNotToConform(w.type(), to);
        if (witnessAnswer) {
            // object turns out to be null, positive answer is correct
            // object turns out non-null, positive answer is also correct
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
            assert repOK();
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
            assert repOK();
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
            assert repOK();
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
    private void addFactPrimordial(LogicNode condition, Map<LogicNode, GuardingNode> to, GuardingNode anchor) {
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
            addFact(!isTrue, ((LogicNegationNode) condition).getValue(), anchor);
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
            addNullness(isTrue, nullCheck.getValue(), anchor);
        } else if (condition instanceof ObjectEqualsNode) {
            addFactObjectEqualsNode(isTrue, (ObjectEqualsNode) condition, anchor);
        } else {
            addFactPrimordial(condition, isTrue ? trueFacts : falseFacts, anchor);
        }
        assert repOK();
    }

    /**
     * An instanceof hint is tracked differently depending on whether it's an interface-test or not
     * (because type-refinements currently lacks the ability to track interface types).
     *
     */
    private void addFactInstanceOf(boolean isTrue, InstanceOfNode instanceOf, GuardingNode anchor) {
        ValueNode object = instanceOf.getValue();
        if (isTrue) {
            if (knownNotToPassInstanceOf(object, instanceOf.type())) {
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
        if (isDependencyTainted(equals.getX(), anchor)) {
            return;
        }
        if (isDependencyTainted(equals.getY(), anchor)) {
            return;
        }
        assert anchor instanceof FixedNode;
        ValueNode x = GraphUtil.unproxify(equals.getX());
        ValueNode y = GraphUtil.unproxify(equals.getY());
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
                addNullness(true, equals.getX(), anchor);
                addNullness(true, equals.getY(), anchor);
            } else if (isNonNull(x) || isNonNull(y)) {
                metricObjectEqualsRegistered.increment();
                addNullness(false, equals.getX(), anchor);
                addNullness(false, equals.getY(), anchor);
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
                    trackCC(equals.getX(), best, anchor);
                    trackCC(equals.getY(), best, anchor);
                }
            } else if (wx == null) {
                typeRefinements.put(x, new Witness(wy));
            } else if (wy == null) {
                typeRefinements.put(y, new Witness(wx));
            }
        } else {
            if (isNull(x) && !isNonNull(y)) {
                metricObjectEqualsRegistered.increment();
                addNullness(false, equals.getY(), anchor);
            } else if (!isNonNull(x) && isNull(y)) {
                metricObjectEqualsRegistered.increment();
                addNullness(false, equals.getX(), anchor);
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
        ValueNode scrutinee = GraphUtil.unproxify(value);
        boolean wasNull = isNull(scrutinee);
        boolean wasNonNull = isNonNull(scrutinee);
        if (isNull) {
            if (wasNonNull) {
                impossiblePath();
            } else {
                metricNullnessRegistered.increment();
                versionNr++;
                Witness w = getOrElseAddTypeInfo(scrutinee);
                w.trackDN(anchor);
            }
        } else {
            if (wasNull) {
                impossiblePath();
            } else {
                metricNullnessRegistered.increment();
                trackNN(scrutinee, anchor);
            }
        }
        assert repOK();
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
        trueFacts.clear();
        falseFacts.clear();
    }

    /**
     * <p>
     * If the argument is known null due to its stamp, there's no need to have an anchor for that
     * fact and this method returns null.
     * </p>
     *
     * <p>
     * Otherwise, if an anchor is found it is returned, null otherwise.
     * </p>
     */
    public GuardingNode nonTrivialNullAnchor(ValueNode object) {
        assert FlowUtil.hasLegalObjectStamp(object);
        if (StampTool.isObjectAlwaysNull(object)) {
            return null;
        }
        ValueNode scrutinee = GraphUtil.unproxify(object);
        Witness w = typeRefinements.get(scrutinee);
        if (w != null && w.isNull()) {
            return w.guard();
        }
        return null;
    }

    /**
     * This method:
     * <ul>
     * <li>
     * attempts to find an existing {@link com.oracle.graal.nodes.extended.GuardingNode} that
     * implies the property stated by the arguments. If found, returns it as positive evidence.</li>
     * <li>
     * otherwise, if the property of interest is known not to hold, negative evidence is returned.</li>
     * <li>
     * otherwise, null is returned.</li>
     * </ul>
     */
    public Evidence outcome(boolean isTrue, LogicNode cond) {

        // attempt to find an anchor for the condition of interest, verbatim
        if (isTrue) {
            GuardingNode existingGuard = trueFacts.get(cond);
            if (existingGuard != null) {
                return new Evidence(existingGuard);
            }
            if (falseFacts.containsKey(cond)) {
                return Evidence.COUNTEREXAMPLE;
            }
        } else {
            GuardingNode existingGuard = falseFacts.get(cond);
            if (existingGuard != null) {
                return new Evidence(existingGuard);
            }
            if (trueFacts.containsKey(cond)) {
                return Evidence.COUNTEREXAMPLE;
            }
        }

        if (cond instanceof IsNullNode) {
            return outcomeIsNullNode(isTrue, (IsNullNode) cond);
        }

        if (cond instanceof InstanceOfNode) {
            return outcomeInstanceOfNode(isTrue, (InstanceOfNode) cond);
        }

        if (cond instanceof ShortCircuitOrNode) {
            return outcomeShortCircuitOrNode(isTrue, (ShortCircuitOrNode) cond);
        }

        // can't produce evidence
        return null;
    }

    /**
     * Utility method for {@link #outcome(boolean, com.oracle.graal.nodes.LogicNode)}
     */
    private Evidence outcomeIsNullNode(boolean isTrue, IsNullNode isNullNode) {
        if (isTrue) {
            // grab an anchor attesting nullness
            final GuardingNode replacement = nonTrivialNullAnchor(isNullNode.getValue());
            if (replacement != null) {
                return new Evidence(replacement);
            }
            if (isNonNull(isNullNode.getValue())) {
                return Evidence.COUNTEREXAMPLE;
            }
        } else {
            // grab an anchor attesting non-nullness
            final Witness w = typeInfo(isNullNode.getValue());
            if (w != null && w.isNonNull()) {
                return new Evidence(w.guard());
            }
            if (isNull(isNullNode.getValue())) {
                return Evidence.COUNTEREXAMPLE;
            }
        }
        // can't produce evidence
        return null;
    }

    /**
     * Utility method for {@link #outcome(boolean, com.oracle.graal.nodes.LogicNode)}
     */
    private Evidence outcomeInstanceOfNode(boolean isTrue, InstanceOfNode iOf) {
        final Witness w = typeInfo(iOf.getValue());
        if (isTrue) {
            if (isNull(iOf.getValue())) {
                return Evidence.COUNTEREXAMPLE;
            }
            // grab an anchor attesting instanceof
            if ((w != null) && (w.type() != null)) {
                if (w.isNonNull()) {
                    if (iOf.type().isAssignableFrom(w.type())) {
                        return new Evidence(w.guard());
                    }
                }
                if (State.knownNotToConform(w.type(), iOf.type())) {
                    // null -> fails instanceof
                    // non-null but non-conformant -> also fails instanceof
                    return Evidence.COUNTEREXAMPLE;
                }
            }
        } else {
            // grab an anchor attesting not-instanceof
            // (1 of 2) attempt determining nullness
            final GuardingNode nullGuard = nonTrivialNullAnchor(iOf.getValue());
            if (nullGuard != null) {
                return new Evidence(nullGuard);
            }
            // (2 of 2) attempt determining known-not-to-conform
            if (w != null && !w.cluelessAboutType()) {
                if (State.knownNotToConform(w.type(), iOf.type())) {
                    return new Evidence(w.guard());
                }
            }
        }
        // can't produce evidence
        return null;
    }

    /**
     * Utility method for {@link #outcome(boolean, com.oracle.graal.nodes.LogicNode)}
     */
    private Evidence outcomeShortCircuitOrNode(boolean isTrue, ShortCircuitOrNode orNode) {
        if (!isTrue) {
            // too tricky to reason about
            return null;
        }
        CastCheckExtractor cce = CastCheckExtractor.extract(orNode);
        if (cce != null) {
            // grab an anchor attesting check-cast
            Witness w = typeInfo(cce.subject);
            if (w != null && w.type() != null) {
                if (cce.type.isAssignableFrom(w.type())) {
                    return new Evidence(w.guard());
                }
                if (isNonNull(cce.subject) && State.knownNotToConform(w.type(), cce.type)) {
                    return Evidence.COUNTEREXAMPLE;
                }
            }
        }
        // search for positive-evidence for the first or-input
        Evidence evidenceX = outcome(!orNode.isXNegated(), orNode.getX());
        if (evidenceX != null && evidenceX.isPositive()) {
            return evidenceX;
        }
        // search for positive-evidence for the second or-input
        Evidence evidenceY = outcome(!orNode.isYNegated(), orNode.getY());
        if (evidenceY != null && evidenceY.isPositive()) {
            return evidenceY;
        }
        // check for contradictions on both or-inputs
        if (evidenceX != null && evidenceY != null) {
            assert evidenceX.isNegative() && evidenceY.isNegative();
            return Evidence.COUNTEREXAMPLE;
        }
        // can't produce evidence
        return null;
    }

}

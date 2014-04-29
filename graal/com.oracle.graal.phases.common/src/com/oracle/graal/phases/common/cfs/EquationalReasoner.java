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

import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeBitMap;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.calc.ObjectEqualsNode;
import com.oracle.graal.nodes.extended.GuardedNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.java.CheckCastNode;
import com.oracle.graal.nodes.java.InstanceOfNode;
import com.oracle.graal.nodes.spi.ValueProxy;
import com.oracle.graal.compiler.common.type.IllegalStamp;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.nodes.util.GraphUtil;

import java.util.IdentityHashMap;
import java.util.Set;

/**
 * <p>
 * This class implements a simple partial evaluator that recursively reduces a given
 * {@link com.oracle.graal.nodes.calc.FloatingNode} into a simpler one based on the current state.
 * Such evaluator comes handy when visiting a {@link com.oracle.graal.nodes.FixedNode} N, just
 * before updating the state for N. At the pre-state, an {@link EquationalReasoner} can be used to
 * reduce N's inputs (actually only those inputs of Value and Condition
 * {@link com.oracle.graal.graph.InputType InputType}). For an explanation of where it's warranted
 * to replace "old input" with "reduced input", see the inline comments in method
 * {@link EquationalReasoner#deverbosify(com.oracle.graal.graph.Node n) deverbosify(Node n)}
 * </p>
 *
 * <p>
 * The name {@link EquationalReasoner EquationalReasoner} was chosen because it conveys what it
 * does.
 * </p>
 */
public final class EquationalReasoner {

    private static final DebugMetric metricInstanceOfRemoved = Debug.metric("InstanceOfRemoved");
    private static final DebugMetric metricNullCheckRemoved = Debug.metric("NullCheckRemoved");
    private static final DebugMetric metricObjectEqualsRemoved = Debug.metric("ObjectEqualsRemoved");
    private static final DebugMetric metricEquationalReasoning = Debug.metric("EquationalReasoning");
    private static final DebugMetric metricDowncasting = Debug.metric("Downcasting");

    private final StructuredGraph graph;
    private final CanonicalizerTool tool;
    private final LogicConstantNode trueConstant;
    private final LogicConstantNode falseConstant;
    private final ConstantNode nullConstant;

    private State state;
    private NodeBitMap visited;

    /**
     * The reduction of a {@link com.oracle.graal.nodes.calc.FloatingNode} performed by
     * {@link EquationalReasoner EquationalReasoner} may result in a FloatingNode being added to the
     * graph. Those nodes aren't tracked in the {@link EquationalReasoner#visited visited}
     * {@link com.oracle.graal.graph.NodeBitMap NodeBitMap} but in this set instead (those nodes are
     * added after the {@link com.oracle.graal.graph.NodeBitMap} was obtained).
     */
    final Set<ValueNode> added = java.util.Collections.newSetFromMap(new IdentityHashMap<ValueNode, Boolean>());

    /**
     * The reduction of a FloatingNode performed by {@link EquationalReasoner EquationalReasoner}
     * may result in a FloatingNode being added to the graph. Those nodes are tracked in this map,
     * to avoid recomputing them.
     *
     * The substitutions tracked in this field become invalid as described in
     * {@link #updateState(com.oracle.graal.phases.common.cfs.State) updateState(State)}
     */
    private final IdentityHashMap<ValueNode, ValueNode> substs = new IdentityHashMap<>();

    public EquationalReasoner(StructuredGraph graph, CanonicalizerTool tool, LogicConstantNode trueConstant, LogicConstantNode falseConstant, ConstantNode nullConstant) {
        this.graph = graph;
        this.tool = tool;
        this.trueConstant = trueConstant;
        this.falseConstant = falseConstant;
        this.nullConstant = nullConstant;
    }

    /**
     * {@link #added} grows during a run of
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReductionPhase
     * FlowSensitiveReductionPhase}, and doesn't survive across runs.
     */
    public void forceState(State s) {
        state = s;
        substs.clear();
        added.clear();
        visited = null;
        versionNrAsofLastForce = s.versionNr;
    }

    /**
     * <p>
     * Gaining more precise type information about an SSA value doesn't "invalidate" as such any of
     * the substitutions tracked in {@link EquationalReasoner#substs substs}, at least not in the
     * sense of making the value tracked by one such entry "wrong". However, clearing the
     * {@link EquationalReasoner#substs substs} is still justified because next time they are
     * computed, the newly computed reduction could (in principle) be more effective (due to the
     * more precise type information).
     * </p>
     *
     * <p>
     * Between clearings of cached substitutions, it is expected they get applied a number of times
     * to justify the bookkeeping cost.
     * </p>
     *
     */
    public void updateState(State s) {
        assert s != null;
        if (state == null || state != s || state.versionNr != versionNrAsofLastForce) {
            forceState(s);
        }
    }

    private int versionNrAsofLastForce = 0;

    /**
     * Reduce the argument based on the state at the program point where the argument is consumed.
     * For most FixedNodes, that's how their inputs can be reduced. Two exceptions:
     * <ul>
     * <li>
     * the condition of a {@link com.oracle.graal.nodes.GuardingPiNode}, see
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction#visitGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode)}
     * </li>
     * <li>
     * the condition of a {@link com.oracle.graal.nodes.FixedGuardNode}, see
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction#visitFixedGuardNode(com.oracle.graal.nodes.FixedGuardNode)}
     * </li>
     *
     * </ul>
     *
     *
     * <p>
     * Part of the reduction work is delegated to baseCase-style reducers, whose contract explicitly
     * requires them not to deverbosify the argument's inputs --- the decision is made based on the
     * argument only (thus "base case"). Returning the unmodified argument is how a baseCase-style
     * tells this method to fall to the default case (for a floating node only: walk into the
     * argument's inputs, canonicalize followed by
     * {@link #rememberSubstitution(com.oracle.graal.nodes.ValueNode, com.oracle.graal.nodes.ValueNode)
     * rememberSubstitution()} if any input changed).
     * </p>
     *
     * <p>
     * This method must behave as a function (idempotent query method), ie refrain from mutating the
     * state other than updating caches:
     * <ul>
     * <li>{@link EquationalReasoner#added EquationalReasoner#added},</li>
     * <li>{@link EquationalReasoner#visited EquationalReasoner#visited} and</li>
     * <li>the cache updated via
     * {@link EquationalReasoner#rememberSubstitution(com.oracle.graal.nodes.ValueNode, com.oracle.graal.nodes.ValueNode)
     * EquationalReasoner#rememberSubstitution(ValueNode, FloatingNode)}.</li>
     * </ul>
     * </p>
     *
     * <p>
     * In turn, baseCase-style reducers are even more constrained: besides behaving as functions,
     * their contract prevents them from updating any caches (basically because they already grab
     * the answer from caches, if the answer isn't there they should just return their unmodified
     * argument).
     * </p>
     *
     * <p>
     * This method returns:
     * <ul>
     * <li>
     * the original argument, in case no reduction possible.</li>
     * <li>
     * a {@link com.oracle.graal.nodes.ValueNode ValueNode} different from the argument, in case the
     * conditions for a reduction were met. The node being returned might be already in the graph.
     * In any case it's canonicalized already, the caller need not perform that again.</li>
     * <li>
     * the unmodified argument, in case no reduction was made. Otherwise, a maximally reduced
     * {@link com.oracle.graal.nodes.ValueNode}.</li>
     * </ul>
     * </p>
     *
     * @see com.oracle.graal.phases.common.cfs.FlowSensitiveReduction#deverbosifyInputsInPlace(com.oracle.graal.nodes.ValueNode)
     *
     * @see com.oracle.graal.phases.common.cfs.BaseReduction.Tool
     *
     */
    public Node deverbosify(final Node n) {

        // --------------------------------------------------------------------
        // cases that don't initiate any call-chain that may enter this method
        // --------------------------------------------------------------------

        if (n == null) {
            return null;
        }
        assert !(n instanceof GuardNode) : "This phase not yet ready to run during MidTier";
        if (!(n instanceof ValueNode)) {
            return n;
        }
        ValueNode v = (ValueNode) n;
        if (v.stamp() instanceof IllegalStamp) {
            return v;
        }
        if (FlowUtil.isLiteralNode(v)) {
            return v;
        }
        ValueNode result = substs.get(v);
        if (result != null) {
            // picked cached substitution
            return result;
        }
        if ((visited != null && visited.contains(n)) || added.contains(v)) {
            return v;
        }

        // --------------------------------------------------------------------
        // stack overflow prevention via added, visited
        // --------------------------------------------------------------------

        if (visited == null) {
            visited = graph.createNodeBitMap();
        }
        visited.mark(n);

        /*
         * Past this point, if we ever want `n` to be deverbosified, it must be looked-up by one of
         * the cases above. One sure way to achieve that is with `rememberSubstitution(old, new)`
         */
        if (v instanceof ValueProxy) {
            return downcast(v);
        }

        if (n instanceof FloatingNode) {
            /*
             * `deverbosifyFloatingNode()` will drill down over floating inputs, when that not
             * possible anymore it resorts to calling `downcast()`. Thus it's ok to take the
             * `deverbosifyFloatingNode()` route first, as no downcasting opportunity will be
             * missed.
             */
            return deverbosifyFloatingNode((FloatingNode) n);
        }

        if (FlowUtil.hasLegalObjectStamp(v)) {
            return downcast(v);
        }

        return n;
    }

    /**
     * This method:
     *
     * <ul>
     * <li>
     * Recurses only over floating inputs to attempt reductions, leave anything else as is.</li>
     * <li>
     * Performs copy-on-write aka lazy-DAG-copying as described in source comments, in-line.</li>
     * <li>
     * Usage: must be called only from {@link #deverbosify(com.oracle.graal.graph.Node)
     * deverbosify(Node)}.</li>
     * </ul>
     */
    public Node deverbosifyFloatingNode(final FloatingNode n) {

        assert n != null : "Should have been caught in deverbosify()";
        assert !(n instanceof ValueProxy) : "Should have been caught in deverbosify()";
        assert !FlowUtil.isLiteralNode(n) : "Should have been caught in deverbosify()";

        if (n instanceof PhiNode) {
            /*
             * Each input to a PhiNode should be deverbosified with the state applicable to the path
             * providing such input, as done in visitAbstractEndNode()
             */
            return n;
        }

        final FloatingNode f = baseCaseFloating(n);
        if (f != n) {
            return f;
        }

        FloatingNode changed = null;
        for (ValueNode i : FlowUtil.distinctValueAndConditionInputs(f)) {
            /*
             * Although deverbosify() is invoked below, it's only for floating inputs. That way, the
             * state can't be used to make invalid conclusions.
             */
            Node j = (i instanceof FloatingNode) ? deverbosify(i) : i;
            if (i != j) {
                assert j != f;
                if (changed == null) {
                    changed = (FloatingNode) f.copyWithInputs();
                    added.add(changed);
                    // copyWithInputs() implies graph.unique(changed)
                    assert changed.isAlive();
                    assert FlowUtil.lacksUsages(changed);
                }
                /*
                 * Note: we don't trade i for j at each usage of i (doing so would change meaning)
                 * but only at those usages consumed by `changed`. In turn, `changed` won't replace
                 * `n` at arbitrary usages, but only where such substitution is valid as per the
                 * state holding there. In practice, this means the buck stops at the "root"
                 * FixedNode on whose inputs deverbosify() is invoked for the first time, via
                 * deverbosifyInputsInPlace().
                 */
                FlowUtil.replaceInPlace(changed, i, j);
            }
        }
        if (changed == null) {
            assert visited.contains(f) || added.contains(f);
            if (FlowUtil.hasLegalObjectStamp(f)) {
                /*
                 * No input has changed doesn't imply there's no witness to refine the
                 * floating-object value.
                 */
                ValueNode d = downcast(f);
                return d;
            } else {
                return f;
            }
        }
        FlowUtil.inferStampAndCheck(changed);
        added.add(changed);
        ValueNode canon = (ValueNode) changed.canonical(tool);
        // might be already in `added`, no problem adding it again.
        added.add(canon);
        rememberSubstitution(f, canon);
        return canon;
    }

    /**
     * In case of doubt (on whether a reduction actually triggered) it's always ok to invoke "
     * <code>rememberSubstitution(f, downcast(f))</code>": this method records a map entry only if
     * pre-image and image differ.
     *
     * @return the image of the substitution (ie, the second argument) unmodified.
     */
    private <M extends ValueNode> M rememberSubstitution(ValueNode from, M to) {
        assert from != null && to != null;
        if (from == to) {
            return to;
        }
        // we don't track literals because they map to themselves
        if (FlowUtil.isLiteralNode(from)) {
            assert from == to;
            return to;
        }
        /*
         * It's ok for different keys (which were not unique in the graph after all) to map to the
         * same value. However any given key can't map to different values.
         */
        ValueNode image = substs.get(from);
        if (image != null) {
            assert image == to;
            return to;
        }
        substs.put(from, to);
        return to;
    }

    /**
     * The contract for this baseCase-style method is covered in
     * {@link EquationalReasoner#deverbosify(com.oracle.graal.graph.Node)
     * EquationalReasoner#deverbosify()}
     *
     * @return a {@link com.oracle.graal.nodes.calc.FloatingNode} different from the argument, in
     *         case a reduction was made. The node being returned might be already in the graph. In
     *         any case it's canonicalized already, the caller need not perform that again. In case
     *         no reduction was made, this method returns the unmodified argument.
     */
    private FloatingNode baseCaseFloating(final FloatingNode f) {
        if (f instanceof LogicNode) {
            FloatingNode result = baseCaseLogicNode((LogicNode) f);
            return rememberSubstitution(f, result);
        }
        return f;
    }

    /**
     * <p>
     * Reduce the argument based on the state at the program point for it (ie, based on
     * "valid facts" only, without relying on any floating-guard-assumption).
     * </p>
     *
     * <p>
     * The inputs of the argument aren't traversed into, for that
     * {@link EquationalReasoner#deverbosify(com.oracle.graal.graph.Node)
     * EquationalReasoner#deverbosify()} should be used instead.
     * </p>
     * <p>
     * This method must behave as a function (idempotent query method): it should refrain from
     * changing the state, as well as from updating caches (other than DebugMetric-s).
     * </p>
     *
     * @return a {@link com.oracle.graal.nodes.LogicNode} different from the argument, in case a
     *         reduction was made. The node being returned might be already in the graph. In any
     *         case it's canonicalized already, the caller need not perform that again. In case no
     *         reduction was made, this method returns the unmodified argument.
     *
     */
    public FloatingNode baseCaseLogicNode(LogicNode condition) {
        assert condition != null;
        if (condition instanceof LogicConstantNode) {
            return condition;
        } else if (state.trueFacts.containsKey(condition)) {
            metricEquationalReasoning.increment();
            return trueConstant;
        } else if (state.falseFacts.containsKey(condition)) {
            metricEquationalReasoning.increment();
            return falseConstant;
        } else {
            if (condition instanceof InstanceOfNode) {
                return baseCaseInstanceOfNode((InstanceOfNode) condition);
            } else if (condition instanceof IsNullNode) {
                return baseCaseIsNullNode((IsNullNode) condition);
            } else if (condition instanceof ObjectEqualsNode) {
                return baseCaseObjectEqualsNode((ObjectEqualsNode) condition);
            }
        }
        return condition;
    }

    /**
     * Actually the same result delivered by this method could be obtained by just letting
     * {@link EquationalReasoner#deverbosify(com.oracle.graal.graph.Node)
     * EquationalReasoner#deverbosify()} handle the argument in the default case for floating nodes
     * (ie, deverbosify inputs followed by canonicalize). However it's done here for metrics
     * purposes.
     *
     * @return a {@link com.oracle.graal.nodes.LogicConstantNode}, in case a reduction was made;
     *         otherwise the unmodified argument.
     *
     */
    private LogicNode baseCaseInstanceOfNode(InstanceOfNode instanceOf) {
        ValueNode scrutinee = GraphUtil.unproxify(instanceOf.object());
        if (!FlowUtil.hasLegalObjectStamp(scrutinee)) {
            return instanceOf;
        }
        if (state.isNull(scrutinee)) {
            metricInstanceOfRemoved.increment();
            return falseConstant;
        } else if (state.isNonNull(scrutinee) && state.knownToConform(scrutinee, instanceOf.type())) {
            metricInstanceOfRemoved.increment();
            return trueConstant;
        }
        return instanceOf;
    }

    /**
     * @return a {@link com.oracle.graal.nodes.LogicConstantNode}, in case a reduction was
     *         performed; otherwise the unmodified argument.
     *
     */
    private FloatingNode baseCaseIsNullNode(IsNullNode isNull) {
        ValueNode object = isNull.object();
        if (!FlowUtil.hasLegalObjectStamp(object)) {
            return isNull;
        }
        ValueNode scrutinee = GraphUtil.unproxify(isNull.object());
        GuardingNode evidence = nonTrivialNullAnchor(scrutinee);
        if (evidence != null) {
            metricNullCheckRemoved.increment();
            return trueConstant;
        } else if (state.isNonNull(scrutinee)) {
            metricNullCheckRemoved.increment();
            return falseConstant;
        }
        return isNull;
    }

    /**
     * @return a {@link com.oracle.graal.nodes.LogicConstantNode}, in case a reduction was made;
     *         otherwise the unmodified argument.
     */
    private LogicNode baseCaseObjectEqualsNode(ObjectEqualsNode equals) {
        if (!FlowUtil.hasLegalObjectStamp(equals.x()) || !FlowUtil.hasLegalObjectStamp(equals.y())) {
            return equals;
        }
        ValueNode x = GraphUtil.unproxify(equals.x());
        ValueNode y = GraphUtil.unproxify(equals.y());
        if (state.isNull(x) && state.isNonNull(y) || state.isNonNull(x) && state.isNull(y)) {
            metricObjectEqualsRemoved.increment();
            return falseConstant;
        } else if (state.isNull(x) && state.isNull(y)) {
            metricObjectEqualsRemoved.increment();
            return trueConstant;
        }
        return equals;
    }

    /**
     * It's always ok to use "<code>downcast(object)</code>" instead of " <code>object</code>"
     * because this method re-wraps the argument in a {@link com.oracle.graal.nodes.PiNode} only if
     * the new stamp is strictly more refined than the original.
     *
     * <p>
     * This method does not
     * {@link #rememberSubstitution(com.oracle.graal.nodes.ValueNode, com.oracle.graal.nodes.ValueNode)}
     * .
     * </p>
     *
     * @return One of:
     *         <ul>
     *         <li>a {@link com.oracle.graal.nodes.PiNode} with more precise stamp than the input if
     *         the state warrants such downcasting</li>
     *         <li>a {@link com.oracle.graal.nodes.java.CheckCastNode CheckCastNode} for the same
     *         scrutinee in question</li>
     *         <li>the unmodified argument otherwise.</li>
     *         </ul>
     */
    ValueNode downcast(final ValueNode object) {

        // -------------------------------------------------
        // actions based only on the stamp of the input node
        // -------------------------------------------------

        if (!FlowUtil.hasLegalObjectStamp(object)) {
            return object;
        }
        if (FlowUtil.isLiteralNode(object)) {
            return object;
        }
        if (StampTool.isObjectAlwaysNull(object.stamp())) {
            return nonTrivialNull(object);
        }

        // ------------------------------------------
        // actions based on the stamp and the witness
        // ------------------------------------------

        ValueNode scrutinee = GraphUtil.unproxify(object);

        PiNode untrivialNull = nonTrivialNull(scrutinee);
        if (untrivialNull != null) {
            return untrivialNull;
        }

        Witness w = state.typeInfo(scrutinee);
        if (w == null) {
            // no additional hints being tracked for the scrutinee
            return object;
        }

        assert !w.clueless();

        ObjectStamp inputStamp = (ObjectStamp) object.stamp();
        ObjectStamp witnessStamp = w.asStamp();
        if (inputStamp.equals(witnessStamp) || !FlowUtil.isMorePrecise(witnessStamp, inputStamp)) {
            // the witness offers no additional precision over current one
            fixupTypeProfileStamp(object);
            return object;
        }

        assert !FlowUtil.isMorePrecise(inputStamp.type(), w.type());

        ValueNode result;
        if (object instanceof ValueProxy) {
            result = downcastValueProxy((ValueProxy) object, w);
        } else {
            result = downcastedUtil(object, w);
        }

        return result;
    }

    /**
     * TODO TypeProfileProxyNode.inferStamp doesn't infer non-null from non-null payload
     *
     * <p>
     * And there's a bunch of asserts in
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReductionPhase} that assert no
     * type-precision gets lost. Thus the need to fix-up on our own, as done here.
     * </p>
     */
    private static void fixupTypeProfileStamp(ValueNode object) {
        if (!(object instanceof TypeProfileProxyNode)) {
            return;
        }
        TypeProfileProxyNode profile = (TypeProfileProxyNode) object;
        ObjectStamp outgoinStamp = (ObjectStamp) profile.stamp();
        ObjectStamp payloadStamp = (ObjectStamp) profile.getObject().stamp();
        if (payloadStamp.nonNull() && !outgoinStamp.nonNull()) {
            profile.setStamp(FlowUtil.asNonNullStamp(outgoinStamp));
        }
    }

    /**
     * <p>
     * Porcelain method.
     * </p>
     *
     * <p>
     * Utility to create, add to the graph,
     * {@link EquationalReasoner#rememberSubstitution(com.oracle.graal.nodes.ValueNode, com.oracle.graal.nodes.ValueNode)}
     * , and return a {@link com.oracle.graal.nodes.PiNode} that narrows into the given stamp,
     * anchoring the payload.
     * </p>
     *
     * <p>
     * The resulting node might not have been in the graph already.
     * </p>
     */
    private PiNode wrapInPiNode(ValueNode payload, GuardingNode anchor, ObjectStamp newStamp, boolean remember) {
        try (Debug.Scope s = Debug.scope("Downcast", payload)) {
            assert payload != anchor : payload.graph().toString();
            metricDowncasting.increment();
            PiNode result = graph.unique(new PiNode(payload, newStamp, anchor.asNode()));
            // we've possibly got a new node in the graph --- bookkeeping is in order.
            added.add(result);
            if (remember) {
                rememberSubstitution(payload, result);
            }
            Debug.log("Downcasting from %s to %s", payload, result);
            return result;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
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
        return state.knownNull.get(GraphUtil.unproxify(object));
    }

    /**
     *
     * This method returns:
     * <ul>
     * <li><b>null</b>, if the argument is known null due to its stamp. Otherwise,</li>
     * <li><b>a PiNode</b> wrapping the null constant and an anchor offering evidence as to why the
     * argument is known null, if such anchor is available. Otherwise,</li>
     * <li><b>null</b></li>
     * </ul>
     * <p>
     * This method does not
     * {@link #rememberSubstitution(com.oracle.graal.nodes.ValueNode, com.oracle.graal.nodes.ValueNode)}
     * .
     * </p>
     */
    public PiNode nonTrivialNull(ValueNode object) {
        assert FlowUtil.hasLegalObjectStamp(object);
        GuardingNode anchor = nonTrivialNullAnchor(object);
        if (anchor == null) {
            return null;
        }
        if (object instanceof GuardedNode && StampTool.isObjectAlwaysNull(object.stamp())) {
            return (PiNode) object;
        }
        // notice nullConstant is wrapped, not object
        PiNode result = wrapInPiNode(nullConstant, anchor, (ObjectStamp) StampFactory.alwaysNull(), false);
        return result;
    }

    // @formatter:off
    /**
     * <p>ValueProxys can be classified along two dimensions,
     * in addition to the fixed-floating dichotomy.</p>
     *
     * <p>
     *     First, we might be interested in separating those ValueProxys
     *     that are entitled to change (usually narrow) their stamp from those that aren't.
     *     In the first category are:
     *       PiNode, PiArrayNode, GuardingPiNode,
     *       CheckCastNode, UnsafeCastNode, and
     *       GuardedValueNode.
     * </p>
     *
     * <p>
     *     A note on stamp-narrowing ValueProxys:
     *     our state abstraction tracks only the type refinements induced by CheckCastNode and GuardingPiNode
     *     (which are fixed nodes, unlike the other stamp-narrowing ValueProxys;
     *     the reason being that the state abstraction can be updated only at fixed nodes).
     *     As a result, the witness for a (PiNode, PiArrayNode, UnsafeCastNode, or GuardedValueNode)
     *     may be less precise than the proxy's stamp. We don't want to lose such precision,
     *     thus <code>downcast(proxy) == proxy</code> in such cases.
     * </p>
     *
     * <p>
     *     The second classification focuses on
     *     the additional information that travels with the proxy
     *     (in addition to its "payload", ie getOriginalValue(), and any narrowing-stamp).
     *     Such additional information boils down to:
     *
     *   (a) type profile (TypeProfileProxyNode)
     *   (b) type profile (CheckCastNode)
     *   (c) anchor (GuardedValueNode)
     *   (d) anchor (PiNode)
     *   (e) anchor and array length (PiArrayNode)
     *   (f) optional anchor (UnsafeCastNode)
     *   (g) deopt-condition (GuardingPiNode)
     *   (h) LocationIdentity (MemoryProxyNOde)
     *   (i) control-flow dependency (FixedValueAnchorNode)
     *   (j) proxyPoint (ProxyNode -- think loops)
     *</p>
     */
    // @formatter:on
    private ValueNode downcastValueProxy(ValueProxy proxy, Witness w) {
        assert FlowUtil.hasLegalObjectStamp((ValueNode) proxy);
        assert FlowUtil.hasLegalObjectStamp((proxy).getOriginalNode());
        assert GraphUtil.unproxify((ValueNode) proxy) == GraphUtil.unproxify(proxy.getOriginalNode());

        assert GraphUtil.unproxify((ValueNode) proxy) == GraphUtil.unproxify((proxy).getOriginalNode());

        if (proxy instanceof PiNode) {
            return downcastPiNodeOrPiArrayNode((PiNode) proxy, w);
        } else if (proxy instanceof GuardingPiNode) {
            return downcastGuardingPiNode((GuardingPiNode) proxy, w);
        } else if (proxy instanceof TypeProfileProxyNode) {
            return downcastTypeProfileProxyNode((TypeProfileProxyNode) proxy);
        } else if (proxy instanceof CheckCastNode) {
            return downcastCheckCastNode((CheckCastNode) proxy, w);
        } else if (proxy instanceof ProxyNode || proxy instanceof GuardedValueNode) {
            // TODO scaladacapo return downcastedUtil((ValueNode) proxy, w);
            return (ValueNode) proxy;
        }

        assert false : "TODO case not yet handled";

        // TODO complete the missing implementation for the cases not yet handled

        return ((ValueNode) proxy);
    }

    /**
     * <p>
     * Why would we want to downcast a GuardingPiNode? Is it even possible? Like, for example, a
     * GuardingPiNode originating in the lowering of a CheckCastNode (carried out by
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction#visitCheckCastNode(com.oracle.graal.nodes.java.CheckCastNode)
     * visitCheckCastNode()}).
     * </p>
     *
     * <p>
     * It's both possible and desirable. Example: <code>
     *         Number n = (Number) o;
     *         if (n instanceof Integer) {
     *            return n.intValue();
     *         }
     *     </code>
     *
     * The receiver of intValue() is a usage of a previous checkCast, for which the current witness
     * provides a more refined type (and an anchor). In this case, the advantage of downcasting a
     * GuardingPiNode is clear: devirtualizing the `intValue()` callsite.
     * </p>
     *
     * @see #downcastValueProxy
     */
    public ValueNode downcastGuardingPiNode(GuardingPiNode envelope, Witness w) {
        assert envelope != w.guard().asNode() : "The stamp of " + envelope + " would lead to downcasting with that very same GuardingPiNode as guard.";
        return downcastedUtil(envelope, w);
    }

    /**
     * <p>
     * This method accepts both {@link com.oracle.graal.nodes.PiNode} and
     * {@link com.oracle.graal.nodes.PiArrayNode} argument.
     * </p>
     *
     * <p>
     * In case a witness reveals a strictly more precise type than the
     * {@link com.oracle.graal.nodes.PiNode}'s stamp, this method wraps the argument in a new
     * {@link com.oracle.graal.nodes.PiNode} with updated stamp, and returns it.
     * </p>
     *
     * <p>
     * A {@link com.oracle.graal.nodes.PiArrayNode} argument ends up wrapped in a
     * {@link com.oracle.graal.nodes.PiNode}. Thus, the
     * {@link com.oracle.graal.nodes.PiArrayNode#length} information doesn't get lost.
     * </p>
     *
     * <p>
     * Note: {@link com.oracle.graal.nodes.PiNode}'s semantics allow un-packing its payload as soon
     * as it type conforms to that of the {@link com.oracle.graal.nodes.PiNode} (that's what
     * {@link com.oracle.graal.nodes.PiNode#canonical(com.oracle.graal.graph.spi.CanonicalizerTool)
     * PiNode.canonical()} does). Not clear the benefits of duplicating that logic here.
     * </p>
     *
     * @see #downcastValueProxy
     */
    private ValueNode downcastPiNodeOrPiArrayNode(PiNode envelope, Witness w) {
        return downcastedUtil(envelope, w);
    }

    /**
     * <p>
     * In a case the payload of the {@link com.oracle.graal.nodes.TypeProfileProxyNode} can be
     * downcasted, this method returns a copy-on-write version with the downcasted payload.
     * </p>
     *
     * <p>
     * Otherwise returns the unmodified argument.
     * </p>
     *
     * @see #downcastValueProxy
     */
    private ValueNode downcastTypeProfileProxyNode(TypeProfileProxyNode envelope) {
        ValueNode payload = envelope.getOriginalNode();
        ValueNode d = downcast(payload);
        if (payload != d) {
            TypeProfileProxyNode changed = (TypeProfileProxyNode) envelope.copyWithInputs();
            added.add(changed);
            // copyWithInputs() implies graph.unique(changed)
            FlowUtil.replaceInPlace(changed, payload, d);
            FlowUtil.inferStampAndCheck(changed);
            fixupTypeProfileStamp(changed);
            /*
             * It's not prudent to (1) obtain the canonical() of the (changed) TypeProfileProxyNode
             * to (2) replace its usages; because we're potentially walking a DAG (after all,
             * TypeProfileProxyNode is a floating-node). Those steps, which admittedly are needed,
             * are better performed upon replacing in-place the inputs of a FixedNode, or during
             * Canonicalize.
             */
            return changed;
        }
        fixupTypeProfileStamp(envelope);
        return envelope;
    }

    /**
     * <p>
     * Re-wrap the checkCast in a type-refining {@link com.oracle.graal.nodes.PiNode PiNode} only if
     * the downcasted scrutinee does not conform to the checkCast's target-type.
     * </p>
     */
    private ValueNode downcastCheckCastNode(CheckCastNode checkCast, Witness w) {

        final ResolvedJavaType toType = checkCast.type();

        if (checkCast.object() instanceof CheckCastNode) {
            ValueNode innerMost = checkCast;
            while (innerMost instanceof CheckCastNode) {
                innerMost = ((CheckCastNode) innerMost).object();
            }
            ValueNode deepest = downcast(innerMost);
            ResolvedJavaType deepestType = ((ObjectStamp) deepest.stamp()).type();
            if ((deepestType != null && deepestType.equals(toType)) || FlowUtil.isMorePrecise(deepestType, toType)) {
                assert !w.knowsBetterThan(deepest);
                return deepest;
            }
        }

        ValueNode subject = downcast(checkCast.object());
        ObjectStamp subjectStamp = (ObjectStamp) subject.stamp();
        ResolvedJavaType subjectType = subjectStamp.type();

        if (subjectType != null && toType.isAssignableFrom(subjectType)) {
            assert !w.knowsBetterThan(subject);
            return subject;
        }

        return downcastedUtil(checkCast, w);
    }

    /**
     * <p>
     * Porcelain method.
     * </p>
     *
     * <p>
     * This method wraps the argument in a new {@link com.oracle.graal.nodes.PiNode PiNode} (created
     * to hold an updated stamp) provided the argument's stamp can be strictly refined, and returns
     * it.
     * </p>
     */
    private ValueNode downcastedUtil(ValueNode subject, Witness w) {

        ObjectStamp originalStamp = (ObjectStamp) subject.stamp();
        ObjectStamp outgoingStamp = originalStamp;

        if (w.isNonNull() && !outgoingStamp.nonNull()) {
            outgoingStamp = FlowUtil.asNonNullStamp(outgoingStamp);
        }
        if (FlowUtil.isMorePrecise(w.type(), outgoingStamp.type())) {
            outgoingStamp = FlowUtil.asRefinedStamp(outgoingStamp, w.type());
        }

        if (outgoingStamp != originalStamp) {
            assert FlowUtil.isMorePrecise(outgoingStamp, originalStamp);

            boolean isWitnessGuardAnAliasForScrutinee = false;
            if (w.guard() instanceof GuardingPiNode || w.guard() instanceof PiNode) {
                /*
                 * The guard offered by the witness canonicalizes into its subject (a possibly
                 * type-refined scrutinee) provided its subject conforms as per stamp.
                 */
                if (w.guard().asNode().stamp().equals(outgoingStamp)) {
                    isWitnessGuardAnAliasForScrutinee = true;
                }
            }

            ValueNode result;
            if (isWitnessGuardAnAliasForScrutinee) {
                result = w.guard().asNode();
                assert !w.knowsBetterThan(result);
                return result; // TODO this works. explain why.
            } else {
                result = wrapInPiNode(subject, w.guard(), outgoingStamp, true);
                assert !w.knowsBetterThan(result);
                return result;
            }

        } else {
            assert !w.knowsBetterThan(subject);
            return subject;
        }
    }

}

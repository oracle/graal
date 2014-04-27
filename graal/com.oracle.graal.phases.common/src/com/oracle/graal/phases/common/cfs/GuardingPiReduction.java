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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.phases.tiers.PhaseContext;

/**
 * <p>
 * This class implements control-flow sensitive reductions for
 * {@link com.oracle.graal.nodes.GuardingPiNode}.
 * </p>
 * 
 * @see #visitGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode)
 */
public abstract class GuardingPiReduction extends BaseReduction {

    public GuardingPiReduction(FixedNode start, State initialState, PhaseContext context) {
        super(start, initialState, context);
    }

    /**
     * <p>
     * By the time a {@link com.oracle.graal.nodes.GuardingPiNode GuardingPiNode} is visited, the
     * available type refinements may allow reductions similar to those performed for
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction#visitCheckCastNode(com.oracle.graal.nodes.java.CheckCastNode)
     * CheckCastNode}.
     * </p>
     * 
     * <ol>
     * <li>
     * If the condition needs no reduction (ie, it's already a
     * {@link com.oracle.graal.nodes.LogicConstantNode LogicConstantNode}), this method basically
     * gives up (thus letting other phases take care of it).</li>
     * <li>
     * Otherwise, an attempt is made to find a {@link com.oracle.graal.nodes.extended.GuardingNode}
     * that implies the combination of (negated, condition) of the
     * {@link com.oracle.graal.nodes.GuardingPiNode} being visited. Details in
     * {@link #tryRemoveGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode)}. If found, the node
     * can be removed.</li>
     * <li>
     * Otherwise, the node is lowered to a {@link com.oracle.graal.nodes.FixedGuardNode} and its
     * usages replaced with {@link com.oracle.graal.nodes.PiNode}. Details in
     * {@link #visitGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode)}.</li>
     * </ol>
     * 
     * <p>
     * Precondition: the condition hasn't been deverbosified yet.
     * </p>
     * 
     */
    protected final void visitGuardingPiNode(GuardingPiNode envelope) {

        if (!FlowUtil.hasLegalObjectStamp(envelope)) {
            // this situation exercised by com.oracle.graal.jtt.optimize.NCE_FlowSensitive02
            return;
        }
        if (!FlowUtil.hasLegalObjectStamp(envelope.object())) {
            return;
        }

        /*
         * (1 of 3) Cover the case of GuardingPiNode(LogicConstantNode, ...)
         */

        if (envelope.condition() instanceof LogicConstantNode) {
            if (FlowUtil.alwaysFails(envelope.isNegated(), envelope.condition())) {
                state.impossiblePath();
                // let GuardingPiNode(false).canonical() prune the dead-code control-path
                return;
            }
            // if not always-fails and condition-constant, then it always-succeeds!
            assert FlowUtil.alwaysSucceeds(envelope.isNegated(), envelope.condition());
            // let GuardingPiNode(true).canonical() replaceAtUsages
            return;
        }

        /*
         * The trick used in visitFixedGuardNode to look up an equivalent GuardingNode for the
         * combination of (negated, condition) at hand doesn't work for GuardingPiNode, because the
         * condition showing up here (a ShortCircuitOrNode that can be detected by
         * CastCheckExtractor) doesn't appear as key in trueFacts, falseFacts. Good thing we have
         * CastCheckExtractor!
         */

        /*
         * (2 of 3) Cover the case of the condition known-to-be-false or known-to-be-true, but not
         * LogicConstantNode.
         * 
         * If deverbosify(condition) == falseConstant, it would be safe to set:
         * `envelope.setCondition(falseConstant)` (only the API won't allow).
         * 
         * On the other hand, it's totally unsafe to do something like that for trueConstant. What
         * we can do about that case is the province of `tryRemoveGuardingPiNode(envelope)`
         */

        if (tryRemoveGuardingPiNode(envelope)) {
            return;
        }

        /*
         * Experience has shown that an attempt to eliminate the current GuardingPiNode by using a
         * GuardingNode already in scope and with equivalent condition (grabbed from `trueFacts`
         * resp. `falseFacts`) proves futile. Therefore we're not even attempting that here.
         */

        /*
         * (3 of 3) Neither always-succeeds nor always-fails, ie we don't known. Converting to
         * FixedGuardNode allows tracking the condition via a GuardingNode, thus potentially
         * triggering simplifications down the road.
         */
        FixedGuardNode fixedGuard = graph.add(new FixedGuardNode(envelope.condition(), envelope.getReason(), envelope.getAction(), envelope.isNegated()));
        graph.addBeforeFixed(envelope, fixedGuard);

        if (!FlowUtil.lacksUsages(envelope)) {
            // not calling wrapInPiNode() because we don't want to rememberSubstitution()
            PiNode replacement = graph.unique(new PiNode(envelope.object(), envelope.stamp(), fixedGuard));
            reasoner.added.add(replacement);
            // before removing the GuardingPiNode replace its usages
            envelope.replaceAtUsages(replacement);
        }

        graph.removeFixed(envelope);

        state.addFact(!fixedGuard.isNegated(), fixedGuard.condition(), fixedGuard);

    }

    /**
     * <p>
     * Based on flow-sensitive knowledge, two pre-requisites have to be fulfilled in order to remove
     * a {@link com.oracle.graal.nodes.GuardingPiNode}:
     * 
     * <ul>
     * <li>the condition must refer only to the payload of the
     * {@link com.oracle.graal.nodes.GuardingPiNode}</li>
     * <li>the condition must check properties about which the state tracks not only a true/false
     * answer, but also an anchor witnessing that fact</li>
     * <li>the condition may not check anything else beyond what's stated in the items above.</li>
     * </ul>
     * </p>
     * 
     * <p>
     * Provided a condition as above can be reduced to a constant (and an anchor obtained in the
     * process), this method replaces all usages of the
     * {@link com.oracle.graal.nodes.GuardingPiNode} (necessarily of
     * {@link com.oracle.graal.graph.InputType#Value}) with a {@link com.oracle.graal.nodes.PiNode}
     * that wraps the payload and the anchor in question.
     * </p>
     * 
     * <p>
     * Precondition: the condition hasn't been deverbosified yet.
     * </p>
     * 
     * @see #visitGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode)
     * 
     */
    private boolean tryRemoveGuardingPiNode(GuardingPiNode envelope) {

        LogicNode cond = envelope.condition();
        ValueNode payload = envelope.object();

        ObjectStamp outgoingStamp = (ObjectStamp) envelope.stamp();
        ObjectStamp payloadStamp = (ObjectStamp) payload.stamp();

        if (isNullCheckOn(cond, payload)) {
            if (envelope.isNegated()) {
                /*
                 * GuardingPiNode succeeds if payload non-null
                 */
                if (!outgoingStamp.equals(FlowUtil.asNonNullStamp(payloadStamp))) {
                    warnAboutOutOfTheBlueGuardingPiNode(envelope);
                }
                return tryRemoveGuardingPiNodeNonNullCond(envelope);
            } else {
                /*
                 * GuardingPiNode succeeds if payload null
                 */
                ValueNode replacement = StampTool.isObjectAlwaysNull(payload) ? payload : reasoner.untrivialNull(payload);
                if (replacement != null) {
                    // replacement == null means !isKnownNull(payload)
                    removeGuardingPiNode(envelope, replacement);
                    return true;
                }
                return false;
            }
        } else if (CastCheckExtractor.isInstanceOfCheckOn(cond, payload)) {
            if (envelope.isNegated()) {
                return false;
            }
            /*
             * GuardingPiNode succeeds if payload instanceof <something>
             */
            InstanceOfNode io = (InstanceOfNode) cond;
            assert io.type() != null;
            Witness w = state.typeInfo(payload);
            if (w != null && w.isNonNull() && isEqualOrMorePrecise(w.type(), io.type())) {
                ValueNode d = reasoner.downcasted(payload);
                removeGuardingPiNode(envelope, d);
                return true;
            }
            return false;
        } else if (cond instanceof ShortCircuitOrNode) {
            if (envelope.isNegated()) {
                return false;
            }
            CastCheckExtractor cce = CastCheckExtractor.extract(cond);
            if (cce == null || cce.subject != payload) {
                return false;
            }
            /*
             * GuardingPiNode succeeds if payload check-casts toType
             */
            return tryRemoveGuardingPiNodeCheckCastCond(envelope, cce.type);
        }

        return false;
    }

    /**
     * Porcelain method.
     * 
     * This method handles the case where the GuardingPiNode succeeds if payload known to be
     * non-null.
     * 
     * @see #tryRemoveGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode)
     */
    private boolean tryRemoveGuardingPiNodeNonNullCond(GuardingPiNode envelope) {

        ValueNode payload = envelope.object();

        if (state.isNull(payload)) {
            // the GuardingPiNode fails always
            postponedDeopts.addDeoptBefore(envelope, envelope.getReason());
            state.impossiblePath();
            return true;
        }

        if (StampTool.isObjectNonNull(payload)) {
            // payload needs no downcasting, it satisfies as-is the GuardingPiNode's condition.
            if (precisionLoss(envelope, payload)) {
                /*
                 * TODO The GuardingPiNode has an outgoing stamp whose narrowing goes beyond what
                 * the condition checks. That's suspicious.
                 */
                PiNode replacement = graph.unique(new PiNode(payload, envelope.stamp()));
                reasoner.added.add(replacement);
                removeGuardingPiNode(envelope, replacement);
                return true;
            } else {
                removeGuardingPiNode(envelope, payload);
                return true;
            }
        }
        // if a non-null witness available, the GuardingPiNode can be removed

        Witness w = state.typeInfo(payload);
        GuardingNode nonNullAnchor = (w != null && w.isNonNull()) ? w.guard() : null;
        if (nonNullAnchor != null) {
            PiNode replacement = graph.unique(new PiNode(payload, envelope.stamp(), nonNullAnchor.asNode()));
            reasoner.added.add(replacement);
            removeGuardingPiNode(envelope, replacement);
            return true;
        }

        /*
         * TODO What about, nodes that always denote non-null values? (Even though their stamp
         * forgot to make that clear) Candidates: ObjectGetClassNode, Parameter(0) on instance
         */

        return false;
    }

    /**
     * Porcelain method.
     * 
     * This method handles the case where the GuardingPiNode succeeds if payload null or its actual
     * type equal or subtype of `toType`
     * 
     * @see #tryRemoveGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode)
     * 
     */
    private boolean tryRemoveGuardingPiNodeCheckCastCond(GuardingPiNode envelope, ResolvedJavaType toType) {
        assert toType != null;
        ValueNode payload = envelope.object();

        ObjectStamp outgoingStamp = (ObjectStamp) envelope.stamp();
        ObjectStamp payloadStamp = (ObjectStamp) payload.stamp();

        if (!outgoingStamp.equals(FlowUtil.asRefinedStamp(payloadStamp, toType))) {
            warnAboutOutOfTheBlueGuardingPiNode(envelope);
        }

        ValueNode d = reasoner.downcasted(payload);
        if (d == null) {
            return false;
        }

        if (StampTool.isObjectAlwaysNull(d)) {
            removeGuardingPiNode(envelope, d);
            return true;
        }
        ObjectStamp dStamp = (ObjectStamp) d.stamp();
        if (isEqualOrMorePrecise(dStamp.type(), toType)) {
            removeGuardingPiNode(envelope, d);
            return true;
        }
        return false;
    }

    /*
     * TODO There should be an assert in GuardingPiNode to detect that as soon as it happens
     * (constructor, setStamp).
     */
    private static void warnAboutOutOfTheBlueGuardingPiNode(GuardingPiNode envelope) {
        Debug.log(String.format("GuardingPiNode has an outgoing stamp whose narrowing goes beyond what its condition checks: %s", envelope));
    }

    private static boolean isNullCheckOn(LogicNode cond, ValueNode subject) {
        if (!(cond instanceof IsNullNode)) {
            return false;
        }
        IsNullNode isNull = (IsNullNode) cond;
        return isNull.object() == subject;
    }

    /**
     * Porcelain method.
     */
    private void removeGuardingPiNode(GuardingPiNode envelope, ValueNode replacement) {
        assert !precisionLoss(envelope, replacement);
        metricGuardingPiNodeRemoved.increment();
        envelope.replaceAtUsages(replacement);
        assert FlowUtil.lacksUsages(envelope);
        graph.removeFixed(envelope);
    }

    public static boolean isEqualOrMorePrecise(ResolvedJavaType a, ResolvedJavaType b) {
        return a.equals(b) || FlowUtil.isMorePrecise(a, b);
    }

    public static boolean isEqualOrMorePrecise(ObjectStamp a, ObjectStamp b) {
        return a.equals(b) || FlowUtil.isMorePrecise(a, b);
    }

}

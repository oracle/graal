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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.phases.tiers.PhaseContext;

import static com.oracle.graal.api.meta.DeoptimizationAction.InvalidateReprofile;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;

/**
 * <p>
 * This class implements control-flow sensitive reductions for
 * {@link com.oracle.graal.nodes.java.CheckCastNode}.
 * </p>
 *
 * @see #visitCheckCastNode(com.oracle.graal.nodes.java.CheckCastNode)
 * */
public abstract class CheckCastReduction extends GuardingPiReduction {

    public CheckCastReduction(FixedNode start, State initialState, PhaseContext context) {
        super(start, initialState, context);
    }

    /**
     * <p>
     * This phase is able to refine the types of reference-values at use sites provided a
     * {@link com.oracle.graal.nodes.extended.GuardingNode GuardingNode} is available witnessing
     * that fact.
     * </p>
     *
     * <p>
     * This method turns non-redundant {@link com.oracle.graal.nodes.java.CheckCastNode}s into
     * {@link com.oracle.graal.nodes.GuardingPiNode}s. Once such lowering has been performed (during
     * run N of this phase) follow-up runs attempt to further simplify the resulting node, see
     * {@link EquationalReasoner#downcastedGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode, Witness)}
     * and {@link #visitGuardingPiNode(com.oracle.graal.nodes.GuardingPiNode)}
     * </p>
     *
     * <p>
     * Precondition: the inputs (ie, object) hasn't been deverbosified yet.
     * </p>
     * */
    protected final void visitCheckCastNode(CheckCastNode checkCast) {

        /*
         * checkCast.object() hasn't been deverbosified yet.
         */

        if (!FlowUtil.hasLegalObjectStamp(checkCast)) {
            // This situation is exercised by test Class_cast01
            return;
        }

        final ValueNode subject = checkCast.object();
        final ResolvedJavaType toType = checkCast.type();
        ObjectStamp subjectStamp = (ObjectStamp) subject.stamp();
        ResolvedJavaType subjectType = subjectStamp.type();

        // --------- checkCast deemed redundant by subject-stamp alone ---------
        // --------- in particular due to stamp informing always null ----------
        boolean isRedundantPerStamp = StampTool.isObjectAlwaysNull(subject) || (subjectType != null && toType.isAssignableFrom(subjectType));
        if (isRedundantPerStamp) {
            metricCheckCastRemoved.increment();
            checkCast.replaceAtUsages(subject);
            graph.removeFixed(checkCast);
            return;
        }

        assert !StampTool.isObjectAlwaysNull(subject) : "Null as per stamp subjects should have been handled above";

        // --------- checkCast deemed unsatisfiable by subject-stamp alone ---------
        if (state.knownNotToConform(subject, toType)) {
            postponedDeopts.addDeoptBefore(checkCast, checkCast.isForStoreCheck() ? ArrayStoreException : ClassCastException);
            state.impossiblePath();
            // let FixedGuardNode(false).simplify() prune the dead-code control-path
            return;
        }

        /*
         * Remark: subject may be TypeProxyNode, GuardedValueNode, ProxyNode, GuardingPiNode, among
         * others.
         */

        PiNode untrivialNull = reasoner.untrivialNull(subject);
        if (untrivialNull != null) {
            metricCheckCastRemoved.increment();
            checkCast.replaceAtUsages(untrivialNull);
            graph.removeFixed(checkCast);
            return;
        }

        Witness w = state.typeInfo(subject);

        if (w == null) {
            /*
             * If there's no witness, attempting `downcasted(subject)` is futile.
             */
            visitCheckCastNodeLackingWitness(checkCast);
            return;
        }

        visitCheckCastNodeWithWitness(checkCast);

    }

    /**
     * Given that no witness is available for the {@link com.oracle.graal.nodes.java.CheckCastNode}
     * 's subject there's no point in downcasting such subject, ie no
     * {@link com.oracle.graal.nodes.PiNode} can be fabricated for the subject.
     *
     * @see #lowerCheckCastAnchorFriendlyWay(com.oracle.graal.nodes.java.CheckCastNode,
     *      com.oracle.graal.nodes.ValueNode)
     *
     * */
    private void visitCheckCastNodeLackingWitness(CheckCastNode checkCast) {
        final ValueNode subject = checkCast.object();
        final ResolvedJavaType toType = checkCast.type();
        if (toType.isInterface()) {
            return;
        }
        assert reasoner.downcasted(subject) == subject;
        lowerCheckCastAnchorFriendlyWay(checkCast, subject);
    }

    /**
     * Porcelain method.
     *
     * <p>
     * Rather than tracking the CheckCastNode via {@link com.oracle.graal.phases.common.cfs.State
     * State} (doing so woud add a special case because a
     * {@link com.oracle.graal.nodes.java.CheckCastNode} isn't a
     * {@link com.oracle.graal.nodes.extended.GuardingNode}) this method creates an anchor by
     * lowering the CheckCastNode into a FixedGuardNode. Not the same way as done by
     * {@link com.oracle.graal.nodes.java.CheckCastNode#lower(com.oracle.graal.nodes.spi.LoweringTool)}
     * which lowers into a {@link com.oracle.graal.nodes.GuardingPiNode} (which is not a
     * {@link com.oracle.graal.nodes.extended.GuardingNode}).
     * </p>
     *
     * <p>
     * With that, state tracking can proceed as usual.
     * </p>
     *
     * @see #visitCheckCastNode(com.oracle.graal.nodes.java.CheckCastNode)
     *
     * */
    public void lowerCheckCastAnchorFriendlyWay(CheckCastNode checkCast, ValueNode subject) {
        ValueNode originalCheckCastObject = checkCast.object();

        ObjectStamp subjectStamp = (ObjectStamp) subject.stamp();
        final ResolvedJavaType toType = checkCast.type();
        ObjectStamp resultStamp = (ObjectStamp) StampFactory.declared(toType);
        JavaTypeProfile profile = checkCast.profile();

        assert FlowUtil.isLegalObjectStamp(subjectStamp);
        assert subjectStamp.type() == null || !toType.isAssignableFrom(subjectStamp.type()) : "No need to lower in an anchor-friendly way in the first place";

        /*
         * Depending on what is known about the subject:
         * 
         * (a) definitely-non-null
         * 
         * (b) null-not-seen-in-profiling
         * 
         * (c) runtime-null-check-needed
         * 
         * the condition (of the cast-guard to be emitted) and the stamp (of the PiNode to be
         * emitted) are going to be different. Each of the three branches below deals with one of
         * the cases above.
         */
        LogicNode condition;
        if (subjectStamp.nonNull()) {
            /*
             * (1 of 3) definitely-non-null
             */
            // addWithoutUnique for the same reason as in CheckCastNode.lower()
            condition = graph.addWithoutUnique(new InstanceOfNode(toType, subject, profile));
            reasoner.added.add(condition);
            resultStamp = FlowUtil.asNonNullStamp(resultStamp);
            // TODO fix in CheckCastNode.lower()
        } else {
            if (profile != null && profile.getNullSeen() == ProfilingInfo.TriState.FALSE) {
                /*
                 * (2 of 3) null-not-seen-in-profiling
                 */
                IsNullNode isNN = graph.unique(new IsNullNode(subject));
                reasoner.added.add(isNN);
                FixedGuardNode nullCheck = graph.add(new FixedGuardNode(isNN, UnreachedCode, InvalidateReprofile, true));
                graph.addBeforeFixed(checkCast, nullCheck);
                // not calling wrapInPiNode() because we don't want to rememberSubstitution()
                PiNode nonNullGuarded = graph.unique(new PiNode(subject, FlowUtil.asNonNullStamp(subjectStamp), nullCheck));
                reasoner.added.add(nonNullGuarded);
                // addWithoutUnique for the same reason as in CheckCastNode.lower()
                condition = graph.addWithoutUnique(new InstanceOfNode(toType, nonNullGuarded, profile));
                reasoner.added.add(condition);
                resultStamp = FlowUtil.asNonNullStamp(resultStamp);
            } else {
                /*
                 * (3 of 3) runtime-null-check-needed
                 */
                // addWithoutUnique for the same reason as in CheckCastNode.lower()
                InstanceOfNode typeTest = graph.addWithoutUnique(new InstanceOfNode(toType, subject, profile));
                reasoner.added.add(typeTest);
                LogicNode nullTest = graph.unique(new IsNullNode(subject));
                reasoner.added.add(nullTest);
                // TODO (ds) replace with probability of null-seen when available
                final double shortCircuitProbability = NOT_FREQUENT_PROBABILITY;
                condition = LogicNode.or(nullTest, typeTest, shortCircuitProbability);
                reasoner.added.add(condition);
            }
        }

        /*
         * Add a cast-guard (checking only what needs to be checked) and a PiNode (to be used in
         * place of the CheckCastNode).
         */
        FixedGuardNode castGuard = graph.add(new FixedGuardNode(condition, checkCast.isForStoreCheck() ? ArrayStoreException : ClassCastException, InvalidateReprofile));
        graph.addBeforeFixed(checkCast, castGuard);

        assert FlowUtil.isLegalObjectStamp(resultStamp);
        Witness w = state.typeInfo(subject);
        assert !isTypeOfWitnessBetter(w, resultStamp);

        if (!FlowUtil.lacksUsages(checkCast)) {
            // not calling wrapInPiNode() because we don't want to rememberSubstitution()
            PiNode checkedObject = graph.unique(new PiNode(subject, resultStamp, castGuard));
            reasoner.added.add(checkedObject);
            assert !precisionLoss(originalCheckCastObject, checkedObject);
            assert !precisionLoss(subject, checkedObject);
            checkCast.replaceAtUsages(checkedObject);
        }

        graph.removeFixed(checkCast);

        if (resultStamp.nonNull()) {
            state.trackIO(subject, toType, castGuard);
        } else {
            state.trackCC(subject, toType, castGuard);
        }
    }

    /**
     * Porcelain method.
     * */
    public static boolean isTypeOfWitnessBetter(Witness w, ObjectStamp stamp) {
        if (w == null) {
            return false;
        }
        return FlowUtil.isMorePrecise(w.type(), stamp.type());
    }

    /**
     *
     * Please note in this method "subject" refers to the downcasted input to the checkCast.
     *
     * @see #visitCheckCastNode(com.oracle.graal.nodes.java.CheckCastNode)
     * */
    private void visitCheckCastNodeWithWitness(CheckCastNode checkCast) {

        final ResolvedJavaType toType = checkCast.type();

        ValueNode subject;
        if (checkCast.object() instanceof CheckCastNode) {
            subject = reasoner.downcasted(checkCast);
            if (subject == checkCast) {
                subject = reasoner.downcasted(checkCast.object());
            }
        } else {
            subject = reasoner.downcasted(checkCast.object());
        }

        ObjectStamp subjectStamp = (ObjectStamp) subject.stamp();
        ResolvedJavaType subjectType = subjectStamp.type();

        // TODO move this check to downcasted()
        assert !precisionLoss(checkCast.object(), subject);

        /*
         * At this point, two sources of (partial) information: the witness and the stamp of
         * subject. The latter might be more precise than the witness (eg, subject might be
         * GuardedValueNode)
         */

        // --------- checkCast made redundant by downcasting its input ---------
        if (subjectType != null && toType.isAssignableFrom(subjectType)) {
            checkCast.replaceAtUsages(subject);
            graph.removeFixed(checkCast);
            return;
        }

        /*
         * At this point, `downcasted()` might or might not have delivered a more precise value. If
         * more precise, it wasn't precise enough to conform to `toType`. Even so, for the
         * `toType.isInterface()` case (dealt with below) we'll replace the checkCast's input with
         * that value (its class-stamp being more precise than the original).
         */

        if (toType.isInterface()) {
            boolean wasDowncasted = (subject != checkCast.object());
            if (wasDowncasted) {
                FlowUtil.replaceInPlace(checkCast, checkCast.object(), subject);
            }
            return;
        }

        lowerCheckCastAnchorFriendlyWay(checkCast, subject);

    }

}

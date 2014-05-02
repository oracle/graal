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

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.tiers.PhaseContext;

/**
 * <p>
 * This class implements control-flow sensitive reductions for
 * {@link com.oracle.graal.nodes.FixedGuardNode}.
 * </p>
 *
 * <p>
 * The laundry-list of all flow-sensitive reductions is summarized in
 * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction}
 * </p>
 * 
 * @see #visitFixedGuardNode(com.oracle.graal.nodes.FixedGuardNode)
 */
public abstract class FixedGuardReduction extends CheckCastReduction {

    public FixedGuardReduction(FixedNode start, State initialState, PhaseContext context) {
        super(start, initialState, context);
    }

    /**
     * <p>
     * Upon visiting a {@link com.oracle.graal.nodes.FixedGuardNode}, based on flow-sensitive
     * conditions, we need to determine whether:
     * <ul>
     * <li>it is redundant (in which case it should be simplified), or</li>
     * <li>flow-sensitive information can be gained from it.</li>
     * </ul>
     * </p>
     * 
     * <p>
     * This method realizes the above by inspecting the
     * {@link com.oracle.graal.nodes.FixedGuardNode}'s condition:
     * <ol>
     * <li>a constant condition signals the node won't be reduced here</li>
     * <li>the outcome of the condition can be predicted:</li>
     * <ul>
     * <li>
     * "always succeeds", after finding an equivalent (or stronger)
     * {@link com.oracle.graal.nodes.extended.GuardingNode} in scope. The
     * {@link com.oracle.graal.nodes.FixedGuardNode} is removed after replacing its usages with the
     * existing guarding node</li>
     * <li>
     * "always fails", which warrants making that explicit by making the condition constant, see
     * {@link #markFixedGuardNodeAlwaysFails(com.oracle.graal.nodes.FixedGuardNode)}</li>
     * </ul>
     * <li>otherwise the condition is tracked flow-sensitively</li>
     * </ol>
     * </p>
     * 
     * <p>
     * Precondition: the condition hasn't been deverbosified yet.
     * </p>
     */
    protected final void visitFixedGuardNode(FixedGuardNode f) {

        /*
         * A FixedGuardNode with LogicConstantNode condition is left untouched.
         * `FixedGuardNode.simplify()` will eventually remove the FixedGuardNode (in case it
         * "always succeeds") or kill code ("always fails").
         */

        if (f.condition() instanceof LogicConstantNode) {
            if (FlowUtil.alwaysFails(f.isNegated(), f.condition())) {
                state.impossiblePath();
                // let FixedGuardNode(false).simplify() prune the dead-code control-path
                return;
            }
            assert FlowUtil.alwaysSucceeds(f.isNegated(), f.condition());
            return;
        }

        /*
         * Attempt to eliminate the current FixedGuardNode by using another GuardingNode already in
         * scope and with equivalent condition.
         */

        GuardingNode existingGuard = f.isNegated() ? state.falseFacts.get(f.condition()) : state.trueFacts.get(f.condition());
        if (existingGuard != null) {
            // assert existingGuard instanceof FixedGuardNode;
            metricFixedGuardNodeRemoved.increment();
            f.replaceAtUsages(existingGuard.asNode());
            graph.removeFixed(f);
            return;
        }

        final LogicNode cond = f.condition();
        final boolean isTrue = !f.isNegated();

        /*
         * A FixedGuardNode can only be removed provided a replacement anchor is found (so called
         * "evidence"), ie an anchor that amounts to the same combination of (negated, condition) as
         * for the FixedGuardNode at hand. Just deverbosifying the condition in place isn't
         * semantics-preserving.
         */

        // TODO what about isDependencyTainted

        if (cond instanceof IsNullNode) {
            final IsNullNode isNullNode = (IsNullNode) cond;
            if (isTrue) {
                // grab an anchor attesting nullness
                final GuardingNode replacement = reasoner.nonTrivialNullAnchor(isNullNode.object());
                if (replacement != null) {
                    removeFixedGuardNode(f, replacement);
                    return;
                }
                if (state.isNonNull(isNullNode.object())) {
                    markFixedGuardNodeAlwaysFails(f);
                    return;
                }
                // can't produce evidence, fall-through to addFact
            } else {
                // grab an anchor attesting non-nullness
                final Witness w = state.typeInfo(isNullNode.object());
                if (w != null && w.isNonNull()) {
                    removeFixedGuardNode(f, w.guard());
                    return;
                }
                if (state.isNull(isNullNode.object())) {
                    markFixedGuardNodeAlwaysFails(f);
                    return;
                }
                // can't produce evidence, fall-through to addFact
            }
        } else if (cond instanceof InstanceOfNode) {
            final InstanceOfNode iOf = (InstanceOfNode) cond;
            final Witness w = state.typeInfo(iOf.object());
            if (isTrue) {
                // grab an anchor attesting instanceof
                if (w != null) {
                    if (w.isNonNull() && w.type() != null) {
                        if (iOf.type().isAssignableFrom(w.type())) {
                            removeFixedGuardNode(f, w.guard());
                            return;
                        }
                        if (State.knownNotToConform(w.type(), iOf.type())) {
                            markFixedGuardNodeAlwaysFails(f);
                            return;
                        }
                    }
                }
                if (state.isNull(iOf.object())) {
                    markFixedGuardNodeAlwaysFails(f);
                    return;
                }
                // can't produce evidence, fall-through to addFact
            } else {
                // grab an anchor attesting not-instanceof
                // (1 of 2) attempt determining nullness
                final GuardingNode nullGuard = reasoner.nonTrivialNullAnchor(iOf.object());
                if (nullGuard != null) {
                    removeFixedGuardNode(f, nullGuard);
                    return;
                }
                // (2 of 2) attempt determining known-not-to-conform
                if (w != null && !w.cluelessAboutType()) {
                    if (State.knownNotToConform(w.type(), iOf.type())) {
                        removeFixedGuardNode(f, w.guard());
                        return;
                    }
                }
                // can't produce evidence, fall-through to addFact
            }
        } else if (isTrue && cond instanceof ShortCircuitOrNode) {
            CastCheckExtractor cce = CastCheckExtractor.extract(cond);
            if (cce != null && !State.isDependencyTainted(cce.subject, f)) {
                // grab an anchor attesting check-cast
                Witness w = state.typeInfo(cce.subject);
                if (w != null && w.type() != null) {
                    if (cce.type.isAssignableFrom(w.type())) {
                        removeFixedGuardNode(f, w.guard());
                        return;
                    }
                    if (State.knownNotToConform(w.type(), cce.type)) {
                        markFixedGuardNodeAlwaysFails(f);
                        return;
                    }
                }
            }
            // can't produce evidence, fall-through to addFact
        }

        state.addFact(isTrue, cond, f);
    }

    /**
     * Porcelain method.
     */
    private void markFixedGuardNodeAlwaysFails(FixedGuardNode f) {
        metricFixedGuardNodeRemoved.increment();
        state.impossiblePath();
        f.setCondition(f.isNegated() ? trueConstant : falseConstant);
        // `f.condition()` if unused will be removed in finished()
    }

    /**
     * Porcelain method.
     * 
     * <p>
     * The `replacement` guard must be such that it implies the `old` guard. Moreover, rhe
     * `replacement` guard must be in scope.
     * </p>
     */
    private void removeFixedGuardNode(FixedGuardNode old, GuardingNode replacement) {
        if (replacement == null) {
            return;
        }
        metricFixedGuardNodeRemoved.increment();
        old.replaceAtUsages(replacement.asNode());
        graph.removeFixed(old);
        // `old.condition()` if unused will be removed in finished()
    }

}

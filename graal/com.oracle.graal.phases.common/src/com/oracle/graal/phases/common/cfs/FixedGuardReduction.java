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
 * @see #visitFixedGuardNode(com.oracle.graal.nodes.FixedGuardNode)
 * */
public abstract class FixedGuardReduction extends CheckCastReduction {

    public FixedGuardReduction(FixedNode start, State initialState, PhaseContext context) {
        super(start, initialState, context);
    }

    /**
     * In case the condition is constant,
     * {@link com.oracle.graal.nodes.FixedGuardNode#simplify(com.oracle.graal.graph.spi.SimplifierTool)
     * FixedGuardNode#simplify(SimplifierTool)} will eventually remove the
     * {@link com.oracle.graal.nodes.FixedGuardNode} ("always succeeds") or kill the code that
     * should be killed ("always fails").
     * 
     * <p>
     * The only thing we do here is tracking as true fact (from this program point onwards) the
     * condition of the {@link com.oracle.graal.nodes.FixedGuardNode FixedGuardNode}.
     * </p>
     * 
     * <p>
     * Precondition: the condition hasn't been deverbosified yet.
     * </p>
     */
    protected final void visitFixedGuardNode(FixedGuardNode f) {

        /*
         * A FixedGuardNode with LogicConstantNode condition is left untouched.
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
         * FixedGuardNode requires handling similar to that of GuardingPiNode, (ie the condition
         * can't simply be deverbosified in place). A replacement anchor is needed, ie an anchor
         * that amounts to the same combination of (negated, condition) for the FixedGuardNode at
         * hand.
         */

        // TODO what about isDependencyTainted

        if (cond instanceof IsNullNode) {
            final IsNullNode isNullNode = (IsNullNode) cond;
            if (isTrue) {
                // grab an anchor attesting nullness
                final GuardingNode replacement = reasoner.untrivialNullAnchor(isNullNode.object());
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
                final GuardingNode nullGuard = reasoner.untrivialNullAnchor(iOf.object());
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
     * */
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
     * The `replacement` guard must be such that it implies the `old` guard.
     * </p>
     * */
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

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
import com.oracle.graal.nodes.extended.GuardingNode;
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

    public FixedGuardReduction(StartNode start, State initialState, PhaseContext context) {
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

        final boolean isTrue = !f.isNegated();
        final Evidence evidence = state.outcome(isTrue, f.condition());

        // can't produce evidence, must be information gain
        if (evidence == null) {
            state.addFact(isTrue, f.condition(), f);
            return;
        }

        if (evidence.isPositive()) {
            /*
             * A FixedGuardNode can only be removed provided a replacement anchor is found (so
             * called "evidence"), ie an anchor that amounts to the same combination of (negated,
             * condition) as for the FixedGuardNode at hand. Just deverbosifying the condition in
             * place isn't semantics-preserving.
             * 
             * Eliminate the current FixedGuardNode by using another GuardingNode already in scope,
             * a GuardingNode that guards a condition that is at least as strong as that of the
             * FixedGuardNode.
             */
            removeFixedGuardNode(f, evidence.success);
            return;
        }

        assert evidence.isNegative();
        markFixedGuardNodeAlwaysFails(f);

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
        assert replacement != null;
        metricFixedGuardNodeRemoved.increment();
        old.replaceAtUsages(replacement.asNode());
        graph.removeFixed(old);
        // `old.condition()` if unused will be removed in finished()
    }

}

/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.types.*;
import com.oracle.graal.nodes.type.*;

/**
 * A guard is a node that deoptimizes based on a conditional expression. Guards are not attached to a certain frame
 * state, they can move around freely and will always use the correct frame state when the nodes are scheduled (i.e.,
 * the last emitted frame state). The node that is guarded has a data dependency on the guard and the guard in turn has
 * a data dependency on the condition. A guard may only be executed if it is guaranteed that the guarded node is
 * executed too (if no exceptions are thrown). Therefore, an {@linkplain AnchorNode anchor} is placed after a control
 * flow split and the guard has a data dependency to the anchor. The anchor is the most distant node that is
 * post-dominated by the guarded node and the guard can be scheduled anywhere between those two nodes. This ensures
 * maximum flexibility for the guard node and guarantees that deoptimization occurs only if the control flow would have
 * reached the guarded node (without taking exceptions into account).
 */
public final class GuardNode extends FloatingNode implements Canonicalizable, LIRLowerable, TypeFeedbackProvider, Node.IterableNodeType, Negatable {

    @Input private BooleanNode condition;
    @Input(notDataflow = true) private FixedNode anchor;
    private final DeoptimizationReason reason;
    private final CiDeoptAction action;
    private boolean negated;
    private final long leafGraphId;

    public FixedNode anchor() {
        return anchor;
    }

    public void setAnchor(FixedNode x) {
        updateUsages(anchor, x);
        anchor = x;
    }

    /**
     * The instruction that produces the tested boolean value.
     */
    public BooleanNode condition() {
        return condition;
    }

    public void setCondition(BooleanNode x) {
        updateUsages(condition, x);
        condition = x;
    }

    public boolean negated() {
        return negated;
    }

    public DeoptimizationReason reason() {
        return reason;
    }

    public CiDeoptAction action() {
        return action;
    }

    public GuardNode(BooleanNode condition, FixedNode anchor, DeoptimizationReason reason, CiDeoptAction action, boolean negated, long leafGraphId) {
        super(StampFactory.dependency());
        this.condition = condition;
        this.anchor = anchor;
        this.reason = reason;
        this.action = action;
        this.negated = negated;
        this.leafGraphId = leafGraphId;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name && negated) {
            return "!" + super.toString(verbosity);
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitGuardCheck(condition(), reason(), action(), negated(), leafGraphId);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (condition() instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) condition();
            if (c.asConstant().asBoolean() != negated) {
                if (!dependencies().isEmpty()) {
                    for (Node usage : usages()) {
                        if (usage instanceof ValueNode) {
                            ((ValueNode) usage).dependencies().addAll(dependencies());
                        }
                    }
                }
                this.replaceAtUsages(null);
                return null;
            }
        }
        return this;
    }

    @Override
    public void typeFeedback(TypeFeedbackTool tool) {
        if (condition instanceof ConditionalTypeFeedbackProvider) {
            ((ConditionalTypeFeedbackProvider) condition).typeFeedback(tool);
        }
    }

    @Override
    public void negate() {
        negated = !negated;
    }
}

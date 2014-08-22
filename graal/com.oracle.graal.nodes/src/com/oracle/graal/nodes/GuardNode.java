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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.extended.*;

/**
 * A guard is a node that deoptimizes based on a conditional expression. Guards are not attached to
 * a certain frame state, they can move around freely and will always use the correct frame state
 * when the nodes are scheduled (i.e., the last emitted frame state). The node that is guarded has a
 * data dependency on the guard and the guard in turn has a data dependency on the condition. A
 * guard may only be executed if it is guaranteed that the guarded node is executed too (if no
 * exceptions are thrown). Therefore, an anchor is placed after a control flow split and the guard
 * has a data dependency to the anchor. The anchor is the most distant node that is post-dominated
 * by the guarded node and the guard can be scheduled anywhere between those two nodes. This ensures
 * maximum flexibility for the guard node and guarantees that deoptimization occurs only if the
 * control flow would have reached the guarded node (without taking exceptions into account).
 */
@NodeInfo(nameTemplate = "Guard(!={p#negated}) {p#reason/s}", allowedUsageTypes = {InputType.Guard})
public class GuardNode extends FloatingAnchoredNode implements Canonicalizable, IterableNodeType, GuardingNode {

    @Input(InputType.Condition) LogicNode condition;
    private final DeoptimizationReason reason;
    private Constant speculation;
    private DeoptimizationAction action;
    private boolean negated;

    public static GuardNode create(LogicNode condition, AnchoringNode anchor, DeoptimizationReason reason, DeoptimizationAction action, boolean negated, Constant speculation) {
        return new GuardNodeGen(condition, anchor, reason, action, negated, speculation);
    }

    protected GuardNode(LogicNode condition, AnchoringNode anchor, DeoptimizationReason reason, DeoptimizationAction action, boolean negated, Constant speculation) {
        super(StampFactory.forVoid(), anchor);
        this.condition = condition;
        this.reason = reason;
        this.action = action;
        this.negated = negated;
        this.speculation = speculation;
    }

    /**
     * The instruction that produces the tested boolean value.
     */
    public LogicNode condition() {
        return condition;
    }

    public boolean negated() {
        return negated;
    }

    public DeoptimizationReason reason() {
        return reason;
    }

    public DeoptimizationAction action() {
        return action;
    }

    public Constant getSpeculation() {
        return speculation;
    }

    public void setSpeculation(Constant speculation) {
        this.speculation = speculation;
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
    public Node canonical(CanonicalizerTool tool) {
        if (condition() instanceof LogicNegationNode) {
            LogicNegationNode negation = (LogicNegationNode) condition();
            return GuardNode.create(negation.getValue(), getAnchor(), reason, action, !negated, speculation);
        }
        if (condition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition();
            if (c.getValue() != negated) {
                return null;
            }
        }
        return this;
    }

    public FixedWithNextNode lowerGuard() {
        return null;
    }

    public void negate() {
        negated = !negated;
    }

    public void setAction(DeoptimizationAction invalidaterecompile) {
        this.action = invalidaterecompile;
    }
}

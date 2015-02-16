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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(nameTemplate = "FixedGuard(!={p#negated}) {p#reason/s}", allowedUsageTypes = {InputType.Guard})
public final class FixedGuardNode extends AbstractFixedGuardNode implements Lowerable, IterableNodeType {
    public static final NodeClass TYPE = NodeClass.get(FixedGuardNode.class);

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action) {
        this(condition, deoptReason, action, false);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated) {
        super(TYPE, condition, deoptReason, action, negated);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        super.simplify(tool);

        if (condition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition();
            if (c.getValue() == isNegated()) {
                FixedNode currentNext = this.next();
                if (currentNext != null) {
                    tool.deleteBranch(currentNext);
                }

                DeoptimizeNode deopt = graph().add(new DeoptimizeNode(getAction(), getReason()));
                deopt.setStateBefore(stateBefore());
                setNext(deopt);
            }
            this.replaceAtUsages(null);
            graph().removeFixed(this);
        } else if (condition() instanceof ShortCircuitOrNode) {
            ShortCircuitOrNode shortCircuitOr = (ShortCircuitOrNode) condition();
            if (isNegated() && hasNoUsages()) {
                graph().addAfterFixed(this, graph().add(new FixedGuardNode(shortCircuitOr.getY(), getReason(), getAction(), !shortCircuitOr.isYNegated())));
                graph().replaceFixedWithFixed(this, graph().add(new FixedGuardNode(shortCircuitOr.getX(), getReason(), getAction(), !shortCircuitOr.isXNegated())));
            }
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().allowsFloatingGuards()) {
            /*
             * Don't allow guards with action None and reason RuntimeConstraint to float. In cases
             * where 2 guards are testing equivalent conditions they might be lowered at the same
             * location. If the guard with the None action is lowered before the the other guard
             * then the code will be stuck repeatedly deoptimizing without invalidating the code.
             * Conditional elimination will eliminate the guard if it's truly redundant in this
             * case.
             */
            if (getAction() != DeoptimizationAction.None || getReason() != DeoptimizationReason.RuntimeConstraint) {
                ValueNode guard = tool.createGuard(this, condition(), getReason(), getAction(), isNegated()).asNode();
                this.replaceAtUsages(guard);
                ValueAnchorNode newAnchor = graph().add(new ValueAnchorNode(guard.asNode()));
                graph().replaceFixedWithFixed(this, newAnchor);
            }
        } else {
            lowerToIf().lower(tool);
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}

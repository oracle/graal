/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(nameTemplate = "ConditionAnchor(!={p#negated})", allowedUsageTypes = {InputType.Guard})
public class ConditionAnchorNode extends FixedWithNextNode implements Canonicalizable.Unary<Node>, Lowerable, GuardingNode {

    @Input(InputType.Condition) LogicNode condition;
    protected boolean negated;

    public static ConditionAnchorNode create(LogicNode condition) {
        return USE_GENERATED_NODES ? new ConditionAnchorNodeGen(condition) : new ConditionAnchorNode(condition);
    }

    protected ConditionAnchorNode(LogicNode condition) {
        this(condition, false);
    }

    public static ConditionAnchorNode create(LogicNode condition, boolean negated) {
        return USE_GENERATED_NODES ? new ConditionAnchorNodeGen(condition, negated) : new ConditionAnchorNode(condition, negated);
    }

    protected ConditionAnchorNode(LogicNode condition, boolean negated) {
        super(StampFactory.forVoid());
        this.negated = negated;
        this.condition = condition;
    }

    public LogicNode condition() {
        return condition;
    }

    public boolean isNegated() {
        return negated;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name && negated) {
            return "!" + super.toString(verbosity);
        } else {
            return super.toString(verbosity);
        }
    }

    public Node canonical(CanonicalizerTool tool, Node forValue) {
        if (condition instanceof LogicNegationNode) {
            LogicNegationNode negation = (LogicNegationNode) condition;
            return ConditionAnchorNode.create(negation.getValue(), !negated);
        }
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            if (c.getValue() != negated) {
                return null;
            } else {
                return ValueAnchorNode.create(null);
            }
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            ValueAnchorNode newAnchor = graph().add(ValueAnchorNode.create(null));
            graph().replaceFixedWithFixed(this, newAnchor);
        }
    }

    public Node getValue() {
        return condition;
    }
}

/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.TriState;

@NodeInfo
public abstract class BinaryOpLogicNode extends LIRLowerableLogicNode implements Canonicalizable.Binary<ValueNode> {

    public static final NodeClass<BinaryOpLogicNode> TYPE = NodeClass.create(BinaryOpLogicNode.class);
    @Input protected ValueNode x;
    @Input protected ValueNode y;

    @Override
    public ValueNode getX() {
        return x;
    }

    @Override
    public ValueNode getY() {
        return y;
    }

    public void setX(ValueNode newX) {
        assert newX != null;
        updateUsages(x, newX);
        this.x = newX;
    }

    public void setY(ValueNode newY) {
        assert newY != null;
        updateUsages(y, newY);
        this.y = newY;
    }

    public BinaryOpLogicNode(NodeClass<? extends BinaryOpLogicNode> c, ValueNode x, ValueNode y) {
        super(c);
        assert x != null;
        assert y != null;
        this.x = x;
        this.y = y;
    }

    /**
     * Ensure a canonical ordering of inputs for commutative nodes to improve GVN results. Order the
     * inputs by increasing {@link Node#id} and call {@link Graph#findDuplicate(Node)} on the node
     * if it's currently in a graph.
     *
     * @return the original node or another node with the same inputs, ignoring ordering.
     */
    @SuppressWarnings("deprecation")
    public LogicNode maybeCommuteInputs() {
        assert this instanceof BinaryCommutative : Assertions.errorMessageContext("this", this);
        if (!y.isConstant() && (x.isConstant() || x.getId() > y.getId())) {
            ValueNode tmp = x;
            x = y;
            y = tmp;
            if (graph() != null) {
                // See if this node already exists
                LogicNode duplicate = graph().findDuplicate(this);
                if (duplicate != null) {
                    return duplicate;
                }
            }
        }
        return this;
    }

    public abstract Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp);

    public abstract Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp);

    public abstract TriState tryFold(Stamp xStamp, Stamp yStamp);

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        // fold conditions of the form forX op forY where forX=(c ? condTrueVal : condFalseVal)
        // for example for integer test of (c ? 0 : 1 ) & 1 == 0) to c
        if (forX instanceof ConditionalNode conditional) {
            final ValueNode condTrueVal = conditional.trueValue();
            final ValueNode condFalseVal = conditional.falseValue();
            // evaluate the conditional true and false value against the forY of this condition, if
            // they are both known, i.e., both evaluate to a clear result use the input of the
            // conditional in its respective form instead
            final TriState trueValCond = tryFold(condTrueVal.stamp(NodeView.DEFAULT), forY.stamp(NodeView.DEFAULT));
            final TriState falseValCond = tryFold(condFalseVal.stamp(NodeView.DEFAULT), forY.stamp(NodeView.DEFAULT));
            if (trueValCond.isUnknown() || falseValCond.isUnknown()) {
                return this;
            }
            if (trueValCond == falseValCond) {
                return LogicConstantNode.forBoolean(trueValCond.toBoolean());
            } else {
                return trueValCond.toBoolean() ? conditional.condition() : LogicNegationNode.create(conditional.condition());
            }
        }
        return this;
    }
}

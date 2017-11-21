/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Add;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "+")
public class AddNode extends BinaryArithmeticNode<Add> implements NarrowableArithmeticNode, BinaryCommutative<ValueNode> {

    public static final NodeClass<AddNode> TYPE = NodeClass.create(AddNode.class);

    public AddNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected AddNode(NodeClass<? extends AddNode> c, ValueNode x, ValueNode y) {
        super(c, ArithmeticOpTable::getAdd, x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<Add> op = ArithmeticOpTable.forStamp(x.stamp(view)).getAdd();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        if (x.isConstant() && !y.isConstant()) {
            return canonical(null, op, y, x, view);
        } else {
            return canonical(null, op, x, y, view);
        }
    }

    private static boolean isLoopIncrement(AddNode self) {
        assert self != null;
        if (self.getX() instanceof PhiNode && ((PhiNode) self.getX()).isLoopPhi()) {
            return true;
        }
        if (self.getY() instanceof PhiNode && ((PhiNode) self.getY()).isLoopPhi()) {
            return true;
        }
        return false;
    }

    private static ValueNode canonical(AddNode addNode, BinaryOp<Add> op, ValueNode forX, ValueNode forY, NodeView view) {
        AddNode self = addNode;
        boolean associative = op.isAssociative();
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (op.isNeutral(c)) {
                return forX;
            }
            if (associative && self != null) {
                // canonicalize expressions like "(a + 1) + 2"
                ValueNode reassociated = reassociate(self, ValueNode.isConstantPredicate(), forX, forY, view);
                if (reassociated != self) {
                    return reassociated;
                }
            }
        }
        if (associative) {
            if (forX instanceof SubNode) {
                SubNode sub = (SubNode) forX;
                if (sub.getY() == forY) {
                    // (a - b) + b
                    return sub.getX();
                }
            }
            if (forY instanceof SubNode) {
                SubNode sub = (SubNode) forY;
                if (sub.getY() == forX) {
                    // b + (a - b)
                    return sub.getX();
                }
            }
            /* Attempt to reassociate to push down constants.
             * We must check that self != null because
             * 1. If this add has not yet been created and as of the form (x + C1) + C2,
             * the constant reassociate() call has not yet happened and so we would infinitely recurse.
             * 2. If this node is created as part of a reassociate() call, aggressively reassociating here might
             * undo the intended results.
             * Additionally, we must check this add isn't a loop induction variable,
             * since reassociation can break loop analysis
             */
            if (self != null && forX instanceof AddNode && !isLoopIncrement(self)) {
                AddNode add = (AddNode) forX;
                if (add.getY().isConstant() && !forY.isConstant()) {
                    // (x + C) + y -> (x + y) + C
                    ValueNode left = BinaryArithmeticNode.add(add.getX(), forY, view);
                    return BinaryArithmeticNode.add(left, add.getY(), view);
                }
            }
            if (self != null && forY instanceof AddNode && !isLoopIncrement(self)) {
                AddNode add = (AddNode) forY;
                if (add.getY().isConstant()) {
                    // x + (y + C) -> (x + y) + C
                    ValueNode left = BinaryArithmeticNode.add(forX, add.getX(), view);
                    return BinaryArithmeticNode.add(left, add.getY(), view);
                }
            }
        }
        if (forX instanceof NegateNode) {
            return BinaryArithmeticNode.sub(forY, ((NegateNode) forX).getValue(), view);
        } else if (forY instanceof NegateNode) {
            return BinaryArithmeticNode.sub(forX, ((NegateNode) forY).getValue(), view);
        }
        if (self == null) {
            self = (AddNode) new AddNode(forX, forY).maybeCommuteInputs();
        }
        return self;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (forX.isConstant() && !forY.isConstant()) {
            // we try to swap and canonicalize
            ValueNode improvement = canonical(tool, forY, forX);
            if (improvement != this) {
                return improvement;
            }
            // if this fails we only swap
            return new AddNode(forY, forX);
        }
        BinaryOp<Add> op = getOp(forX, forY);
        NodeView view = NodeView.from(tool);
        return canonical(this, op, forX, forY, view);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        Value op1 = nodeValueMap.operand(getX());
        assert op1 != null : getX() + ", this=" + this;
        Value op2 = nodeValueMap.operand(getY());
        if (shouldSwapInputs(nodeValueMap)) {
            Value tmp = op1;
            op1 = op2;
            op2 = tmp;
        }
        nodeValueMap.setResult(this, gen.emitAdd(op1, op2, false));
    }
}

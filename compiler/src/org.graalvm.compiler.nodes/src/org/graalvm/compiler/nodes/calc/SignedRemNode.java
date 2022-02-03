/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;

@NodeInfo(shortName = "%")
public class SignedRemNode extends IntegerDivRemNode implements LIRLowerable {

    public static final NodeClass<SignedRemNode> TYPE = NodeClass.create(SignedRemNode.class);

    public SignedRemNode(ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        this(TYPE, x, y, zeroCheck);
    }

    protected SignedRemNode(NodeClass<? extends SignedRemNode> c, ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        super(c, IntegerStamp.OPS.getRem().foldStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT)), Op.REM, Type.SIGNED, x, y, zeroCheck);
    }

    public static ValueNode create(ValueNode x, ValueNode y, GuardingNode zeroCheck, NodeView view) {
        Stamp stamp = IntegerStamp.OPS.getRem().foldStamp(x.stamp(view), y.stamp(view));
        return canonical(null, x, y, zeroCheck, stamp, view, null);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(IntegerStamp.OPS.getRem().foldStamp(getX().stamp(NodeView.DEFAULT), getY().stamp(NodeView.DEFAULT)));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        return canonical(this, forX, forY, getZeroCheck(), stamp(view), view, tool);
    }

    /**
     * This is used as a hook to allow "safe" SignedRemNodes to be created during canonicalization.
     */
    protected SignedRemNode createWithInputs(ValueNode forX, ValueNode forY, GuardingNode forZeroCheck) {
        return new SignedRemNode(forX, forY, forZeroCheck);
    }

    private static ValueNode canonical(SignedRemNode self, ValueNode forX, ValueNode forY, GuardingNode zeroCheck, Stamp stamp, NodeView view, CanonicalizerTool tool) {
        if (forX.isConstant() && forY.isConstant()) {
            long y = forY.asJavaConstant().asLong();
            if (y == 0) {
                /* This will trap, cannot canonicalize. */
                return self != null ? self : new SignedRemNode(forX, forY, zeroCheck);
            }
            return ConstantNode.forIntegerStamp(stamp, forX.asJavaConstant().asLong() % y);
        } else if (forY.isConstant() && forX.stamp(view) instanceof IntegerStamp && forY.stamp(view) instanceof IntegerStamp) {
            long constY = forY.asJavaConstant().asLong();
            IntegerStamp xStamp = (IntegerStamp) forX.stamp(view);
            IntegerStamp yStamp = (IntegerStamp) forY.stamp(view);
            if (constY < 0 && constY != CodeUtil.minValue(yStamp.getBits())) {
                Stamp newStamp = IntegerStamp.OPS.getRem().foldStamp(forX.stamp(view), forY.stamp(view));
                return canonical(self, forX, ConstantNode.forIntegerStamp(yStamp, -constY), zeroCheck, newStamp, view, tool);
            }

            if (constY == 1) {
                return ConstantNode.forIntegerStamp(stamp, 0);
            } else if (CodeUtil.isPowerOf2(constY) && tool != null && tool.allUsagesAvailable()) {
                if (allUsagesCompareAgainstZero(self)) {
                    // x % y == 0 <=> (x & (y-1)) == 0
                    return new AndNode(forX, ConstantNode.forIntegerStamp(yStamp, constY - 1));
                } else {
                    if (xStamp.isPositive()) {
                        // x & (y - 1)
                        return new AndNode(forX, ConstantNode.forIntegerStamp(stamp, constY - 1));
                    } else if (xStamp.isNegative()) {
                        // -((-x) & (y - 1))
                        return new NegateNode(new AndNode(new NegateNode(forX), ConstantNode.forIntegerStamp(stamp, constY - 1)));
                    }
                }
            }
        }
        if (self != null && self.hasNoUsages() && self.next() instanceof SignedDivNode) {
            SignedDivNode div = (SignedDivNode) self.next();
            if (div.x == self.x && div.y == self.y && div.getZeroCheck() == self.getZeroCheck() && div.stateBefore() == self.stateBefore()) {
                // left over from canonicalizing ((a - a % b) / b) into (a / b)
                return null;
            }
        }
        if (self != null && self.x == forX && self.y == forY) {
            return self;
        } else {
            if (self != null) {
                return self.createWithInputs(forX, forY, zeroCheck);
            }
            return new SignedRemNode(forX, forY, zeroCheck);
        }
    }

    private static boolean allUsagesCompareAgainstZero(SignedRemNode self) {
        if (self == null) {
            // If the node was not yet created, then we do not know its usages yet.
            return false;
        }

        for (Node usage : self.usages()) {
            if (usage instanceof IntegerEqualsNode) {
                IntegerEqualsNode equalsNode = (IntegerEqualsNode) usage;
                ValueNode node = equalsNode.getY();
                if (node == self) {
                    node = equalsNode.getX();
                }
                if (node instanceof ConstantNode) {
                    ConstantNode constantNode = (ConstantNode) node;
                    Constant constant = constantNode.asConstant();
                    if (constant instanceof PrimitiveConstant && ((PrimitiveConstant) constant).asLong() == 0) {
                        continue;
                    }
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitRem(gen.operand(getX()), gen.operand(getY()), gen.state(this)));
    }
}

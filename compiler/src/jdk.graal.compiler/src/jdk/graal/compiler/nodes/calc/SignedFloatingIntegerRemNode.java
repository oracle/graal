/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Rem;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(cycles = CYCLES_32, size = SIZE_1, shortName = "%")
public class SignedFloatingIntegerRemNode extends FloatingIntegerDivRemNode<BinaryOp.Rem> {

    public static final NodeClass<SignedFloatingIntegerRemNode> TYPE = NodeClass.create(SignedFloatingIntegerRemNode.class);

    protected SignedFloatingIntegerRemNode(ValueNode x, ValueNode y) {
        super(TYPE, getArithmeticOpTable(x).getRem(), x, y, null);
    }

    protected SignedFloatingIntegerRemNode(ValueNode x, ValueNode y, GuardingNode floatingGuard) {
        super(TYPE, getArithmeticOpTable(x).getRem(), x, y, floatingGuard);
    }

    protected SignedFloatingIntegerRemNode(ValueNode x, ValueNode y, GuardingNode floatingGuard, boolean divisionOverflowIsJVMSCompliant) {
        super(TYPE, getArithmeticOpTable(x).getRem(), x, y, floatingGuard, divisionOverflowIsJVMSCompliant);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view, GuardingNode floatingGuard, boolean divisionOverflowIsJVMSCompliant) {
        BinaryOp<Rem> op = ArithmeticOpTable.forStamp(x.stamp(view)).getRem();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return new SignedFloatingIntegerRemNode(x, y, floatingGuard, divisionOverflowIsJVMSCompliant).canonical(null);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        if (forX.isConstant() && forY.isConstant()) {
            long yConst = forY.asJavaConstant().asLong();
            if (yConst == 0) {
                // Replacing a previous never 0 with constant zero can create this situation
                if (floatingGuard == null) {
                    throw GraalError.shouldNotReachHere("Must have never been a floating rem"); // ExcludeFromJacocoGeneratedReport
                } else {
                    return this;
                }
            }
            return ConstantNode.forIntegerStamp(stamp, forX.asJavaConstant().asLong() % yConst);
        } else if (forY.isConstant()) {
            ValueNode v = SignedRemNode.canonical(this, stamp(view), forX, forY, view, tool);
            if (v != null) {
                return v;
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        assert x.stamp(NodeView.DEFAULT) instanceof IntegerStamp && y.stamp(NodeView.DEFAULT) instanceof IntegerStamp : Assertions.errorMessageContext("this", this, "x", x, "y", y);
        builder.setResult(this, builder.getLIRGeneratorTool().getArithmetic().emitRem(
                        builder.operand(getX()), builder.operand(getY()), null/* no state needed */));
    }

    @Override
    protected BinaryOp<Rem> getOp(ArithmeticOpTable table) {
        return table.getRem();
    }
}

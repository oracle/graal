/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.Constant;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;
import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public abstract class TernaryArithmeticNode<OP> extends TernaryNode implements ArithmeticOperation, ArithmeticLIRLowerable, Canonicalizable.Ternary<ValueNode> {

    @SuppressWarnings("rawtypes") public static final NodeClass<TernaryArithmeticNode> TYPE = NodeClass.create(TernaryArithmeticNode.class);

    protected TernaryArithmeticNode(NodeClass<? extends TernaryArithmeticNode<OP>> c, ArithmeticOpTable.TernaryOp<OP> opForStampComputation, ValueNode x, ValueNode y, ValueNode z) {
        super(c, opForStampComputation.foldStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT), z.stamp(NodeView.DEFAULT)), x, y, z);
    }

    protected abstract ArithmeticOpTable.TernaryOp<OP> getOp(ArithmeticOpTable table);

    private ArithmeticOpTable.TernaryOp<OP> getOp(ValueNode forX, ValueNode forY, ValueNode forZ) {
        ArithmeticOpTable table = getArithmeticOpTable(forX);
        assert table.equals(getArithmeticOpTable(forY)) && table.equals(getArithmeticOpTable(forZ)) : "Should have the same type";
        return getOp(table);
    }

    @Override
    public final ArithmeticOpTable.TernaryOp<OP> getArithmeticOp() {
        return getOp(getX(), getY(), getZ());
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY, ValueNode forZ) {
        NodeView view = NodeView.from(tool);
        ValueNode result = tryConstantFold(getOp(forX, forY, forZ), forX, forY, forZ, stamp(view));
        if (result != null) {
            return result;
        }
        return this;
    }

    public static <OP> ConstantNode tryConstantFold(ArithmeticOpTable.TernaryOp<OP> op, ValueNode forX, ValueNode forY, ValueNode forZ, Stamp stamp) {
        if (forX.isConstant() && forY.isConstant() && forZ.isConstant()) {
            Constant ret = op.foldConstant(forX.asConstant(), forY.asConstant(), forZ.asConstant());
            if (ret != null) {
                return ConstantNode.forPrimitive(stamp, ret);
            }
        }
        return null;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        assert stampX.isCompatible(x.stamp(NodeView.DEFAULT)) : Assertions.errorMessageContext("this", this, "xStamp", x.stamp(NodeView.DEFAULT), "stampX", stampX);
        assert stampY.isCompatible(y.stamp(NodeView.DEFAULT)) : Assertions.errorMessageContext("this", this, "yStamp", y.stamp(NodeView.DEFAULT), "stampY", stampY);
        assert stampZ.isCompatible(z.stamp(NodeView.DEFAULT)) : Assertions.errorMessageContext("this", this, "zStamp", z.stamp(NodeView.DEFAULT), "stampZ", stampZ);
        return getArithmeticOp().foldStamp(stampX, stampY, stampZ);
    }

    public static ValueNode ternaryFloatOp(ValueNode v1, ValueNode v2, ValueNode v3, NodeView view, ArithmeticOpTable.TernaryOp<?> op) {
        if (FloatStamp.OPS.getFMA().equals(op)) {
            return FusedMultiplyAddNode.create(v1, v2, v3, view);
        }
        throw GraalError.shouldNotReachHere(String.format("%s is not a ternary operation!", op));
    }
}

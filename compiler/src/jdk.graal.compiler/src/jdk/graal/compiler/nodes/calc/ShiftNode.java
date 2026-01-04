/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.ShiftOp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;

/**
 * The {@code ShiftOp} class represents shift operations.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public abstract class ShiftNode<OP> extends BinaryNode implements ArithmeticOperation, ArithmeticLIRLowerable, NarrowableArithmeticNode {

    @SuppressWarnings("rawtypes") public static final NodeClass<ShiftNode> TYPE = NodeClass.create(ShiftNode.class);

    /**
     * Creates a new shift operation.
     *
     * @param x the first input value
     * @param s the second input value
     */
    protected ShiftNode(NodeClass<? extends ShiftNode<OP>> c, ShiftOp<OP> opForStampComputation, ValueNode x, ValueNode s) {
        super(c, opForStampComputation.foldStamp(x.stamp(NodeView.DEFAULT), s.stamp(NodeView.DEFAULT)), x, s);
    }

    protected abstract ShiftOp<OP> getOp(ArithmeticOpTable table);

    protected final ShiftOp<OP> getOp(ValueNode forValue) {
        return getOp(BinaryArithmeticNode.getArithmeticOpTable(forValue));
    }

    @Override
    public final ShiftOp<OP> getArithmeticOp() {
        return getOp(getX());
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return getArithmeticOp().foldStamp(stampX, stampY);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode valueNode = canonical(getOp(forX), stamp(NodeView.DEFAULT), forX, forY, view);
        if (valueNode != null) {
            return valueNode;
        }
        return this;
    }

    @SuppressWarnings("unused")
    public static <OP> ValueNode canonical(ShiftOp<OP> op, Stamp stamp, ValueNode forX, ValueNode forY, NodeView view) {
        if (forX.isConstant() && forY.isConstant()) {
            Constant amount = forY.asConstant();
            return ConstantNode.forPrimitive(stamp, op.foldConstant(forX.asConstant(), amount));
        }
        return null;
    }

    public static ValueNode shiftOp(ValueNode x, ValueNode y, NodeView view, ShiftOp<?> op) {
        if (IntegerStamp.OPS.getShl().equals(op)) {
            return LeftShiftNode.create(x, y, view);
        } else if (IntegerStamp.OPS.getShr().equals(op)) {
            return RightShiftNode.create(x, y, view);
        } else if (IntegerStamp.OPS.getUShr().equals(op)) {
            return UnsignedRightShiftNode.create(x, y, view);
        } else if (CollectionsUtil.setOf(IntegerStamp.OPS.getShiftOps()).contains(op)) {
            GraalError.unimplemented(String.format("creating %s via ShiftNode#shiftOp is not implemented yet", op));
        } else {
            GraalError.shouldNotReachHere(String.format("%s is not a shift operation!", op));
        }
        return null;
    }

    public int getShiftAmountMask() {
        return getArithmeticOp().getShiftAmountMask(stamp(NodeView.DEFAULT));
    }

    @Override
    public boolean isNarrowable(int resultBits) {
        assert CodeUtil.isPowerOf2(resultBits);
        int narrowMask = resultBits <= 32 ? Integer.SIZE - 1 : Long.SIZE - 1;
        int wideMask = getShiftAmountMask();
        assert (wideMask & narrowMask) == narrowMask : String.format("wideMask %x should be wider than narrowMask %x", wideMask, narrowMask);

        /*
         * Shifts are special because narrowing them also changes the implicit mask of the shift
         * amount. We can narrow only if (y & wideMask) == (y & narrowMask) for all possible values
         * of y.
         */
        if (!(getY().stamp(NodeView.DEFAULT) instanceof IntegerStamp yStamp)) {
            return false;
        }
        return (yStamp.mayBeSet() & (wideMask & ~narrowMask)) == 0;
    }
}

/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import java.io.Serializable;
import java.util.function.Function;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.compiler.common.type.ArithmeticOpTable;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.ShiftOp;
import com.oracle.graal.compiler.common.type.IntegerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ArithmeticOperation;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;

/**
 * The {@code ShiftOp} class represents shift operations.
 */
@NodeInfo
public abstract class ShiftNode<OP> extends BinaryNode implements ArithmeticOperation, ArithmeticLIRLowerable, NarrowableArithmeticNode {

    @SuppressWarnings("rawtypes") public static final NodeClass<ShiftNode> TYPE = NodeClass.create(ShiftNode.class);

    protected interface SerializableShiftFunction<T> extends Function<ArithmeticOpTable, ShiftOp<T>>, Serializable {
    }

    protected final SerializableShiftFunction<OP> getOp;

    /**
     * Creates a new shift operation.
     *
     * @param x the first input value
     * @param s the second input value
     */
    protected ShiftNode(NodeClass<? extends ShiftNode<OP>> c, SerializableShiftFunction<OP> getOp, ValueNode x, ValueNode s) {
        super(c, getOp.apply(ArithmeticOpTable.forStamp(x.stamp())).foldStamp(x.stamp(), (IntegerStamp) s.stamp()), x, s);
        assert ((IntegerStamp) s.stamp()).getBits() == 32;
        this.getOp = getOp;
    }

    protected final ShiftOp<OP> getOp(ValueNode forValue) {
        return getOp.apply(ArithmeticOpTable.forStamp(forValue.stamp()));
    }

    public final ShiftOp<OP> getArithmeticOp() {
        return getOp(getX());
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return getArithmeticOp().foldStamp(stampX, (IntegerStamp) stampY);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && forY.isConstant()) {
            JavaConstant amount = forY.asJavaConstant();
            assert amount.getJavaKind() == JavaKind.Int;
            return ConstantNode.forPrimitive(stamp(), getOp(forX).foldConstant(forX.asConstant(), amount.asInt()));
        }
        return this;
    }

    public int getShiftAmountMask() {
        return getArithmeticOp().getShiftAmountMask(stamp());
    }

    public boolean isNarrowable(int resultBits) {
        assert CodeUtil.isPowerOf2(resultBits);
        int narrowMask = resultBits - 1;
        int wideMask = getShiftAmountMask();
        assert (wideMask & narrowMask) == narrowMask : String.format("wideMask %x should be wider than narrowMask %x", wideMask, narrowMask);

        /*
         * Shifts are special because narrowing them also changes the implicit mask of the shift
         * amount. We can narrow only if (y & wideMask) == (y & narrowMask) for all possible values
         * of y.
         */
        IntegerStamp yStamp = (IntegerStamp) getY().stamp();
        return (yStamp.upMask() & (wideMask & ~narrowMask)) == 0;
    }
}

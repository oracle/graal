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

import java.io.*;
import java.util.function.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.ShiftOp;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code ShiftOp} class represents shift operations.
 */
@NodeInfo
public abstract class ShiftNode<OP> extends BinaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass TYPE = NodeClass.get(ShiftNode.class);

    protected interface SerializableShiftFunction<T> extends Function<ArithmeticOpTable, ShiftOp<T>>, Serializable {
    }

    protected final SerializableShiftFunction<OP> getOp;

    /**
     * Creates a new shift operation.
     *
     * @param x the first input value
     * @param s the second input value
     */
    protected ShiftNode(NodeClass c, SerializableShiftFunction<OP> getOp, ValueNode x, ValueNode s) {
        super(c, getOp.apply(ArithmeticOpTable.forStamp(x.stamp())).foldStamp(x.stamp(), (IntegerStamp) s.stamp()), x, s);
        assert ((IntegerStamp) s.stamp()).getBits() == 32;
        this.getOp = getOp;
    }

    protected final ShiftOp<OP> getOp(ValueNode forValue) {
        return getOp.apply(ArithmeticOpTable.forStamp(forValue.stamp()));
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(getOp(getX()).foldStamp(getX().stamp(), (IntegerStamp) getY().stamp()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && forY.isConstant()) {
            JavaConstant amount = forY.asJavaConstant();
            assert amount.getKind() == Kind.Int;
            return ConstantNode.forPrimitive(stamp(), getOp(forX).foldConstant(forX.asConstant(), amount.asInt()));
        }
        return this;
    }

    public int getShiftAmountMask() {
        return getOp(getX()).getShiftAmountMask(stamp());
    }

}

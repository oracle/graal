/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo
public class BitCountNode extends UnaryNode implements LIRLowerable {

    public static BitCountNode create(ValueNode value) {
        return USE_GENERATED_NODES ? new BitCountNodeGen(value) : new BitCountNode(value);
    }

    protected BitCountNode(ValueNode value) {
        super(StampFactory.forInteger(Kind.Int, 0, ((PrimitiveStamp) value.stamp()).getBits()), value);
        assert value.getKind() == Kind.Int || value.getKind() == Kind.Long;
    }

    @Override
    public boolean inferStamp() {
        IntegerStamp valueStamp = (IntegerStamp) getValue().stamp();
        assert (valueStamp.downMask() & IntegerStamp.defaultMask(valueStamp.getBits())) == valueStamp.downMask();
        assert (valueStamp.upMask() & IntegerStamp.defaultMask(valueStamp.getBits())) == valueStamp.upMask();
        return updateStamp(StampFactory.forInteger(Kind.Int, bitCount(valueStamp.downMask()), bitCount(valueStamp.upMask())));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            Constant c = forValue.asConstant();
            return ConstantNode.forInt(forValue.getKind() == Kind.Int ? bitCount(c.asInt()) : bitCount(c.asLong()));
        }
        return this;
    }

    @NodeIntrinsic
    public static int bitCount(int v) {
        return Integer.bitCount(v);
    }

    @NodeIntrinsic
    public static int bitCount(long v) {
        return Long.bitCount(v);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitBitCount(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}

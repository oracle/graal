/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.code.CodeUtil;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.compiler.common.type.IntegerStamp;
import com.oracle.graal.compiler.common.type.PrimitiveStamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.UnaryNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public final class BitCountNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<BitCountNode> TYPE = NodeClass.create(BitCountNode.class);

    public BitCountNode(ValueNode value) {
        super(TYPE, StampFactory.forInteger(JavaKind.Int, 0, ((PrimitiveStamp) value.stamp()).getBits()), value);
        assert value.getStackKind() == JavaKind.Int || value.getStackKind() == JavaKind.Long;
    }

    @Override
    public boolean inferStamp() {
        IntegerStamp valueStamp = (IntegerStamp) getValue().stamp();
        assert (valueStamp.downMask() & CodeUtil.mask(valueStamp.getBits())) == valueStamp.downMask();
        assert (valueStamp.upMask() & CodeUtil.mask(valueStamp.getBits())) == valueStamp.upMask();
        return updateStamp(StampFactory.forInteger(JavaKind.Int, Long.bitCount(valueStamp.downMask()), Long.bitCount(valueStamp.upMask())));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            JavaConstant c = forValue.asJavaConstant();
            return ConstantNode.forInt(forValue.getStackKind() == JavaKind.Int ? Integer.bitCount(c.asInt()) : Long.bitCount(c.asLong()));
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitBitCount(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}

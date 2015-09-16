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
package com.oracle.graal.replacements.amd64;

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

/**
 * Count the number of leading zeros using the {@code lzcntq} or {@code lzcntl} instructions.
 */
@NodeInfo
public final class AMD64CountLeadingZerosNode extends UnaryNode implements LIRLowerable {
    public static final NodeClass<AMD64CountLeadingZerosNode> TYPE = NodeClass.create(AMD64CountLeadingZerosNode.class);

    public AMD64CountLeadingZerosNode(ValueNode value) {
        super(TYPE, StampFactory.forInteger(JavaKind.Int, 0, ((PrimitiveStamp) value.stamp()).getBits()), value);
        assert value.getStackKind() == JavaKind.Int || value.getStackKind() == JavaKind.Long;
    }

    @Override
    public boolean inferStamp() {
        assert value.getStackKind() == JavaKind.Int || value.getStackKind() == JavaKind.Long;
        IntegerStamp valueStamp = (IntegerStamp) getValue().stamp();
        long mask = CodeUtil.mask(valueStamp.getBits());
        // Don't count zeros from the mask in the result.
        int adjust = Long.numberOfLeadingZeros(mask);
        assert adjust == 0 || adjust == 32;
        int min = Long.numberOfLeadingZeros(valueStamp.upMask() & mask) - adjust;
        int max = Long.numberOfLeadingZeros(valueStamp.downMask() & mask) - adjust;
        return updateStamp(StampFactory.forInteger(JavaKind.Int, min, max));
    }

    public static ValueNode tryFold(ValueNode value) {
        if (value.isConstant()) {
            JavaConstant c = value.asJavaConstant();
            if (value.getStackKind() == JavaKind.Int) {
                return ConstantNode.forInt(Integer.numberOfLeadingZeros(c.asInt()));
            } else {
                return ConstantNode.forInt(Long.numberOfLeadingZeros(c.asLong()));
            }
        }
        return null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode folded = tryFold(forValue);
        return folded != null ? folded : this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitCountLeadingZeros(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}

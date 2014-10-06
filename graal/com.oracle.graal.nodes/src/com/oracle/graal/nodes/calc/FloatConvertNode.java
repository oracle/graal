/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.FloatConvertOp;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A {@code FloatConvert} converts between integers and floating point numbers according to Java
 * semantics.
 */
@NodeInfo
public class FloatConvertNode extends UnaryArithmeticNode implements ConvertNode, Lowerable, ArithmeticLIRLowerable {

    protected final FloatConvertOp reverseOp;

    public static FloatConvertNode create(FloatConvert op, ValueNode input) {
        return USE_GENERATED_NODES ? new FloatConvertNodeGen(op, input) : new FloatConvertNode(op, input);
    }

    private FloatConvertNode(ArithmeticOpTable table, FloatConvert op, ValueNode input) {
        super(table.getFloatConvert(op), input);
        ArithmeticOpTable revTable = ArithmeticOpTable.forStamp(stamp());
        reverseOp = revTable.getFloatConvert(op.reverse());
    }

    protected FloatConvertNode(FloatConvert op, ValueNode input) {
        this(ArithmeticOpTable.forStamp(input.stamp()), op, input);
    }

    public FloatConvert getFloatConvert() {
        return ((FloatConvertOp) getOp()).getFloatConvert();
    }

    @Override
    public Constant convert(Constant c) {
        return op.foldConstant(c);
    }

    @Override
    public Constant reverse(Constant c) {
        return reverseOp.foldConstant(c);
    }

    @Override
    public boolean isLossless() {
        switch (getFloatConvert()) {
            case F2D:
            case I2D:
                return true;
            default:
                return false;
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        if (forValue instanceof FloatConvertNode) {
            FloatConvertNode other = (FloatConvertNode) forValue;
            if (other.isLossless() && other.op == this.reverseOp) {
                return other.getValue();
            }
        }
        return this;
    }

    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitFloatConvert(getFloatConvert(), builder.operand(getValue())));
    }
}

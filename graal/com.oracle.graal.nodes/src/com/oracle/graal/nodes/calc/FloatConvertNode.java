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
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A {@code FloatConvert} converts between integers and floating point numbers according to Java
 * semantics.
 */
public class FloatConvertNode extends ConvertNode implements Lowerable, ArithmeticLIRLowerable {

    private final FloatConvert op;

    public FloatConvertNode(FloatConvert op, ValueNode input) {
        super(createStamp(op, input), input);
        this.op = op;
    }

    private static Stamp createStamp(FloatConvert op, ValueNode input) {
        switch (op) {
            case I2F:
            case I2D:
                assert input.stamp() instanceof IntegerStamp && ((IntegerStamp) input.stamp()).getBits() == 32;
                break;
            case L2F:
            case L2D:
                assert input.stamp() instanceof IntegerStamp && ((IntegerStamp) input.stamp()).getBits() == 64;
                break;
            case F2I:
            case F2L:
            case F2D:
                assert input.stamp() instanceof FloatStamp && ((FloatStamp) input.stamp()).getBits() == 32;
                break;
            case D2I:
            case D2L:
            case D2F:
                assert input.stamp() instanceof FloatStamp && ((FloatStamp) input.stamp()).getBits() == 64;
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }

        switch (op) {
            case F2I:
            case D2I:
                return StampFactory.forKind(Kind.Int);
            case F2L:
            case D2L:
                return StampFactory.forKind(Kind.Long);
            case I2F:
            case L2F:
            case D2F:
                return StampFactory.forKind(Kind.Float);
            case I2D:
            case L2D:
            case F2D:
                return StampFactory.forKind(Kind.Double);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public FloatConvert getOp() {
        return op;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(createStamp(op, getValue()));
    }

    private static Constant convert(FloatConvert op, Constant value) {
        switch (op) {
            case F2I:
                return Constant.forInt((int) value.asFloat());
            case D2I:
                return Constant.forInt((int) value.asDouble());
            case F2L:
                return Constant.forLong((long) value.asFloat());
            case D2L:
                return Constant.forLong((long) value.asDouble());
            case I2F:
                return Constant.forFloat(value.asInt());
            case L2F:
                return Constant.forFloat(value.asLong());
            case D2F:
                return Constant.forFloat((float) value.asDouble());
            case I2D:
                return Constant.forDouble(value.asInt());
            case L2D:
                return Constant.forDouble(value.asLong());
            case F2D:
                return Constant.forDouble(value.asFloat());
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Constant convert(Constant c) {
        return convert(op, c);
    }

    @Override
    public Constant reverse(Constant c) {
        return convert(op.reverse(), c);
    }

    @Override
    public boolean isLossless() {
        switch (op) {
            case F2D:
            case I2D:
                return true;
            default:
                return false;
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            return ConstantNode.forConstant(evalConst(forValue.asConstant()), null);
        } else if (forValue instanceof FloatConvertNode) {
            FloatConvertNode other = (FloatConvertNode) forValue;
            if (other.isLossless() && other.op == this.op.reverse()) {
                return other.getValue();
            }
        }
        return this;
    }

    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitFloatConvert(op, builder.operand(getValue())));
    }
}

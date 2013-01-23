/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.meta.Kind.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code ConvertNode} class represents a conversion between primitive types.
 */
public final class ConvertNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    public enum Op {
        I2L(Int, Long), L2I(Long, Int), I2B(Int, Byte), I2C(Int, Char), I2S(Int, Short), F2D(Float, Double), D2F(Double, Float), I2F(Int, Float), I2D(Int, Double), F2I(Float, Int), D2I(Double, Int), L2F(
                        Long, Float), L2D(Long, Double), F2L(Float, Long), D2L(Double, Long), UNSIGNED_I2L(Int, Long), MOV_I2F(Int, Float), MOV_L2D(Long, Double), MOV_F2I(Float, Int), MOV_D2L(Double,
                        Long);

        public final Kind from;
        public final Kind to;

        private Op(Kind from, Kind to) {
            this.from = from;
            this.to = to;
        }
    }

    @Input private ValueNode value;

    public final Op opcode;

    public ValueNode value() {
        return value;
    }

    /**
     * Constructs a new Convert instance.
     * 
     * @param opcode the operation
     * @param value the instruction producing the input value
     */
    public ConvertNode(Op opcode, ValueNode value) {
        super(StampFactory.forKind(opcode.to.getStackKind()));
        assert value.kind() == opcode.from : opcode + " : " + value.kind() + " != " + opcode.from;
        this.opcode = opcode;
        this.value = value;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (value instanceof ConstantNode) {
            Constant c = ((ConstantNode) value).asConstant();
            switch (opcode) {
                case I2L:
                    return ConstantNode.forLong(c.asInt(), graph());
                case L2I:
                    return ConstantNode.forInt((int) c.asLong(), graph());
                case I2B:
                    return ConstantNode.forByte((byte) c.asInt(), graph());
                case I2C:
                    return ConstantNode.forChar((char) c.asInt(), graph());
                case I2S:
                    return ConstantNode.forShort((short) c.asInt(), graph());
                case F2D:
                    return ConstantNode.forDouble(c.asFloat(), graph());
                case D2F:
                    return ConstantNode.forFloat((float) c.asDouble(), graph());
                case I2F:
                    return ConstantNode.forFloat(c.asInt(), graph());
                case I2D:
                    return ConstantNode.forDouble(c.asInt(), graph());
                case F2I:
                    return ConstantNode.forInt((int) c.asFloat(), graph());
                case D2I:
                    return ConstantNode.forInt((int) c.asDouble(), graph());
                case L2F:
                    return ConstantNode.forFloat(c.asLong(), graph());
                case L2D:
                    return ConstantNode.forDouble(c.asLong(), graph());
                case F2L:
                    return ConstantNode.forLong((long) c.asFloat(), graph());
                case D2L:
                    return ConstantNode.forLong((long) c.asDouble(), graph());
                case UNSIGNED_I2L:
                    return ConstantNode.forLong(c.asInt() & 0xffffffffL, graph());
                case MOV_I2F:
                    return ConstantNode.forFloat(java.lang.Float.intBitsToFloat(c.asInt()), graph());
                case MOV_L2D:
                    return ConstantNode.forDouble(java.lang.Double.longBitsToDouble(c.asLong()), graph());
                case MOV_F2I:
                    return ConstantNode.forInt(java.lang.Float.floatToRawIntBits(c.asFloat()), graph());
                case MOV_D2L:
                    return ConstantNode.forLong(java.lang.Double.doubleToRawLongBits(c.asDouble()), graph());
            }
        }
        return this;
    }

    @Override
    public boolean inferStamp() {
        Stamp newStamp;
        switch (opcode) {
            case I2L:
                newStamp = StampTool.intToLong(value().integerStamp());
                break;
            case L2I:
                newStamp = StampTool.longToInt(value().integerStamp());
                break;
            case I2B:
                newStamp = StampTool.intToByte(value().integerStamp());
                break;
            case I2C:
                newStamp = StampTool.intToChar(value().integerStamp());
                break;
            case I2S:
                newStamp = StampTool.intToShort(value().integerStamp());
                break;
            default:
                return false;
        }
        return updateStamp(newStamp);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitConvert(opcode, gen.operand(value())));
    }

    @NodeIntrinsic
    public static native <S, T> S convert(@ConstantNodeParameter
    Op op, T value);
}

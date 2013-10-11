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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code ConvertNode} class represents a conversion between primitive types.
 */
public class ConvertNode extends FloatingNode implements Canonicalizable, Lowerable, ArithmeticLIRLowerable {

    public static enum Op {
        I2L(Int, Long, true),
        L2I(Long, Int, false),
        I2B(Int, Byte, false),
        I2C(Int, Char, false),
        I2S(Int, Short, false),
        F2D(Float, Double, true),
        D2F(Double, Float, false),
        I2F(Int, Float, false),
        I2D(Int, Double, true),
        F2I(Float, Int, false),
        D2I(Double, Int, false),
        L2F(Long, Float, false),
        L2D(Long, Double, false),
        F2L(Float, Long, false),
        D2L(Double, Long, false),
        UNSIGNED_I2L(Int, Long, true),
        MOV_I2F(Int, Float, false),
        MOV_L2D(Long, Double, false),
        MOV_F2I(Float, Int, false),
        MOV_D2L(Double, Long, false);

        public final Kind from;
        public final Kind to;
        public final boolean lossless;

        private Op(Kind from, Kind to, boolean lossless) {
            this.from = from;
            this.to = to;
            this.lossless = lossless;
        }

        public boolean isLossless() {
            return lossless;
        }

        public static Op getOp(Kind from, Kind to) {
            switch (from) {
                case Int:
                    switch (to) {
                        case Byte:
                            return I2B;
                        case Char:
                            return I2C;
                        case Short:
                            return I2S;
                        case Long:
                            return I2L;
                        case Float:
                            return I2F;
                        case Double:
                            return I2D;
                        default:
                            throw GraalInternalError.shouldNotReachHere();
                    }
                case Long:
                    switch (to) {
                        case Int:
                            return L2I;
                        case Float:
                            return L2F;
                        case Double:
                            return L2D;
                        default:
                            throw GraalInternalError.shouldNotReachHere();
                    }
                case Float:
                    switch (to) {
                        case Int:
                            return F2I;
                        case Long:
                            return F2L;
                        case Double:
                            return F2D;
                        default:
                            throw GraalInternalError.shouldNotReachHere();
                    }
                case Double:
                    switch (to) {
                        case Int:
                            return D2I;
                        case Long:
                            return D2L;
                        case Float:
                            return D2F;
                        default:
                            throw GraalInternalError.shouldNotReachHere();
                    }
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
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

    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 1;
        Constant c = inputs[0];
        switch (opcode) {
            case I2L:
                return Constant.forLong(c.asInt());
            case L2I:
                return Constant.forInt((int) c.asLong());
            case I2B:
                return Constant.forByte((byte) c.asInt());
            case I2C:
                return Constant.forChar((char) c.asInt());
            case I2S:
                return Constant.forShort((short) c.asInt());
            case F2D:
                return Constant.forDouble(c.asFloat());
            case D2F:
                return Constant.forFloat((float) c.asDouble());
            case I2F:
                return Constant.forFloat(c.asInt());
            case I2D:
                return Constant.forDouble(c.asInt());
            case F2I:
                return Constant.forInt((int) c.asFloat());
            case D2I:
                return Constant.forInt((int) c.asDouble());
            case L2F:
                return Constant.forFloat(c.asLong());
            case L2D:
                return Constant.forDouble(c.asLong());
            case F2L:
                return Constant.forLong((long) c.asFloat());
            case D2L:
                return Constant.forLong((long) c.asDouble());
            case UNSIGNED_I2L:
                return Constant.forLong(c.asInt() & 0xffffffffL);
            case MOV_I2F:
                return Constant.forFloat(java.lang.Float.intBitsToFloat(c.asInt()));
            case MOV_L2D:
                return Constant.forDouble(java.lang.Double.longBitsToDouble(c.asLong()));
            case MOV_F2I:
                return Constant.forInt(java.lang.Float.floatToRawIntBits(c.asFloat()));
            case MOV_D2L:
                return Constant.forLong(java.lang.Double.doubleToRawLongBits(c.asDouble()));
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value.isConstant()) {
            return ConstantNode.forPrimitive(evalConst(value.asConstant()), graph());
        }
        return this;
    }

    @Override
    public boolean inferStamp() {
        Stamp stamp = value.stamp();
        if (!(stamp instanceof IntegerStamp)) {
            if (stamp instanceof FloatStamp) {
                return false;
            }
            assert stamp instanceof IllegalStamp;
            return updateStamp(stamp);
        }
        Stamp newStamp;
        IntegerStamp integerStamp = (IntegerStamp) stamp;
        switch (opcode) {
            case I2L:
                newStamp = StampTool.intToLong(integerStamp);
                break;
            case L2I:
                newStamp = StampTool.longToInt(integerStamp);
                break;
            case I2B:
                newStamp = StampTool.intToByte(integerStamp);
                break;
            case I2C:
                newStamp = StampTool.intToChar(integerStamp);
                break;
            case I2S:
                newStamp = StampTool.intToShort(integerStamp);
                break;
            default:
                return false;
        }
        return updateStamp(newStamp);
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void generate(ArithmeticLIRGenerator gen) {
        gen.setResult(this, gen.emitConvert(opcode, gen.operand(value())));
    }

    public static ValueNode convert(Kind toKind, ValueNode value) {
        Kind fromKind = value.kind();
        if (fromKind == toKind) {
            return value;
        }
        return value.graph().unique(new ConvertNode(Op.getOp(fromKind, toKind), value));
    }

    @NodeIntrinsic
    public static native float convert(@ConstantNodeParameter Op op, int value);

    @NodeIntrinsic
    public static native int convert(@ConstantNodeParameter Op op, float value);

    @NodeIntrinsic
    public static native double convert(@ConstantNodeParameter Op op, long value);

    @NodeIntrinsic
    public static native long convert(@ConstantNodeParameter Op op, double value);

}

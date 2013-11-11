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

    @Input private ValueNode value;

    private final Kind from;
    private final Kind to;

    public ValueNode value() {
        return value;
    }

    /**
     * Constructs a new Convert instance.
     * 
     * @param from the kind of the incoming value
     * @param to the result kind
     * @param value the instruction producing the input value
     */
    public ConvertNode(Kind from, Kind to, ValueNode value) {
        super(StampFactory.forKind(to.getStackKind()));
        assert value.kind() == from.getStackKind() : "convert(" + from + ", " + to + ") : " + value.kind() + " != " + from;
        this.from = from;
        this.to = to;
        this.value = value;
    }

    public Kind getFromKind() {
        return from;
    }

    public Kind getToKind() {
        return to;
    }

    public boolean isLossless() {
        if (from == to) {
            return true;
        }
        switch (from) {
            case Byte:
                return true;
            case Short:
            case Char:
                return to != Kind.Byte;
            case Int:
                return to == Kind.Long || to == Kind.Double;
            case Float:
                return to == Kind.Double;
            case Long:
            case Double:
                return false;
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    public static Constant convert(Kind from, Kind to, Constant c) {
        switch (from) {
            case Byte:
                byte byteVal = (byte) c.asInt();
                switch (to) {
                    case Byte:
                        return Constant.forByte(byteVal);
                    case Short:
                        return Constant.forShort(byteVal);
                    case Char:
                        return Constant.forChar((char) byteVal);
                    case Int:
                        return Constant.forInt(byteVal);
                    case Long:
                        return Constant.forLong(byteVal);
                    case Float:
                        return Constant.forFloat(byteVal);
                    case Double:
                        return Constant.forDouble(byteVal);
                }
                break;
            case Char:
                char charVal = (char) c.asInt();
                switch (to) {
                    case Byte:
                        return Constant.forByte((byte) charVal);
                    case Short:
                        return Constant.forShort((short) charVal);
                    case Char:
                        return Constant.forChar(charVal);
                    case Int:
                        return Constant.forInt(charVal);
                    case Long:
                        return Constant.forLong(charVal);
                    case Float:
                        return Constant.forFloat(charVal);
                    case Double:
                        return Constant.forDouble(charVal);
                }
                break;
            case Short:
                short shortVal = (short) c.asInt();
                switch (to) {
                    case Byte:
                        return Constant.forByte((byte) shortVal);
                    case Short:
                        return Constant.forShort(shortVal);
                    case Char:
                        return Constant.forChar((char) shortVal);
                    case Int:
                        return Constant.forInt(shortVal);
                    case Long:
                        return Constant.forLong(shortVal);
                    case Float:
                        return Constant.forFloat(shortVal);
                    case Double:
                        return Constant.forDouble(shortVal);
                }
                break;
            case Int:
                int intVal = c.asInt();
                switch (to) {
                    case Byte:
                        return Constant.forByte((byte) intVal);
                    case Short:
                        return Constant.forShort((short) intVal);
                    case Char:
                        return Constant.forChar((char) intVal);
                    case Int:
                        return Constant.forInt(intVal);
                    case Long:
                        return Constant.forLong(intVal);
                    case Float:
                        return Constant.forFloat(intVal);
                    case Double:
                        return Constant.forDouble(intVal);
                }
                break;
            case Long:
                long longVal = c.asLong();
                switch (to) {
                    case Byte:
                        return Constant.forByte((byte) longVal);
                    case Short:
                        return Constant.forShort((short) longVal);
                    case Char:
                        return Constant.forChar((char) longVal);
                    case Int:
                        return Constant.forInt((int) longVal);
                    case Long:
                        return Constant.forLong(longVal);
                    case Float:
                        return Constant.forFloat(longVal);
                    case Double:
                        return Constant.forDouble(longVal);
                }
                break;
            case Float:
                float floatVal = c.asFloat();
                switch (to) {
                    case Byte:
                        return Constant.forByte((byte) floatVal);
                    case Short:
                        return Constant.forShort((short) floatVal);
                    case Char:
                        return Constant.forChar((char) floatVal);
                    case Int:
                        return Constant.forInt((int) floatVal);
                    case Long:
                        return Constant.forLong((long) floatVal);
                    case Float:
                        return Constant.forFloat(floatVal);
                    case Double:
                        return Constant.forDouble(floatVal);
                }
                break;
            case Double:
                double doubleVal = c.asDouble();
                switch (to) {
                    case Byte:
                        return Constant.forByte((byte) doubleVal);
                    case Short:
                        return Constant.forShort((short) doubleVal);
                    case Char:
                        return Constant.forChar((char) doubleVal);
                    case Int:
                        return Constant.forInt((int) doubleVal);
                    case Long:
                        return Constant.forLong((long) doubleVal);
                    case Float:
                        return Constant.forFloat((float) doubleVal);
                    case Double:
                        return Constant.forDouble(doubleVal);
                }
                break;
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 1;
        return convert(from, to, inputs[0]);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (from == to) {
            return value;
        } else if (value.isConstant()) {
            return ConstantNode.forPrimitive(evalConst(value.asConstant()), graph());
        } else if (value instanceof ConvertNode) {
            ConvertNode other = (ConvertNode) value;
            if (other.isLossless() && other.to != Kind.Char) {
                if (other.from == this.to) {
                    return other.value();
                } else {
                    return graph().unique(new ConvertNode(other.from, this.to, other.value()));
                }
            }
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
        switch (to) {
            case Byte:
            case Short:
            case Char:
            case Int:
                newStamp = StampTool.narrowingKindConversion(integerStamp, to);
                break;
            case Long:
                newStamp = StampTool.intToLong(integerStamp);
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
        gen.setResult(this, gen.emitConvert(from, to, gen.operand(value())));
    }

    public static ValueNode convert(StructuredGraph graph, Kind toKind, ValueNode value) {
        Kind fromKind = value.kind();
        if (fromKind == toKind) {
            return value;
        }
        return graph.unique(new ConvertNode(fromKind, toKind, value));
    }
}

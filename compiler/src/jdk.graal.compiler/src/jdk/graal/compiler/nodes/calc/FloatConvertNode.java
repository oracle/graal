/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;

import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.FloatConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * A {@code FloatConvert} converts between integers and floating point numbers according to Java
 * semantics.
 */
@NodeInfo(cycles = CYCLES_8)
public final class FloatConvertNode extends UnaryArithmeticNode<FloatConvertOp> implements ConvertNode, Lowerable, ArithmeticLIRLowerable {
    public static final NodeClass<FloatConvertNode> TYPE = NodeClass.create(FloatConvertNode.class);

    protected final FloatConvert op;

    public FloatConvertNode(FloatConvert op, ValueNode input) {
        super(TYPE, BinaryArithmeticNode.getArithmeticOpTable(input).getFloatConvert(op), input);
        this.op = op;
    }

    public static ValueNode create(FloatConvert op, ValueNode input, NodeView view) {
        ValueNode synonym = findSynonym(input, ArithmeticOpTable.forStamp(input.stamp(view)).getFloatConvert(op));
        if (synonym != null) {
            return synonym;
        }
        return new FloatConvertNode(op, input);
    }

    @Override
    protected UnaryOp<FloatConvertOp> getOp(ArithmeticOpTable table) {
        return table.getFloatConvert(op);
    }

    public FloatConvert getFloatConvert() {
        return op;
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        return getArithmeticOp().foldConstant(c);
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        FloatConvertOp reverse = ArithmeticOpTable.forStamp(stamp(NodeView.DEFAULT)).getFloatConvert(op.reverse());
        return reverse.foldConstant(c);
    }

    @Override
    public boolean isLossless() {
        switch (getFloatConvert()) {
            case F2D:
            case I2D:
            case UI2D:
                return true;
            case I2F:
            case L2D:
            case L2F:
                if (value.stamp(NodeView.DEFAULT) instanceof IntegerStamp inputStamp) {
                    return isLosslessIntegerToFloatingPoint(inputStamp, (FloatStamp) stamp(NodeView.DEFAULT), false);
                } else {
                    return false;
                }
            case UI2F:
            case UL2D:
            case UL2F:
                if (value.stamp(NodeView.DEFAULT) instanceof IntegerStamp inputStamp) {
                    return isLosslessIntegerToFloatingPoint(inputStamp, (FloatStamp) stamp(NodeView.DEFAULT), true);
                } else {
                    return false;
                }
            default:
                return false;
        }
    }

    public boolean inputCanBeNaN() {
        Stamp inputStamp = getValue().stamp(NodeView.DEFAULT);
        return ArithmeticOpTable.forStamp(inputStamp).getFloatConvert(op).inputCanBeNaN(inputStamp);
    }

    public boolean canOverflow() {
        Stamp inputStamp = getValue().stamp(NodeView.DEFAULT);
        return ArithmeticOpTable.forStamp(inputStamp).getFloatConvert(op).canOverflowInteger(inputStamp);
    }

    private static boolean isLosslessIntegerToFloatingPoint(IntegerStamp inputStamp, FloatStamp resultStamp, boolean unsigned) {
        int mantissaBits = switch (resultStamp.getBits()) {
            case 32 -> 24;
            case 64 -> 53;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(resultStamp.getBits()); // ExcludeFromJacocoGeneratedReport
        };
        long max = 1L << mantissaBits;
        long min = -(1L << mantissaBits);
        if (unsigned) {
            return Long.compareUnsigned(inputStamp.unsignedUpperBound(), max) <= 0;
        } else {
            return min <= inputStamp.lowerBound() && inputStamp.upperBound() <= max;
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        if (forValue instanceof FloatConvertNode other) {
            if (other.isLossless() && other.op == this.op.reverse()) {
                return other.getValue();
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitFloatConvert(getFloatConvert(), nodeValueMap.operand(getValue()), inputCanBeNaN(), canOverflow()));
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return false;
    }

    /**
     * Indicates whether this target platform supports lowering floating point conversions to
     * unsigned integer.
     */
    public static boolean supportsFloatToUnsignedConvert(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> true;
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }

    /**
     * Indicates whether this target platform supports lowering conversions from unsigned integers
     * to floating point.
     */
    public static boolean supportsUnsignedToFloatConvert(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> AMD64BaseAssembler.supportsFullAVX512(amd64.getFeatures());
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }
}

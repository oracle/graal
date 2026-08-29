/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi.nodes;

import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdConcatNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdInsertNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIUtils;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsic node for the {@code VectorSupport.convert} method. This operation applies a conversion
 * operation to each element of a vector {@code v}, producing a result vector:
 * <p/>
 *
 * {@code
 *     result = <OP(v.0), OP(v.1), ..., OP(v.n)>
 * }
 *
 * <p/>
 * The conversion operation is identified by an integer code which we map to the corresponding
 * operation. The available {@linkplain ConversionOp operations} include numeric conversions as well
 * as bitwise reinterpretation.
 */
@NodeInfo(nameTemplate = "VectorAPIConvert {p#op/s}")
public class VectorAPIConvertNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIConvertNode> TYPE = NodeClass.create(VectorAPIConvertNode.class);

    /** The conversion operation to be performed. */
    public enum ConversionOp {
        /**
         * A conversion corresponding to implicit or explicit Java numeric conversions on signed
         * integers or floating-point numbers: sign extension, narrowing, integer/floating-point
         * conversions.
         */
        CAST,
        /** A conversion representing an explicitly requested zero-extension on integers. */
        UCAST,
        /**
         * A reinterpretation of the bits in the vector. This may be used element-wise to, for
         * example, reinterpret 4 {@code float}s as 4 {@code int}s. It may also be used purely
         * bitwise, changing the vector length but keeping the total bit length, for example,
         * reinterpreting 4 {@code float}s as 16 {@code byte}s.
         */
        REINTERPRET
    }

    private final SimdStamp fromStamp;
    private final SimdStamp toStamp;
    private final ConversionOp op;

    /* Indices into the macro argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int FROM_VCLASS_ARG_INDEX = 1;
    private static final int FROM_ECLASS_ARG_INDEX = 2;
    private static final int FROM_LENGTH_ARG_INDEX = 3;
    private static final int TO_VCLASS_ARG_INDEX = 4;
    private static final int TO_ECLASS_ARG_INDEX = 5;
    private static final int TO_LENGTH_ARG_INDEX = 6;
    private static final int VALUE_ARG_INDEX = 7;

    protected VectorAPIConvertNode(MacroParams macroParams, SimdStamp fromStamp, SimdStamp toStamp, ConversionOp op, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.fromStamp = fromStamp;
        this.toStamp = maybeConstantVectorStamp(toStamp, constantValue);
        this.op = op;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIConvertNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp fromStamp = improveVectorStamp(null, macroParams.arguments, FROM_VCLASS_ARG_INDEX, FROM_ECLASS_ARG_INDEX, FROM_LENGTH_ARG_INDEX, providers);
        SimdStamp toStamp = improveVectorStamp(null, macroParams.arguments, TO_VCLASS_ARG_INDEX, TO_ECLASS_ARG_INDEX, TO_LENGTH_ARG_INDEX, providers);
        ConversionOp op = improveOp(null, macroParams.arguments);
        SimdConstant constantValue = improveConstant(null, fromStamp, toStamp, op, macroParams.arguments, providers);
        return new VectorAPIConvertNode(macroParams, fromStamp, toStamp, op, constantValue, null);
    }

    private static ConversionOp improveOp(ConversionOp oldOp, ValueNode[] arguments) {
        if (oldOp != null) {
            return oldOp;
        }
        ValueNode oprId = arguments[OPRID_ARG_INDEX];
        if (!(oprId.isJavaConstant() && oprId.asJavaConstant().getJavaKind() == JavaKind.Int)) {
            return null;
        }
        int conversionCode = oprId.asJavaConstant().asInt();
        ConversionOp conversion = VectorAPIOperations.lookupConversion(conversionCode);
        return conversion;
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, SimdStamp newFromStamp, SimdStamp newToStamp, ConversionOp newOp, ValueNode[] args, CoreProviders providers) {
        if (oldConstant != null) {
            return oldConstant;
        }
        if (newFromStamp == null || newToStamp == null || newOp == null) {
            return null;
        }
        SimdConstant valueConstant = maybeConstantValue(args[VALUE_ARG_INDEX], providers);
        if (valueConstant == null) {
            return null;
        }
        if (newOp == ConversionOp.REINTERPRET) {
            int fromBits = newFromStamp.getVectorLength() * PrimitiveStamp.getBits(newFromStamp.getComponent(0));
            int toBits = newToStamp.getVectorLength() * PrimitiveStamp.getBits(newToStamp.getComponent(0));
            if (fromBits == toBits) {
                return (SimdConstant) newFromStamp.getOps().getReinterpret().foldConstant(newToStamp, valueConstant);
            } else {
                /*
                 * Unreachable path, possible through inlining AbstractVector.convert0. No need to
                 * try to constant fold here, it should go away.
                 */
                return null;
            }
        } else {
            boolean padResult = newFromStamp.getVectorLength() < newToStamp.getVectorLength();
            if (newFromStamp.getVectorLength() > newToStamp.getVectorLength()) {
                /*
                 * This is a castShape operation that only uses part of the input vector. Currently
                 * we only constant fold this case for part=0, i.e., the lowest elements. We can
                 * represent this as a simple cut. The case for other parts of the input vector
                 * involves a blend. TODO GR-62819: also constant-fold those cases.
                 */
                Constant[] cutValues = Arrays.copyOf(valueConstant.getValues().toArray(new Constant[newToStamp.getVectorLength()]), newToStamp.getVectorLength());
                valueConstant = new SimdConstant(cutValues);
            }
            Stamp from = newFromStamp.getComponent(0);
            Stamp to = newToStamp.getComponent(0);
            if (from.equals(to)) {
                /* Casting a value to itself. */
                return padResult ? padConstant(valueConstant, newToStamp) : valueConstant;
            } else if (from.isIntegerStamp() && to.isIntegerStamp()) {
                ArithmeticOpTable.IntegerConvertOp<?> conversion = PrimitiveStamp.getBits(from) < PrimitiveStamp.getBits(to)
                                ? (newOp == ConversionOp.UCAST ? newFromStamp.getOps().getZeroExtend() : newFromStamp.getOps().getSignExtend())
                                : newFromStamp.getOps().getNarrow();
                SimdConstant converted = (SimdConstant) conversion.foldConstant(PrimitiveStamp.getBits(from), PrimitiveStamp.getBits(to), valueConstant);
                return padResult ? padConstant(converted, newToStamp) : converted;
            } else {
                FloatConvert floatConvert = FloatConvert.forStamps(from, to);
                if (floatConvert != null) {
                    ArithmeticOpTable.FloatConvertOp conversion = newFromStamp.getOps().getFloatConvert(floatConvert);
                    SimdConstant converted = (SimdConstant) conversion.foldConstant(valueConstant);
                    return padResult ? padConstant(converted, newToStamp) : converted;
                } else if (from.isIntegerStamp() && PrimitiveStamp.getBits(from) == Byte.SIZE && to.isFloatStamp()) {
                    /*
                     * We need to support i8 -> float conversions via an intermediate. This is
                     * necessary to make castShape work.
                     */
                    IntegerStamp scalarIntermediateStamp = StampFactory.forInteger(PrimitiveStamp.getBits(to));
                    SimdConstant intermediateConstant = (SimdConstant) newFromStamp.getOps().getSignExtend().foldConstant(Byte.SIZE, PrimitiveStamp.getBits(to), valueConstant);
                    floatConvert = FloatConvert.forStamps(scalarIntermediateStamp, to);
                    SimdStamp simdIntermediateStamp = SimdStamp.broadcast(scalarIntermediateStamp, newFromStamp.getVectorLength());
                    ArithmeticOpTable.FloatConvertOp conversion = simdIntermediateStamp.getOps().getFloatConvert(floatConvert);
                    SimdConstant converted = (SimdConstant) conversion.foldConstant(intermediateConstant);
                    return padResult ? padConstant(converted, newToStamp) : converted;
                }
                /* Other subword <-> float conversion, don't constant fold for now. */
                return null;
            }
        }
    }

    /** Fills the result lanes beyond the converted lanes with zeros. */
    private static SimdConstant padConstant(SimdConstant converted, SimdStamp resultStamp) {
        Constant[] values = Arrays.copyOf(converted.getValues().toArray(new Constant[0]), resultStamp.getVectorLength());
        Arrays.fill(values, converted.getVectorLength(), values.length, VectorAPIUtils.zeroConstant(resultStamp.getComponent(0)));
        return new SimdConstant(values);
    }

    public ValueNode inputVector() {
        return arguments.get(VALUE_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(inputVector());
    }

    @Override
    public Stamp vectorStamp() {
        return toStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        SimdConstant constantValue = maybeConstantValue(this, tool);
        if (speciesStamp.isExactType() && fromStamp != null && toStamp != null && op != null && constantValue != null) {
            /* Nothing to improve. */
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, TO_VCLASS_ARG_INDEX);
        SimdStamp newFromStamp = improveVectorStamp(fromStamp, args, FROM_VCLASS_ARG_INDEX, FROM_ECLASS_ARG_INDEX, FROM_LENGTH_ARG_INDEX, tool);
        SimdStamp newToStamp = improveVectorStamp(toStamp, args, TO_VCLASS_ARG_INDEX, TO_ECLASS_ARG_INDEX, TO_LENGTH_ARG_INDEX, tool);
        ConversionOp newOp = improveOp(op, args);
        SimdConstant newConstantValue = improveConstant(constantValue, newFromStamp, newToStamp, newOp, args, tool);
        if (newSpeciesStamp != speciesStamp || newFromStamp != fromStamp || newToStamp != toStamp || newOp != op || newConstantValue != constantValue) {
            return new VectorAPIConvertNode(copyParamsWithImprovedStamp(newSpeciesStamp), newFromStamp, newToStamp, newOp, newConstantValue, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (!speciesStamp.isExactType() || fromStamp == null || toStamp == null || op == null) {
            return false;
        }
        if (op == ConversionOp.REINTERPRET) {
            int fromBits = PrimitiveStamp.getBits(fromStamp.getComponent(0)) * fromStamp.getVectorLength();
            int toBits = PrimitiveStamp.getBits(toStamp.getComponent(0)) * toStamp.getVectorLength();
            return fromBits == toBits && vectorArch.getSupportedVectorMoveLength(toStamp.getComponent(0), toStamp.getVectorLength()) == toStamp.getVectorLength();
        } else if (fromStamp.getVectorLength() >= toStamp.getVectorLength()) {
            /*
             * Same length, or something like an element-wise extension from <i16, i16, i16, i16> to
             * <i32, i32>. The latter throws away some elements and then converts the rest.
             */
            GraalError.guarantee(op == ConversionOp.CAST || op == ConversionOp.UCAST, "unexpected op: %s", op);
            Stamp from = fromStamp.getComponent(0);
            Stamp to = toStamp.getComponent(0);
            if (from.equals(to)) {
                /*
                 * Casting a vector to itself, this is a nop. This can happen for conversions
                 * between shuffles (represented as int vectors) and actual int vectors.
                 */
                return true;
            } else if (from.isIntegerStamp() && to.isIntegerStamp()) {
                return canExpandIntegerCast(vectorArch, op, toStamp.getVectorLength(), from, to);
            } else {
                FloatConvert floatConvert = FloatConvert.forStamps(from, to);
                if (floatConvert != null) {
                    return vectorArch.getSupportedVectorConvertLength(to, from, toStamp.getVectorLength(), floatConvert) == toStamp.getVectorLength();
                } else if (canExpandByteToFloat(vectorArch, op, toStamp.getVectorLength(), from, to)) {
                    return true;
                }
                /*
                 * We don't support more general obscure conversions like f64 -> i16 directly. In
                 * the future we could handle such cases by chaining conversions: f64 -> i64 -> i16.
                 */
                return false;
            }
        } else {
            /*
             * This is a conversion from a vector with fewer elements to one with more elements,
             * such as <i32, i32> to <i16, i16, i16, i16>, or <i16, i16> to <i32, i32, i32, i32>.
             * The element types may also be the same, as in <i32, i32> to <i32, i32, i32, i32>, or
             * have the same size, as in <f32, f32> to <i32, i32, i32, i32>. Convert the available
             * elements and pad the remaining result elements with zeros.
             */
            Stamp from = fromStamp.getComponent(0);
            Stamp to = toStamp.getComponent(0);
            boolean canConvert;
            if (from.equals(to)) {
                canConvert = true;
            } else if (from.isIntegerStamp() && to.isIntegerStamp()) {
                canConvert = canExpandIntegerCast(vectorArch, op, fromStamp.getVectorLength(), from, to);
            } else {
                FloatConvert floatConvert = FloatConvert.forStamps(from, to);
                canConvert = floatConvert != null &&
                                vectorArch.getSupportedVectorConvertLength(to, from, fromStamp.getVectorLength(), floatConvert) == fromStamp.getVectorLength();
                if (floatConvert == null) {
                    canConvert = canExpandByteToFloat(vectorArch, op, fromStamp.getVectorLength(), from, to);
                }
            }
            return canConvert && canPadConvertedVector(vectorArch, toStamp, fromStamp.getVectorLength());
        }
    }

    /**
     * Checks whether an i8 to floating point conversion can be expanded through an integer
     * intermediate. This is necessary to make {@code castShape} work because there is no direct
     * {@link FloatConvert} for this conversion.
     */
    private static boolean canExpandByteToFloat(VectorArchitecture vectorArch, ConversionOp op, int vectorLength, Stamp from, Stamp to) {
        if (!(from.isIntegerStamp() && PrimitiveStamp.getBits(from) == Byte.SIZE && to.isFloatStamp())) {
            return false;
        }
        IntegerStamp intermediate = StampFactory.forInteger(PrimitiveStamp.getBits(to));
        if (!canExpandIntegerCast(vectorArch, op, vectorLength, from, intermediate)) {
            return false;
        }
        FloatConvert floatConvert = FloatConvert.forStamps(intermediate, to);
        return vectorArch.getSupportedVectorConvertLength(to, intermediate, vectorLength, floatConvert) == vectorLength;
    }

    /**
     * Checks whether the target can represent {@code resultStamp} and place a converted vector in
     * its low lanes, with all remaining lanes set to zero. Padding uses either an insert or the same
     * concatenation strategy as the nibble lookup padding in {@link VectorAPIUnaryOpNode}.
     */
    private static boolean canPadConvertedVector(VectorArchitecture vectorArch, SimdStamp resultStamp, int convertedLength) {
        Stamp elementStamp = resultStamp.getComponent(0);
        if (vectorArch.getSupportedVectorMoveLength(elementStamp, resultStamp.getVectorLength()) != resultStamp.getVectorLength()) {
            return false;
        }

        SimdStamp convertedStamp = SimdStamp.broadcast(elementStamp, convertedLength);
        if (vectorArch.supportsVectorInsert(resultStamp, convertedStamp, 0)) {
            return true;
        }

        int currentLength = convertedLength;
        int elementBytes = PrimitiveStamp.getBits(elementStamp) / Byte.SIZE;
        while (currentLength < resultStamp.getVectorLength()) {
            if (currentLength * 2 > resultStamp.getVectorLength() || !vectorArch.supportsVectorConcat(currentLength * elementBytes)) {
                return false;
            }
            currentLength *= 2;
        }
        return true;
    }

    public static boolean canExpandIntegerCast(VectorArchitecture vectorArch, ConversionOp op, int vectorLength, Stamp fromElement, Stamp toElement) {
        boolean zeroExtend = op == ConversionOp.UCAST;
        ArithmeticOpTable.IntegerConvertOp<?> conversion = PrimitiveStamp.getBits(fromElement) < PrimitiveStamp.getBits(toElement)
                        ? (zeroExtend ? IntegerStamp.OPS.getZeroExtend() : IntegerStamp.OPS.getSignExtend())
                        : IntegerStamp.OPS.getNarrow();
        return vectorArch.getSupportedVectorConvertLength(toElement, fromElement, vectorLength, conversion) == vectorLength;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return asSimdConstant(this, vectorArch);
        }
        ValueNode value = expanded.get(inputVector());
        GraalError.guarantee(value.stamp(NodeView.DEFAULT).isCompatible(fromStamp), "%s - %s", value, fromStamp);
        if (op == ConversionOp.REINTERPRET) {
            if (fromStamp.isCompatible(toStamp)) {
                return value;
            } else {
                return ReinterpretNode.create(toStamp, value, NodeView.DEFAULT);
            }
        } else {
            Stamp from = fromStamp.getComponent(0);
            Stamp to = toStamp.getComponent(0);

            if (from instanceof LogicValueStamp || to instanceof LogicValueStamp) {
                GraalError.guarantee(fromStamp.isCompatible(toStamp), "%s - %s", fromStamp, toStamp);
                return value;
            } else if (fromStamp.getVectorLength() > toStamp.getVectorLength()) {
                value = new SimdCutNode(value, 0, toStamp.getVectorLength());
            }
            ValueNode converted;
            if (from.equals(to)) {
                converted = value;
            } else if (from.isIntegerStamp() && to.isIntegerStamp()) {
                converted = expandIntegerCast(op, value, PrimitiveStamp.getBits(from), PrimitiveStamp.getBits(to));
            } else {
                FloatConvert floatConvert = FloatConvert.forStamps(from, to);
                if (floatConvert != null) {
                    converted = new FloatConvertNode(floatConvert, value);
                } else {
                    GraalError.guarantee(from.isIntegerStamp() && PrimitiveStamp.getBits(from) == Byte.SIZE && to.isFloatStamp(),
                                    "unsupported %s -> %s conversion, should have been verified before", from, to);
                    IntegerStamp intermediate = StampFactory.forInteger(PrimitiveStamp.getBits(to));
                    ValueNode convertedIntermediate = expandIntegerCast(op, value, PrimitiveStamp.getBits(from), PrimitiveStamp.getBits(intermediate));
                    floatConvert = FloatConvert.forStamps(intermediate, to);
                    converted = new FloatConvertNode(floatConvert, convertedIntermediate);
                }
            }

            if (fromStamp.getVectorLength() < toStamp.getVectorLength()) {
                converted = graph().addOrUniqueWithInputs(converted);
                return padConvertedVector(vectorArch, converted);
            }
            return converted;
        }
    }

    /**
     * Places {@code converted} in the low lanes of the result and fills the upper lanes with zeros.
     * An insert handles targets where the converted value is smaller than half of the result. A
     * sequence of concatenations is used on targets which support concatenation instead.
     */
    private ValueNode padConvertedVector(VectorArchitecture vectorArch, ValueNode converted) {
        SimdStamp convertedStamp = (SimdStamp) converted.stamp(NodeView.DEFAULT);
        if (vectorArch.supportsVectorInsert(toStamp, convertedStamp, 0)) {
            ValueNode zeros = zeroVector(toStamp);
            return graph().addOrUniqueWithInputs(SimdInsertNode.create(zeros, converted, 0));
        }

        ValueNode padded = converted;
        int currentLength = convertedStamp.getVectorLength();
        while (currentLength < toStamp.getVectorLength()) {
            SimdStamp zeroStamp = SimdStamp.broadcast(toStamp.getComponent(0), currentLength);
            ValueNode zeros = zeroVector(zeroStamp);
            padded = graph().addOrUnique(new SimdConcatNode(padded, zeros));
            currentLength *= 2;
        }
        return padded;
    }

    private ValueNode zeroVector(SimdStamp vectorStamp) {
        return graph().unique(SimdConstant.constantNodeForBroadcast(VectorAPIUtils.zeroConstant(vectorStamp.getComponent(0)), vectorStamp.getVectorLength()));
    }

    public static ValueNode expandIntegerCast(ConversionOp op, ValueNode value, int fromBits, int toBits) {
        if (fromBits < toBits) {
            boolean zeroExtend = op == ConversionOp.UCAST;
            if (zeroExtend) {
                return new ZeroExtendNode(value, fromBits, toBits);
            } else {
                return new SignExtendNode(value, fromBits, toBits);
            }
        } else {
            return new NarrowNode(value, fromBits, toBits);
        }
    }
}

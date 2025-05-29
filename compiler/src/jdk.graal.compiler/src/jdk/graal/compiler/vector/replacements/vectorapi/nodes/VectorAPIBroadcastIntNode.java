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

import java.util.Collections;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;

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
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Intrinsic node for the {@code VectorSupport.broadcastInt} method. This badly named operation is
 * <em>not</em> a broadcast (see {@link VectorAPIFromBitsCoercedNode} for that). This operation
 * applies a bitshift operation to each element of a vector {@code x} with a scalar shift amount
 * {@code s}, producing a result vector:
 * <p/>
 *
 * {@code
 *     result = <OP(x.0, s), OP(x.1, s), ..., OP(x.n, s)>
 * }
 *
 * <p/>
 * A mask is currently not supported. The shift operation is identified by an integer opcode which
 * we map to the corresponding Graal operation.
 */
@NodeInfo(nameTemplate = "VectorAPIBroadcastInt {p#op/s}")
public class VectorAPIBroadcastIntNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIBroadcastIntNode> TYPE = NodeClass.create(VectorAPIBroadcastIntNode.class);

    private final SimdStamp vectorStamp;
    private final ArithmeticOpTable.ShiftOp<?> op;

    /* Indices into the macro argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int VCLASS_ARG_INDEX = 1;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int VALUE_ARG_INDEX = 5;
    private static final int SHIFT_ARG_INDEX = 6;
    private static final int MASK_ARG_INDEX = 7;

    protected VectorAPIBroadcastIntNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.ShiftOp<?> op, SimdConstant constantValue) {
        this(macroParams, vectorStamp, op, constantValue, null);
    }

    protected VectorAPIBroadcastIntNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.ShiftOp<?> op, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = vectorStamp;
        this.op = op;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIBroadcastIntNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        ArithmeticOpTable.ShiftOp<?> op = improveShiftOp(null, macroParams.arguments, vectorStamp);
        SimdConstant constantValue = improveConstant(null, vectorStamp, op, macroParams.arguments, providers);
        return new VectorAPIBroadcastIntNode(macroParams, vectorStamp, op, constantValue);
    }

    private static ArithmeticOpTable.ShiftOp<?> improveShiftOp(ArithmeticOpTable.ShiftOp<?> oldOp, ValueNode[] arguments, SimdStamp vectorStamp) {
        if (oldOp != null) {
            return oldOp;
        }
        int opcode = oprIdAsConstantInt(arguments, OPRID_ARG_INDEX, vectorStamp);
        if (opcode == -1) {
            return null;
        }
        if (vectorStamp.isIntegerStamp()) {
            return VectorAPIOperations.lookupIntegerShiftOp(opcode);
        }
        return null;
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, SimdStamp newVectorStamp, ArithmeticOpTable.BinaryOp<?> newOp, ValueNode[] args, CoreProviders providers) {
        if (oldConstant != null) {
            return oldConstant;
        }
        if (newVectorStamp == null || newOp == null) {
            return null;
        }
        ValueNode mask = args[MASK_ARG_INDEX];
        if (!mask.isNullConstant()) {
            /* TODO GR-62819: masked constant folding */
            return null;
        }
        SimdConstant valueConstant = maybeConstantValue(args[VALUE_ARG_INDEX], providers);
        if (valueConstant == null) {
            return null;
        }
        JavaConstant shiftConstant = args[SHIFT_ARG_INDEX].asJavaConstant();
        if (shiftConstant == null) {
            return null;
        }
        ArithmeticOpTable.ShiftOp<?> simdOp = (ArithmeticOpTable.ShiftOp<?>) newVectorStamp.liftScalarOp(newOp);
        return (SimdConstant) simdOp.foldConstant(valueConstant, shiftConstant);
    }

    private ValueNode getVector() {
        return getArgument(VALUE_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return Collections.singletonList(getVector());
    }

    @Override
    public SimdStamp vectorStamp() {
        return vectorStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        SimdConstant constantValue = maybeConstantValue(this, tool);
        if (speciesStamp.isExactType() && vectorStamp != null && op != null && constantValue != null) {
            /* Nothing to improve. */
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        ArithmeticOpTable.ShiftOp<?> newOp = improveShiftOp(op, args, newVectorStamp);
        SimdConstant newConstantValue = improveConstant(constantValue, vectorStamp, op, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || (newOp != null && !newOp.equals(op)) || (newConstantValue != null && !newConstantValue.equals(constantValue))) {
            return new VectorAPIBroadcastIntNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newOp, newConstantValue, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        if (!((ObjectStamp) stamp).isExactType() || vectorStamp == null || op == null) {
            return false;
        }
        if (!getArgument(MASK_ARG_INDEX).isNullConstant()) {
            return false;
        }

        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp().getVectorLength();
        boolean supportedDirectly = vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, op) == vectorLength;
        if (supportedDirectly) {
            return true;
        } else {
            /*
             * Special case for byte shifts on AMD64: See if we can extend to shorts, shift, and
             * narrow back to bytes. Not relevant for AArch64, which has native byte shifts and
             * takes the "supportedDirectly" path above.
             */
            if (PrimitiveStamp.getBits(elementStamp) == Byte.SIZE) {
                IntegerStamp byteStamp = (IntegerStamp) elementStamp;
                IntegerStamp shortStamp = StampFactory.forInteger(Short.SIZE);
                ArithmeticOpTable.IntegerConvertOp<?> extend = (op.equals(byteStamp.getOps().getUShr()) ? byteStamp.getOps().getZeroExtend() : byteStamp.getOps().getSignExtend());
                return vectorArch.getSupportedVectorConvertLength(shortStamp, byteStamp, vectorLength, extend) == vectorLength &&
                                vectorArch.getSupportedVectorShiftWithScalarCount(shortStamp, vectorLength, op) == vectorLength &&
                                vectorArch.getSupportedVectorConvertLength(byteStamp, shortStamp, vectorLength, shortStamp.getOps().getNarrow()) == vectorLength;
            }
            return false;
        }
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return asSimdConstant(this, vectorArch);
        }
        ValueNode value = expanded.get(getVector());
        ValueNode shiftAmount = getArgument(SHIFT_ARG_INDEX);
        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp().getVectorLength();
        boolean supportedDirectly = vectorArch.getSupportedVectorShiftWithScalarCount(elementStamp, vectorLength, op) == vectorLength;
        if (supportedDirectly) {
            return ShiftNode.shiftOp(value, shiftAmount, NodeView.DEFAULT, op);
        } else {
            GraalError.guarantee(PrimitiveStamp.getBits(elementStamp) == Byte.SIZE, "unexpected stamp: %s", elementStamp);
            IntegerStamp byteStamp = (IntegerStamp) elementStamp;
            ValueNode extendedVector = (op.equals(byteStamp.getOps().getUShr())
                            ? ZeroExtendNode.create(value, Byte.SIZE, Short.SIZE, NodeView.DEFAULT)
                            : SignExtendNode.create(value, Byte.SIZE, Short.SIZE, NodeView.DEFAULT));
            ValueNode shiftedVector = ShiftNode.shiftOp(extendedVector, shiftAmount, NodeView.DEFAULT, op);
            ValueNode narrowedVector = NarrowNode.create(shiftedVector, Short.SIZE, Byte.SIZE, NodeView.DEFAULT);
            return narrowedVector;
        }
    }
}

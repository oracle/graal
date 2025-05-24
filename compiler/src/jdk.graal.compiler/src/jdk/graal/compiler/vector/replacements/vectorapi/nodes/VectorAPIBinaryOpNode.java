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

import java.util.List;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;

/**
 * Intrinsic node for the {@code VectorSupport.binaryOp} method. This operation applies a binary
 * arithmetic operation to the corresponding elements of two vectors {@code x} and {@code y},
 * producing a result vector:
 * <p/>
 *
 * {@code
 *     result = <OP(x.0, y.0), OP(x.1, y.1), ..., OP(x.n, y.n)>
 * }
 *
 * <p/>
 * If a mask is present, the operation is only applied to the elements selected by the mask. The
 * other elements get their value from the corresponding element of the {@code x} vector. The binary
 * operation is identified by an integer opcode which we map to the corresponding Graal operation.
 */
@NodeInfo(nameTemplate = "VectorAPIBinaryOp {p#op/s}")
public class VectorAPIBinaryOpNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIBinaryOpNode> TYPE = NodeClass.create(VectorAPIBinaryOpNode.class);

    private final SimdStamp vectorStamp;
    private final ArithmeticOpTable.BinaryOp<?> op;

    /* Indices into the macro argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int VMCLASS_ARG_INDEX = 1;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int X_ARG_INDEX = 5;
    private static final int Y_ARG_INDEX = 6;
    private static final int MASK_ARG_INDEX = 7;

    protected VectorAPIBinaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.BinaryOp<?> op, SimdConstant constantValue) {
        this(macroParams, vectorStamp, op, constantValue, null);
    }

    protected VectorAPIBinaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.BinaryOp<?> op, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = vectorStamp;
        this.op = op;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIBinaryOpNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VMCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        ArithmeticOpTable.BinaryOp<?> op = improveBinaryOp(null, macroParams.arguments, OPRID_ARG_INDEX, vectorStamp, providers);
        SimdConstant constantValue = improveConstant(null, vectorStamp, op, macroParams.arguments, providers);
        return new VectorAPIBinaryOpNode(macroParams, vectorStamp, op, constantValue);
    }

    private ValueNode vectorX() {
        return getArgument(X_ARG_INDEX);
    }

    private ValueNode vectorY() {
        return getArgument(Y_ARG_INDEX);
    }

    private ValueNode mask() {
        return getArgument(MASK_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vectorX(), vectorY(), mask());
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
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VMCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VMCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        ArithmeticOpTable.BinaryOp<?> newOp = improveBinaryOp(op, args, OPRID_ARG_INDEX, newVectorStamp, tool);
        SimdConstant newConstantValue = improveConstant(constantValue, newVectorStamp, newOp, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || (newOp != null && !newOp.equals(op)) || (newConstantValue != null && !newConstantValue.equals(constantValue))) {
            return new VectorAPIBinaryOpNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newOp, newConstantValue, stateAfter());
        }
        return this;
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
        SimdConstant xConstant = maybeConstantValue(args[X_ARG_INDEX], providers);
        if (xConstant == null) {
            return null;
        }
        SimdConstant yConstant = maybeConstantValue(args[Y_ARG_INDEX], providers);
        if (yConstant == null) {
            return null;
        }
        ArithmeticOpTable.BinaryOp<?> simdOp = (ArithmeticOpTable.BinaryOp<?>) newVectorStamp.liftScalarOp(newOp);
        return (SimdConstant) simdOp.foldConstant(xConstant, yConstant);
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (!speciesStamp.isExactType() || vectorStamp == null || op == null) {
            return false;
        }
        Stamp elementStamp = vectorStamp.getComponent(0);
        if (!mask().isNullConstant() && vectorArch.getSupportedVectorBlendLength(elementStamp, vectorStamp.getVectorLength()) != vectorStamp.getVectorLength()) {
            return false;
        }
        return vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorStamp.getVectorLength(), op) == vectorStamp.getVectorLength();
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return asSimdConstant(this, vectorArch);
        }
        ValueNode x = expanded.get(vectorX());
        ValueNode y = expanded.get(vectorY());
        ValueNode operation;
        if (SimdStamp.isOpmask(x.stamp(NodeView.DEFAULT))) {
            if (SimdStamp.OPMASK_OPS.getAnd().equals(op)) {
                operation = BinaryArithmeticNode.and(x, y);
            } else if (SimdStamp.OPMASK_OPS.getOr().equals(op)) {
                operation = BinaryArithmeticNode.or(x, y);
            } else if (SimdStamp.OPMASK_OPS.getXor().equals(op)) {
                operation = BinaryArithmeticNode.xor(x, y);
            } else {
                throw GraalError.shouldNotReachHere("unexpected operation for binary op mask arithmetic");
            }
        } else if (x.stamp(NodeView.DEFAULT).isIntegerStamp()) {
            operation = BinaryArithmeticNode.binaryIntegerOp(x, y, NodeView.DEFAULT, op);
        } else {
            operation = BinaryArithmeticNode.binaryFloatOp(x, y, NodeView.DEFAULT, op);
        }
        if (!mask().isNullConstant()) {
            ValueNode mask = expanded.get(mask());
            operation = VectorAPIBlendNode.expandBlendHelper(mask, x, operation);
        }
        return operation;
    }
}

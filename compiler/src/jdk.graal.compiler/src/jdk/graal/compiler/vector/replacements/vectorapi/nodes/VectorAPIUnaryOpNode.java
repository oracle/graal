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
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;

/**
 * Intrinsic node for the {@code VectorSupport.unaryOp} method. This operation applies a unary
 * arithmetic operation to each element of a vector {@code v}, producing a result vector:
 * <p/>
 *
 * {@code
 *     result = <OP(v.0), OP(v.1), ..., OP(v.n)>
 * }
 *
 * <p/>
 * A mask is currently not supported. The unary operation is identified by an integer opcode which
 * we map to the corresponding Graal operation.
 */
@NodeInfo(nameTemplate = "VectorAPIUnaryOp {p#op/s}")
public class VectorAPIUnaryOpNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIUnaryOpNode> TYPE = NodeClass.create(VectorAPIUnaryOpNode.class);

    private final SimdStamp vectorStamp;
    private final ArithmeticOpTable.UnaryOp<?> op;

    /* Indices into the macro argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int VCLASS_ARG_INDEX = 1;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int VALUE_ARG_INDEX = 5;
    private static final int MASK_ARG_INDEX = 6;

    protected VectorAPIUnaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.UnaryOp<?> op, SimdConstant constantValue) {
        this(macroParams, vectorStamp, op, constantValue, null);
    }

    protected VectorAPIUnaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.UnaryOp<?> op, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = vectorStamp;
        this.op = op;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIUnaryOpNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        ArithmeticOpTable.UnaryOp<?> op = computeOp(null, macroParams.arguments, vectorStamp);
        SimdConstant constantValue = improveConstant(null, vectorStamp, op, macroParams.arguments, providers);
        return new VectorAPIUnaryOpNode(macroParams, vectorStamp, op, constantValue);
    }

    private static ArithmeticOpTable.UnaryOp<?> computeOp(ArithmeticOpTable.UnaryOp<?> oldOp, ValueNode[] arguments, SimdStamp vectorStamp) {
        if (oldOp != null) {
            return oldOp;
        }
        int opcode = oprIdAsConstantInt(arguments, OPRID_ARG_INDEX, vectorStamp);
        if (opcode == -1) {
            return null;
        }
        if (vectorStamp.isIntegerStamp()) {
            return VectorAPIOperations.lookupIntegerUnaryOp(opcode);
        } else {
            return VectorAPIOperations.lookupFloatingPointUnaryOp(opcode);
        }
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, SimdStamp newVectorStamp, ArithmeticOpTable.UnaryOp<?> newOp, ValueNode[] args, CoreProviders providers) {
        if (oldConstant != null) {
            return oldConstant;
        }
        if (newVectorStamp == null) {
            return null;
        }
        if (newOp == null) {
            return null;
        }
        ValueNode mask = args[MASK_ARG_INDEX];
        if (!mask.isNullConstant()) {
            /* TODO GR-62819: masked constant folding */
            return null;
        }
        SimdConstant xConstant = maybeConstantValue(args[VALUE_ARG_INDEX], providers);
        if (xConstant == null) {
            return null;
        }
        ArithmeticOpTable.UnaryOp<?> simdOp = (ArithmeticOpTable.UnaryOp<?>) newVectorStamp.liftScalarOp(newOp);
        return (SimdConstant) simdOp.foldConstant(xConstant);
    }

    private ValueNode getVector() {
        return getArgument(VALUE_ARG_INDEX);
    }

    private ValueNode mask() {
        return getArgument(MASK_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(getVector(), mask());
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
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        ArithmeticOpTable.UnaryOp<?> newOp = computeOp(op, args, newVectorStamp);
        SimdConstant newConstantValue = improveConstant(constantValue, newVectorStamp, newOp, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || (newOp != null && !newOp.equals(op)) || (newConstantValue != null && !newConstantValue.equals(constantValue))) {
            return new VectorAPIUnaryOpNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newOp, newConstantValue, stateAfter());
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
        Stamp elementStamp = vectorStamp.getComponent(0);
        if (!mask().isNullConstant() && vectorArch.getSupportedVectorBlendLength(elementStamp, vectorStamp.getVectorLength()) != vectorStamp.getVectorLength()) {
            return false;
        }
        return vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorStamp.getVectorLength(), op) == vectorStamp.getVectorLength();
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            SimdConstant constantValue = maybeConstantValue(this, null);
            return new ConstantNode(constantValue, vectorStamp);
        }
        ValueNode value = expanded.get(getVector());
        ValueNode result;
        if (value.stamp(NodeView.DEFAULT).isIntegerStamp()) {
            result = UnaryArithmeticNode.unaryIntegerOp(value, NodeView.DEFAULT, op);
        } else {
            result = UnaryArithmeticNode.unaryFloatOp(value, NodeView.DEFAULT, op);
        }
        if (!mask().isNullConstant()) {
            ValueNode mask = expanded.get(mask());
            result = VectorAPIBlendNode.expandBlendHelper(mask, value, result);
        }
        return result;
    }
}

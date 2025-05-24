/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.TernaryArithmeticNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;

/**
 * Parsing and implementing VectorSupport::ternaryOp intrinsics.
 */
@NodeInfo(nameTemplate = "VectorAPITernaryOp {p#op/s}")
public class VectorAPITernaryOpNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPITernaryOpNode> TYPE = NodeClass.create(VectorAPITernaryOpNode.class);

    private final SimdStamp vectorStamp;
    private final ArithmeticOpTable.TernaryOp<?> op;

    /* Indices into the argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int VCLASS_ARG_INDEX = 1;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int X_ARG_INDEX = 5;
    private static final int Y_ARG_INDEX = 6;
    private static final int Z_ARG_INDEX = 7;
    private static final int MASK_ARG_INDEX = 8;

    protected VectorAPITernaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.TernaryOp<?> op, SimdConstant constantValue) {
        this(macroParams, vectorStamp, op, constantValue, null);
    }

    protected VectorAPITernaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ArithmeticOpTable.TernaryOp<?> op, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = vectorStamp;
        this.op = op;
        this.stateAfter = stateAfter;
    }

    private static ArithmeticOpTable.TernaryOp<?> computeOp(ArithmeticOpTable.TernaryOp<?> oldOp, ValueNode[] arguments, SimdStamp vectorStamp) {
        if (oldOp != null) {
            return oldOp;
        }
        int opcode = oprIdAsConstantInt(arguments, OPRID_ARG_INDEX, vectorStamp);
        if (opcode == -1) {
            return null;
        }
        if (vectorStamp.isIntegerStamp()) {
            throw GraalError.shouldNotReachHere("No integral ternary operation");
        } else {
            return VectorAPIOperations.lookupFloatingPointTernaryOp(opcode);
        }
    }

    public static VectorAPITernaryOpNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        ArithmeticOpTable.TernaryOp<?> op = computeOp(null, macroParams.arguments, vectorStamp);
        SimdConstant constantValue = improveConstant(null, vectorStamp, op, macroParams.arguments, providers);
        return new VectorAPITernaryOpNode(macroParams, vectorStamp, op, constantValue);
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, SimdStamp newVectorStamp, ArithmeticOpTable.TernaryOp<?> newOp, ValueNode[] args, CoreProviders providers) {
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
        SimdConstant zConstant = maybeConstantValue(args[Z_ARG_INDEX], providers);
        if (zConstant == null) {
            return null;
        }
        ArithmeticOpTable.TernaryOp<?> simdOp = (ArithmeticOpTable.TernaryOp<?>) newVectorStamp.liftScalarOp(newOp);
        return (SimdConstant) simdOp.foldConstant(xConstant, yConstant, zConstant);
    }

    private ValueNode vectorX() {
        return getArgument(X_ARG_INDEX);
    }

    private ValueNode vectorY() {
        return getArgument(Y_ARG_INDEX);
    }

    private ValueNode vectorZ() {
        return getArgument(Z_ARG_INDEX);
    }

    private ValueNode mask() {
        return getArgument(MASK_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vectorX(), vectorY(), vectorZ(), mask());
    }

    @Override
    public Stamp vectorStamp() {
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
        ArithmeticOpTable.TernaryOp<?> newOp = computeOp(op, args, newVectorStamp);
        SimdConstant newConstantValue = improveConstant(constantValue, newVectorStamp, newOp, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || (newOp != null && !newOp.equals(op)) || (newConstantValue != null && !newConstantValue.equals(constantValue))) {
            return new VectorAPITernaryOpNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newOp, newConstantValue, stateAfter());
        }
        return this;
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
        ValueNode valueX = expanded.get(vectorX());
        ValueNode valueY = expanded.get(vectorY());
        ValueNode valueZ = expanded.get(vectorZ());
        if (valueX.stamp(NodeView.DEFAULT).isIntegerStamp()) {
            throw GraalError.shouldNotReachHere("No integral ternary operation");
        }

        ValueNode result = TernaryArithmeticNode.ternaryFloatOp(valueX, valueY, valueZ, NodeView.DEFAULT, op);
        if (!mask().isNullConstant()) {
            ValueNode mask = expanded.get(mask());
            result = VectorAPIBlendNode.expandBlendHelper(mask, valueX, result);
        }
        return result;
    }
}

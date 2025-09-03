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
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIUtils;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsic node for the {@code VectorSupport.fromBitsCoerced} method. This operation takes a
 * scalar {@code value} of type {@code long}, representing the coerced bits of some primitive value.
 * It interprets those bits as a value of a target type and broadcasts that value, producing a
 * result vector:
 * <p/>
 *
 * {@code
 * x = reinterpret(value);
 * result = <x, x, ..., x>
 * }
 *
 * <p/>
 * A second operation mode builds a vector mask from the input scalar, this mode is not supported
 * yet by Graal.
 */
@NodeInfo
public class VectorAPIFromBitsCoercedNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIFromBitsCoercedNode> TYPE = NodeClass.create(VectorAPIFromBitsCoercedNode.class);

    private enum Mode {
        BROADCAST
    }

    private final SimdStamp vectorStamp;
    private final Mode mode;

    /* Indices into the macro argument list for relevant input values. */
    private static final int VMCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 1;
    private static final int LENGTH_ARG_INDEX = 2;
    private static final int VALUE_ARG_INDEX = 3;

    private static final int MODE_ARG_INDEX = 4;

    protected VectorAPIFromBitsCoercedNode(MacroParams macroParams, SimdStamp vectorStamp, Mode mode, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = vectorStamp;
        this.mode = mode;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIFromBitsCoercedNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp valueStamp = improveVectorStamp(null, macroParams.arguments, VMCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        Mode mode = improveMode(null, macroParams.arguments);
        SimdConstant constantValue = improveConstant(null, valueStamp, mode, macroParams.arguments);
        return new VectorAPIFromBitsCoercedNode(macroParams, valueStamp, mode, constantValue, null);
    }

    private static Mode improveMode(Mode oldMode, ValueNode[] arguments) {
        if (oldMode != null) {
            return oldMode;
        }
        ValueNode mode = arguments[MODE_ARG_INDEX];
        if (mode.isJavaConstant() && mode.asJavaConstant().getJavaKind() == JavaKind.Int && mode.asJavaConstant().asInt() == 0) {
            return Mode.BROADCAST;
        }
        return null;
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, SimdStamp newVectorStamp, Mode newMode, ValueNode[] args) {
        if (oldConstant != null) {
            return oldConstant;
        }
        if (newVectorStamp == null || SimdStamp.isOpmask(newVectorStamp)) {
            return null;
        }
        if (newMode != Mode.BROADCAST) {
            return null;
        }
        ValueNode value = args[VALUE_ARG_INDEX];
        if (!value.isJavaConstant()) {
            return null;
        }
        JavaConstant elementConstant = reinterpretedConstant(value.asJavaConstant().asLong(), newVectorStamp.getComponent(0));
        if (elementConstant == null) {
            return null;
        }
        return SimdConstant.broadcast(elementConstant, newVectorStamp.getVectorLength());
    }

    private static JavaConstant reinterpretedConstant(long constantBits, Stamp toStamp) {
        if (toStamp instanceof FloatStamp floatStamp) {
            return floatStamp.getBits() == Float.SIZE ? JavaConstant.forFloat(Float.intBitsToFloat((int) constantBits))
                            : JavaConstant.forDouble(Double.longBitsToDouble(constantBits));
        } else if (toStamp instanceof IntegerStamp integerStamp) {
            return JavaConstant.forPrimitiveInt(integerStamp.getBits(), constantBits);
        }
        return null;
    }

    public ValueNode getValue() {
        return getArgument(VALUE_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return Collections.emptyList();
    }

    @Override
    public SimdStamp vectorStamp() {
        return vectorStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        SimdConstant constantValue = maybeConstantValue(this, tool);
        if (speciesStamp.isExactType() && vectorStamp != null && mode != null && constantValue != null) {
            /* Nothing to improve. */
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VMCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VMCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        Mode newMode = improveMode(mode, args);
        SimdConstant newConstantValue = improveConstant(constantValue, newVectorStamp, newMode, args);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || newMode != mode || newConstantValue != constantValue) {
            return new VectorAPIFromBitsCoercedNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newMode, newConstantValue, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        if (!((ObjectStamp) stamp).isExactType() || vectorStamp == null || mode != Mode.BROADCAST) {
            return false;
        }
        if (SimdStamp.isOpmask(vectorStamp)) {
            /*
             * We're asked to take a long value and turn it into a vector of logic values. We do
             * this by broadcasting the input value to a vector, then comparing its elements to
             * zero. Use byte to minimize the size of the operation.
             */
            IntegerStamp elementStamp = IntegerStamp.create(Byte.SIZE);
            boolean canBroadcast = vectorArch.getSupportedVectorMoveLength(elementStamp, vectorStamp.getVectorLength()) == vectorStamp.getVectorLength();
            boolean canCompare = vectorArch.getSupportedVectorComparisonLength(elementStamp, CanonicalCondition.EQ, vectorStamp.getVectorLength()) == vectorStamp.getVectorLength();
            return canBroadcast && canCompare;
        }
        /*
         * We don't have a way of checking for supported broadcast lengths, it's implied that sane
         * targets allow us any kind of broadcast or that we can emulate them.
         */
        return vectorArch.getSupportedVectorMoveLength(vectorStamp.getComponent(0), vectorStamp.getVectorLength()) == vectorStamp.getVectorLength();
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return asSimdConstant(this, vectorArch);
        }
        /*
         * The input value represents a scalar using its long bits. This node represents
         * reinterpretation of that scalar as a different type and a broadcast of the reinterpreted
         * value as a vector.
         */
        IntegerStamp inputStamp = (IntegerStamp) getValue().stamp(NodeView.DEFAULT);
        GraalError.guarantee(PrimitiveStamp.getBits(inputStamp) == 64, "expected long stamp: %s", inputStamp);
        Stamp resultElementStamp = vectorStamp.getComponent(0);
        if (resultElementStamp instanceof LogicValueStamp) {
            // For opmasks, we create a vector of resultElementStamp and compare with 0, use byte
            // to minimize the size of the operation
            resultElementStamp = IntegerStamp.create(Byte.SIZE);
        }
        GraalError.guarantee(resultElementStamp instanceof PrimitiveStamp, "expected primitive stamp: %s", resultElementStamp);
        ValueNode inputValue = getValue();
        boolean needNarrow = PrimitiveStamp.getBits(resultElementStamp) < PrimitiveStamp.getBits(inputStamp);
        if (needNarrow) {
            inputValue = new NarrowNode(inputValue, PrimitiveStamp.getBits(resultElementStamp));
        }
        Stamp maybeNarrowedInputStamp = inputValue.stamp(NodeView.DEFAULT);
        boolean needReinterpret = !maybeNarrowedInputStamp.isCompatible(resultElementStamp);
        if (needReinterpret) {
            GraalError.guarantee(resultElementStamp instanceof FloatStamp, "reinterpret should not be needed for integers after narrowing: %s", resultElementStamp);
            inputValue = new ReinterpretNode(resultElementStamp.getStackKind(), inputValue);
        }
        ValueNode broadcast = new SimdBroadcastNode(inputValue, vectorStamp.getVectorLength());

        if (SimdStamp.isOpmask(vectorStamp)) {
            return VectorAPIUtils.isNonzero(broadcast, vectorArch);
        } else {
            return broadcast;
        }
    }
}

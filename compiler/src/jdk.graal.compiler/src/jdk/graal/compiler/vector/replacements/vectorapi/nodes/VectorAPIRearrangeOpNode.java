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
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteWithVectorIndicesNode;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Intrinsic node for the {@code VectorSupport.rearrangeOp} method. This operation applies a shuffle
 * {@code sh} to the elements of the input vector {@code v}, the indices are wrapped around:
 * <p/>
 *
 * {@code
 *     result = <x[sh[0] & (n-1)], x[sh[1] & (n-1)], ..., x[sh[n-1] & (n-1)]>
 * }
 *
 * The shuffle is a vector of integer indices.
 */
@NodeInfo
public class VectorAPIRearrangeOpNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIRearrangeOpNode> TYPE = NodeClass.create(VectorAPIRearrangeOpNode.class);

    private final SimdStamp vectorStamp;

    /* Indices into the macro argument list for relevant input values. */
    private static final int VCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int V_ARG_INDEX = 5;
    private static final int SH_ARG_INDEX = 6;
    private static final int M_ARG_INDEX = 7;

    protected VectorAPIRearrangeOpNode(MacroParams macroParams, SimdStamp vectorStamp, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = vectorStamp;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIRearrangeOpNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        SimdConstant constantValue = improveConstant(null, macroParams.arguments, providers);
        return new VectorAPIRearrangeOpNode(macroParams, vectorStamp, constantValue, null);
    }

    private ValueNode vector() {
        return getArgument(V_ARG_INDEX);
    }

    private ValueNode shuffle() {
        return getArgument(SH_ARG_INDEX);
    }

    private ValueNode mask() {
        return getArgument(M_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vector(), shuffle(), mask());
    }

    @Override
    public Stamp vectorStamp() {
        return vectorStamp;
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, ValueNode[] args, CoreProviders providers) {
        if (oldConstant != null) {
            return oldConstant;
        }
        SimdConstant v = maybeConstantValue(args[V_ARG_INDEX], providers);
        SimdConstant sh = maybeConstantValue(args[SH_ARG_INDEX], providers);
        if (v == null || sh == null) {
            return null;
        }
        Constant[] newValues = new Constant[v.getVectorLength()];
        GraalError.guarantee(CodeUtil.isPowerOf2(v.getVectorLength()), "vector length must be power of 2: %s", v.getVectorLength());
        int indexMask = v.getVectorLength() - 1;
        for (int i = 0; i < v.getVectorLength(); i++) {
            int shuffledIndex = (int) ((JavaConstant) sh.getValue(i)).asLong() & indexMask;
            newValues[i] = v.getValue(shuffledIndex);
        }
        return new SimdConstant(newValues);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        SimdConstant constantValue = maybeConstantValue(this, tool);
        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        SimdConstant newConstantValue = improveConstant(constantValue, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || newConstantValue != constantValue) {
            return new VectorAPIRearrangeOpNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newConstantValue, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (!speciesStamp.isExactType() || vectorStamp == null) {
            return false;
        }
        ObjectStamp shuffleStamp = (ObjectStamp) shuffle().stamp(NodeView.DEFAULT);
        if (!shuffleStamp.nonNull()) {
            return false;
        }
        int vectorLength = vectorStamp.getVectorLength();
        Stamp elementStamp = vectorStamp.getComponent(0);
        if (!mask().isNullConstant() && vectorArch.getSupportedVectorBlendLength(elementStamp, vectorStamp.getVectorLength()) != vectorStamp.getVectorLength()) {
            return false;
        }
        return vectorArch.getSupportedVectorPermuteLength(elementStamp, vectorLength) == vectorLength;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return asSimdConstant(this, vectorArch);
        }
        ValueNode vector = expanded.get(vector());
        int length = vectorStamp.getVectorLength();
        ValueNode shuffle = expanded.get(shuffle());
        ValueNode shuffleMask = ConstantNode.forIntegerBits(PrimitiveStamp.getBits(vectorStamp.getComponent(0)), length - 1);
        shuffle = AndNode.create(shuffle, new SimdBroadcastNode(shuffleMask, length), NodeView.DEFAULT);
        ValueNode result = SimdPermuteWithVectorIndicesNode.create(vector, shuffle);
        if (!mask().isNullConstant()) {
            ValueNode mask = expanded.get(mask());
            ValueNode zero;
            if (vectorStamp.getComponent(0) instanceof IntegerStamp i) {
                zero = ConstantNode.forIntegerBits(i.getBits(), 0);
            } else {
                zero = PrimitiveStamp.getBits(vectorStamp.getComponent(0)) == Float.SIZE ? ConstantNode.forFloat(0) : ConstantNode.forDouble(0);
            }
            zero = new SimdBroadcastNode(zero, vectorStamp.getVectorLength());
            result = VectorAPIBlendNode.expandBlendHelper(mask, zero, result);
        }
        return result;
    }
}

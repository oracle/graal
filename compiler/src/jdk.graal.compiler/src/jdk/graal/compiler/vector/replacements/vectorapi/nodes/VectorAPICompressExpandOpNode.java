/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.collections.EconomicMap;

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
import jdk.graal.compiler.nodes.calc.CompressBitsNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.amd64.IntegerToOpMaskNode;
import jdk.graal.compiler.vector.nodes.amd64.OpMaskToIntegerNode;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdCompressNode;
import jdk.graal.compiler.vector.nodes.simd.SimdExpandNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;

/**
 * Intrinsics node for {@code VectorSupport::compressExpandOp}. There are 3 kinds of operation here
 * <ul>
 * <li>Compress: Similar to {@link jdk.graal.compiler.nodes.calc.CompressBitsNode} but it compresses
 * the elements of a vector instead of compressing the bits of an integer.
 * <li>Expand: Similar to {@link jdk.graal.compiler.nodes.calc.ExpandBitsNode} but it expands the
 * elements of a vector instead of expanding the bits of an integer.
 * <li>Mask compress: The first operand is {@code null}, the second operand is compressed using
 * itself as the bit mask.
 * </ul>
 */
@NodeInfo
public class VectorAPICompressExpandOpNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPICompressExpandOpNode> TYPE = NodeClass.create(VectorAPICompressExpandOpNode.class);

    private static final int OPR_ARG_INDEX = 0;
    private static final int VCLASS_ARG_INDEX = 1;
    private static final int MCLASS_ARG_INDEX = 2;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int V_ARG_INDEX = 5;
    private static final int M_ARG_INDEX = 6;

    private static final int COMPRESS_OP = VectorAPIOperations.Constants.CONSTANT_MAP.get("VECTOR_OP_COMPRESS");
    private static final int EXPAND_OP = VectorAPIOperations.Constants.CONSTANT_MAP.get("VECTOR_OP_EXPAND");
    private static final int MASK_COMPRESS_OP = VectorAPIOperations.Constants.CONSTANT_MAP.get("VECTOR_OP_MASK_COMPRESS");

    private final SimdStamp vectorStamp;

    protected VectorAPICompressExpandOpNode(MacroParams macroParams, SimdStamp vectorStamp, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, macroParams, constantValue);
        this.vectorStamp = vectorStamp;
        this.stateAfter = stateAfter;
    }

    public static VectorAPICompressExpandOpNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveResultStamp(null, macroParams.arguments, providers);
        return new VectorAPICompressExpandOpNode(macroParams, vectorStamp, null, null);
    }

    private ValueNode opr() {
        return getArgument(OPR_ARG_INDEX);
    }

    private ValueNode source() {
        return getArgument(V_ARG_INDEX);
    }

    private ValueNode mask() {
        return getArgument(M_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return source().isNullConstant() ? List.of(mask()) : List.of(source(), mask());
    }

    @Override
    public SimdStamp vectorStamp() {
        return vectorStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (speciesStamp.isExactType() && vectorStamp != null) {
            /* Nothing to improve. */
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveResultBoxStamp(tool);
        SimdStamp newVectorStamp = improveResultStamp(vectorStamp, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp) {
            return new VectorAPICompressExpandOpNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, null, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (!speciesStamp.isExactType() || vectorStamp == null || !opr().isJavaConstant()) {
            return false;
        }
        Stamp elementStamp = vectorStamp.getComponent(0);
        int opr = opr().asJavaConstant().asInt();
        GraalError.guarantee(opr == COMPRESS_OP || opr == EXPAND_OP || opr == MASK_COMPRESS_OP, "%d", opr);
        if (opr == MASK_COMPRESS_OP) {
            return elementStamp instanceof LogicValueStamp;
        } else {
            return vectorArch.getSupportedVectorCompressExpandLength(elementStamp, vectorStamp.getVectorLength()) == vectorStamp.getVectorLength();
        }
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        int opr = opr().asJavaConstant().asInt();
        ValueNode mask = expanded.get(mask());
        if (opr == COMPRESS_OP) {
            ValueNode src = expanded.get(source());
            return SimdCompressNode.create(src, mask);
        } else if (opr == EXPAND_OP) {
            ValueNode src = expanded.get(source());
            return SimdExpandNode.create(src, mask);
        } else {
            GraalError.guarantee(opr == MASK_COMPRESS_OP, "unexpected opcode %d", opr);
            ValueNode maskToInt = OpMaskToIntegerNode.create(mask);
            ValueNode compressedInt = new CompressBitsNode(maskToInt, maskToInt);
            return new IntegerToOpMaskNode(compressedInt, mask.stamp(NodeView.DEFAULT).unrestricted());
        }
    }

    private static SimdStamp improveResultStamp(SimdStamp oldStamp, ValueNode[] arguments, CoreProviders providers) {
        if (oldStamp != null) {
            return oldStamp;
        }
        ValueNode oprNode = arguments[OPR_ARG_INDEX];
        if (!oprNode.isJavaConstant()) {
            return null;
        }
        int opr = oprNode.asJavaConstant().asInt();
        return improveVectorStamp(null, arguments, opr == MASK_COMPRESS_OP ? MCLASS_ARG_INDEX : VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
    }

    private ObjectStamp improveResultBoxStamp(CoreProviders providers) {
        if (!opr().isJavaConstant()) {
            return (ObjectStamp) stamp;
        }
        int opr = opr().asJavaConstant().asInt();
        return improveSpeciesStamp(providers, opr == MASK_COMPRESS_OP ? MCLASS_ARG_INDEX : VCLASS_ARG_INDEX);
    }
}

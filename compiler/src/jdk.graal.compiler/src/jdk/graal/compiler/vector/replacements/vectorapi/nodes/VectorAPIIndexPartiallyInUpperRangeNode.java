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

import java.util.Collections;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIUtils;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

/**
 * Intrinsic node for the {@code VectorSupport.indexPartiallyInUpperRange} method. This method
 * provides a mask from {@code offset} and {@code limit}, all elements at indices from {@code 0}
 * (inclusive) to {@code limit - offset} (exclusive) are set, while the remaining elements are
 * unset.
 * <p>
 * {@code result[i] = i < limit - offset ? true : false}
 * <p>
 * It is ensured by the caller that {@code 0 < limit - offset < VLENGTH}.
 */
@NodeInfo
public class VectorAPIIndexPartiallyInUpperRangeNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIIndexPartiallyInUpperRangeNode> TYPE = NodeClass.create(VectorAPIIndexPartiallyInUpperRangeNode.class);

    private final SimdStamp logicStamp;

    /* Indices into the macro argument list for relevant input values. */
    private static final int MCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 1;
    private static final int LENGTH_ARG_INDEX = 2;
    private static final int OFFSET_ARG_INDEX = 3;
    private static final int LIMIT_ARG_INDEX = 4;

    protected VectorAPIIndexPartiallyInUpperRangeNode(MacroParams macroParams, SimdStamp logicStamp, FrameState stateAfter) {
        super(TYPE, macroParams, null /* TODO GR-62819: masked constant folding */);
        this.logicStamp = logicStamp;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIIndexPartiallyInUpperRangeNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp logicStamp = improveVectorStamp(null, macroParams.arguments, MCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        return new VectorAPIIndexPartiallyInUpperRangeNode(macroParams, logicStamp, null);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return Collections.emptyList();
    }

    @Override
    public SimdStamp vectorStamp() {
        return logicStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (speciesStamp.isExactType() && logicStamp != null) {
            /* Nothing to improve. */
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, MCLASS_ARG_INDEX);
        SimdStamp newLogicStamp = improveVectorStamp(null, args, MCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        if (newSpeciesStamp != speciesStamp || newLogicStamp != logicStamp) {
            return new VectorAPIIndexPartiallyInUpperRangeNode(copyParamsWithImprovedStamp(newSpeciesStamp), newLogicStamp, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (!((ObjectStamp) stamp).isExactType() || logicStamp == null) {
            return false;
        }

        // In the general case, we use a comparison indexVector < limit - offset
        Stamp indexStamp;
        if (logicStamp.getComponent(0) instanceof LogicValueStamp) {
            indexStamp = IntegerStamp.create(Byte.SIZE);
        } else {
            indexStamp = logicStamp.getComponent(0);
        }
        int length = logicStamp.getVectorLength();
        return vectorArch.getSupportedVectorComparisonLength(indexStamp, CanonicalCondition.LT, length) == length;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        int width;
        if (logicStamp.getComponent(0) instanceof LogicValueStamp) {
            width = Byte.SIZE;
        } else {
            width = PrimitiveStamp.getBits(logicStamp.getComponent(0));
        }
        int length = logicStamp.getVectorLength();
        ConstantNode lhs = VectorAPIUtils.iotaVector(width, length);
        ValueNode sub = SubNode.create(getArgument(LIMIT_ARG_INDEX), getArgument(OFFSET_ARG_INDEX), NodeView.DEFAULT);
        sub = IntegerConvertNode.convert(sub, IntegerStamp.create(width), NodeView.DEFAULT);
        ValueNode rhs = new SimdBroadcastNode(sub, length);
        return SimdPrimitiveCompareNode.simdCompare(CanonicalCondition.LT, lhs, rhs, false, vectorArch);
    }
}

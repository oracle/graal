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

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdBlendWithLogicMaskNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

/**
 * Intrinsic node for the {@code VectorSupport.blend} method. This operation merges two vectors
 * {@code f} and {@code t} under the control of a mask {@code m}, picking each element from the
 * corresponding element of {@code f} or {@code t} according to whether the respective element of
 * {@code m} is false or true:
 * <p/>
 *
 * {@code
 *     result = <m.0 ? t.0 : f.0, m.1 ? t.1 : f.1, ..., m.n ? t.n : f.n>
 * }
 *
 * <p/>
 * Note that, unlike the Java conditional expression, the false input comes before the true input.
 * This matches the conventions in hardware.
 */
@NodeInfo
public class VectorAPIBlendNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIBlendNode> TYPE = NodeClass.create(VectorAPIBlendNode.class);

    private final SimdStamp vectorStamp;

    /* Indices into the macro argument list for relevant input values. */
    private static final int VCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 2;
    private static final int LENGTH_ARG_INDEX = 3;
    private static final int FALSE_ARG_INDEX = 4;
    private static final int TRUE_ARG_INDEX = 5;
    private static final int MASK_ARG_INDEX = 6;

    protected VectorAPIBlendNode(MacroParams macroParams, SimdStamp vectorStamp, FrameState stateAfter) {
        super(TYPE, macroParams, null /* TODO GR-62819: masked constant folding */);
        this.vectorStamp = vectorStamp;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIBlendNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        return new VectorAPIBlendNode(macroParams, vectorStamp, null);
    }

    public ValueNode falseVector() {
        return arguments.get(FALSE_ARG_INDEX);
    }

    public ValueNode trueVector() {
        return arguments.get(TRUE_ARG_INDEX);
    }

    public ValueNode maskVector() {
        return arguments.get(MASK_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(falseVector(), trueVector(), maskVector());
    }

    @Override
    public Stamp vectorStamp() {
        return vectorStamp;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (!((ObjectStamp) stamp).isExactType()) {
            return false;
        }
        return vectorArch.getSupportedVectorBlendLength(vectorStamp.getComponent(0), vectorStamp.getVectorLength()) == vectorStamp.getVectorLength();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (speciesStamp.isExactType() && vectorStamp != null) {
            /* Nothing to improve. */
            return this;
        }

        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, toArgumentArray(), VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp) {
            return new VectorAPIBlendNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, stateAfter());
        }
        return this;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        ValueNode falseValues = expanded.get(falseVector());
        ValueNode trueValues = expanded.get(trueVector());
        ValueNode mask = expanded.get(maskVector());

        return expandBlendHelper(mask, falseValues, trueValues);
    }

    /**
     * Builds a blend operation. This helper may be called for the expansion of other Vector API
     * macro nodes.
     */
    public static ValueNode expandBlendHelper(ValueNode mask, ValueNode falseValues, ValueNode trueValues) {
        return SimdBlendWithLogicMaskNode.create(falseValues, trueValues, mask);
    }
}

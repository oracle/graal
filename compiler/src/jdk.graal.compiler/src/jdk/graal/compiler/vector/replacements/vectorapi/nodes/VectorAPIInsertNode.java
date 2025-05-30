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
import jdk.graal.compiler.vector.nodes.simd.SimdInsertNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

import jdk.graal.compiler.core.common.type.ArithmeticStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
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

/**
 * Intrinsics node for the {@code VectorSupport::insert} method. This returns a new vector that has
 * the same elements as the argument except at the position i at which it is replaced with val.
 * <p>
 * {@code
 * result = v;
 * result[i] = reinterpretAsElementType(val);
 * }
 *
 * {@snippet :
 * public static <V extends Vector<E>, E> V insert(Class<? extends V> vClass, Class<E> eClass,
 *                 int length,
 *                 V v, int i, long val,
 *                 VecInsertOp<V> defaultImpl);
 * }
 */
@NodeInfo
public class VectorAPIInsertNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIInsertNode> TYPE = NodeClass.create(VectorAPIInsertNode.class);

    /* Indices into the macro argument list for relevant input values. */
    private static final int VCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 1;
    private static final int LENGTH_ARG_INDEX = 2;
    private static final int V_ARG_INDEX = 3;
    private static final int IDX_ARG_INDEX = 4;
    private static final int VAL_ARG_INDEX = 5;

    private final SimdStamp vectorStamp;

    protected VectorAPIInsertNode(MacroParams params, SimdStamp vectorStamp, SimdConstant constantValue, FrameState stateAfter) {
        super(TYPE, params, constantValue);
        this.vectorStamp = vectorStamp;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIInsertNode create(MacroParams params, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, params.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        SimdConstant constantValue = improveConstant(null, vectorStamp, params.arguments, providers);
        return new VectorAPIInsertNode(params, vectorStamp, constantValue, null);
    }

    private static SimdConstant improveConstant(SimdConstant oldConstant, SimdStamp newVectorStamp, ValueNode[] args, CoreProviders providers) {
        if (oldConstant != null) {
            return oldConstant;
        }
        if (newVectorStamp == null) {
            return null;
        }
        SimdConstant vectorConstant = maybeConstantValue(args[V_ARG_INDEX], providers);
        if (vectorConstant == null) {
            return null;
        }
        ValueNode idx = args[IDX_ARG_INDEX];
        ValueNode val = args[VAL_ARG_INDEX];
        if (!idx.isJavaConstant() || !(0 <= idx.asJavaConstant().asInt() && idx.asJavaConstant().asInt() < newVectorStamp.getVectorLength()) || !val.isJavaConstant()) {
            return null;
        }
        /*
         * Narrow the given constant to the appropriate number of bits, then reinterpret as the
         * target type.
         */
        ArithmeticStamp scalarTargetStamp = (ArithmeticStamp) newVectorStamp.getComponent(0);
        int bits = PrimitiveStamp.getBits(scalarTargetStamp);
        JavaConstant narrowBits = bits < Long.SIZE
                        ? (JavaConstant) IntegerStamp.create(bits).getOps().getNarrow().foldConstant(Long.SIZE, bits, val.asJavaConstant())
                        : val.asJavaConstant();
        JavaConstant reinterpretValue = (JavaConstant) scalarTargetStamp.getOps().getReinterpret().foldConstant(scalarTargetStamp, narrowBits);
        return SimdConstant.insert(vectorConstant, idx.asJavaConstant().asInt(), reinterpretValue);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vector());
    }

    @Override
    public Stamp vectorStamp() {
        return vectorStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        SimdConstant constantValue = maybeConstantValue(this, tool);
        if (speciesStamp.isExactType() && vectorStamp != null && constantValue != null) {
            /* Nothing to improve. */
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        SimdConstant newConstantValue = improveConstant(constantValue, newVectorStamp, args, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || newConstantValue != constantValue) {
            return new VectorAPIInsertNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newConstantValue, stateAfter());
        }

        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return true;
        }
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        return speciesStamp.isExactType() && vectorStamp != null && idx().isJavaConstant();
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        if (isRepresentableSimdConstant(this, vectorArch)) {
            return asSimdConstant(this, vectorArch);
        }
        ValueNode vector = expanded.get(vector());
        ValueNode val = val();
        PrimitiveStamp eStamp = (PrimitiveStamp) vectorStamp.getComponent(0);
        if (eStamp.getBits() < Long.SIZE) {
            val = NarrowNode.create(val, eStamp.getBits(), NodeView.DEFAULT);
        }
        if (eStamp.isFloatStamp()) {
            val = ReinterpretNode.create(eStamp.getStackKind(), val, NodeView.DEFAULT);
        }
        return SimdInsertNode.create(vector, val, idx().asJavaConstant().asInt());
    }

    private ValueNode vector() {
        return getArgument(V_ARG_INDEX);
    }

    private ValueNode idx() {
        return getArgument(IDX_ARG_INDEX);
    }

    private ValueNode val() {
        return getArgument(VAL_ARG_INDEX);
    }
}

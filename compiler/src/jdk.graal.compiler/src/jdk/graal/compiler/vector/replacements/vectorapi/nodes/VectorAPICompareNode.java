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

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.calc.Condition.CanonicalizedCondition;
import jdk.graal.compiler.core.common.type.ArithmeticStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIUtils;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsic node for the {@code VectorSupport.compare} method. This operation applies a comparison
 * operation to the corresponding elements of two vectors {@code x} and {@code y}, producing a
 * result mask vector:
 * <p/>
 *
 * {@code
 *     result = <OP(x.0, y.0), OP(x.1, y.1), ..., OP(x.n, y.n)>
 * }
 *
 * <p/>
 * An input mask is currently not supported. The comparison operation is identified by an integer
 * condition code which we map to the corresponding Graal operation.
 */
@NodeInfo(nameTemplate = "VectorAPICompare {p#condition/s}")
public class VectorAPICompareNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPICompareNode> TYPE = NodeClass.create(VectorAPICompareNode.class);

    private final SimdStamp inputStamp;
    private final SimdStamp logicStamp;
    private final Condition condition;
    private final CanonicalizedCondition canonicalizedCondition;

    /* Indices into the macro argument list for relevant input values. */
    private static final int COND_ARG_INDEX = 0;
    private static final int VCLASS_ARG_INDEX = 1;
    private static final int MCLASS_ARG_INDEX = 2;
    private static final int ECLASS_ARG_INDEX = 3;
    private static final int LENGTH_ARG_INDEX = 4;
    private static final int X_ARG_INDEX = 5;
    private static final int Y_ARG_INDEX = 6;
    private static final int M_ARG_INDEX = 7;

    protected VectorAPICompareNode(MacroParams macroParams, SimdStamp inputStamp, SimdStamp logicStamp, Condition condition, FrameState stateAfter) {
        super(TYPE, macroParams, null /* TODO GR-62819: masked constant folding */);
        this.inputStamp = inputStamp;
        this.logicStamp = logicStamp;
        this.condition = condition;
        this.canonicalizedCondition = condition != null ? condition.canonicalize() : null;
        this.stateAfter = stateAfter;
    }

    public static VectorAPICompareNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp inputStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        Condition condition = improveCondition(null, macroParams.arguments);
        return new VectorAPICompareNode(macroParams, inputStamp, computeLogicStamp(inputStamp, VectorAPIUtils.vectorArchitecture(providers)), condition, null);
    }

    private static Condition improveCondition(Condition oldCondition, ValueNode[] arguments) {
        if (oldCondition != null) {
            return oldCondition;
        }
        ValueNode cond = arguments[COND_ARG_INDEX];
        if (!(cond.isJavaConstant() && cond.asJavaConstant().getJavaKind() == JavaKind.Int)) {
            return null;
        }
        int conditionCode = cond.asJavaConstant().asInt();
        Condition condition = VectorAPIOperations.lookupCondition(conditionCode);
        return condition;
    }

    private ValueNode vectorX() {
        return getArgument(X_ARG_INDEX);
    }

    private ValueNode vectorY() {
        return getArgument(Y_ARG_INDEX);
    }

    private ValueNode mask() {
        return getArgument(M_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vectorX(), vectorY(), mask());
    }

    @Override
    public SimdStamp vectorStamp() {
        return logicStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (speciesStamp.isExactType() && inputStamp != null && condition != null) {
            /* Nothing to improve. */
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, MCLASS_ARG_INDEX);
        SimdStamp newInputStamp = improveVectorStamp(inputStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        SimdStamp newLogicStamp = computeLogicStamp(newInputStamp, VectorAPIUtils.vectorArchitecture(tool));
        Condition newCondition = improveCondition(condition, args);
        if (newSpeciesStamp != speciesStamp || newInputStamp != inputStamp || newCondition != condition) {
            return new VectorAPICompareNode(copyParamsWithImprovedStamp(newSpeciesStamp), newInputStamp, newLogicStamp, newCondition, stateAfter());
        }

        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (!((ObjectStamp) stamp).isExactType()) {
            return false;
        }
        Stamp elementStamp = inputStamp.getComponent(0);
        int length = inputStamp.getVectorLength();
        if (canonicalizedCondition == null) {
            return false;
        }

        if (canonicalizedCondition.mustNegate()) {
            SimdStamp resultStamp = this.vectorStamp();
            ArithmeticStamp resultElementStamp = (ArithmeticStamp) resultStamp.getComponent(0);
            if (vectorArch.getSupportedVectorArithmeticLength(resultElementStamp, length, resultElementStamp.getOps().getNot()) != length) {
                return false;
            }
        }
        return vectorArch.getSupportedVectorComparisonLength(elementStamp, canonicalizedCondition.getCanonicalCondition(), length) == length;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        ValueNode x = expanded.get(vectorX());
        ValueNode y = expanded.get(vectorY());

        if (canonicalizedCondition.mustMirror()) {
            ValueNode tmp = y;
            y = x;
            x = tmp;
        }
        // Negating a condition flips its order-ness
        // LT, LE, GT, GE, EQ are all ordered while NE is unordered
        boolean unordered = canonicalizedCondition.mustNegate() && canonicalizedCondition.getCanonicalCondition() != CanonicalCondition.EQ;
        ValueNode compare = SimdPrimitiveCompareNode.simdCompare(canonicalizedCondition.getCanonicalCondition(), x, y, unordered, vectorArch);

        if (canonicalizedCondition.mustNegate()) {
            compare = NotNode.create(compare);
        }
        if (!mask().isNullConstant()) {
            ValueNode mask = expanded.get(mask());
            compare = AndNode.create(mask, compare, NodeView.DEFAULT);
        }
        return compare;
    }
}

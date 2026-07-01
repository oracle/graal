/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
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
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIUtils;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsic node for the {@code VectorSupport.indexVector} method. This operation adds a scaled
 * iota vector to an input vector:
 * <p>
 * {@code result = v + <0, 1, ..., n - 1> * scale}
 */
@NodeInfo
public class VectorAPIIndexVectorNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPIIndexVectorNode> TYPE = NodeClass.create(VectorAPIIndexVectorNode.class);

    /* Indices into the macro argument list for relevant input values. */
    private static final int VCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 1;
    private static final int LENGTH_ARG_INDEX = 2;
    private static final int VECTOR_ARG_INDEX = 3;
    private static final int SCALE_ARG_INDEX = 4;

    private final SimdStamp vectorStamp;

    protected VectorAPIIndexVectorNode(MacroParams macroParams, SimdStamp vectorStamp, FrameState stateAfter) {
        super(TYPE, macroParams, null);
        this.vectorStamp = vectorStamp;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIIndexVectorNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        return new VectorAPIIndexVectorNode(macroParams, vectorStamp, null);
    }

    private ValueNode vector() {
        return getArgument(VECTOR_ARG_INDEX);
    }

    private ValueNode scale() {
        return getArgument(SCALE_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vector());
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
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp) {
            return new VectorAPIIndexVectorNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (!((ObjectStamp) stamp).isExactType() || vectorStamp == null) {
            return false;
        }
        Integer scaleConstant = constantScale(scale());
        if (scaleConstant != null && scaleConstant == 0 && vectorStamp.isIntegerStamp()) {
            return true;
        }
        ArithmeticStamp elementStamp = (ArithmeticStamp) vectorStamp.getComponent(0);
        int vectorLength = vectorStamp.getVectorLength();
        if (vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, elementStamp.getOps().getAdd()) != vectorLength) {
            return false;
        }
        if (scaleConstant != null && scaleConstant == 1) {
            return true;
        }
        return vectorArch.getSupportedVectorArithmeticLength(elementStamp, vectorLength, elementStamp.getOps().getMul()) == vectorLength;
    }

    private static Integer constantScale(ValueNode scale) {
        if (scale.isJavaConstant() && scale.asJavaConstant().getJavaKind() == JavaKind.Int) {
            return scale.asJavaConstant().asInt();
        }
        return null;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        Integer scale = constantScale(scale());
        ValueNode vector = expanded.get(vector());
        if (scale != null && scale == 0 && vectorStamp.isIntegerStamp()) {
            return vector;
        }
        ArithmeticStamp elementStamp = (ArithmeticStamp) vectorStamp.getComponent(0);
        int vectorLength = vectorStamp.getVectorLength();
        ValueNode iota = VectorAPIUtils.iotaVector(elementStamp, vectorLength);
        if (scale == null || scale != 1) {
            ValueNode scaleVector = scaleVector(scale(), scale, elementStamp, vectorLength);
            iota = applyOp(iota, scaleVector, elementStamp, elementStamp.getOps().getMul());
        }
        return applyOp(vector, iota, elementStamp, elementStamp.getOps().getAdd());
    }

    private static ValueNode scaleVector(ValueNode scale, Integer scaleConstant, ArithmeticStamp elementStamp, int vectorLength) {
        if (scaleConstant != null) {
            return SimdConstant.constantNodeForBroadcast(constantForScale(scaleConstant, elementStamp), vectorLength);
        }
        ValueNode scaleElement;
        if (elementStamp instanceof IntegerStamp integerStamp) {
            scaleElement = IntegerConvertNode.convert(scale, integerStamp, NodeView.DEFAULT);
        } else if (elementStamp instanceof FloatStamp floatStamp) {
            FloatConvert convert = FloatConvert.forStamps(scale.stamp(NodeView.DEFAULT), floatStamp);
            GraalError.guarantee(convert != null, "unsupported scale conversion from %s to %s", scale.stamp(NodeView.DEFAULT), floatStamp);
            scaleElement = new FloatConvertNode(convert, scale);
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(elementStamp); // ExcludeFromJacocoGeneratedReport
        }
        return new SimdBroadcastNode(scaleElement, vectorLength);
    }

    private static JavaConstant constantForScale(int scale, ArithmeticStamp elementStamp) {
        if (elementStamp instanceof IntegerStamp integerStamp) {
            return JavaConstant.forPrimitiveInt(integerStamp.getBits(), scale);
        } else if (elementStamp instanceof FloatStamp floatStamp) {
            return floatStamp.getBits() == Float.SIZE ? JavaConstant.forFloat(scale) : JavaConstant.forDouble(scale);
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(elementStamp); // ExcludeFromJacocoGeneratedReport
    }

    private static ValueNode applyOp(ValueNode x, ValueNode y, Stamp elementStamp, ArithmeticOpTable.BinaryOp<?> op) {
        if (elementStamp instanceof IntegerStamp) {
            return BinaryArithmeticNode.binaryIntegerOp(x, y, NodeView.DEFAULT, op);
        } else if (elementStamp instanceof FloatStamp) {
            return BinaryArithmeticNode.binaryFloatOp(x, y, NodeView.DEFAULT, op);
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(elementStamp); // ExcludeFromJacocoGeneratedReport
    }
}

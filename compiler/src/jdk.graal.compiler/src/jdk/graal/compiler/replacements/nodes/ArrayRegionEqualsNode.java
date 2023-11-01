/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.StrideUtil;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.util.ConstantReflectionUtil;
import jdk.graal.compiler.replacements.NodeStrideUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

// JaCoCo Exclude

/**
 * Compares two array regions with a given length. This node can compare regions of arrays of the
 * same primitive element kinds. As a special case, it also supports comparing an array region
 * interpreted as {@code char}s with an array region interpreted as {@code byte}s, in which case the
 * {@code byte} values are zero-extended for the comparison. In this case, the first kind must be
 * {@code char}, and the underlying array must be a {@code byte} array (this condition is not
 * checked). Other combinations of kinds are currently not allowed.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public class ArrayRegionEqualsNode extends PureFunctionStubIntrinsicNode implements Canonicalizable, ConstantReflectionUtil.ArrayBaseOffsetProvider {

    public static final NodeClass<ArrayRegionEqualsNode> TYPE = NodeClass.create(ArrayRegionEqualsNode.class);

    private final Stride strideA;
    private final Stride strideB;

    /**
     * Pointer to the first array object.
     */
    @Input protected ValueNode arrayA;

    /**
     * Byte offset to be added to the first array pointer. Must include the array's base offset!
     */
    @Input protected ValueNode offsetA;

    /**
     * Pointer to the second array object.
     */
    @Input protected ValueNode arrayB;

    /**
     * Byte offset to be added to the second array pointer. Must include the array's base offset!
     */
    @Input protected ValueNode offsetB;

    /**
     * Length of the array region.
     */
    @Input protected ValueNode length;

    /**
     * Optional argument for dispatching to any combination of strides at runtime, as described in
     * {@link StrideUtil}.
     */
    @OptionalInput protected ValueNode dynamicStrides;

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, null, NamedLocationIdentity.getArrayLocation(arrayKind));
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, null, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    Stride strideA,
                    Stride strideB,
                    LocationIdentity locationIdentity) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, null, locationIdentity);
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, null, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides, LocationIdentity locationIdentity) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, null, locationIdentity);
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
    }

    protected ArrayRegionEqualsNode(NodeClass<? extends ArrayRegionEqualsNode> c, ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides,
                    Stride strideA,
                    Stride strideB,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(c, StampFactory.forKind(JavaKind.Boolean), runtimeCheckedCPUFeatures, locationIdentity);
        this.strideA = strideA;
        this.strideB = strideB;
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.arrayB = arrayB;
        this.offsetB = offsetB;
        this.length = length;
        this.dynamicStrides = dynamicStrides;
    }

    @NodeIntrinsic
    @GenerateStub(name = "arrayRegionEqualsS1S1", parameters = {"S1", "S1"})
    @GenerateStub(name = "arrayRegionEqualsS1S2", parameters = {"S1", "S2"})
    @GenerateStub(name = "arrayRegionEqualsS1S4", parameters = {"S1", "S4"})
    @GenerateStub(name = "arrayRegionEqualsS2S1", parameters = {"S2", "S1"})
    @GenerateStub(name = "arrayRegionEqualsS2S2", parameters = {"S2", "S2"})
    @GenerateStub(name = "arrayRegionEqualsS2S4", parameters = {"S2", "S4"})
    @GenerateStub(name = "arrayRegionEqualsS4S1", parameters = {"S4", "S1"})
    @GenerateStub(name = "arrayRegionEqualsS4S2", parameters = {"S4", "S2"})
    @GenerateStub(name = "arrayRegionEqualsS4S4", parameters = {"S4", "S4"})
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "arrayRegionEqualsDynamicStrides")
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, int dynamicStrides);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, int dynamicStrides,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    public ValueNode getArrayA() {
        return arrayA;
    }

    public ValueNode getOffsetA() {
        return offsetA;
    }

    public ValueNode getArrayB() {
        return arrayB;
    }

    public ValueNode getOffsetB() {
        return offsetB;
    }

    public Stride getStrideA() {
        return strideA;
    }

    public Stride getStrideB() {
        return strideB;
    }

    public ValueNode getLength() {
        return length;
    }

    public ValueNode getDynamicStrides() {
        return dynamicStrides;
    }

    public int getDirectStubCallIndex() {
        return NodeStrideUtil.getDirectStubCallIndex(dynamicStrides, strideA, strideB);
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayEqualsForeignCalls.getRegionEqualsStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        if (getDirectStubCallIndex() < 0) {
            return new ValueNode[]{arrayA, offsetA, arrayB, offsetB, length, dynamicStrides};
        } else {
            return new ValueNode[]{arrayA, offsetA, arrayB, offsetB, length};
        }
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        if (getDirectStubCallIndex() < 0) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitArrayEqualsDynamicStrides(
                            getRuntimeCheckedCPUFeatures(),
                            gen.operand(arrayA),
                            gen.operand(offsetA),
                            gen.operand(arrayB),
                            gen.operand(offsetB),
                            gen.operand(length),
                            gen.operand(dynamicStrides)));
        } else {
            gen.setResult(this, gen.getLIRGeneratorTool().emitArrayEquals(
                            NodeStrideUtil.getConstantStrideA(dynamicStrides, strideA),
                            NodeStrideUtil.getConstantStrideB(dynamicStrides, strideB),
                            getRuntimeCheckedCPUFeatures(),
                            gen.operand(arrayA),
                            gen.operand(offsetA),
                            gen.operand(arrayB),
                            gen.operand(offsetB),
                            gen.operand(length)));
        }
    }

    @Override
    public int getArrayBaseOffset(MetaAccessProvider metaAccess, @SuppressWarnings("unused") ValueNode array, JavaKind arrayKind) {
        return metaAccess.getArrayBaseOffset(arrayKind);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        if ((dynamicStrides == null || dynamicStrides.isJavaConstant()) && length.isJavaConstant()) {
            int len = length.asJavaConstant().asInt();
            Stride constStrideA = NodeStrideUtil.getConstantStrideA(dynamicStrides, strideA);
            Stride constStrideB = NodeStrideUtil.getConstantStrideB(dynamicStrides, strideB);
            if (len * Math.max(constStrideA.value, constStrideB.value) < GraalOptions.ArrayRegionEqualsConstantLimit.getValue(tool.getOptions()) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayA, offsetA, constStrideA, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayB, offsetB, constStrideB, len, this)) {
                Integer startIndex1 = ConstantReflectionUtil.startIndex(tool, arrayA, offsetA.asJavaConstant(), constStrideA, this);
                Integer startIndex2 = ConstantReflectionUtil.startIndex(tool, arrayB, offsetB.asJavaConstant(), constStrideB, this);
                return ConstantNode.forBoolean(arrayRegionEquals(tool, arrayA, constStrideA, startIndex1, arrayB, constStrideB, startIndex2, len));
            }
        }
        return this;
    }

    private static boolean arrayRegionEquals(CanonicalizerTool tool, ValueNode a, Stride constStrideA, int startIndexA, ValueNode b, Stride constStrideB, int startIndexB, int len) {
        JavaKind arrayKindA = a.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindB = b.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        for (int i = 0; i < len; i++) {
            int valueA = ConstantReflectionUtil.readTypePunned(constantReflection, a.asJavaConstant(), arrayKindA, constStrideA, startIndexA + i);
            int valueB = ConstantReflectionUtil.readTypePunned(constantReflection, b.asJavaConstant(), arrayKindB, constStrideB, startIndexB + i);
            if (valueA != valueB) {
                return false;
            }
        }
        return true;
    }
}

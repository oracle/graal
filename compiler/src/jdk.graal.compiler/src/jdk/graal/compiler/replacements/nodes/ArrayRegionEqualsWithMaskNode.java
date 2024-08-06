/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.nodes.ComputeObjectAddressNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.util.ConstantReflectionUtil;
import jdk.graal.compiler.replacements.NodeStrideUtil;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

// JaCoCo Exclude

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public final class ArrayRegionEqualsWithMaskNode extends PureFunctionStubIntrinsicNode implements Canonicalizable, ConstantReflectionUtil.ArrayBaseOffsetProvider {

    public static final NodeClass<ArrayRegionEqualsWithMaskNode> TYPE = NodeClass.create(ArrayRegionEqualsWithMaskNode.class);

    /**
     * Stride for reading type punned array elements of {@link #arrayA}. Allowed values are
     * {@link Stride#S1}, {@link Stride#S2} and {@link Stride#S4}.
     */
    private final Stride strideA;
    /**
     * Stride for reading type punned array elements of {@link #arrayB}. Allowed values are
     * {@link Stride#S1}, {@link Stride#S2} and {@link Stride#S4}.
     */
    private final Stride strideB;
    /**
     * Stride for reading type punned array elements of {@link #arrayMask}. Allowed values are
     * {@link Stride#S1}, {@link Stride#S2} and {@link Stride#S4}.
     */
    private final Stride strideMask;

    @Input protected ValueNode arrayA;
    @Input protected ValueNode offsetA;
    @Input protected ValueNode arrayB;
    @Input protected ValueNode offsetB;
    /**
     * Direct pointer to the memory region to be OR-ed to arrayA.
     */
    @Input protected ValueNode arrayMask;
    @Input protected ValueNode length;

    /**
     * Optional argument for dispatching to any combination of strides at runtime, as described in
     * {@link StrideUtil}. When this parameter is used, {@link #strideMask} is assumed to be equal
     * to {@link #strideB}.
     */
    @OptionalInput protected ValueNode dynamicStrides;

    public ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter Stride strideMask) {
        this(arrayA, offsetA, arrayB, offsetB, arrayMask, length, null, strideA, strideB, strideMask, null, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter Stride strideMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(arrayA, offsetA, arrayB, offsetB, arrayMask, length, null, strideA, strideB, strideMask, runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length, ValueNode dynamicStrides) {
        this(arrayA, offsetA, arrayB, offsetB, arrayMask, length, dynamicStrides, null, null, null, null, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length, ValueNode dynamicStrides,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(arrayA, offsetA, arrayB, offsetB, arrayMask, length, dynamicStrides, null, null, null, runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length,
                    ValueNode dynamicStrides,
                    Stride strideA,
                    Stride strideB,
                    Stride strideMask,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean), runtimeCheckedCPUFeatures, locationIdentity);
        assert validStride(strideA);
        assert validStride(strideB);
        assert validStride(strideMask);
        this.strideA = strideA;
        this.strideB = strideB;
        this.strideMask = strideMask;
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.arrayB = arrayB;
        this.offsetB = offsetB;
        this.arrayMask = arrayMask;
        this.length = length;
        this.dynamicStrides = dynamicStrides;
    }

    private static boolean validStride(Stride stride) {
        return stride == null || stride.value <= 4;
    }

    public Stride getStrideA() {
        return strideA;
    }

    public Stride getStrideB() {
        return strideB;
    }

    public Stride getStrideMask() {
        return strideMask;
    }

    public ValueNode getDynamicStrides() {
        return dynamicStrides;
    }

    public ValueNode getLength() {
        return length;
    }

    public int getDirectStubCallIndex() {
        return NodeStrideUtil.getDirectStubCallIndex(dynamicStrides, strideA, strideB);
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayEqualsWithMaskForeignCalls.getStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        if (getDirectStubCallIndex() < 0) {
            return new ValueNode[]{arrayA, offsetA, arrayB, offsetB, arrayMask, length, dynamicStrides};
        } else {
            return new ValueNode[]{arrayA, offsetA, arrayB, offsetB, arrayMask, length};
        }
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        if (getDirectStubCallIndex() < 0) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitArrayEqualsWithMaskDynamicStrides(
                            getRuntimeCheckedCPUFeatures(),
                            gen.operand(arrayA),
                            gen.operand(offsetA),
                            gen.operand(arrayB),
                            gen.operand(offsetB),
                            gen.operand(arrayMask),
                            gen.operand(length),
                            gen.operand(dynamicStrides)));
        } else {
            gen.setResult(this, gen.getLIRGeneratorTool().emitArrayEqualsWithMask(
                            NodeStrideUtil.getConstantStrideA(dynamicStrides, strideA),
                            NodeStrideUtil.getConstantStrideB(dynamicStrides, strideB),
                            NodeStrideUtil.getConstantStrideB(dynamicStrides, strideMask),
                            getRuntimeCheckedCPUFeatures(), gen.operand(arrayA),
                            gen.operand(offsetA),
                            gen.operand(arrayB),
                            gen.operand(offsetB),
                            gen.operand(arrayMask),
                            gen.operand(length)));
        }
    }

    @Override
    public int getArrayBaseOffset(MetaAccessProvider metaAccess, @SuppressWarnings("unused") ValueNode array, JavaKind arrayKind) {
        return metaAccess.getArrayBaseOffset(arrayKind);
    }

    @NodeIntrinsic
    @GenerateStub(name = "arrayRegionEqualsWithMaskS1S2S1", parameters = {"S1", "S2", "S1"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS2S2S1", parameters = {"S2", "S2", "S1"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS1S1", parameters = {"S1", "S1", "S1"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS1S2", parameters = {"S1", "S2", "S2"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS1S4", parameters = {"S1", "S4", "S4"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS2S1", parameters = {"S2", "S1", "S1"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS2S2", parameters = {"S2", "S2", "S2"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS2S4", parameters = {"S2", "S4", "S4"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS4S1", parameters = {"S4", "S1", "S1"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS4S2", parameters = {"S4", "S2", "S2"})
    @GenerateStub(name = "arrayRegionEqualsWithMaskS4S4", parameters = {"S4", "S4", "S4"})
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, Pointer mask, int length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter Stride strideMask);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, Pointer mask, int length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter Stride strideMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "arrayRegionEqualsWithMaskDynamicStrides")
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, Pointer mask, int length, int stride);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, Pointer mask, int length, int stride,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        if ((dynamicStrides == null || dynamicStrides.isJavaConstant()) && length.isJavaConstant() && arrayMask instanceof ComputeObjectAddressNode) {
            int len = length.asJavaConstant().asInt();
            Stride constStrideA = NodeStrideUtil.getConstantStrideA(dynamicStrides, strideA);
            Stride constStrideB = NodeStrideUtil.getConstantStrideB(dynamicStrides, strideB);
            Stride constStrideMask = NodeStrideUtil.getConstantStrideB(dynamicStrides, strideMask);
            ValueNode arrayMaskNode = ((ComputeObjectAddressNode) arrayMask).getObject();
            ValueNode offsetMaskNode = ((ComputeObjectAddressNode) arrayMask).getOffset();
            if (len * Math.max(constStrideA.value, constStrideB.value) < GraalOptions.ArrayRegionEqualsConstantLimit.getValue(tool.getOptions()) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayA, offsetA, constStrideA, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayB, offsetB, constStrideB, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayMaskNode, offsetMaskNode, constStrideMask, len, this)) {
                Integer startIndexA = ConstantReflectionUtil.startIndex(tool, arrayA, offsetA.asJavaConstant(), constStrideA, this);
                Integer startIndexB = ConstantReflectionUtil.startIndex(tool, arrayB, offsetB.asJavaConstant(), constStrideB, this);
                Integer startIndexMask = ConstantReflectionUtil.startIndex(tool, arrayMaskNode, offsetMaskNode.asJavaConstant(), constStrideMask, this);
                return ConstantNode.forBoolean(constantFold(tool, arrayA, startIndexA, arrayB, startIndexB, arrayMaskNode, startIndexMask, len, constStrideA, constStrideB, constStrideMask));
            }
        }
        return this;
    }

    private static boolean constantFold(CanonicalizerTool tool, ValueNode a, int startIndexA, ValueNode b, int startIndexB, ValueNode mask, int startIndexMask, int len,
                    Stride constStrideA, Stride constStrideB, Stride constStrideMask) {
        JavaKind arrayKindA = a.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindB = b.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindM = mask.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        for (int i = 0; i < len; i++) {
            int valueA = ConstantReflectionUtil.readTypePunned(constantReflection, a.asJavaConstant(), arrayKindA, constStrideA, startIndexA + i);
            int valueB = ConstantReflectionUtil.readTypePunned(constantReflection, b.asJavaConstant(), arrayKindB, constStrideB, startIndexB + i);
            int valueM = ConstantReflectionUtil.readTypePunned(constantReflection, mask.asJavaConstant(), arrayKindM, constStrideMask, startIndexMask + i);
            if ((valueA | valueM) != valueB) {
                return false;
            }
        }
        return true;
    }
}

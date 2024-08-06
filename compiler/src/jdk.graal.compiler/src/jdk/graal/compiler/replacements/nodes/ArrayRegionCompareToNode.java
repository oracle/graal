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
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
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
 * Lexicographically compares two array regions with a given length. Supported strides are
 * {@link JavaKind#Byte}, {@link JavaKind#Char} and {@link JavaKind#Int}. The first array's stride
 * must be greater or equal to the second array's stride. Array strides are not required to match
 * the respective array's element sizes, e.g. this node can also read {@code char} values from byte
 * arrays. If {@code strideA} is greater than {@code strideB}, elements read from {@code arrayB} are
 * zero-extended to match {@code strideA} for the comparison.
 *
 * This node differs from {@link ArrayCompareToNode} in the following features:
 * <ul>
 * <li>Support for arbitrary byte offsets into the given arrays</li>
 * <li>Support for 32-bit strides</li>
 * <li>The {@code length} parameter denotes the length of the region to be compared instead of the
 * full array length. The region given by the respective array offset and the region length must be
 * inside the array's bounds.</li>
 * </ul>
 *
 * Returns the result of {@code arrayA[i] - arrayB[i]} at the first index {@code i} where
 * {@code arrayA[i] != arrayB[i]}. If no such index exists, returns 0.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public class ArrayRegionCompareToNode extends PureFunctionStubIntrinsicNode implements Canonicalizable, ConstantReflectionUtil.ArrayBaseOffsetProvider {

    public static final NodeClass<ArrayRegionCompareToNode> TYPE = NodeClass.create(ArrayRegionCompareToNode.class);

    /**
     * Element size of arrayA.
     */
    protected final Stride strideA;
    /**
     * Element size of arrayB.
     */
    protected final Stride strideB;

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
     * Length of the array region (as number of array elements). The caller is responsible for
     * ensuring that the region, starting at the respective offset, is within array bounds.
     */
    @Input protected ValueNode length;

    /**
     * Optional argument for dispatching to any combination of strides at runtime, as described in
     * {@link StrideUtil}.
     */
    @OptionalInput protected ValueNode dynamicStrides;

    public ArrayRegionCompareToNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, Stride strideA, Stride strideB, LocationIdentity locationIdentity) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, null, locationIdentity);
    }

    public ArrayRegionCompareToNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides, LocationIdentity locationIdentity) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, null, locationIdentity);
    }

    public ArrayRegionCompareToNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, null, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionCompareToNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionCompareToNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, null, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionCompareToNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
    }

    protected ArrayRegionCompareToNode(NodeClass<? extends ArrayRegionCompareToNode> c, ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    ValueNode dynamicStrides,
                    Stride strideA,
                    Stride strideB,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(c, StampFactory.forKind(JavaKind.Int), runtimeCheckedCPUFeatures, locationIdentity);
        this.strideA = strideA;
        this.strideB = strideB;
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.arrayB = arrayB;
        this.offsetB = offsetB;
        this.length = length;
        this.dynamicStrides = dynamicStrides;
        GraalError.guarantee(strideA == null || strideA.value <= 4, "unsupported strideA");
        GraalError.guarantee(strideB == null || strideB.value <= 4, "unsupported strideB");
    }

    @NodeIntrinsic
    @GenerateStub(name = "arrayRegionCompareToS1S1", parameters = {"S1", "S1"})
    @GenerateStub(name = "arrayRegionCompareToS1S2", parameters = {"S1", "S2"})
    @GenerateStub(name = "arrayRegionCompareToS1S4", parameters = {"S1", "S4"})
    @GenerateStub(name = "arrayRegionCompareToS2S1", parameters = {"S2", "S1"})
    @GenerateStub(name = "arrayRegionCompareToS2S2", parameters = {"S2", "S2"})
    @GenerateStub(name = "arrayRegionCompareToS2S4", parameters = {"S2", "S4"})
    @GenerateStub(name = "arrayRegionCompareToS4S1", parameters = {"S4", "S1"})
    @GenerateStub(name = "arrayRegionCompareToS4S2", parameters = {"S4", "S2"})
    @GenerateStub(name = "arrayRegionCompareToS4S4", parameters = {"S4", "S4"})
    public static native int compare(Object arrayA, long offsetA, Object arrayB, long offsetB, int length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB);

    @NodeIntrinsic
    public static native int compare(Object arrayA, long offsetA, Object arrayB, long offsetB, int length,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "arrayRegionCompareToDynamicStrides")
    public static native int compare(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, int dynamicStrides);

    @NodeIntrinsic
    public static native int compare(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, int dynamicStrides,
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

    public int getDirectStubCallIndex() {
        return NodeStrideUtil.getDirectStubCallIndex(dynamicStrides, strideA, strideB);
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayRegionCompareToForeignCalls.getStub(this);
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
            gen.setResult(this, gen.getLIRGeneratorTool().emitArrayRegionCompareTo(
                            getRuntimeCheckedCPUFeatures(),
                            gen.operand(arrayA),
                            gen.operand(offsetA),
                            gen.operand(arrayB),
                            gen.operand(offsetB),
                            gen.operand(length),
                            gen.operand(dynamicStrides)));
        } else {
            gen.setResult(this, gen.getLIRGeneratorTool().emitArrayRegionCompareTo(
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
                return ConstantNode.forInt(foldResult(tool, constStrideA, constStrideB, arrayA, startIndex1, arrayB, startIndex2, len));
            }
        }
        return this;
    }

    private static int foldResult(CanonicalizerTool tool, Stride constStrideA, Stride constStrideB, ValueNode a, int startIndexA, ValueNode b, int startIndexB, int len) {
        JavaKind arrayKindA = a.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindB = b.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        for (int i = 0; i < len; i++) {
            int valueA = ConstantReflectionUtil.readTypePunned(constantReflection, a.asJavaConstant(), arrayKindA, constStrideA, startIndexA + i);
            int valueB = ConstantReflectionUtil.readTypePunned(constantReflection, b.asJavaConstant(), arrayKindB, constStrideB, startIndexB + i);
            int cmp = valueA - valueB;
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}

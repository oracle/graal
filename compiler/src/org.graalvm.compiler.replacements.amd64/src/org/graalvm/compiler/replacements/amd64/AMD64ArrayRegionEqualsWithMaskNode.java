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
package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.core.common.GraalOptions.UseGraalStubs;
import static org.graalvm.compiler.core.common.StrideUtil.NONE;
import static org.graalvm.compiler.core.common.StrideUtil.S1;
import static org.graalvm.compiler.core.common.StrideUtil.S2;
import static org.graalvm.compiler.core.common.StrideUtil.S4;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.ConstantReflectionUtil;
import org.graalvm.compiler.replacements.NodeStrideUtil;
import org.graalvm.compiler.replacements.nodes.PureFunctionStubIntrinsicNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

// JaCoCo Exclude

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public final class AMD64ArrayRegionEqualsWithMaskNode extends PureFunctionStubIntrinsicNode implements Canonicalizable, LIRLowerable, ConstantReflectionUtil.ArrayBaseOffsetProvider {

    public static final NodeClass<AMD64ArrayRegionEqualsWithMaskNode> TYPE = NodeClass.create(AMD64ArrayRegionEqualsWithMaskNode.class);

    /**
     * {@link JavaKind} of the arrays {@link #arrayA}, {@link #arrayB}, and {@link #arrayMask}, e.g.
     * if this is {@link JavaKind#Byte}, all arrays must be {@code byte[]} arrays, and the intrinsic
     * will use the corresponding array base offset when reading array elements. This value can also
     * be set to {@link JavaKind#Void}, which changes the operation's behavior in the following way:
     * {@link #arrayA} and {@link #arrayB} may be any type, and their respective base offset must be
     * added to {@link #offsetA} and {@link #offsetB}; {@link #arrayMask} must be a {@code byte[]}
     * array.
     */
    private final JavaKind arrayKind;
    /**
     * Stride for reading type punned array elements of {@link #arrayA}. Allowed values are
     * {@link JavaKind#Byte}, {@link JavaKind#Char} and {@link JavaKind#Int}.
     */
    private final JavaKind strideA;
    /**
     * Stride for reading type punned array elements of {@link #arrayB}. Allowed values are
     * {@link JavaKind#Byte}, {@link JavaKind#Char} and {@link JavaKind#Int}.
     */
    private final JavaKind strideB;
    /**
     * Stride for reading type punned array elements of {@link #arrayMask}. Allowed values are
     * {@link JavaKind#Byte}, {@link JavaKind#Char} and {@link JavaKind#Int}.
     */
    private final JavaKind strideMask;

    @Input protected ValueNode arrayA;
    @Input protected ValueNode offsetA;
    @Input protected ValueNode arrayB;
    @Input protected ValueNode offsetB;
    @Input protected ValueNode arrayMask;
    @Input protected ValueNode length;

    /**
     * Optional argument for dispatching to any combination of strides at runtime, as described in
     * {@link org.graalvm.compiler.core.common.StrideUtil}. When this parameter is used,
     * {@link #strideMask} is assumed to be equal to {@link #strideB}.
     */
    @OptionalInput protected ValueNode dynamicStrides;

    public AMD64ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind strideA,
                    @ConstantNodeParameter JavaKind strideB,
                    @ConstantNodeParameter JavaKind strideMask) {
        this(arrayA, offsetA, arrayB, offsetB, arrayMask, length, null, arrayKind, strideA, strideB, strideMask, null,
                        defaultLocationIdentity(arrayKind, strideA, strideB, strideMask));
    }

    public AMD64ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind strideA,
                    @ConstantNodeParameter JavaKind strideB,
                    @ConstantNodeParameter JavaKind strideMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(arrayA, offsetA, arrayB, offsetB, arrayMask, length, null, arrayKind, strideA, strideB, strideMask, runtimeCheckedCPUFeatures,
                        defaultLocationIdentity(arrayKind, strideA, strideB, strideMask));
    }

    public AMD64ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length, ValueNode dynamicStrides) {
        this(arrayA, offsetA, arrayB, offsetB, arrayMask, length, dynamicStrides, NONE, null, null, null, null, LocationIdentity.ANY_LOCATION);
    }

    public AMD64ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length, ValueNode dynamicStrides,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(arrayA, offsetA, arrayB, offsetB, arrayMask, length, dynamicStrides, NONE, null, null, null, runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
    }

    public AMD64ArrayRegionEqualsWithMaskNode(
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length,
                    ValueNode dynamicStrides,
                    JavaKind arrayKind,
                    JavaKind strideA,
                    JavaKind strideB,
                    JavaKind strideMask,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean), runtimeCheckedCPUFeatures, locationIdentity);
        assert validStride(strideA);
        assert validStride(strideB);
        assert validStride(strideMask);
        this.arrayKind = arrayKind;
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

    private static boolean validStride(JavaKind stride) {
        return stride == null || stride == S1 || stride == S2 || stride == S4;
    }

    public JavaKind getArrayKind() {
        return arrayKind;
    }

    public JavaKind getStrideA() {
        return strideA;
    }

    public JavaKind getStrideB() {
        return strideB;
    }

    public JavaKind getStrideMask() {
        return strideMask;
    }

    public ValueNode getDynamicStrides() {
        return dynamicStrides;
    }

    public ValueNode getLength() {
        return length;
    }

    private static boolean sameKinds(JavaKind arrayKind, JavaKind strideA, JavaKind strideB, JavaKind strideMask) {
        return strideA == arrayKind && strideB == arrayKind && strideMask == arrayKind;
    }

    private static LocationIdentity defaultLocationIdentity(JavaKind arrayKind, JavaKind strideA, JavaKind strideB, JavaKind strideMask) {
        return !sameKinds(arrayKind, strideA, strideB, strideMask) || arrayKind == NONE ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    public int getDirectStubCallIndex() {
        return NodeStrideUtil.getDirectStubCallIndex(dynamicStrides, strideA, strideB);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                final Value result;
                if (getDirectStubCallIndex() < 0) {
                    result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null,
                                    gen.operand(arrayA),
                                    gen.operand(offsetA),
                                    gen.operand(arrayB),
                                    gen.operand(offsetB),
                                    gen.operand(arrayMask),
                                    gen.operand(length),
                                    gen.operand(dynamicStrides));
                } else {
                    result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null,
                                    gen.operand(arrayA),
                                    gen.operand(offsetA),
                                    gen.operand(arrayB),
                                    gen.operand(offsetB),
                                    gen.operand(arrayMask),
                                    gen.operand(length));
                }
                gen.setResult(this, result);
                return;
            }
        }
        generateArrayRegionEquals(gen);
    }

    @Override
    public int getArrayBaseOffset(MetaAccessProvider metaAccess, @SuppressWarnings("unused") ValueNode array, JavaKind elementKind) {
        return metaAccess.getArrayBaseOffset(elementKind);
    }

    private void generateArrayRegionEquals(NodeLIRBuilderTool gen) {
        final Value result;
        MetaAccessProvider metaAccess = gen.getLIRGeneratorTool().getMetaAccess();
        int maskBaseOffset = metaAccess.getArrayBaseOffset(arrayKind == NONE ? JavaKind.Byte : arrayKind);
        if (getDirectStubCallIndex() < 0) {
            result = gen.getLIRGeneratorTool().emitArrayEquals(
                            0, 0, maskBaseOffset,
                            getRuntimeCheckedCPUFeatures(), gen.operand(arrayA),
                            gen.operand(offsetA),
                            gen.operand(arrayB),
                            gen.operand(offsetB),
                            gen.operand(arrayMask),
                            gen.operand(length),
                            gen.operand(dynamicStrides));
        } else {
            result = gen.getLIRGeneratorTool().emitArrayEquals(
                            NodeStrideUtil.getConstantStrideA(dynamicStrides, strideA),
                            NodeStrideUtil.getConstantStrideB(dynamicStrides, strideB),
                            NodeStrideUtil.getConstantStrideB(dynamicStrides, strideMask),
                            0, 0, maskBaseOffset,
                            getRuntimeCheckedCPUFeatures(), gen.operand(arrayA),
                            gen.operand(offsetA),
                            gen.operand(arrayB),
                            gen.operand(offsetB),
                            gen.operand(arrayMask),
                            gen.operand(length));
        }
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, Object mask, int length,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind kindA,
                    @ConstantNodeParameter JavaKind kindB,
                    @ConstantNodeParameter JavaKind kindMask);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, Object mask, int length,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind kindA,
                    @ConstantNodeParameter JavaKind kindB,
                    @ConstantNodeParameter JavaKind kindMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, Object mask, int length, int stride);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, Object mask, int length, int stride,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        if ((dynamicStrides == null || dynamicStrides.isJavaConstant()) && length.isJavaConstant()) {
            int len = length.asJavaConstant().asInt();
            JavaKind constStrideA = NodeStrideUtil.getConstantStrideA(dynamicStrides, strideA);
            JavaKind constStrideB = NodeStrideUtil.getConstantStrideB(dynamicStrides, strideB);
            JavaKind constStrideMask = NodeStrideUtil.getConstantStrideB(dynamicStrides, strideMask);
            if (len * Math.max(constStrideA.getByteCount(), constStrideB.getByteCount()) < GraalOptions.ArrayRegionEqualsConstantLimit.getValue(tool.getOptions()) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayA, offsetA, constStrideA, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayB, offsetB, constStrideB, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayMask, null, constStrideMask, len, this)) {
                Integer startIndexA = ConstantReflectionUtil.startIndex(tool, arrayA, offsetA.asJavaConstant(), constStrideA, this);
                Integer startIndexB = ConstantReflectionUtil.startIndex(tool, arrayB, offsetB.asJavaConstant(), constStrideB, this);
                return ConstantNode.forBoolean(constantFold(tool, arrayA, startIndexA, arrayB, startIndexB, arrayMask, len, constStrideA, constStrideB, constStrideMask));
            }
        }
        return this;
    }

    private static boolean constantFold(CanonicalizerTool tool, ValueNode a, int startIndexA, ValueNode b, int startIndexB, ValueNode mask, int len,
                    JavaKind constStrideA, JavaKind constStrideB, JavaKind constStrideMask) {
        JavaKind arrayKindA = a.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindB = b.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindM = mask.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        for (int i = 0; i < len; i++) {
            int valueA = ConstantReflectionUtil.readTypePunned(constantReflection, a.asJavaConstant(), arrayKindA, constStrideA, startIndexA + i);
            int valueB = ConstantReflectionUtil.readTypePunned(constantReflection, b.asJavaConstant(), arrayKindB, constStrideB, startIndexB + i);
            int valueM = ConstantReflectionUtil.readTypePunned(constantReflection, mask.asJavaConstant(), arrayKindM, constStrideMask, i);
            if ((valueA | valueM) != valueB) {
                return false;
            }
        }
        return true;
    }
}

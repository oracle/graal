/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.replacements.ArrayIndexOf.NONE;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S1;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S2;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S4;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.ConstantReflectionUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

// JaCoCo Exclude

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class AMD64ArrayRegionEqualsWithMaskNode extends FixedWithNextNode implements Canonicalizable, LIRLowerable, MemoryAccess, ConstantReflectionUtil.ArrayBaseOffsetProvider {

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
    private final LocationIdentity locationIdentity;

    @Input protected ValueNode arrayA;
    @Input protected ValueNode offsetA;
    @Input protected ValueNode arrayB;
    @Input protected ValueNode offsetB;
    @Input protected ValueNode arrayMask;
    @Input protected ValueNode length;

    @OptionalInput(Memory) private MemoryKill lastLocationAccess;

    public AMD64ArrayRegionEqualsWithMaskNode(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind strideA,
                    @ConstantNodeParameter JavaKind strideB,
                    @ConstantNodeParameter JavaKind strideMask,
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length) {
        this(arrayKind, strideA, strideB, strideMask, defaultLocationIdentity(arrayKind, strideA, strideB, strideMask), arrayA, offsetA, arrayB, offsetB, arrayMask, length);
    }

    public AMD64ArrayRegionEqualsWithMaskNode(
                    JavaKind arrayKind,
                    JavaKind strideA,
                    JavaKind strideB,
                    JavaKind strideMask,
                    LocationIdentity locationIdentity,
                    ValueNode arrayA,
                    ValueNode offsetA,
                    ValueNode arrayB,
                    ValueNode offsetB,
                    ValueNode arrayMask,
                    ValueNode length) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        assert validStride(strideA);
        assert validStride(strideB);
        assert validStride(strideMask);
        this.arrayKind = arrayKind;
        this.strideA = strideA;
        this.strideB = strideB;
        this.strideMask = strideMask;
        this.locationIdentity = locationIdentity;
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.arrayB = arrayB;
        this.offsetB = offsetB;
        this.arrayMask = arrayMask;
        this.length = length;
    }

    private static boolean validStride(JavaKind stride) {
        return stride == S1 || stride == S2 || stride == S4;
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

    public ValueNode getLength() {
        return length;
    }

    private static boolean sameKinds(JavaKind arrayKind, JavaKind strideA, JavaKind strideB, JavaKind strideMask) {
        return strideA == arrayKind && strideB == arrayKind && strideMask == arrayKind;
    }

    private static LocationIdentity defaultLocationIdentity(JavaKind arrayKind, JavaKind strideA, JavaKind strideB, JavaKind strideMask) {
        return !sameKinds(arrayKind, strideA, strideB, strideMask) || arrayKind == NONE ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null,
                                gen.operand(arrayA),
                                gen.operand(offsetA),
                                gen.operand(arrayB),
                                gen.operand(offsetB),
                                gen.operand(arrayMask),
                                gen.operand(length));
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
        Value result;
        MetaAccessProvider metaAccess = gen.getLIRGeneratorTool().getMetaAccess();
        int maskBaseOffset = metaAccess.getArrayBaseOffset(arrayKind == NONE ? JavaKind.Byte : arrayKind);
        result = gen.getLIRGeneratorTool().emitArrayEquals(getStrideA(), getStrideB(), getStrideMask(),
                        0, 0, maskBaseOffset,
                        gen.operand(arrayA),
                        gen.operand(offsetA),
                        gen.operand(arrayB),
                        gen.operand(offsetB),
                        gen.operand(arrayMask),
                        gen.operand(length));
        gen.setResult(this, result);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    @NodeIntrinsic
    public static native boolean regionEquals(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind kind1,
                    @ConstantNodeParameter JavaKind kind2,
                    @ConstantNodeParameter JavaKind kindMask,
                    Object array1, long offset1, Object array2, long offset2, Object mask, int length);

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (length.isJavaConstant()) {
            int len = length.asJavaConstant().asInt();
            if (len * Math.max(strideA.getByteCount(), strideB.getByteCount()) < GraalOptions.ArrayRegionEqualsConstantLimit.getValue(tool.getOptions()) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayA, offsetA, strideA, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayB, offsetB, strideB, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayMask, null, strideMask, len, this)) {
                Integer startIndex1 = ConstantReflectionUtil.startIndex(tool, arrayA, offsetA.asJavaConstant(), strideA, this);
                Integer startIndex2 = ConstantReflectionUtil.startIndex(tool, arrayB, offsetB.asJavaConstant(), strideB, this);
                return ConstantNode.forBoolean(constantFold(tool, arrayA, startIndex1, arrayB, startIndex2, arrayMask, len));
            }
        }
        return this;
    }

    private boolean constantFold(CanonicalizerTool tool, ValueNode a, int startIndexA, ValueNode b, int startIndexB, ValueNode mask, int len) {
        JavaKind arrayKindA = a.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindB = b.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindM = mask.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        for (int i = 0; i < len; i++) {
            int valueA = ConstantReflectionUtil.readTypePunned(constantReflection, a.asJavaConstant(), arrayKindA, strideA, startIndexA + i);
            int valueB = ConstantReflectionUtil.readTypePunned(constantReflection, b.asJavaConstant(), arrayKindB, strideB, startIndexB + i);
            int valueM = ConstantReflectionUtil.readTypePunned(constantReflection, mask.asJavaConstant(), arrayKindM, strideMask, i);
            if ((valueA | valueM) != valueB) {
                return false;
            }
        }
        return true;
    }
}

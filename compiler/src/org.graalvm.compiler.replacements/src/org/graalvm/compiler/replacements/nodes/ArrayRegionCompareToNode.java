/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.core.common.GraalOptions.UseGraalStubs;
import static org.graalvm.compiler.nodeinfo.InputType.Memory;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
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
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public class ArrayRegionCompareToNode extends FixedWithNextNode implements Canonicalizable, LIRLowerable, MemoryAccess, ConstantReflectionUtil.ArrayBaseOffsetProvider {

    public static final NodeClass<ArrayRegionCompareToNode> TYPE = NodeClass.create(ArrayRegionCompareToNode.class);

    /** {@link JavaKind} of the arrays to compare. */
    protected final JavaKind strideA;
    protected final JavaKind strideB;
    protected final LocationIdentity locationIdentity;

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

    @OptionalInput(Memory) protected MemoryKill lastLocationAccess;

    public ArrayRegionCompareToNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, JavaKind strideA, JavaKind strideB, LocationIdentity locationIdentity) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, strideA, strideB, locationIdentity);
    }

    public ArrayRegionCompareToNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter JavaKind strideA,
                    @ConstantNodeParameter JavaKind strideB) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, strideA, strideB, strideA != strideB ? LocationIdentity.ANY_LOCATION : NamedLocationIdentity.getArrayLocation(strideA));
    }

    protected ArrayRegionCompareToNode(NodeClass<? extends ArrayRegionCompareToNode> c, ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    JavaKind strideA,
                    JavaKind strideB,
                    LocationIdentity locationIdentity) {
        super(c, StampFactory.forKind(JavaKind.Boolean));
        this.strideA = strideA;
        this.strideB = strideB;
        this.locationIdentity = locationIdentity;
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.arrayB = arrayB;
        this.offsetB = offsetB;
        this.length = length;

        GraalError.guarantee(allowedStrides().contains(strideA), "unsupported strideA");
        GraalError.guarantee(allowedStrides().contains(strideB), "unsupported strideB");
        GraalError.guarantee(strideA.getByteCount() >= strideB.getByteCount(), "strideA must be greater or equal to strideB");
    }

    private static EnumSet<JavaKind> allowedStrides() {
        return EnumSet.of(JavaKind.Byte, JavaKind.Char, JavaKind.Int);
    }

    @NodeIntrinsic
    public static native int compare(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, @ConstantNodeParameter JavaKind strideA, @ConstantNodeParameter JavaKind strideB);

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

    public JavaKind getStrideA() {
        return strideA;
    }

    public JavaKind getStrideB() {
        return strideB;
    }

    public ValueNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, gen.operand(arrayA), gen.operand(offsetA), gen.operand(arrayB), gen.operand(offsetB), gen.operand(length));
                gen.setResult(this, result);
                return;
            }
        }
        generateArrayCompare(gen);
    }

    @Override
    public int getArrayBaseOffset(MetaAccessProvider metaAccess, @SuppressWarnings("unused") ValueNode array, JavaKind elementKind) {
        return metaAccess.getArrayBaseOffset(elementKind);
    }

    protected void generateArrayCompare(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitArrayRegionCompareTo(strideA, strideB, gen.operand(arrayA), gen.operand(offsetA), gen.operand(arrayB), gen.operand(offsetB),
                        gen.operand(length)));
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

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (length.isJavaConstant()) {
            int len = length.asJavaConstant().asInt();
            if (len * Math.max(strideA.getByteCount(), strideB.getByteCount()) < GraalOptions.ArrayRegionEqualsConstantLimit.getValue(tool.getOptions()) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayA, offsetA, strideA, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayB, offsetB, strideB, len, this)) {
                Integer startIndex1 = ConstantReflectionUtil.startIndex(tool, arrayA, offsetA.asJavaConstant(), strideA, this);
                Integer startIndex2 = ConstantReflectionUtil.startIndex(tool, arrayB, offsetB.asJavaConstant(), strideB, this);
                return ConstantNode.forInt(foldResult(tool, arrayA, startIndex1, arrayB, startIndex2, len));
            }
        }

        return this;
    }

    protected int foldResult(CanonicalizerTool tool, ValueNode a, int startIndexA, ValueNode b, int startIndexB, int len) {
        JavaKind arrayKindA = a.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindB = b.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        for (int i = 0; i < len; i++) {
            int valueA = ConstantReflectionUtil.readTypePunned(constantReflection, a.asJavaConstant(), arrayKindA, strideA, startIndexA + i);
            int valueB = ConstantReflectionUtil.readTypePunned(constantReflection, b.asJavaConstant(), arrayKindB, strideB, startIndexB + i);
            int cmp = valueA - valueB;
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}

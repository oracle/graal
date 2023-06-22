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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.replacements.NodeStrideUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Specialized arraycopy stub that assumes disjoint source and target arrays, and supports
 * compression and inflation depending on {@code strideSrc} and {@code strideDst}.
 */
@NodeInfo(allowedUsageTypes = {Memory}, cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public class ArrayCopyWithConversionsNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<ArrayCopyWithConversionsNode> TYPE = NodeClass.create(ArrayCopyWithConversionsNode.class);

    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte), NamedLocationIdentity.OFF_HEAP_LOCATION};

    private final Stride strideSrc;
    private final Stride strideDst;
    @Input protected ValueNode arraySrc;
    @Input protected ValueNode offsetSrc;
    @Input protected ValueNode arrayDst;
    @Input protected ValueNode offsetDst;
    @Input protected ValueNode length;

    /**
     * Optional argument for dispatching to any combination of strides at runtime, as described in
     * {@link org.graalvm.compiler.core.common.StrideUtil}.
     */
    @OptionalInput protected ValueNode dynamicStrides;

    /**
     * Arraycopy operation for arbitrary source and destination arrays, with arbitrary byte offset,
     * with support for arbitrary compression and inflation of {@link JavaKind#Byte 8 bit},
     * {@link JavaKind#Char 16 bit} or {@link JavaKind#Int 32 bit} array elements.
     *
     * @param strideSrc source stride. May be {@link JavaKind#Byte 8 bit}, {@link JavaKind#Char 16
     *            bit} or {@link JavaKind#Int 32 bit}.
     * @param strideDst target stride. May be {@link JavaKind#Byte 8 bit}, {@link JavaKind#Char 16
     *            bit} or {@link JavaKind#Int 32 bit}.
     * @param arraySrc source array.
     * @param offsetSrc offset to be added to arraySrc, in bytes. Must include array base offset!
     * @param arrayDst destination array.
     * @param offsetDst offset to be added to arrayDst, in bytes. Must include array base offset!
     * @param length length of the region to copy, scaled to strideDst.
     */
    public ArrayCopyWithConversionsNode(ValueNode arraySrc, ValueNode offsetSrc, ValueNode arrayDst, ValueNode offsetDst, ValueNode length,
                    @ConstantNodeParameter Stride strideSrc,
                    @ConstantNodeParameter Stride strideDst) {
        this(TYPE, arraySrc, offsetSrc, arrayDst, offsetDst, length, null, strideSrc, strideDst, null);
    }

    public ArrayCopyWithConversionsNode(ValueNode arraySrc, ValueNode offsetSrc, ValueNode arrayDst, ValueNode offsetDst, ValueNode length,
                    @ConstantNodeParameter Stride strideSrc,
                    @ConstantNodeParameter Stride strideDst,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arraySrc, offsetSrc, arrayDst, offsetDst, length, null, strideSrc, strideDst, runtimeCheckedCPUFeatures);
    }

    /**
     * Variant with dynamicStride parameter, as described in
     * {@link org.graalvm.compiler.core.common.StrideUtil}.
     */
    public ArrayCopyWithConversionsNode(ValueNode arraySrc, ValueNode offsetSrc, ValueNode arrayDst, ValueNode offsetDst, ValueNode length, ValueNode dynamicStrides) {
        this(TYPE, arraySrc, offsetSrc, arrayDst, offsetDst, length, dynamicStrides, null, null, null);
    }

    public ArrayCopyWithConversionsNode(ValueNode arraySrc, ValueNode offsetSrc, ValueNode arrayDst, ValueNode offsetDst, ValueNode length, ValueNode dynamicStrides,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arraySrc, offsetSrc, arrayDst, offsetDst, length, dynamicStrides, null, null, runtimeCheckedCPUFeatures);
    }

    protected ArrayCopyWithConversionsNode(NodeClass<? extends ArrayCopyWithConversionsNode> c,
                    ValueNode arraySrc, ValueNode offsetSrc, ValueNode arrayDst, ValueNode offsetDst, ValueNode length, ValueNode dynamicStrides,
                    @ConstantNodeParameter Stride strideSrc,
                    @ConstantNodeParameter Stride strideDst,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        // TruffleStrings is using the same stub generated by this node to read from
        // byte[]/char[]/int[] arrays and native buffers, so we have to use LocationIdentity.any()
        // here.
        super(c, StampFactory.forKind(JavaKind.Void), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.strideSrc = strideSrc;
        this.strideDst = strideDst;
        this.arraySrc = arraySrc;
        this.offsetSrc = offsetSrc;
        this.arrayDst = arrayDst;
        this.offsetDst = offsetDst;
        this.length = length;
        this.dynamicStrides = dynamicStrides;
    }

    @NodeIntrinsic
    @GenerateStub(name = "arrayCopyWithConversionsS1S1", parameters = {"S1", "S1"})
    @GenerateStub(name = "arrayCopyWithConversionsS1S2", parameters = {"S1", "S2"})
    @GenerateStub(name = "arrayCopyWithConversionsS1S4", parameters = {"S1", "S4"})
    @GenerateStub(name = "arrayCopyWithConversionsS2S1", parameters = {"S2", "S1"})
    @GenerateStub(name = "arrayCopyWithConversionsS2S2", parameters = {"S2", "S2"})
    @GenerateStub(name = "arrayCopyWithConversionsS2S4", parameters = {"S2", "S4"})
    @GenerateStub(name = "arrayCopyWithConversionsS4S1", parameters = {"S4", "S1"})
    @GenerateStub(name = "arrayCopyWithConversionsS4S2", parameters = {"S4", "S2"})
    @GenerateStub(name = "arrayCopyWithConversionsS4S4", parameters = {"S4", "S4"})
    public static native void arrayCopy(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length,
                    @ConstantNodeParameter Stride strideSrc,
                    @ConstantNodeParameter Stride strideDst);

    @NodeIntrinsic
    public static native void arrayCopy(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length,
                    @ConstantNodeParameter Stride strideSrc,
                    @ConstantNodeParameter Stride strideDst,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "arrayCopyWithConversionsDynamicStrides")
    public static native void arrayCopy(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length, int stride);

    @NodeIntrinsic
    public static native void arrayCopy(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length, int stride,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    public int getDirectStubCallIndex() {
        return NodeStrideUtil.getDirectStubCallIndex(dynamicStrides, strideSrc, strideDst);
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayCopyWithConversionsForeignCalls.getStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        if (getDirectStubCallIndex() < 0) {
            return new ValueNode[]{arraySrc, offsetSrc, arrayDst, offsetDst, length, dynamicStrides};
        } else {
            return new ValueNode[]{arraySrc, offsetSrc, arrayDst, offsetDst, length};
        }
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        if (getDirectStubCallIndex() < 0) {
            gen.getLIRGeneratorTool().emitArrayCopyWithConversion(
                            getRuntimeCheckedCPUFeatures(),
                            gen.operand(arraySrc),
                            gen.operand(offsetSrc),
                            gen.operand(arrayDst),
                            gen.operand(offsetDst),
                            gen.operand(length),
                            gen.operand(dynamicStrides));
        } else {
            gen.getLIRGeneratorTool().emitArrayCopyWithConversion(
                            NodeStrideUtil.getConstantStrideA(dynamicStrides, strideSrc),
                            NodeStrideUtil.getConstantStrideB(dynamicStrides, strideDst),
                            getRuntimeCheckedCPUFeatures(),
                            gen.operand(arraySrc),
                            gen.operand(offsetSrc),
                            gen.operand(arrayDst),
                            gen.operand(offsetDst),
                            gen.operand(length));
        }
    }

    /**
     * TruffleStrings is using the same stub generated by this node to write to {@code byte[]}
     * arrays and native buffers.
     */
    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }
}

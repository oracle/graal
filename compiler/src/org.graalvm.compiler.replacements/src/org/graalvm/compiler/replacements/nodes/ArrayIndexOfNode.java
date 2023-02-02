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

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_16;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.ConstantReflectionUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

/**
 * Stub-call node for various indexOf-operations.
 *
 * Parameters:
 * <ul>
 * <li>{@code arrayPointer}: pointer to a java array or native memory location.</li>
 * <li>{@code arrayOffset}: byte offset to be added to the array pointer. This offset must include
 * the array base offset!</li>
 * <li>{@code arrayLength}: array length respective to the element size given by
 * {@code stride}.</li>
 * <li>{@code fromIndex}: start index of the indexOf search, respective to the element size given by
 * {@code stride}.</li>
 * <li>{@code searchValues}: between 1-4 int values to be searched. These values are ALWAYS expected
 * to be INT (4 bytes), if the {@code stride} is smaller, they should be zero-extended!</li>
 * </ul>
 *
 * The boolean parameters {@code findTwoConsecutive} and {@code withMask} determine the search
 * algorithm:
 * <ul>
 * <li>If both are {@code false}, the operation finds the index of the first occurrence of
 * <i>any</i> of the 1-4 {@code searchValues}.</li>
 * <li>If {@code findTwoConsecutive} is {@code true} and {@code withMask} is {@code false}, the
 * number of search values must be two. The operation will then search for the first occurrence of
 * both values in succession.</li>
 * <li>If {@code withMask} is {@code true} and {@code findTwoConsecutive} is {@code false}, the
 * number of search values must be two. The operation will then search for the first index {@code i}
 * where {@code (array[i] | searchValues[1]) == searchValues[0]}.</li>
 * <li>If {@code findTwoConsecutive} is {@code true} and {@code withMask} is {@code true}, the
 * number of search values must be four. The operation will then search for the first index
 * {@code i} where
 * {@code (array[i] | searchValues[2]) == searchValues[0] && (array[i + 1] | searchValues[3]) == searchValues[1]}.</li>
 * </ul>
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = SIZE_16)
public class ArrayIndexOfNode extends PureFunctionStubIntrinsicNode implements Canonicalizable {

    public static final NodeClass<ArrayIndexOfNode> TYPE = NodeClass.create(ArrayIndexOfNode.class);

    private final Stride stride;
    private final boolean findTwoConsecutive;
    private final boolean withMask;

    @Input private ValueNode arrayPointer;
    @Input private ValueNode arrayOffset;
    @Input private ValueNode arrayLength;
    @Input private ValueNode fromIndex;
    @Input private NodeInputList<ValueNode> searchValues;

    public ArrayIndexOfNode(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, stride, findTwoConsecutive, withMask, null, defaultLocationIdentity(arrayKind),
                        arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfNode(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, stride, findTwoConsecutive, withMask, runtimeCheckedCPUFeatures, defaultLocationIdentity(arrayKind),
                        arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfNode(
                    Stride stride,
                    boolean findTwoConsecutive,
                    boolean withMask,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, stride, findTwoConsecutive, withMask, runtimeCheckedCPUFeatures, locationIdentity, arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfNode(
                    NodeClass<? extends ArrayIndexOfNode> c,
                    Stride stride,
                    boolean findTwoConsecutive,
                    boolean withMask,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        super(c, StampFactory.forKind(JavaKind.Int), runtimeCheckedCPUFeatures, locationIdentity);
        GraalError.guarantee(stride.value <= 4, "unsupported stride");
        GraalError.guarantee(!(!withMask && findTwoConsecutive) || searchValues.length == 2, "findTwoConsecutive without mask requires exactly two search values");
        GraalError.guarantee(!(withMask && findTwoConsecutive) || searchValues.length == 4, "findTwoConsecutive with mask requires exactly four search values");
        GraalError.guarantee(!(withMask && !findTwoConsecutive) || searchValues.length == 2, "indexOf with mask requires exactly two search values");
        this.stride = stride;
        this.findTwoConsecutive = findTwoConsecutive;
        this.withMask = withMask;
        this.arrayPointer = arrayPointer;
        this.arrayOffset = arrayOffset;
        this.arrayLength = arrayLength;
        this.fromIndex = fromIndex;
        this.searchValues = new NodeInputList<>(this, searchValues);
    }

    public static ArrayIndexOfNode createIndexOfSingle(GraphBuilderContext b, JavaKind arrayKind, Stride stride, ValueNode array, ValueNode arrayLength, ValueNode fromIndex, ValueNode searchValue) {
        ValueNode baseOffset = ConstantNode.forLong(b.getMetaAccess().getArrayBaseOffset(arrayKind), b.getGraph());
        return new ArrayIndexOfNode(TYPE, stride, false, false, null, defaultLocationIdentity(arrayKind),
                        array, baseOffset, arrayLength, fromIndex, searchValue);
    }

    private static LocationIdentity defaultLocationIdentity(JavaKind arrayKind) {
        return arrayKind == JavaKind.Void ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    public boolean isFindTwoConsecutive() {
        return findTwoConsecutive;
    }

    public boolean isWithMask() {
        return withMask;
    }

    public ValueNode getArrayPointer() {
        return arrayPointer;
    }

    public ValueNode getArrayOffset() {
        return arrayOffset;
    }

    public ValueNode getArrayLength() {
        return arrayLength;
    }

    public ValueNode getFromIndex() {
        return fromIndex;
    }

    public NodeInputList<ValueNode> getSearchValues() {
        return searchValues;
    }

    public int getNumberOfValues() {
        return searchValues.size();
    }

    public Stride getStride() {
        return stride;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayIndexOfForeignCalls.getStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        ValueNode[] args = new ValueNode[4 + searchValues.size()];
        args[0] = arrayPointer;
        args[1] = arrayOffset;
        args[2] = arrayLength;
        args[3] = fromIndex;
        for (int i = 0; i < searchValues.size(); i++) {
            args[4 + i] = searchValues.get(i);
        }
        return args;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitArrayIndexOf(
                        stride,
                        findTwoConsecutive,
                        withMask,
                        getRuntimeCheckedCPUFeatures(),
                        gen.operand(arrayPointer),
                        gen.operand(arrayOffset),
                        gen.operand(arrayLength),
                        gen.operand(fromIndex),
                        searchValuesAsOperands(gen)));
    }

    protected int getArrayBaseOffset(MetaAccessProvider metaAccessProvider, @SuppressWarnings("unused") ValueNode array, JavaKind kind) {
        return metaAccessProvider.getArrayBaseOffset(kind);
    }

    private Value[] searchValuesAsOperands(NodeLIRBuilderTool gen) {
        Value[] searchValueOperands = new Value[searchValues.size()];
        for (int i = 0; i < searchValues.size(); i++) {
            searchValueOperands[i] = gen.operand(searchValues.get(i));
        }
        return searchValueOperands;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        if (arrayPointer.isJavaConstant() && ((ConstantNode) arrayPointer).getStableDimension() > 0 &&
                        arrayOffset.isJavaConstant() &&
                        arrayLength.isJavaConstant() &&
                        fromIndex.isJavaConstant() &&
                        searchValuesConstant()) {
            ConstantReflectionProvider provider = tool.getConstantReflection();
            JavaConstant arrayConstant = arrayPointer.asJavaConstant();
            JavaKind constantArrayKind = arrayPointer.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
            int actualArrayLength = provider.readArrayLength(arrayConstant);

            // arrayOffset is given in bytes, scale it to the stride.
            long arrayBaseOffsetBytesConstant = arrayOffset.asJavaConstant().asLong();
            arrayBaseOffsetBytesConstant -= getArrayBaseOffset(tool.getMetaAccess(), arrayPointer, constantArrayKind);
            long arrayOffsetConstant = arrayBaseOffsetBytesConstant / stride.value;

            int arrayLengthConstant = arrayLength.asJavaConstant().asInt();
            assert arrayLengthConstant * stride.value <= actualArrayLength * constantArrayKind.getByteCount();

            int fromIndexConstant = fromIndex.asJavaConstant().asInt();
            int[] valuesConstant = new int[searchValues.size()];
            for (int i = 0; i < searchValues.size(); i++) {
                valuesConstant[i] = searchValues.get(i).asJavaConstant().asInt();
            }
            if (arrayLengthConstant * stride.value < GraalOptions.StringIndexOfConstantLimit.getValue(tool.getOptions())) {
                if (findTwoConsecutive) {
                    assert valuesConstant.length == (withMask ? 4 : 2);
                    for (int i = fromIndexConstant; i < arrayLengthConstant - 1; i++) {
                        int v0 = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, (int) (arrayOffsetConstant + i));
                        int v1 = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, (int) (arrayOffsetConstant + i + 1));
                        if (withMask) {
                            if ((v0 | valuesConstant[2]) == valuesConstant[0] && (v1 | valuesConstant[3]) == valuesConstant[1]) {
                                return ConstantNode.forInt(i);
                            }
                        } else {
                            if (v0 == valuesConstant[0] && v1 == valuesConstant[1]) {
                                return ConstantNode.forInt(i);
                            }
                        }
                    }
                } else {
                    assert !withMask || valuesConstant.length == 2;
                    for (int i = fromIndexConstant; i < arrayLengthConstant; i++) {
                        int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, (int) (arrayOffsetConstant + i));
                        if (withMask) {
                            if ((value | valuesConstant[1]) == valuesConstant[0]) {
                                return ConstantNode.forInt(i);
                            }
                        } else {
                            for (int searchValue : valuesConstant) {
                                if (value == searchValue) {
                                    return ConstantNode.forInt(i);
                                }
                            }
                        }
                    }
                }
                return ConstantNode.forInt(-1);
            }
        }
        return this;
    }

    private boolean searchValuesConstant() {
        for (ValueNode s : searchValues) {
            if (!s.isJavaConstant()) {
                return false;
            }
        }
        return true;
    }

    @NodeIntrinsic
    @GenerateStub(name = "indexOf1S1", parameters = {"Void", "S1", "false", "false"})
    @GenerateStub(name = "indexOf1S2", parameters = {"Void", "S2", "false", "false"})
    @GenerateStub(name = "indexOf1S4", parameters = {"Void", "S4", "false", "false"})
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1);

    @NodeIntrinsic
    @GenerateStub(name = "indexOf2S1", parameters = {"Void", "S1", "false", "false"})
    @GenerateStub(name = "indexOf2S2", parameters = {"Void", "S2", "false", "false"})
    @GenerateStub(name = "indexOf2S4", parameters = {"Void", "S4", "false", "false"})
    @GenerateStub(name = "indexOfWithMaskS1", parameters = {"Void", "S1", "false", "true"})
    @GenerateStub(name = "indexOfWithMaskS2", parameters = {"Void", "S2", "false", "true"})
    @GenerateStub(name = "indexOfWithMaskS4", parameters = {"Void", "S4", "false", "true"})
    @GenerateStub(name = "indexOfTwoConsecutiveS1", parameters = {"Void", "S1", "true", "false"})
    @GenerateStub(name = "indexOfTwoConsecutiveS2", parameters = {"Void", "S2", "true", "false"})
    @GenerateStub(name = "indexOfTwoConsecutiveS4", parameters = {"Void", "S4", "true", "false"})
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2);

    @NodeIntrinsic
    @GenerateStub(name = "indexOf3S1", parameters = {"Void", "S1", "false", "false"})
    @GenerateStub(name = "indexOf3S2", parameters = {"Void", "S2", "false", "false"})
    @GenerateStub(name = "indexOf3S4", parameters = {"Void", "S4", "false", "false"})
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3);

    @NodeIntrinsic
    @GenerateStub(name = "indexOf4S1", parameters = {"Void", "S1", "false", "false"})
    @GenerateStub(name = "indexOf4S2", parameters = {"Void", "S2", "false", "false"})
    @GenerateStub(name = "indexOf4S4", parameters = {"Void", "S4", "false", "false"})
    @GenerateStub(name = "indexOfTwoConsecutiveWithMaskS1", parameters = {"Void", "S1", "true", "true"})
    @GenerateStub(name = "indexOfTwoConsecutiveWithMaskS2", parameters = {"Void", "S2", "true", "true"})
    @GenerateStub(name = "indexOfTwoConsecutiveWithMaskS4", parameters = {"Void", "S4", "true", "true"})
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4);

}

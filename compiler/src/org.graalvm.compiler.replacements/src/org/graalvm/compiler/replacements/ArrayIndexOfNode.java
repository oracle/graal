/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.core.common.GraalOptions.UseGraalStubs;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_512;
import static org.graalvm.compiler.replacements.ArrayIndexOf.NONE;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S1;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S2;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S4;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
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
 * <li>{@code arrayOffset}: byte offset to be added to the array pointer. If {@code arrayKind} is
 * {@link JavaKind#Void}, this offset must include the array base offset!</li>
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
@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class ArrayIndexOfNode extends FixedWithNextNode implements Canonicalizable, LIRLowerable, MemoryAccess {

    public static final NodeClass<ArrayIndexOfNode> TYPE = NodeClass.create(ArrayIndexOfNode.class);

    private final JavaKind arrayKind;
    private final JavaKind stride;
    private final boolean findTwoConsecutive;
    private final boolean withMask;
    private final LocationIdentity locationIdentity;

    @Input private ValueNode arrayPointer;
    @Input private ValueNode arrayOffset;
    @Input private ValueNode arrayLength;
    @Input private ValueNode fromIndex;
    @Input private NodeInputList<ValueNode> searchValues;

    @OptionalInput(InputType.Memory) private MemoryKill lastLocationAccess;

    public ArrayIndexOfNode(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind stride,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, arrayKind, stride, findTwoConsecutive, withMask, arrayKind == NONE ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(arrayKind),
                        arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfNode(
                    JavaKind arrayKind,
                    JavaKind stride,
                    boolean findTwoConsecutive,
                    boolean withMask,
                    LocationIdentity locationIdentity,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, arrayKind, stride, findTwoConsecutive, withMask, locationIdentity, arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfNode(
                    NodeClass<? extends ArrayIndexOfNode> c,
                    JavaKind arrayKind,
                    JavaKind stride,
                    boolean findTwoConsecutive,
                    boolean withMask,
                    LocationIdentity locationIdentity,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        super(c, StampFactory.forKind(JavaKind.Int));
        GraalError.guarantee(arrayKind == S1 || arrayKind == S2 || arrayKind == S4 || arrayKind == NONE, "unsupported arrayKind");
        GraalError.guarantee(!(!withMask && findTwoConsecutive) || searchValues.length == 2, "findTwoConsecutive without mask requires exactly two search values");
        GraalError.guarantee(!(withMask && findTwoConsecutive) || searchValues.length == 4, "findTwoConsecutive with mask requires exactly four search values");
        GraalError.guarantee(!(withMask && !findTwoConsecutive) || searchValues.length == 2, "indexOf with mask requires exactly two search values");
        this.arrayKind = arrayKind;
        this.stride = stride;
        this.findTwoConsecutive = findTwoConsecutive;
        this.withMask = withMask;
        this.locationIdentity = locationIdentity;
        this.arrayPointer = arrayPointer;
        this.arrayOffset = arrayOffset;
        this.arrayLength = arrayLength;
        this.fromIndex = fromIndex;
        this.searchValues = new NodeInputList<>(this, searchValues);
    }

    public JavaKind getArrayKind() {
        return arrayKind;
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

    public JavaKind getStride() {
        return stride;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                Value[] operands = new Value[4 + searchValues.size()];
                operands[0] = gen.operand(arrayPointer);
                operands[1] = gen.operand(arrayOffset);
                operands[2] = gen.operand(arrayLength);
                operands[3] = gen.operand(fromIndex);
                for (int i = 0; i < searchValues.size(); i++) {
                    operands[4 + i] = gen.operand(searchValues.get(i));
                }
                Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, operands);
                gen.setResult(this, result);
                return;
            }
        }
        generateArrayIndexOf(gen);
    }

    private void generateArrayIndexOf(NodeLIRBuilderTool gen) {
        int arrayBaseOffset = arrayKind == JavaKind.Void ? 0 : getArrayBaseOffset(gen.getLIRGeneratorTool().getMetaAccess(), arrayPointer, arrayKind);
        Value result = gen.getLIRGeneratorTool().emitArrayIndexOf(arrayBaseOffset, stride, findTwoConsecutive, withMask,
                        gen.operand(arrayPointer), gen.operand(arrayOffset), gen.operand(arrayLength), gen.operand(fromIndex), searchValuesAsOperands(gen));
        gen.setResult(this, result);
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
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
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
            if (arrayKind == NONE) {
                arrayBaseOffsetBytesConstant -= getArrayBaseOffset(tool.getMetaAccess(), arrayPointer, constantArrayKind);
            }
            long arrayOffsetConstant = arrayBaseOffsetBytesConstant / stride.getByteCount();

            int arrayLengthConstant = arrayLength.asJavaConstant().asInt();
            assert arrayLengthConstant * stride.getByteCount() <= actualArrayLength * constantArrayKind.getByteCount();

            int fromIndexConstant = fromIndex.asJavaConstant().asInt();
            int[] valuesConstant = new int[searchValues.size()];
            for (int i = 0; i < searchValues.size(); i++) {
                valuesConstant[i] = searchValues.get(i).asJavaConstant().asInt();
            }
            if (arrayLengthConstant * stride.getByteCount() < GraalOptions.StringIndexOfConstantLimit.getValue(tool.getOptions())) {
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
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    @ConstantNodeParameter boolean withMask,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4);

    public static int indexOf(JavaKind arrayKind, JavaKind stride, Object array, long arrayOffset, int arrayLength, int fromIndex, int v1) {
        return optimizedArrayIndexOf(arrayKind, stride, false, false, array, arrayOffset, arrayLength, fromIndex, v1);
    }

    public static int indexOf2Consecutive(JavaKind arrayKind, JavaKind stride, Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2) {
        return optimizedArrayIndexOf(arrayKind, stride, true, false, array, arrayOffset, arrayLength, fromIndex, v1, v2);
    }
}

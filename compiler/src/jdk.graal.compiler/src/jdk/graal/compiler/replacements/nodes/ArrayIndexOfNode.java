/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_16;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.ArrayIndexOfVariant;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.util.ConstantReflectionUtil;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
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
    private final ArrayIndexOfVariant variant;

    @Input private ValueNode arrayPointer;
    @Input private ValueNode arrayOffset;
    @Input private ValueNode arrayLength;
    @Input private ValueNode fromIndex;
    @Input private NodeInputList<ValueNode> searchValues;

    public ArrayIndexOfNode(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, stride, variant, null, LocationIdentity.any(), arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfNode(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, stride, variant, runtimeCheckedCPUFeatures, LocationIdentity.any(), arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfNode(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, stride, variant, null, NamedLocationIdentity.getArrayLocation(arrayKind), arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfNode(
                    Stride stride,
                    ArrayIndexOfVariant variant,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, stride, variant, runtimeCheckedCPUFeatures, locationIdentity, arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues);
    }

    @SuppressWarnings("this-escape")
    public ArrayIndexOfNode(
                    NodeClass<? extends ArrayIndexOfNode> c,
                    Stride stride,
                    ArrayIndexOfVariant variant,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity,
                    ValueNode arrayPointer, ValueNode arrayOffset, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        super(c, StampFactory.forKind(JavaKind.Int), runtimeCheckedCPUFeatures, locationIdentity);
        GraalError.guarantee(stride.value <= 4, "unsupported stride");
        GraalError.guarantee(variant != ArrayIndexOfVariant.MatchAny || searchValues.length > 0 && searchValues.length <= 4, "indexOfAny requires 1 - 4 search values");
        GraalError.guarantee(variant != ArrayIndexOfVariant.MatchRange || searchValues.length == 2 || searchValues.length == 4, "indexOfRange requires exactly two or four search values");
        GraalError.guarantee(variant != ArrayIndexOfVariant.WithMask || searchValues.length == 2, "indexOf with mask requires exactly two search values");
        GraalError.guarantee(variant != ArrayIndexOfVariant.FindTwoConsecutive || searchValues.length == 2, "findTwoConsecutive without mask requires exactly two search values");
        GraalError.guarantee(variant != ArrayIndexOfVariant.FindTwoConsecutiveWithMask || searchValues.length == 4, "findTwoConsecutive with mask requires exactly four search values");
        this.stride = stride;
        this.variant = variant;
        this.arrayPointer = arrayPointer;
        this.arrayOffset = arrayOffset;
        this.arrayLength = arrayLength;
        this.fromIndex = fromIndex;
        this.searchValues = new NodeInputList<>(this, searchValues);
    }

    public static ArrayIndexOfNode createIndexOfSingle(GraphBuilderContext b, JavaKind arrayKind, Stride stride, ValueNode array, ValueNode arrayLength, ValueNode fromIndex, ValueNode searchValue) {
        ValueNode baseOffset = ConstantNode.forLong(b.getMetaAccess().getArrayBaseOffset(arrayKind), b.getGraph());
        return new ArrayIndexOfNode(TYPE, stride, ArrayIndexOfVariant.MatchAny, null, defaultLocationIdentity(arrayKind),
                        array, baseOffset, arrayLength, fromIndex, searchValue);
    }

    private static LocationIdentity defaultLocationIdentity(JavaKind arrayKind) {
        return arrayKind == JavaKind.Void ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64(Stride stride, ArrayIndexOfVariant variant) {
        switch (variant) {
            case MatchAny:
            case WithMask:
            case FindTwoConsecutive:
            case FindTwoConsecutiveWithMask:
                return EnumSet.of(AMD64.CPUFeature.SSE2);
            case MatchRange:
                if (stride == Stride.S1) {
                    return EnumSet.of(AMD64.CPUFeature.SSE2);
                } else {
                    return amd64FeaturesSSE41();
                }
            case Table:
                return amd64FeaturesSSE41();
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(variant); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static EnumSet<AMD64.CPUFeature> amd64FeaturesSSE41() {
        return EnumSet.of(AMD64.CPUFeature.SSE2, AMD64.CPUFeature.SSSE3, AMD64.CPUFeature.SSE4_1);
    }

    public static EnumSet<AArch64.CPUFeature> aarch64FeaturesNone() {
        return EnumSet.noneOf(AArch64.CPUFeature.class);
    }

    public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
        return EnumSet.noneOf(AArch64.CPUFeature.class);
    }

    static boolean isSupported(Architecture arch, Stride stride, ArrayIndexOfVariant variant) {
        return arch instanceof AMD64 && ((AMD64) arch).getFeatures().containsAll(minFeaturesAMD64(stride, variant)) ||
                        arch instanceof AArch64 && ((AArch64) arch).getFeatures().containsAll(minFeaturesAARCH64());
    }

    public ArrayIndexOfVariant getVariant() {
        return variant;
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
                        variant,
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
        if (variant == ArrayIndexOfVariant.Table) {
            return this;
        }
        if (ConstantReflectionUtil.isStableJavaArray(arrayPointer) &&
                        arrayOffset.isJavaConstant() &&
                        arrayLength.isJavaConstant() &&
                        fromIndex.isJavaConstant() &&
                        searchValuesConstant()) {
            final ConstantReflectionProvider provider = tool.getConstantReflection();
            final JavaConstant arrayConstant = arrayPointer.asJavaConstant();
            final JavaKind constantArrayKind = arrayPointer.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
            final int actualArrayLength = provider.readArrayLength(arrayConstant);

            // arrayOffset is given in bytes, scale it to the stride.
            long arrayBaseOffsetBytesConstant = arrayOffset.asJavaConstant().asLong();
            arrayBaseOffsetBytesConstant -= getArrayBaseOffset(tool.getMetaAccess(), arrayPointer, constantArrayKind);
            final long arrayOffsetConstantScaled = arrayBaseOffsetBytesConstant >> stride.log2;

            final int arrayLengthConstant = arrayLength.asJavaConstant().asInt();
            final int fromIndexConstant = fromIndex.asJavaConstant().asInt();
            if (!ConstantReflectionUtil.boundsCheckTypePunned(arrayOffsetConstantScaled, arrayLengthConstant, stride, actualArrayLength, constantArrayKind) ||
                            (fromIndexConstant < 0 || fromIndexConstant > arrayLengthConstant)) {
                /*
                 * One or more arguments is out of bounds for the given constant array.
                 *
                 * This may happen when this node is in a branch that won't be taken for the given
                 * constant input values, but is still visible in the current compilation unit.
                 * Since this isn't necessarily an error, we want to handle this case gracefully and
                 * simply give up on trying to constant-fold. Explicit range checks preceding the
                 * intrinsic call will take care of throwing an exception. Alternatively, we could
                 * deoptimize here.
                 */
                return this;
            }
            final int arrayOffsetConstant = NumUtil.safeToInt(arrayOffsetConstantScaled);

            final int[] valuesConstant = new int[searchValues.size()];
            for (int i = 0; i < searchValues.size(); i++) {
                valuesConstant[i] = searchValues.get(i).asJavaConstant().asInt();
            }
            if (ConstantReflectionUtil.shouldConstantFoldArrayOperation(tool, arrayLengthConstant)) {
                switch (variant) {
                    case MatchAny:
                        for (int i = fromIndexConstant; i < arrayLengthConstant; i++) {
                            int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, arrayOffsetConstant + i);
                            for (int searchValue : valuesConstant) {
                                if (value == searchValue) {
                                    return ConstantNode.forInt(i);
                                }
                            }
                        }
                        break;
                    case MatchRange:
                        for (int i = fromIndexConstant; i < arrayLengthConstant; i++) {
                            int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, arrayOffsetConstant + i);
                            for (int j = 0; j < valuesConstant.length; j += 2) {
                                if (valuesConstant[j] <= value && value <= valuesConstant[j + 1]) {
                                    return ConstantNode.forInt(i);
                                }
                            }
                        }
                        break;
                    case WithMask:
                        for (int i = fromIndexConstant; i < arrayLengthConstant; i++) {
                            int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, arrayOffsetConstant + i);
                            if ((value | valuesConstant[1]) == valuesConstant[0]) {
                                return ConstantNode.forInt(i);
                            }
                        }
                        break;
                    case FindTwoConsecutive:
                        for (int i = fromIndexConstant; i < arrayLengthConstant - 1; i++) {
                            int v0 = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, arrayOffsetConstant + i);
                            int v1 = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, arrayOffsetConstant + i + 1);
                            if (v0 == valuesConstant[0] && v1 == valuesConstant[1]) {
                                return ConstantNode.forInt(i);
                            }
                        }
                        break;
                    case FindTwoConsecutiveWithMask:
                        for (int i = fromIndexConstant; i < arrayLengthConstant - 1; i++) {
                            int v0 = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, arrayOffsetConstant + i);
                            int v1 = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, arrayOffsetConstant + i + 1);
                            if ((v0 | valuesConstant[2]) == valuesConstant[0] && (v1 | valuesConstant[3]) == valuesConstant[1]) {
                                return ConstantNode.forInt(i);
                            }
                        }
                        break;
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
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2);

    @NodeIntrinsic
    @GenerateStub(name = "indexOf1S1", parameters = {"S1", "MatchAny"})
    @GenerateStub(name = "indexOf1S2", parameters = {"S2", "MatchAny"})
    @GenerateStub(name = "indexOf1S4", parameters = {"S4", "MatchAny"})
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1);

    @NodeIntrinsic
    @GenerateStub(name = "indexOf2S1", parameters = {"S1", "MatchAny"})
    @GenerateStub(name = "indexOf2S2", parameters = {"S2", "MatchAny"})
    @GenerateStub(name = "indexOf2S4", parameters = {"S4", "MatchAny"})
    @GenerateStub(name = "indexOfRange1S1", parameters = {"S1", "MatchRange"})
    @GenerateStub(name = "indexOfRange1S2", parameters = {"S2", "MatchRange"}, minimumCPUFeaturesAMD64 = "amd64FeaturesSSE41", minimumCPUFeaturesAARCH64 = "aarch64FeaturesNone")
    @GenerateStub(name = "indexOfRange1S4", parameters = {"S4", "MatchRange"}, minimumCPUFeaturesAMD64 = "amd64FeaturesSSE41", minimumCPUFeaturesAARCH64 = "aarch64FeaturesNone")
    @GenerateStub(name = "indexOfWithMaskS1", parameters = {"S1", "WithMask"})
    @GenerateStub(name = "indexOfWithMaskS2", parameters = {"S2", "WithMask"})
    @GenerateStub(name = "indexOfWithMaskS4", parameters = {"S4", "WithMask"})
    @GenerateStub(name = "indexOfTwoConsecutiveS1", parameters = {"S1", "FindTwoConsecutive"})
    @GenerateStub(name = "indexOfTwoConsecutiveS2", parameters = {"S2", "FindTwoConsecutive"})
    @GenerateStub(name = "indexOfTwoConsecutiveS4", parameters = {"S4", "FindTwoConsecutive"})
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2);

    @NodeIntrinsic
    @GenerateStub(name = "indexOf3S1", parameters = {"S1", "MatchAny"})
    @GenerateStub(name = "indexOf3S2", parameters = {"S2", "MatchAny"})
    @GenerateStub(name = "indexOf3S4", parameters = {"S4", "MatchAny"})
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3);

    @NodeIntrinsic
    @GenerateStub(name = "indexOf4S1", parameters = {"S1", "MatchAny"})
    @GenerateStub(name = "indexOf4S2", parameters = {"S2", "MatchAny"})
    @GenerateStub(name = "indexOf4S4", parameters = {"S4", "MatchAny"})
    @GenerateStub(name = "indexOfRange2S1", parameters = {"S1", "MatchRange"})
    @GenerateStub(name = "indexOfRange2S2", parameters = {"S2", "MatchRange"}, minimumCPUFeaturesAMD64 = "amd64FeaturesSSE41", minimumCPUFeaturesAARCH64 = "aarch64FeaturesNone")
    @GenerateStub(name = "indexOfRange2S4", parameters = {"S4", "MatchRange"}, minimumCPUFeaturesAMD64 = "amd64FeaturesSSE41", minimumCPUFeaturesAARCH64 = "aarch64FeaturesNone")
    @GenerateStub(name = "indexOfTwoConsecutiveWithMaskS1", parameters = {"S1", "FindTwoConsecutiveWithMask"})
    @GenerateStub(name = "indexOfTwoConsecutiveWithMaskS2", parameters = {"S2", "FindTwoConsecutiveWithMask"})
    @GenerateStub(name = "indexOfTwoConsecutiveWithMaskS4", parameters = {"S4", "FindTwoConsecutiveWithMask"})
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4);

    @NodeIntrinsic
    @GenerateStub(name = "indexOfTableS1", parameters = {"S1", "Table"}, minimumCPUFeaturesAMD64 = "amd64FeaturesSSE41", minimumCPUFeaturesAARCH64 = "aarch64FeaturesNone")
    @GenerateStub(name = "indexOfTableS2", parameters = {"S2", "Table"}, minimumCPUFeaturesAMD64 = "amd64FeaturesSSE41", minimumCPUFeaturesAARCH64 = "aarch64FeaturesNone")
    @GenerateStub(name = "indexOfTableS4", parameters = {"S4", "Table"}, minimumCPUFeaturesAMD64 = "amd64FeaturesSSE41", minimumCPUFeaturesAARCH64 = "aarch64FeaturesNone")
    public static native int optimizedArrayIndexOfTable(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, byte[] tables);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOfTable(
                    @ConstantNodeParameter Stride stride,
                    @ConstantNodeParameter ArrayIndexOfVariant variant,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    Object array, long arrayOffset, int arrayLength, int fromIndex, byte[] tables);

}

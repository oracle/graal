/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_16BIT;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_7BIT;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_8BIT;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_BROKEN;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_VALID;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_VALID_MULTIBYTE;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_16;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_8;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_8_STATE_MACHINE_ACCEPTING_STATE;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.utf8GetNextState;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.POPCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding;
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
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * This intrinsic calculates properties of string contents in various encodings, see
 * {@code AMD64CalcStringAttributesOp} for details.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public final class CalcStringAttributesNode extends PureFunctionStubIntrinsicNode implements Canonicalizable {

    public static final NodeClass<CalcStringAttributesNode> TYPE = NodeClass.create(CalcStringAttributesNode.class);

    private static final EnumSet<AMD64.CPUFeature> MINIMUM_FEATURES_AMD64 = EnumSet.of(
                    SSE,
                    SSE2,
                    SSE3,
                    SSSE3,
                    SSE4_1,
                    SSE4_2,
                    POPCNT);
    public static final int MAX_ASCII_VALUE = 0x7f;
    public static final int MAX_LATIN_1_VALUE = 0xff;

    private final CalcStringAttributesEncoding encoding;
    private final boolean assumeValid;

    @Input protected ValueNode array;
    @Input protected ValueNode offset;
    @Input protected ValueNode length;

    /**
     * This constructor is used by the {@code NodeIntrinsic} plugins below, which in turn are used
     * only in {@code AMD64CalcStringAttributesStub}, which is why we are using
     * {@link LocationIdentity#any()} here. The nodes calling the stubs are using more fine-grained
     * location identities, but are calling the same stubs (after
     * {@link #generate(NodeLIRBuilderTool) assembly generation}).
     */
    protected CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid) {
        this(array, offset, length, encoding, assumeValid, null, LocationIdentity.any());
    }

    protected CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(array, offset, length, encoding, assumeValid, runtimeCheckedCPUFeatures, LocationIdentity.any());
    }

    public CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    LocationIdentity locationIdentity) {
        this(array, offset, length, encoding, assumeValid, null, locationIdentity);
    }

    public CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(getReturnValueKind(encoding)), runtimeCheckedCPUFeatures, locationIdentity);
        this.encoding = encoding;
        this.assumeValid = assumeValid;
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    private static JavaKind getReturnValueKind(CalcStringAttributesEncoding encoding) {
        return encoding == UTF_8 || encoding == UTF_16 ? JavaKind.Long : JavaKind.Int;
    }

    public CalcStringAttributesEncoding getOp() {
        return encoding;
    }

    public boolean isAssumeValid() {
        return assumeValid;
    }

    public ValueNode getArray() {
        return array;
    }

    public ValueNode getOffset() {
        return offset;
    }

    public ValueNode getLength() {
        return length;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        // check if all parameters are constant
        if (ConstantReflectionUtil.isStableJavaArray(array) && offset.isJavaConstant() && length.isJavaConstant()) {
            final Stride stride = encoding.stride;
            final ConstantReflectionProvider provider = tool.getConstantReflection();
            final JavaConstant arrayConstant = array.asJavaConstant();
            final JavaKind constantArrayKind = array.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
            int actualArrayLength = provider.readArrayLength(arrayConstant);

            // arrayOffset is given in bytes, scale it to the stride.
            long arrayBaseOffsetBytesConstant = offset.asJavaConstant().asLong();
            arrayBaseOffsetBytesConstant -= tool.getMetaAccess().getArrayBaseOffset(constantArrayKind);
            final long offsetConstantScaled = arrayBaseOffsetBytesConstant >> stride.log2;

            final int lengthConstant = length.asJavaConstant().asInt();
            if (!ConstantReflectionUtil.boundsCheckTypePunned(offsetConstantScaled, lengthConstant, stride, actualArrayLength, constantArrayKind)) {
                /*
                 * This may happen when this node is in a branch that won't be taken for the given
                 * array, but is still visible in the current compilation unit, e.g. for compact
                 * UTF-16 strings:
                 *
                 * // @formatter:off
                 *
                 * byte[] array = {'a', 'b', 'c'};
                 * int stringLength = 3;
                 * boolean isCompactString = true;
                 *
                 * if (isCompactString) {
                 *     // byte length in latin1 is 3
                 *     calcStringAttributesLatin1(array, stringLength);
                 * } else {
                 *     // byte length in utf16 is 6 -> out of bounds. this branch won't be taken,
                 *     // but Graal may still try to constant-fold it
                 *     calcStringAttributesUTF16(array, stringLength);
                 * }
                 *
                 * // @formatter:on
                 */
                return this;
            }
            final int offsetConstant = NumUtil.safeToInt(offsetConstantScaled);

            if (ConstantReflectionUtil.shouldConstantFoldArrayOperation(tool, lengthConstant)) {
                switch (encoding) {
                    case LATIN1 -> {
                        for (int i = 0; i < lengthConstant; i++) {
                            int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, offsetConstant + i);
                            if (value > MAX_ASCII_VALUE) {
                                return ConstantNode.forInt(CR_8BIT);
                            }
                        }
                        return ConstantNode.forInt(CR_7BIT);
                    }
                    case BMP -> {
                        int ret = CR_7BIT;
                        for (int i = 0; i < lengthConstant; i++) {
                            final int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, offsetConstant + i);
                            if (value > MAX_LATIN_1_VALUE) {
                                ret = CR_16BIT;
                                break;
                            }
                            if (value > MAX_ASCII_VALUE) {
                                ret = CR_8BIT;
                            }
                        }
                        return ConstantNode.forInt(ret);
                    }
                    case UTF_8 -> {
                        int ret = CR_7BIT;
                        int state = UTF_8_STATE_MACHINE_ACCEPTING_STATE;
                        long nCodePoints = 0;
                        for (int i = 0; i < lengthConstant; i++) {
                            int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, offsetConstant + i);
                            if (!isUTF8ContinuationByte(value)) {
                                nCodePoints++;
                            }
                            if (value > MAX_ASCII_VALUE) {
                                ret = CR_VALID_MULTIBYTE;
                            }
                            state = utf8GetNextState(state, value);
                        }
                        if (!assumeValid && state != UTF_8_STATE_MACHINE_ACCEPTING_STATE) {
                            ret = CR_BROKEN_MULTIBYTE;
                        }
                        return ConstantNode.forLong(nCodePoints << 32 | ret);
                    }
                    case UTF_16 -> {
                        long nCodePoints = lengthConstant;
                        int last = 0;
                        int ret = CR_7BIT;
                        for (int i = 0; i < lengthConstant; i++) {
                            final int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, offsetConstant + i);
                            if (assumeValid) {
                                if (Character.isHighSurrogate((char) value)) {
                                    nCodePoints--;
                                    ret = CR_VALID_MULTIBYTE;
                                }
                            } else {
                                if (Character.isLowSurrogate((char) value)) {
                                    if (Character.isSurrogatePair((char) last, (char) value)) {
                                        if (ret != CR_BROKEN_MULTIBYTE) {
                                            ret = CR_VALID_MULTIBYTE;
                                        }
                                        nCodePoints--;
                                    } else {
                                        ret = CR_BROKEN_MULTIBYTE;
                                    }
                                } else if (Character.isHighSurrogate((char) last)) {
                                    ret = CR_BROKEN_MULTIBYTE;
                                }
                            }
                            if (ret == CR_7BIT && value > MAX_ASCII_VALUE) {
                                ret = CR_8BIT;
                            }
                            if (ret == CR_8BIT && value > MAX_LATIN_1_VALUE) {
                                ret = CR_16BIT;
                            }
                            last = value;
                        }
                        if (!assumeValid && Character.isHighSurrogate((char) last)) {
                            ret = CR_BROKEN_MULTIBYTE;
                        }
                        return ConstantNode.forLong(nCodePoints << 32 | ret);
                    }
                    case UTF_32 -> {
                        int ret = CR_7BIT;
                        for (int i = 0; i < lengthConstant; i++) {
                            final int value = ConstantReflectionUtil.readTypePunned(provider, arrayConstant, constantArrayKind, stride, offsetConstant + i);
                            if ((Integer.compareUnsigned(value, Character.MAX_CODE_POINT) > 0) || ((value <= Character.MAX_VALUE) && Character.isSurrogate((char) value))) {
                                ret = CR_BROKEN;
                                break;
                            }
                            if (value > Character.MAX_VALUE) {
                                ret = CR_VALID;
                            }
                            if (ret == CR_7BIT && value > MAX_ASCII_VALUE) {
                                ret = CR_8BIT;
                            }
                            if (ret == CR_8BIT && value > MAX_LATIN_1_VALUE) {
                                ret = CR_16BIT;
                            }
                        }
                        return ConstantNode.forInt(ret);
                    }
                }
            }
        }
        return this;
    }

    private static boolean isUTF8ContinuationByte(int value) {
        return (value & 0xc0) == 0x80;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return MINIMUM_FEATURES_AMD64;
    }

    public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
        return EnumSet.noneOf(AArch64.CPUFeature.class);
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return CalcStringAttributesForeignCalls.getStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{array, offset, length};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitCalcStringAttributes(encoding, getRuntimeCheckedCPUFeatures(), gen.operand(array), gen.operand(offset), gen.operand(length), assumeValid));
    }

    /* NodeIntrinsic plugins for snippet stubs. */

    @NodeIntrinsic
    @GenerateStub(name = "calcStringAttributesLatin1", parameters = {"LATIN1", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesBMP", parameters = {"BMP", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF32", parameters = {"UTF_32", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native int intReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid);

    @NodeIntrinsic
    public static native int intReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "calcStringAttributesUTF8Valid", parameters = {"UTF_8", "true"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF8Unknown", parameters = {"UTF_8", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF16Valid", parameters = {"UTF_16", "true"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF16Unknown", parameters = {"UTF_16", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native long longReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid);

    @NodeIntrinsic
    public static native long longReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}

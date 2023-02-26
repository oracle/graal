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
package org.graalvm.compiler.replacements.nodes;

import static jdk.vm.ci.amd64.AMD64.CPUFeature.POPCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * This intrinsic calculates properties of string contents in various encodings, see
 * {@code AMD64CalcStringAttributesOp} for details.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public final class CalcStringAttributesNode extends PureFunctionStubIntrinsicNode {

    public static final NodeClass<CalcStringAttributesNode> TYPE = NodeClass.create(CalcStringAttributesNode.class);

    private static final EnumSet<AMD64.CPUFeature> MINIMUM_FEATURES_AMD64 = EnumSet.of(
                    SSE,
                    SSE2,
                    SSE3,
                    SSSE3,
                    SSE4_1,
                    SSE4_2,
                    POPCNT);

    private final LIRGeneratorTool.CalcStringAttributesEncoding encoding;
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
                    @ConstantNodeParameter LIRGeneratorTool.CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid) {
        this(array, offset, length, encoding, assumeValid, null, LocationIdentity.any());
    }

    protected CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter LIRGeneratorTool.CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(array, offset, length, encoding, assumeValid, runtimeCheckedCPUFeatures, LocationIdentity.any());
    }

    public CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter LIRGeneratorTool.CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    LocationIdentity locationIdentity) {
        this(array, offset, length, encoding, assumeValid, null, locationIdentity);
    }

    public CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter LIRGeneratorTool.CalcStringAttributesEncoding encoding,
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

    private static JavaKind getReturnValueKind(LIRGeneratorTool.CalcStringAttributesEncoding encoding) {
        return encoding == LIRGeneratorTool.CalcStringAttributesEncoding.UTF_8 || encoding == LIRGeneratorTool.CalcStringAttributesEncoding.UTF_16 ? JavaKind.Long : JavaKind.Int;
    }

    public LIRGeneratorTool.CalcStringAttributesEncoding getOp() {
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
                    @ConstantNodeParameter LIRGeneratorTool.CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid);

    @NodeIntrinsic
    public static native int intReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter LIRGeneratorTool.CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "calcStringAttributesUTF8Valid", parameters = {"UTF_8", "true"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF8Unknown", parameters = {"UTF_8", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF16Valid", parameters = {"UTF_16", "true"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF16Unknown", parameters = {"UTF_16", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native long longReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter LIRGeneratorTool.CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid);

    @NodeIntrinsic
    public static native long longReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter LIRGeneratorTool.CalcStringAttributesEncoding encoding,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}

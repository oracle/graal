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
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.replacements.nodes.PureFunctionStubIntrinsicNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * This intrinsic calculates properties of string contents in various encodings, see
 * {@link AMD64CalcStringAttributesOp} for details.
 *
 * @see AMD64CalcStringAttributesOp
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public final class AMD64CalcStringAttributesNode extends PureFunctionStubIntrinsicNode {

    public static final NodeClass<AMD64CalcStringAttributesNode> TYPE = NodeClass.create(AMD64CalcStringAttributesNode.class);

    private static final EnumSet<AMD64.CPUFeature> MINIMUM_FEATURES_AMD64 = EnumSet.of(
                    SSE,
                    SSE2,
                    SSE3,
                    SSSE3,
                    SSE4_1,
                    SSE4_2,
                    POPCNT);

    private final AMD64CalcStringAttributesOp.Op op;
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
    protected AMD64CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid) {
        this(array, offset, length, op, assumeValid, null, LocationIdentity.any());
    }

    protected AMD64CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(array, offset, length, op, assumeValid, runtimeCheckedCPUFeatures, LocationIdentity.any());
    }

    public AMD64CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    LocationIdentity locationIdentity) {
        this(array, offset, length, op, assumeValid, null, locationIdentity);
    }

    public AMD64CalcStringAttributesNode(ValueNode array, ValueNode offset, ValueNode length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(getReturnValueKind(op)), runtimeCheckedCPUFeatures, locationIdentity);
        this.op = op;
        this.assumeValid = assumeValid;
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    private static JavaKind getReturnValueKind(AMD64CalcStringAttributesOp.Op op) {
        return op == AMD64CalcStringAttributesOp.Op.UTF_8 || op == AMD64CalcStringAttributesOp.Op.UTF_16 ? JavaKind.Long : JavaKind.Int;
    }

    public AMD64CalcStringAttributesOp.Op getOp() {
        return op;
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

    public static EnumSet<?> minFeaturesAARCH64() {
        throw GraalError.shouldNotReachHere("not implemented yet");
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return AMD64CalcStringAttributesForeignCalls.getStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{array, offset, length};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitCalcStringAttributes(op, getRuntimeCheckedCPUFeatures(), gen.operand(array), gen.operand(offset), gen.operand(length), assumeValid));
    }

    /* NodeIntrinsic plugins for snippet stubs. */

    @NodeIntrinsic
    @GenerateStub(name = "calcStringAttributesLatin1", parameters = {"LATIN1", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesBMP", parameters = {"BMP", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF32", parameters = {"UTF_32", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native int intReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid);

    @NodeIntrinsic
    public static native int intReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "calcStringAttributesUTF8Valid", parameters = {"UTF_8", "true"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF8Unknown", parameters = {"UTF_8", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF16Valid", parameters = {"UTF_16", "true"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "calcStringAttributesUTF16Unknown", parameters = {"UTF_16", "false"}, minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native long longReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid);

    @NodeIntrinsic
    public static native long longReturnValue(Object array, long offset, int length,
                    @ConstantNodeParameter AMD64CalcStringAttributesOp.Op op,
                    @ConstantNodeParameter boolean assumeValid,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}

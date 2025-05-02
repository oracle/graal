/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.CPUFeature.POPCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * This node converts a given codepoint index to a byte index on a <i>correctly encoded</i> UTF-8 or
 * UTF-16 string, see {@code AMD64CodepointIndexToByteIndexOp} for details.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public final class StringCodepointIndexToByteIndexNode extends PureFunctionStubIntrinsicNode {

    public static final NodeClass<StringCodepointIndexToByteIndexNode> TYPE = NodeClass.create(StringCodepointIndexToByteIndexNode.class);
    private static final EnumSet<AMD64.CPUFeature> MINIMUM_FEATURES_AMD64 = EnumSet.of(SSSE3, POPCNT);

    /**
     * Encoding of input string.
     */
    public enum InputEncoding {
        UTF_8,
        UTF_16
    }

    private final InputEncoding inputEncoding;

    @Input protected ValueNode array;
    @Input protected ValueNode offset;
    @Input protected ValueNode length;
    @Input protected ValueNode index;

    /**
     * This constructor is used by the {@code NodeIntrinsic} plugins below, which in turn are used
     * only in {@code StringCodepointIndexToByteIndexStub}, which is why we are using
     * {@link LocationIdentity#any()} here. The nodes calling the stubs are using more fine-grained
     * location identities, but are calling the same stubs (after
     * {@link #generate(NodeLIRBuilderTool) assembly generation}).
     */
    protected StringCodepointIndexToByteIndexNode(ValueNode array, ValueNode offset, ValueNode length, ValueNode index,
                    @ConstantNodeParameter InputEncoding inputEncoding) {
        this(array, offset, length, index, inputEncoding, null, LocationIdentity.any());
    }

    protected StringCodepointIndexToByteIndexNode(ValueNode array, ValueNode offset, ValueNode length, ValueNode index,
                    @ConstantNodeParameter InputEncoding inputEncoding,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(array, offset, length, index, inputEncoding, runtimeCheckedCPUFeatures, LocationIdentity.any());
    }

    public StringCodepointIndexToByteIndexNode(ValueNode array, ValueNode offset, ValueNode length, ValueNode index,
                    @ConstantNodeParameter InputEncoding inputEncoding,
                    LocationIdentity locationIdentity) {
        this(array, offset, length, index, inputEncoding, null, locationIdentity);
    }

    public StringCodepointIndexToByteIndexNode(ValueNode array, ValueNode offset, ValueNode length, ValueNode index,
                    @ConstantNodeParameter InputEncoding inputEncoding,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(JavaKind.Int), runtimeCheckedCPUFeatures, locationIdentity);
        this.inputEncoding = inputEncoding;
        this.array = array;
        this.offset = offset;
        this.length = length;
        this.index = index;
    }

    public InputEncoding getOp() {
        return inputEncoding;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return MINIMUM_FEATURES_AMD64;
    }

    public static EnumSet<?> minFeaturesAARCH64() {
        throw GraalError.shouldNotReachHere("not implemented yet"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return StringCodepointIndexToByteIndexForeignCalls.getStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{array, offset, length, index};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitCodepointIndexToByteIndex(inputEncoding, getRuntimeCheckedCPUFeatures(), gen.operand(array), gen.operand(offset), gen.operand(length),
                        gen.operand(index)));
    }

    /* NodeIntrinsic plugins for snippet stubs. */

    @NodeIntrinsic
    @GenerateStub(name = "codePointIndexToByteIndexUTF8", parameters = "UTF_8", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    @GenerateStub(name = "codePointIndexToByteIndexUTF16", parameters = "UTF_16", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native int codepointIndexToByteIndex(Object array, long offset, int length, int index,
                    @ConstantNodeParameter InputEncoding inputEncoding);

    @NodeIntrinsic
    public static native int codepointIndexToByteIndex(Object array, long offset, int length, int index,
                    @ConstantNodeParameter InputEncoding inputEncoding,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}

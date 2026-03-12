/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaKind;

/**
 * Stub-call node for implementations of libc's {@code strlen} function.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = SIZE_16)
public class IndexOfZeroNode extends PureFunctionStubIntrinsicNode {

    public static final NodeClass<IndexOfZeroNode> TYPE = NodeClass.create(IndexOfZeroNode.class);

    private static final EnumSet<AMD64.CPUFeature> MINIMUM_FEATURES_AMD64 = EnumSet.of(SSE, SSE2, SSE3, SSSE3, SSE4_1);

    private final Stride stride;

    @Input private ValueNode arrayPointer;

    public IndexOfZeroNode(@ConstantNodeParameter Stride stride, ValueNode arrayPointer) {
        this(stride, null, arrayPointer);
    }

    public IndexOfZeroNode(@ConstantNodeParameter Stride stride, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures, ValueNode arrayPointer) {
        super(TYPE, StampFactory.forKind(JavaKind.Long), runtimeCheckedCPUFeatures, NamedLocationIdentity.OFF_HEAP_LOCATION);
        this.stride = stride;
        this.arrayPointer = arrayPointer;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return MINIMUM_FEATURES_AMD64;
    }

    public Stride getStride() {
        return stride;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return IndexOfZeroForeignCalls.getStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{arrayPointer};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitIndexOfZero(stride, getRuntimeCheckedCPUFeatures(), gen.operand(arrayPointer)));
    }

    @NodeIntrinsic
    @GenerateStub(name = "indexOfZeroS1", parameters = "S1", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    @GenerateStub(name = "indexOfZeroS2", parameters = "S2", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    @GenerateStub(name = "indexOfZeroS4", parameters = "S4", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    public static native long optimizedArrayIndexOf(@ConstantNodeParameter Stride stride, long arrayPtr);

    @NodeIntrinsic
    public static native long optimizedArrayIndexOf(@ConstantNodeParameter Stride stride, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures, long arrayPtr);

}

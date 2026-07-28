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

import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512DQ;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512_VPCLMULQDQ;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLMUL;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

@NodeInfo(cycles = NodeCycles.CYCLES_64, size = NodeSize.SIZE_64)
public final class CRC32UpdateBytesNode extends PureFunctionStubIntrinsicNode {
    public static final NodeClass<CRC32UpdateBytesNode> TYPE = NodeClass.create(CRC32UpdateBytesNode.class);

    public static final ForeignCallDescriptor STUB = ForeignCalls.pureFunctionForeignCallDescriptor("updateBytesCRC32", int.class,
                    int.class, Pointer.class, int.class);

    @Input private ValueNode crc;
    @Input private ValueNode bufferAddress;
    @Input private ValueNode length;

    public CRC32UpdateBytesNode(ValueNode crc, ValueNode bufferAddress, ValueNode length) {
        this(crc, bufferAddress, length, null);
    }

    public CRC32UpdateBytesNode(ValueNode crc, ValueNode bufferAddress, ValueNode length, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.intValue(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.crc = crc;
        this.bufferAddress = bufferAddress;
        this.length = length;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(CLMUL);
    }

    public static EnumSet<AMD64.CPUFeature> maxFeaturesAMD64() {
        return EnumSet.of(CLMUL, AVX, AVX2, AVX512F, AVX512DQ, AVX512BW, AVX512VL, AVX512_VPCLMULQDQ);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{crc, bufferAddress, length};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        var result = gen.getLIRGeneratorTool().emitCRC32UpdateBytes(getRuntimeCheckedCPUFeatures(), gen.operand(crc), gen.operand(bufferAddress), gen.operand(length));
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    @GenerateStub(name = "updateBytesCRC32", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    public static native int update(int crc, Pointer bufferAddress, int length);

    @NodeIntrinsic
    public static native int update(int crc, Pointer bufferAddress, int length, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}

/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.CPUFeature.AES;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

/**
 * Encrypt or decrypt operation using the AES cipher. See {@code com.sun.crypto.provider.AESCrypt}.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_64, size = NodeSize.SIZE_64)
public class CounterModeAESNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<CounterModeAESNode> TYPE = NodeClass.create(CounterModeAESNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("ctrAESCrypt",
                    int.class,
                    new Class<?>[]{Pointer.class, Pointer.class, Pointer.class, Pointer.class, int.class, Pointer.class, Pointer.class},
                    HAS_SIDE_EFFECT,
                    KILLED_LOCATIONS,
                    false,
                    false);

    @Input protected NodeInputList<ValueNode> inputs;

    @SuppressWarnings("this-escape")
    public CounterModeAESNode(ValueNode inAddr,
                    ValueNode outAddr,
                    ValueNode kAddr,
                    ValueNode counterAddr,
                    ValueNode len,
                    ValueNode encryptedCounterAddr,
                    ValueNode usedPtr) {
        this(null,
                        len.stamp(NodeView.DEFAULT),
                        inAddr,
                        outAddr,
                        kAddr,
                        counterAddr,
                        len,
                        encryptedCounterAddr,
                        usedPtr);
    }

    @SuppressWarnings("this-escape")
    public CounterModeAESNode(ValueNode inAddr,
                    ValueNode outAddr,
                    ValueNode kAddr,
                    ValueNode counterAddr,
                    ValueNode len,
                    ValueNode encryptedCounterAddr,
                    ValueNode usedPtr,
                    EnumSet<?> runtimeCheckedCPUFeatures) {
        this(runtimeCheckedCPUFeatures,
                        len.stamp(NodeView.DEFAULT),
                        inAddr,
                        outAddr,
                        kAddr,
                        counterAddr,
                        len,
                        encryptedCounterAddr,
                        usedPtr);
    }

    @SuppressWarnings("this-escape")
    private CounterModeAESNode(EnumSet<?> runtimeCheckedCPUFeatures, Stamp stamp, ValueNode... args) {
        super(TYPE, stamp, runtimeCheckedCPUFeatures, LocationIdentity.any());

        this.inputs = new NodeInputList<>(this, args);
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(SSE2, SSE3, SSSE3, SSE4_1, AES);
    }

    public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
        return EnumSet.of(AArch64.CPUFeature.AES);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
            case AArch64 aarch64 -> aarch64.getFeatures().containsAll(minFeaturesAARCH64());
            default -> false;
        };
    }

    @NodeIntrinsic
    @GenerateStub(name = "ctrAESCrypt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native int apply(Pointer inAddr,
                    Pointer outAddr,
                    Pointer kAddr,
                    Pointer counterAddr,
                    int len,
                    Pointer encryptedCounterAddr,
                    Pointer usedPtr);

    @NodeIntrinsic
    public static native int apply(Pointer inAddr,
                    Pointer outAddr,
                    Pointer kAddr,
                    Pointer counterAddr,
                    int len,
                    Pointer encryptedCounterAddr,
                    Pointer usedPtr,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ValueNode[] getForeignCallArguments() {
        return inputs.toArray(ValueNode.EMPTY_ARRAY);
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        GraalError.guarantee(inputs.size() == 7, "inputs do not match");
        gen.setResult(this, gen.getLIRGeneratorTool().emitCTRAESCrypt(gen.operand(inputs.get(0)),
                        gen.operand(inputs.get(1)),
                        gen.operand(inputs.get(2)),
                        gen.operand(inputs.get(3)),
                        gen.operand(inputs.get(4)),
                        gen.operand(inputs.get(5)),
                        gen.operand(inputs.get(6))));
    }
}

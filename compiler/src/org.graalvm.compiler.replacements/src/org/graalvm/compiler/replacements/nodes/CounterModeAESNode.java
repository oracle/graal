/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
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
                    false,
                    KILLED_LOCATIONS,
                    false,
                    false);

    @Input protected NodeInputList<ValueNode> inputs;

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

    private CounterModeAESNode(EnumSet<?> runtimeCheckedCPUFeatures, Stamp stamp, ValueNode... args) {
        super(TYPE, stamp, runtimeCheckedCPUFeatures, LocationIdentity.any());

        this.inputs = new NodeInputList<>(this, args);
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return AESNode.minFeaturesAMD64();
    }

    public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
        return AESNode.minFeaturesAARCH64();
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        if (arch instanceof AMD64) {
            return ((AMD64) arch).getFeatures().containsAll(minFeaturesAMD64());
        } else if (arch instanceof AArch64) {
            return ((AArch64) arch).getFeatures().containsAll(minFeaturesAARCH64());
        }
        return false;
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

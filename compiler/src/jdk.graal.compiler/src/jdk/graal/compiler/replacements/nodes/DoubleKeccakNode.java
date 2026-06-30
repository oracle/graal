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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1024;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1024;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for {@code sun.security.provider.SHA3Parallel.doubleKeccak}.
 */
@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_1024, cyclesRationale = "Cannot estimate the time of 24 Keccak rounds", size = SIZE_1024)
public final class DoubleKeccakNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<DoubleKeccakNode> TYPE = NodeClass.create(DoubleKeccakNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Long)};
    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("doubleKeccak", int.class, new Class<?>[]{Pointer.class, Pointer.class},
                    HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);

    @Input private ValueNode state0;
    @Input private ValueNode state1;

    public DoubleKeccakNode(ValueNode state0, ValueNode state1) {
        this(state0, state1, null);
    }

    public DoubleKeccakNode(ValueNode state0, ValueNode state1, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.intValue(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.state0 = state0;
        this.state1 = state1;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512F, AMD64.CPUFeature.AVX512BW);
    }

    public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
        return EnumSet.of(AArch64.CPUFeature.SHA3);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
            case AArch64 aarch64 -> aarch64.getFeatures().containsAll(minFeaturesAARCH64());
            default -> false;
        };
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{state0, state1};
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitDoubleKeccak(gen.operand(state0), gen.operand(state1)));
    }

    @NodeIntrinsic
    @GenerateStub(name = "doubleKeccak", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native int doubleKeccak(Pointer state0, Pointer state1);

    @NodeIntrinsic
    public static native int doubleKeccak(Pointer state0, Pointer state1, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}

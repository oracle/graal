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
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512_IFMA;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX_IFMA;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_128, size = NodeSize.SIZE_128)
public class Poly1305ProcessBlocksNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<Poly1305ProcessBlocksNode> TYPE = NodeClass.create(Poly1305ProcessBlocksNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Long)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("poly1305ProcessBlocks",
                    void.class,
                    new Class<?>[]{Pointer.class, int.class, Pointer.class, Pointer.class},
                    HAS_SIDE_EFFECT,
                    KILLED_LOCATIONS,
                    false,
                    false);

    @Input protected ValueNode input;
    @Input protected ValueNode length;
    @Input protected ValueNode accumulator;
    @Input protected ValueNode r;

    public Poly1305ProcessBlocksNode(ValueNode input, ValueNode length, ValueNode accumulator, ValueNode r) {
        this(input, length, accumulator, r, null);
    }

    public Poly1305ProcessBlocksNode(ValueNode input, ValueNode length, ValueNode accumulator, ValueNode r, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.input = input;
        this.length = length;
        this.accumulator = accumulator;
        this.r = r;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{input, length, accumulator, r};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    public static EnumSet<AMD64.CPUFeature> maxFeaturesAMD64() {
        return EnumSet.of(AVX, AVX2, AVX_IFMA, AVX512_IFMA, AVX512VL, AVX512BW, AVX512F);
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(AVX, AVX2, AVX_IFMA);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }

    @NodeIntrinsic
    @GenerateStub(name = "poly1305ProcessBlocks", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    public static native void apply(Pointer input, int length, Pointer accumulator, Pointer r);

    @NodeIntrinsic
    public static native void apply(Pointer input,
                    int length,
                    Pointer accumulator,
                    Pointer r,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitPoly1305ProcessBlocks(runtimeCheckedCPUFeatures, gen.operand(input), gen.operand(length), gen.operand(accumulator), gen.operand(r));
    }
}

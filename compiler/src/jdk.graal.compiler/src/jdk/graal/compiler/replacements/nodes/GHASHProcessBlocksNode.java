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
package jdk.graal.compiler.replacements.nodes;

import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLMUL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.util.EnumSet;

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
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_128, size = NodeSize.SIZE_128)
public class GHASHProcessBlocksNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<GHASHProcessBlocksNode> TYPE = NodeClass.create(GHASHProcessBlocksNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Long)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("ghashProcessBlocks",
                    void.class,
                    new Class<?>[]{Pointer.class, Pointer.class, Pointer.class, int.class},
                    HAS_SIDE_EFFECT,
                    KILLED_LOCATIONS,
                    false,
                    false);

    @Input protected ValueNode state;
    @Input protected ValueNode hashSubkey;
    @Input protected ValueNode data;
    @Input protected ValueNode blocks;

    public GHASHProcessBlocksNode(ValueNode state, ValueNode hashSubkey, ValueNode data, ValueNode blocks) {
        this(state,
                        hashSubkey,
                        data,
                        blocks,
                        null);
    }

    public GHASHProcessBlocksNode(ValueNode state, ValueNode hashSubkey, ValueNode data, ValueNode blocks, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.state = state;
        this.hashSubkey = hashSubkey;
        this.data = data;
        this.blocks = blocks;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{state, hashSubkey, data, blocks};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(SSSE3, CLMUL);
    }

    public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
        return EnumSet.of(AArch64.CPUFeature.PMULL);
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
    @GenerateStub(name = "ghashProcessBlocks", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
    public static native void apply(Pointer state,
                    Pointer hashSubkey,
                    Pointer data,
                    int blocks);

    @NodeIntrinsic
    public static native void apply(Pointer state,
                    Pointer hashSubkey,
                    Pointer data,
                    int blocks,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public boolean canBeEmitted(Architecture arch) {
        return isSupported(arch);
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitGHASHProcessBlocks(gen.operand(state), gen.operand(hashSubkey), gen.operand(data), gen.operand(blocks));
    }
}

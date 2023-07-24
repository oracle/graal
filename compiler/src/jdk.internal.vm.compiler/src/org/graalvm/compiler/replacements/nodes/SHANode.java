/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_64;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for {@code sun.security.provider.SHA.implCompress0}.
 */
@NodeInfo(allowedUsageTypes = Memory)
public abstract class SHANode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<SHANode> TYPE = NodeClass.create(SHANode.class);

    private static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte), NamedLocationIdentity.getArrayLocation(JavaKind.Int)};

    private static ForeignCallDescriptor foreignCallDescriptor(String name) {
        return new ForeignCallDescriptor(name, void.class, new Class<?>[]{Pointer.class, Pointer.class}, false, KILLED_LOCATIONS, false, false);
    }

    @Input protected ValueNode buf;
    @Input protected ValueNode state;

    public SHANode(NodeClass<? extends SHANode> c, ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(c, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.buf = buf;
        this.state = state;
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{buf, state};
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public static final class SHA1Node extends SHANode {

        public static final NodeClass<SHA1Node> TYPE = NodeClass.create(SHA1Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("sha1ImplCompress");

        public SHA1Node(ValueNode buf, ValueNode state) {
            super(TYPE, buf, state, null);
        }

        public SHA1Node(ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, runtimeCheckedCPUFeatures);
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return EnumSet.of(AMD64.CPUFeature.SSSE3, AMD64.CPUFeature.SSE4_1, AMD64.CPUFeature.SHA);
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return EnumSet.of(AArch64.CPUFeature.SHA1);
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

        @Override
        public boolean canBeEmitted(Architecture arch) {
            return isSupported(arch);
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.getLIRGeneratorTool().emitSha1ImplCompress(gen.operand(buf), gen.operand(state));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha1ImplCompress", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native void sha1ImplCompress(Pointer buf, Pointer state);

        @NodeIntrinsic
        public static native void sha1ImplCompress(Pointer buf, Pointer state, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.SHA2.implCompress0}.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public static final class SHA256Node extends SHANode {

        public static final NodeClass<SHA256Node> TYPE = NodeClass.create(SHA256Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("sha256ImplCompress");

        public SHA256Node(ValueNode buf, ValueNode state) {
            super(TYPE, buf, state, null);
        }

        public SHA256Node(ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, runtimeCheckedCPUFeatures);
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return EnumSet.of(AMD64.CPUFeature.SSSE3, AMD64.CPUFeature.SSE4_1, AMD64.CPUFeature.SHA);
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return EnumSet.of(AArch64.CPUFeature.SHA2);
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

        @Override
        public boolean canBeEmitted(Architecture arch) {
            return isSupported(arch);
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.getLIRGeneratorTool().emitSha256ImplCompress(gen.operand(buf), gen.operand(state));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha256ImplCompress", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native void sha256ImplCompress(Pointer buf, Pointer state);

        @NodeIntrinsic
        public static native void sha256ImplCompress(Pointer buf, Pointer state, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }
}

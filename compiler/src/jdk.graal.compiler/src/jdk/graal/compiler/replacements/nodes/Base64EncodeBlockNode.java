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
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

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

@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, size = SIZE_64)
public final class Base64EncodeBlockNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<Base64EncodeBlockNode> TYPE = NodeClass.create(Base64EncodeBlockNode.class);
    private static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("base64EncodeBlock", void.class,
                    new Class<?>[]{Pointer.class, int.class, int.class, Pointer.class, int.class, int.class},
                    HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);

    @Input private ValueNode src;
    @Input private ValueNode sp;
    @Input private ValueNode sl;
    @Input private ValueNode dst;
    @Input private ValueNode dp;
    @Input private ValueNode isURL;

    public Base64EncodeBlockNode(ValueNode src, ValueNode sp, ValueNode sl, ValueNode dst, ValueNode dp, ValueNode isURL) {
        this(src, sp, sl, dst, dp, isURL, null);
    }

    public Base64EncodeBlockNode(ValueNode src, ValueNode sp, ValueNode sl, ValueNode dst, ValueNode dp, ValueNode isURL, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
        this.src = src;
        this.sp = sp;
        this.sl = sl;
        this.dst = dst;
        this.dp = dp;
        this.isURL = isURL;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(SSE2, SSE3, SSSE3, SSE4_1, SSE4_2, AVX, AVX2);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
            case AArch64 aarch64 -> aarch64.getFeatures().contains(AArch64.CPUFeature.ASIMD);
            default -> false;
        };
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{src, sp, sl, dst, dp, isURL};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitBase64EncodeBlock(runtimeCheckedCPUFeatures, gen.operand(src), gen.operand(sp), gen.operand(sl), gen.operand(dst), gen.operand(dp), gen.operand(isURL));
    }

    @NodeIntrinsic
    @GenerateStub(name = "base64EncodeBlock", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    public static native void base64EncodeBlock(Pointer src, int sp, int sl, Pointer dst, int dp, int isURL);

    @NodeIntrinsic
    public static native void base64EncodeBlock(Pointer src, int sp, int sl, Pointer dst, int dp, int isURL, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

}

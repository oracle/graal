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

/**
 * Encrypt or decrypt operation using the AES cipher. See {@code com.sun.crypto.provider.AESCrypt}.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory}, nameTemplate = "AES#{p#cryptMode/s}", cycles = NodeCycles.CYCLES_64, size = NodeSize.SIZE_64)
public class AESNode extends MemoryKillStubIntrinsicNode {

    public enum CryptMode {
        ENCRYPT,
        DECRYPT;

        public boolean isEncrypt() {
            return this == ENCRYPT;
        }
    }

    public static final NodeClass<AESNode> TYPE = NodeClass.create(AESNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte)};

    public static final ForeignCallDescriptor STUB_ENCRYPT = new ForeignCallDescriptor("aesEncrypt",
                    void.class,
                    new Class<?>[]{Pointer.class, Pointer.class, Pointer.class},
                    HAS_SIDE_EFFECT,
                    KILLED_LOCATIONS,
                    false,
                    false);
    public static final ForeignCallDescriptor STUB_DECRYPT = new ForeignCallDescriptor("aesDecrypt",
                    void.class,
                    new Class<?>[]{Pointer.class, Pointer.class, Pointer.class},
                    HAS_SIDE_EFFECT,
                    KILLED_LOCATIONS,
                    false,
                    false);

    public static final ForeignCallDescriptor[] STUBS = {
                    STUB_ENCRYPT,
                    STUB_DECRYPT,
    };

    private final CryptMode cryptMode;

    @Input protected ValueNode from;
    @Input protected ValueNode to;
    @Input protected ValueNode key;

    public AESNode(ValueNode from,
                    ValueNode to,
                    ValueNode key,
                    CryptMode cryptMode) {
        this(from,
                        to,
                        key,
                        cryptMode,
                        null);
    }

    public AESNode(ValueNode from,
                    ValueNode to,
                    ValueNode key,
                    CryptMode cryptMode,
                    EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.from = from;
        this.to = to;
        this.key = key;
        this.cryptMode = cryptMode;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{from, to, key};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(SSE2, SSE3, SSSE3, AES);
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
    @GenerateStub(name = "aesEncrypt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64", parameters = {"ENCRYPT"})
    @GenerateStub(name = "aesDecrypt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64", parameters = {"DECRYPT"})
    public static native void apply(Pointer from,
                    Pointer to,
                    Pointer key,
                    @ConstantNodeParameter CryptMode cryptMode);

    @NodeIntrinsic
    public static native void apply(Pointer from,
                    Pointer to,
                    Pointer key,
                    @ConstantNodeParameter CryptMode cryptMode,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return cryptMode.isEncrypt() ? STUB_ENCRYPT : STUB_DECRYPT;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        if (cryptMode.isEncrypt()) {
            gen.getLIRGeneratorTool().emitAESEncrypt(gen.operand(from), gen.operand(to), gen.operand(key));
        } else {
            gen.getLIRGeneratorTool().emitAESDecrypt(gen.operand(from), gen.operand(to), gen.operand(key));
        }
    }
}

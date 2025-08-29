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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.replacements.nodes.AESNode.CryptMode;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

/**
 * Encrypt or decrypt operation using the CipherBlockChaining AES cipher. See
 * {@code com.sun.crypto.provider.CipherBlockChaining}.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory}, nameTemplate = "CBCAES#{p#cryptMode/s}", cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_64)
public class CipherBlockChainingAESNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<CipherBlockChainingAESNode> TYPE = NodeClass.create(CipherBlockChainingAESNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte)};

    public static final ForeignCallDescriptor STUB_ENCRYPT = new ForeignCallDescriptor("cbcAESEncrypt",
                    int.class,
                    new Class<?>[]{Pointer.class, Pointer.class, Pointer.class, Pointer.class, int.class},
                    HAS_SIDE_EFFECT,
                    KILLED_LOCATIONS,
                    false,
                    false);
    public static final ForeignCallDescriptor STUB_DECRYPT = new ForeignCallDescriptor("cbcAESDecrypt",
                    int.class,
                    new Class<?>[]{Pointer.class, Pointer.class, Pointer.class, Pointer.class, int.class},
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
    @Input protected ValueNode r;
    @Input protected ValueNode len;

    public CipherBlockChainingAESNode(ValueNode from,
                    ValueNode to,
                    ValueNode key,
                    ValueNode r,
                    ValueNode len,
                    CryptMode cryptMode) {
        this(from,
                        to,
                        key,
                        r,
                        len,
                        cryptMode,
                        null);
    }

    public CipherBlockChainingAESNode(ValueNode from,
                    ValueNode to,
                    ValueNode key,
                    ValueNode r,
                    ValueNode len,
                    CryptMode cryptMode,
                    EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, len.stamp(NodeView.DEFAULT), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.from = from;
        this.to = to;
        this.key = key;
        this.r = r;
        this.len = len;
        this.cryptMode = cryptMode;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{from, to, key, r, len};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return AESNode.minFeaturesAMD64();
    }

    public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
        return AESNode.minFeaturesAARCH64();
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
    @GenerateStub(name = "cbcAESEncrypt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64", parameters = {"ENCRYPT"})
    @GenerateStub(name = "cbcAESDecrypt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64", parameters = {"DECRYPT"})
    public static native int apply(Pointer from,
                    Pointer to,
                    Pointer key,
                    Pointer r,
                    int len,
                    @ConstantNodeParameter CryptMode cryptMode);

    @NodeIntrinsic
    public static native int apply(Pointer from,
                    Pointer to,
                    Pointer key,
                    Pointer r,
                    int len,
                    @ConstantNodeParameter CryptMode cryptMode,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return cryptMode.isEncrypt() ? STUB_ENCRYPT : STUB_DECRYPT;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        if (cryptMode.isEncrypt()) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitCBCAESEncrypt(gen.operand(from), gen.operand(to), gen.operand(key), gen.operand(r), gen.operand(len)));
        } else {
            gen.setResult(this, gen.getLIRGeneratorTool().emitCBCAESDecrypt(gen.operand(from), gen.operand(to), gen.operand(key), gen.operand(r), gen.operand(len)));
        }
    }
}

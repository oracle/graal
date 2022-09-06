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
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

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

    @NodeIntrinsic
    @GenerateStub(name = "aesEncrypt", parameters = {"ENCRYPT"})
    @GenerateStub(name = "aesDecrypt", parameters = {"DECRYPT"})
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
        return cryptMode.isEncrypt() ? CryptoForeignCalls.STUB_AES_ENCRYPT : CryptoForeignCalls.STUB_AES_DECRYPT;
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

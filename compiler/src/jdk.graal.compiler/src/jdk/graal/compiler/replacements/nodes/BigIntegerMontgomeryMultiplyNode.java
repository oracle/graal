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

@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_256)
public class BigIntegerMontgomeryMultiplyNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<BigIntegerMontgomeryMultiplyNode> TYPE = NodeClass.create(BigIntegerMontgomeryMultiplyNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Int)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("montgomeryMultiply",
                    void.class,
                    new Class<?>[]{Pointer.class, Pointer.class, Pointer.class, int.class, long.class, Pointer.class},
                    HAS_SIDE_EFFECT,
                    KILLED_LOCATIONS,
                    false,
                    false);

    @Input protected ValueNode a;
    @Input protected ValueNode b;
    @Input protected ValueNode n;
    @Input protected ValueNode len;
    @Input protected ValueNode inv;
    @Input protected ValueNode product;

    public BigIntegerMontgomeryMultiplyNode(ValueNode a, ValueNode b, ValueNode n, ValueNode len, ValueNode inv, ValueNode product) {
        this(a, b, n, len, inv, product, null);
    }

    public BigIntegerMontgomeryMultiplyNode(ValueNode a, ValueNode b, ValueNode n, ValueNode len, ValueNode inv, ValueNode product, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.a = a;
        this.b = b;
        this.n = n;
        this.len = len;
        this.inv = inv;
        this.product = product;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{a, b, n, len, inv, product};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> true;
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }

    @NodeIntrinsic
    @GenerateStub(name = "montgomeryMultiply")
    public static native void apply(Pointer a,
                    Pointer b,
                    Pointer n,
                    int len,
                    long inv,
                    Pointer product);

    @NodeIntrinsic
    public static native void apply(Pointer a,
                    Pointer b,
                    Pointer n,
                    int len,
                    long inv,
                    Pointer product,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitBigIntegerMontgomeryMultiply(gen.operand(a),
                        gen.operand(b),
                        gen.operand(n),
                        gen.operand(len),
                        gen.operand(inv),
                        gen.operand(product));
    }
}

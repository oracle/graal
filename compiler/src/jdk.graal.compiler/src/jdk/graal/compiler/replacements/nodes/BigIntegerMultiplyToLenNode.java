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

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_256)
public class BigIntegerMultiplyToLenNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<BigIntegerMultiplyToLenNode> TYPE = NodeClass.create(BigIntegerMultiplyToLenNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Int)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("multiplyToLen", void.class,
                    new Class<?>[]{Pointer.class, int.class, Pointer.class, int.class, Pointer.class, int.class}, HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);

    @Input protected ValueNode x;
    @Input protected ValueNode xlen;
    @Input protected ValueNode y;
    @Input protected ValueNode ylen;
    @Input protected ValueNode z;
    @Input protected ValueNode zlen;

    public BigIntegerMultiplyToLenNode(ValueNode x,
                    ValueNode xlen,
                    ValueNode y,
                    ValueNode ylen,
                    ValueNode z,
                    ValueNode zlen) {
        this(x,
                        xlen,
                        y,
                        ylen,
                        z,
                        zlen,
                        null);
    }

    public BigIntegerMultiplyToLenNode(ValueNode x,
                    ValueNode xlen,
                    ValueNode y,
                    ValueNode ylen,
                    ValueNode z,
                    ValueNode zlen,
                    EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.x = x;
        this.xlen = xlen;
        this.y = y;
        this.ylen = ylen;
        this.z = z;
        this.zlen = zlen;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{x, xlen, y, ylen, z, zlen};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @NodeIntrinsic
    @GenerateStub(name = "multiplyToLen")
    public static native void apply(Pointer x,
                    int xlen,
                    Pointer y,
                    int ylen,
                    Pointer z,
                    int zlen);

    @NodeIntrinsic
    public static native void apply(Pointer x,
                    int xlen,
                    Pointer y,
                    int ylen,
                    Pointer z,
                    int zlen,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitBigIntegerMultiplyToLen(gen.operand(x),
                        gen.operand(xlen),
                        gen.operand(y),
                        gen.operand(ylen),
                        gen.operand(z),
                        gen.operand(zlen));
    }
}

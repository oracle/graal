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
package jdk.compiler.graal.replacements.nodes;

import java.util.EnumSet;

import jdk.compiler.graal.core.common.spi.ForeignCallDescriptor;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.GenerateStub;
import jdk.compiler.graal.nodeinfo.InputType;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.NamedLocationIdentity;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_256)
public class BigIntegerSquareToLenNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<BigIntegerSquareToLenNode> TYPE = NodeClass.create(BigIntegerSquareToLenNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Int)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("squareToLen",
                    void.class,
                    new Class<?>[]{Pointer.class, int.class, Pointer.class, int.class},
                    false,
                    KILLED_LOCATIONS,
                    false,
                    false);

    @Input protected ValueNode x;
    @Input protected ValueNode len;
    @Input protected ValueNode z;
    @Input protected ValueNode zlen;

    public BigIntegerSquareToLenNode(ValueNode x,
                    ValueNode len,
                    ValueNode z,
                    ValueNode zlen) {
        this(x,
                        len,
                        z,
                        zlen,
                        null);
    }

    public BigIntegerSquareToLenNode(ValueNode x,
                    ValueNode len,
                    ValueNode z,
                    ValueNode zlen,
                    EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.x = x;
        this.len = len;
        this.z = z;
        this.zlen = zlen;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{x, len, z, zlen};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @NodeIntrinsic
    @GenerateStub(name = "squareToLen")
    public static native void apply(Pointer x,
                    int len,
                    Pointer z,
                    int zlen);

    @NodeIntrinsic
    public static native void apply(Pointer x,
                    int len,
                    Pointer z,
                    int zlen,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitBigIntegerSquareToLen(gen.operand(x),
                        gen.operand(len),
                        gen.operand(z),
                        gen.operand(zlen));
    }
}

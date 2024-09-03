/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.nodes.gc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.G1WriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public final class G1PreWriteBarrierNode extends ObjectWriteBarrierNode implements LIRLowerable {

    public static final NodeClass<G1PreWriteBarrierNode> TYPE = NodeClass.create(G1PreWriteBarrierNode.class);

    @OptionalInput(InputType.State) private FrameState stateBefore;
    private final boolean doLoad;

    public G1PreWriteBarrierNode(AddressNode address, ValueNode expectedObject, boolean doLoad) {
        super(TYPE, address, expectedObject, true);
        assert doLoad == (expectedObject == null) : Assertions.errorMessageContext("adr", address, "expectedO", expectedObject, "doLoad", doLoad);
        this.doLoad = doLoad;
    }

    public ValueNode getExpectedObject() {
        return getValue();
    }

    public boolean doLoad() {
        return doLoad;
    }

    @Override
    public Kind getKind() {
        return Kind.PRE_BARRIER;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        ValueNode expectedObject = getExpectedObject();
        if (expectedObject == null || !expectedObject.isJavaConstant() || !expectedObject.asJavaConstant().isNull()) {
            AllocatableValue operand = Value.ILLEGAL;
            boolean nonNull = false;
            LIRGeneratorTool lirGen = generator.getLIRGeneratorTool();
            if (expectedObject != null) {
                operand = lirGen.asAllocatable(generator.operand(expectedObject));
                nonNull = ((ObjectStamp) expectedObject.stamp(NodeView.DEFAULT)).nonNull();
                GraalError.guarantee(expectedObject.stamp(NodeView.DEFAULT) instanceof ObjectStamp, "expecting full size object");
            }
            G1WriteBarrierSetLIRGeneratorTool g1BarrierSet = (G1WriteBarrierSetLIRGeneratorTool) generator.getLIRGeneratorTool().getWriteBarrierSet();
            g1BarrierSet.emitPreWriteBarrier(lirGen, generator.operand(address), operand, nonNull);
        }
    }
}

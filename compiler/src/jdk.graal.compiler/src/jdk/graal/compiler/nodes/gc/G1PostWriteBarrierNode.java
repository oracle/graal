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
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.G1WriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public class G1PostWriteBarrierNode extends ObjectWriteBarrierNode implements LIRLowerable {

    public static final NodeClass<G1PostWriteBarrierNode> TYPE = NodeClass.create(G1PostWriteBarrierNode.class);

    @OptionalInput protected ValueNode object;
    protected final boolean alwaysNull;

    public G1PostWriteBarrierNode(AddressNode address, ValueNode value, ValueNode object, boolean alwaysNull) {
        this(TYPE, address, value, object, alwaysNull);
    }

    private G1PostWriteBarrierNode(NodeClass<? extends G1PostWriteBarrierNode> c, AddressNode address, ValueNode value, ValueNode object, boolean alwaysNull) {
        super(c, address, value, object == null);
        this.object = object;
        this.alwaysNull = alwaysNull;
    }

    public boolean alwaysNull() {
        return alwaysNull;
    }

    @Override
    public Kind getKind() {
        return Kind.POST_BARRIER;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (value.isJavaConstant() && value.asJavaConstant().isNull()) {
            // These can be folded earlier
            return;
        }

        AllocatableValue base;
        LIRGeneratorTool lirGen = generator.getLIRGeneratorTool();
        if (object != null) {
            // Imprecise card mark where the base of the object is marked instead of the card
            // containing address. Used for field stores.
            base = lirGen.asAllocatable(generator.operand(object));
        } else {
            // Precise card mark
            Value addr = generator.operand(address);
            base = lirGen.newVariable(addr.getValueKind());
            lirGen.emitMove(base, addr);
        }
        boolean nonNull = ((ObjectStamp) value.stamp(NodeView.DEFAULT)).nonNull();
        if (base.equals(generator.operand(value))) {
            // If the value being stored is the same as the base then there is nothing to do.
            return;
        }
        G1WriteBarrierSetLIRGeneratorTool g1BarrierSet = (G1WriteBarrierSetLIRGeneratorTool) generator.getLIRGeneratorTool().getWriteBarrierSet();
        g1BarrierSet.emitPostWriteBarrier(lirGen, base, lirGen.asAllocatable(generator.operand(value)), nonNull);
    }
}

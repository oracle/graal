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

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.G1WriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * The {@code G1ReferentFieldReadBarrier} is added when a read access is performed to the referent
 * field of a {@link java.lang.ref.Reference} object (through a {@code LoadFieldNode} or an
 * {@code UnsafeLoadNode}). The return value of the read is passed to the snippet implementing the
 * read barrier and consequently is added to the SATB queue if the concurrent marker is enabled.
 */
@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public final class G1ReferentFieldReadBarrierNode extends ObjectWriteBarrierNode implements LIRLowerable {
    public static final NodeClass<G1ReferentFieldReadBarrierNode> TYPE = NodeClass.create(G1ReferentFieldReadBarrierNode.class);

    public G1ReferentFieldReadBarrierNode(AddressNode address, ValueNode expectedObject) {
        super(TYPE, address, expectedObject, true);
    }

    public ValueNode getExpectedObject() {
        return getValue();
    }

    @Override
    public Kind getKind() {
        return Kind.PRE_BARRIER;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool lirGen = generator.getLIRGeneratorTool();
        G1WriteBarrierSetLIRGeneratorTool g1BarrierSet = (G1WriteBarrierSetLIRGeneratorTool) generator.getLIRGeneratorTool().getWriteBarrierSet();
        g1BarrierSet.emitPreWriteBarrier(lirGen, generator.operand(address), lirGen.asAllocatable(generator.operand(getExpectedObject())), false);
    }
}

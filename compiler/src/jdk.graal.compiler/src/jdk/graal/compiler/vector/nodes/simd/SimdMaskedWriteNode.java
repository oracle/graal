/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.AbstractWriteNode;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

/**
 * Represents a vector store with a given selection mask. The store should execute as if it only
 * accesses memory at the set elements in the mask (e.g. it is valid to access the null address with
 * an all-zero mask). The elements corresponding to the unset elements in the mask will not be
 * written to and remain unchanged.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public class SimdMaskedWriteNode extends AbstractWriteNode implements LIRLowerableAccess {

    public static final NodeClass<SimdMaskedWriteNode> TYPE = NodeClass.create(SimdMaskedWriteNode.class);

    private final LocationIdentity killedLocationIdentity;
    private final MemoryOrderMode memoryOrder;
    @Input protected ValueNode mask;

    public SimdMaskedWriteNode(AddressNode address, ValueNode mask, ValueNode value, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder) {
        super(TYPE, address, location, value, barrierType);
        GraalError.guarantee(((SimdStamp) value.stamp(NodeView.DEFAULT)).getVectorLength() == ((SimdStamp) mask.stamp(NodeView.DEFAULT)).getVectorLength(), "%s - %s", stamp,
                        mask.stamp(NodeView.DEFAULT));
        this.killedLocationIdentity = location;
        this.memoryOrder = memoryOrder;
        this.mask = mask;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        GraalError.guarantee(getBarrierType() == BarrierType.NONE, "Barriers not supported yet");
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        LIRKind writeKind = tool.getLIRKind(getAccessStamp(NodeView.DEFAULT));
        tool.getArithmetic().emitMaskedStore(writeKind, gen.operand(getAddress()), gen.operand(mask), gen.operand(value()), gen.state(this), memoryOrder);
    }

    @Override
    public Stamp getAccessStamp(NodeView view) {
        return value().stamp(view);
    }

    @Override
    public boolean canNullCheck() {
        return false; // Cannot because it will spuriously succeed with a zero mask
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return killedLocationIdentity;
    }
}

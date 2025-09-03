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
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

/**
 * Represents a vector load with a given selection mask. The load should execute as if it only
 * accesses memory at the set elements in the mask (e.g. it is valid to access the null address with
 * an all-zero mask). The elements corresponding to the unset elements in the mask will be set to
 * zeroes.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public class SimdMaskedReadNode extends FixedAccessNode implements LIRLowerableAccess, Canonicalizable {

    public static final NodeClass<SimdMaskedReadNode> TYPE = NodeClass.create(SimdMaskedReadNode.class);

    private final Stamp accessStamp;
    private final MemoryOrderMode memoryOrder;
    @Input protected ValueNode mask;

    public SimdMaskedReadNode(ValueNode mask, AddressNode address, LocationIdentity location, Stamp stamp, BarrierType barrierType, MemoryOrderMode memoryOrder) {
        super(TYPE, address, location, stamp, barrierType);
        GraalError.guarantee(((SimdStamp) stamp).getVectorLength() == ((SimdStamp) mask.stamp(NodeView.DEFAULT)).getVectorLength(), "%s - %s", stamp, mask.stamp(NodeView.DEFAULT));
        this.mask = mask;
        this.accessStamp = stamp;
        this.memoryOrder = memoryOrder;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        GraalError.guarantee(getBarrierType() == BarrierType.NONE, "Barriers not supported yet");
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(getAccessStamp(NodeView.DEFAULT));
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitMaskedLoad(readKind, gen.operand(getAddress()), gen.operand(mask), gen.state(this), memoryOrder));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        // GR-59491, implement canonicalization for this node
        return this;
    }

    @Override
    public boolean canNullCheck() {
        return false; // Cannot because it will spuriously succeed with a zero mask
    }

    @Override
    public Stamp getAccessStamp(NodeView view) {
        return accessStamp;
    }
}

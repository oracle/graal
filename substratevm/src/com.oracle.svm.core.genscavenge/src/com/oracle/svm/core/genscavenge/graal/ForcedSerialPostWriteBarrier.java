/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_4;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.gc.SerialWriteBarrier;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode.Address;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;

/**
 * Post-write barrier that is injected manually and not via {@link BarrierSet}. This needs to be a
 * separate node because barrier nodes may be introduced only at a certain stage of compilation.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_4)
public final class ForcedSerialPostWriteBarrier extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<ForcedSerialPostWriteBarrier> TYPE = NodeClass.create(ForcedSerialPostWriteBarrier.class);

    @Input(InputType.Association) AddressNode address;

    private final boolean precise;

    public ForcedSerialPostWriteBarrier(ValueNode address, boolean precise) {
        super(TYPE, StampFactory.forVoid());
        this.address = (AddressNode) address;
        this.precise = precise;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            SerialWriteBarrier barrier = graph().add(new SerialWriteBarrier(address, precise));
            graph().replaceFixedWithFixed(this, barrier);
            tool.getLowerer().lower(barrier, tool);
        }
    }

    @NodeIntrinsic
    public static native void force(Address obj, @ConstantNodeParameter boolean precise);
}

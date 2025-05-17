/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.gc.shenandoah;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ShenandoahBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.gc.ObjectWriteBarrierNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_4;

@NodeInfo(cycles = CYCLES_8, size = SIZE_4)
public class ShenandoahCardBarrierNode extends ObjectWriteBarrierNode implements LIRLowerable {
    public static final NodeClass<ShenandoahCardBarrierNode> TYPE = NodeClass.create(ShenandoahCardBarrierNode.class);

    public ShenandoahCardBarrierNode(AddressNode address) {
        super(TYPE, address, null, false);
    }

    @Override
    public Kind getKind() {
        return Kind.POST_BARRIER;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        ShenandoahBarrierSetLIRGeneratorTool tool = (ShenandoahBarrierSetLIRGeneratorTool) gen.getLIRGeneratorTool().getBarrierSet();
        tool.emitCardBarrier(gen.getLIRGeneratorTool(), gen.operand(getAddress()));
    }
}

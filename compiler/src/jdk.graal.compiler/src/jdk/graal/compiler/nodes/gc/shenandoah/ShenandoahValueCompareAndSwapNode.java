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

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.ShenandoahBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Value;
import org.graalvm.word.LocationIdentity;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_16;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

/**
 * Replaces ValueCompareAndSwapNode for Shenandoah on reference-CAS.
 * Shenandoah requires some special treatment of reference-CAS to avoid
 * false negatives because from- and to-space references may not match,
 * even though they point to the same object.
 */
@NodeInfo(cycles = CYCLES_16, size = SIZE_64)
public class ShenandoahValueCompareAndSwapNode extends ValueCompareAndSwapNode {
    public static final NodeClass<ShenandoahValueCompareAndSwapNode> TYPE = NodeClass.create(ShenandoahValueCompareAndSwapNode.class);

    public ShenandoahValueCompareAndSwapNode(AddressNode address, ValueNode expectedValue, ValueNode newValue, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder) {
        super(TYPE, address, location, expectedValue, newValue, barrierType, expectedValue.stamp(NodeView.DEFAULT).meet(newValue.stamp(NodeView.DEFAULT)).unrestricted(), memoryOrder);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert getNewValue().stamp(NodeView.DEFAULT).isCompatible(getExpectedValue().stamp(NodeView.DEFAULT));
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        ShenandoahBarrierSetLIRGeneratorTool shenandoahTool = (ShenandoahBarrierSetLIRGeneratorTool) gen.getLIRGeneratorTool().getBarrierSet();
        assert !this.canDeoptimize();
        Value result = shenandoahTool.emitValueCompareAndSwap(tool, tool.getLIRKind(getAccessStamp(NodeView.DEFAULT)),
                gen.operand(getAddress()), gen.operand(getExpectedValue()), gen.operand(getNewValue()), memoryOrder);
        gen.setResult(this, result);
    }
}
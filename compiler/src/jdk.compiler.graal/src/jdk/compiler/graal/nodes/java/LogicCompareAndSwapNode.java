/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.nodes.java;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_8;

import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.core.common.memory.BarrierType;
import jdk.compiler.graal.core.common.memory.MemoryOrderMode;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.gen.LIRGeneratorTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.memory.address.AddressNode;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Represents the low-level version of an atomic compare-and-swap operation.
 *
 * This version returns a boolean indicating is the CAS was successful or not.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_8)
public final class LogicCompareAndSwapNode extends AbstractCompareAndSwapNode {
    public static final NodeClass<LogicCompareAndSwapNode> TYPE = NodeClass.create(LogicCompareAndSwapNode.class);

    public LogicCompareAndSwapNode(AddressNode address, ValueNode expectedValue, ValueNode newValue, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder) {
        super(TYPE, address, location, expectedValue, newValue, barrierType, StampFactory.forInteger(JavaKind.Int, 0, 1), memoryOrder);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert getNewValue().stamp(NodeView.DEFAULT).isCompatible(getExpectedValue().stamp(NodeView.DEFAULT));
        assert !this.canDeoptimize();
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        LIRKind resultKind = tool.getLIRKind(stamp(NodeView.DEFAULT));
        Value trueResult = tool.emitConstant(resultKind, JavaConstant.TRUE);
        Value falseResult = tool.emitConstant(resultKind, JavaConstant.FALSE);
        Value result = tool.emitLogicCompareAndSwap(tool.getLIRKind(getAccessStamp(NodeView.DEFAULT)), gen.operand(getAddress()), gen.operand(getExpectedValue()), gen.operand(getNewValue()),
                        trueResult, falseResult, memoryOrder, getBarrierType());

        gen.setResult(this, result);
    }
}

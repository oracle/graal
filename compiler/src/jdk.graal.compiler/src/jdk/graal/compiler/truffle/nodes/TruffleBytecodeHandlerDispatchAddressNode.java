/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.nodes;

import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_4;

import java.util.function.Supplier;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Represents a node that computes the dispatch address according to the input {@code opcode}.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_4, size = SIZE_4)
public final class TruffleBytecodeHandlerDispatchAddressNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<TruffleBytecodeHandlerDispatchAddressNode> TYPE = NodeClass.create(TruffleBytecodeHandlerDispatchAddressNode.class);

    @Input ValueNode opcode;

    private final Supplier<Object> bytecodeHandlerTableSupplier;

    public TruffleBytecodeHandlerDispatchAddressNode(ValueNode opcode, Supplier<Object> bytecodeHandlerTableSupplier) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.opcode = opcode;
        this.bytecodeHandlerTableSupplier = bytecodeHandlerTableSupplier;
    }

    @Override
    public void lower(LoweringTool tool) {
        // Treat bytecodeHandlerTable as a long[] and return bytecodeHandlerTable[opcode]
        StructuredGraph graph = graph();
        JavaConstant bytecodeHandlerTable = tool.getSnippetReflection().forObject(bytecodeHandlerTableSupplier.get());
        ConstantNode base = ConstantNode.forConstant(bytecodeHandlerTable, tool.getMetaAccess(), graph);
        ConstantNode baseOffset = ConstantNode.forLong(tool.getMetaAccess().getArrayBaseOffset(JavaKind.Long), graph);
        ConstantNode indexShift = ConstantNode.forInt(CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(JavaKind.Long)), graph);
        ValueNode offset = graph.addOrUnique(LeftShiftNode.create(opcode, indexShift, NodeView.DEFAULT));
        ValueNode extendedOffset = graph.addOrUnique(ZeroExtendNode.create(offset, 64, NodeView.DEFAULT));
        ValueNode offsetWithArrayBase = graph.addOrUnique(AddNode.create(extendedOffset, baseOffset, NodeView.DEFAULT));
        OffsetAddressNode address = graph.addOrUnique(new OffsetAddressNode(base, offsetWithArrayBase));
        ValueNode read = FloatingReadNode.createRead(graph, address, NamedLocationIdentity.FINAL_LOCATION,
                        StampFactory.forKind(JavaKind.Long), null, BarrierType.NONE, this);

        replaceAtUsages(read);
        GraphUtil.unlinkFixedNode(this);
        safeDelete();
    }
}

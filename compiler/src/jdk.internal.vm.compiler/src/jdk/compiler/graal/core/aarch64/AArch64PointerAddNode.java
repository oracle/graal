/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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
package jdk.compiler.graal.core.aarch64;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.core.common.type.AbstractPointerStamp;
import jdk.compiler.graal.core.common.type.IntegerStamp;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.aarch64.AArch64ArithmeticOp;
import jdk.compiler.graal.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.compiler.graal.lir.gen.LIRGeneratorTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.FloatingNode;
import jdk.compiler.graal.nodes.spi.ArithmeticLIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(nameTemplate = "AArch64PointerAdd", cycles = CYCLES_1, size = SIZE_1)
public class AArch64PointerAddNode extends FloatingNode implements ArithmeticLIRLowerable {

    public static final NodeClass<AArch64PointerAddNode> TYPE = NodeClass.create(AArch64PointerAddNode.class);

    @Input ValueNode base;
    @Input ValueNode offset;

    public AArch64PointerAddNode(ValueNode base, ValueNode offset) {
        super(TYPE, StampFactory.pointer());
        this.base = base;
        this.offset = offset;
        assert base != null && (base.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp ||
                        IntegerStamp.getBits(base.stamp(NodeView.DEFAULT)) == 64);
        assert offset != null && offset.getStackKind().isNumericInteger();
    }

    public ValueNode getBase() {
        return base;
    }

    public ValueNode getOffset() {
        return offset;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        LIRGeneratorTool tool = builder.getLIRGeneratorTool();
        Value x = builder.operand(base);
        Value y = builder.operand(offset);
        AllocatableValue baseValue = tool.asAllocatable(x);
        AllocatableValue baseReference = LIRKind.derivedBaseFromValue(baseValue);
        LIRKind kind = LIRKind.combineDerived(tool.getLIRKind(stamp(NodeView.DEFAULT)), baseReference, null);
        builder.setResult(this, ((AArch64ArithmeticLIRGenerator) gen).emitBinary(kind, AArch64ArithmeticOp.ADD, true, x, y));
    }
}

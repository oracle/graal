/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_16;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp.Sqrt;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Square root.
 */
@NodeInfo(cycles = CYCLES_16, size = SIZE_1)
public final class SqrtNode extends UnaryArithmeticNode<Sqrt> implements ArithmeticLIRLowerable {

    public static final NodeClass<SqrtNode> TYPE = NodeClass.create(SqrtNode.class);

    protected SqrtNode(ValueNode x) {
        super(TYPE, BinaryArithmeticNode.getArithmeticOpTable(x).getSqrt(), x);
    }

    public static ValueNode create(ValueNode x, NodeView view) {
        if (x.isConstant()) {
            ArithmeticOpTable.UnaryOp<Sqrt> op = ArithmeticOpTable.forStamp(x.stamp(view)).getSqrt();
            return ConstantNode.forPrimitive(op.foldStamp(x.stamp(view)), op.foldConstant(x.asConstant()));
        }
        return new SqrtNode(x);
    }

    @Override
    protected UnaryOp<Sqrt> getOp(ArithmeticOpTable table) {
        return table.getSqrt();
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitMathSqrt(nodeValueMap.operand(getValue())));
    }
}

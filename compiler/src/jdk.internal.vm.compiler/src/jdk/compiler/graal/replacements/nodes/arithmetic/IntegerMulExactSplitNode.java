/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.replacements.nodes.arithmetic;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_4;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_4;

import jdk.compiler.graal.core.common.type.IntegerStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.AbstractBeginNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.MulNode;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.nodes.spi.SimplifierTool;

import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_4, cyclesRationale = "mul+cmp", size = SIZE_4)
public final class IntegerMulExactSplitNode extends BinaryIntegerExactArithmeticSplitNode {
    public static final NodeClass<IntegerMulExactSplitNode> TYPE = NodeClass.create(IntegerMulExactSplitNode.class);

    public IntegerMulExactSplitNode(Stamp stamp, ValueNode x, ValueNode y, AbstractBeginNode next, AbstractBeginNode overflowSuccessor) {
        super(TYPE, stamp, x, y, next, overflowSuccessor);
    }

    @Override
    protected Value generateArithmetic(NodeLIRBuilderTool gen) {
        return gen.getLIRGeneratorTool().getArithmetic().emitMul(gen.operand(getX()), gen.operand(getY()), true);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        NodeView view = NodeView.from(tool);
        if (!IntegerStamp.multiplicationCanOverflow((IntegerStamp) x.stamp(view), (IntegerStamp) y.stamp(view))) {
            tool.deleteBranch(overflowSuccessor);
            tool.addToWorkList(next);
            MulNode replacement = graph().unique(new MulNode(x, y));
            graph().replaceSplitWithFloating(this, replacement, next);
            tool.addToWorkList(replacement);
        }
    }
}

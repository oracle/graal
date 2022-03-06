/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes.arithmetic;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.SimplifierTool;

import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_2, cyclesRationale = "neg+cmp", size = SIZE_2)
public final class IntegerNegExactSplitNode extends IntegerExactArithmeticSplitNode {
    public static final NodeClass<IntegerNegExactSplitNode> TYPE = NodeClass.create(IntegerNegExactSplitNode.class);

    @Input protected ValueNode value;

    public IntegerNegExactSplitNode(Stamp stamp, ValueNode value, AbstractBeginNode next, AbstractBeginNode overflowSuccessor) {
        super(TYPE, stamp, next, overflowSuccessor);
        this.value = value;
    }

    @Override
    protected Value generateArithmetic(NodeLIRBuilderTool gen) {
        return gen.getLIRGeneratorTool().getArithmetic().emitNegate(gen.operand(value), true);
    }

    public ValueNode getValue() {
        return value;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        NodeView view = NodeView.from(tool);
        if (!IntegerStamp.negateCanOverflow((IntegerStamp) value.stamp(view))) {
            tool.deleteBranch(overflowSuccessor);
            tool.addToWorkList(next);
            NegateNode replacement = graph().unique(new NegateNode(value));
            graph().replaceSplitWithFloating(this, replacement, next);
            tool.addToWorkList(replacement);
        }
    }
}

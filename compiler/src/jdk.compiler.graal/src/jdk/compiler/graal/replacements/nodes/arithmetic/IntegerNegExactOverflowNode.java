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

package jdk.compiler.graal.replacements.nodes.arithmetic;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_4;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_2;

import java.util.List;

import jdk.compiler.graal.core.common.type.IntegerStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.AbstractBeginNode;
import jdk.compiler.graal.nodes.IfNode;
import jdk.compiler.graal.nodes.LogicConstantNode;
import jdk.compiler.graal.nodes.LogicNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.Simplifiable;
import jdk.compiler.graal.nodes.spi.SimplifierTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_4, cyclesRationale = "neg+cmp", size = SIZE_2)
public class IntegerNegExactOverflowNode extends LogicNode implements Simplifiable, Canonicalizable {

    public static final NodeClass<IntegerNegExactOverflowNode> TYPE = NodeClass.create(IntegerNegExactOverflowNode.class);

    @Input protected ValueNode value;

    public IntegerNegExactOverflowNode(ValueNode value) {
        super(TYPE);
        this.value = value;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (value.isConstant()) {
            JavaConstant cst = value.asJavaConstant();
            try {
                if (cst.getJavaKind() == JavaKind.Int) {
                    Math.negateExact(cst.asInt());
                } else {
                    assert cst.getJavaKind() == JavaKind.Long;
                    Math.negateExact(cst.asLong());
                }
            } catch (ArithmeticException ex) {
                return LogicConstantNode.forBoolean(true);
            }
            return LogicConstantNode.forBoolean(false);
        }

        if (!IntegerStamp.negateCanOverflow((IntegerStamp) value.stamp(NodeView.DEFAULT))) {
            return LogicConstantNode.forBoolean(false);
        }
        return this;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        // Find all ifs that this node feeds into
        for (IfNode ifNode : usages().filter(IfNode.class).snapshot()) {
            // Replace the if with exact split
            AbstractBeginNode next = ifNode.falseSuccessor();
            AbstractBeginNode overflow = ifNode.trueSuccessor();
            ifNode.clearSuccessors();

            // Try to find corresponding exact nodes that could be combined with the split. They
            // would be directly linked to the BeginNode of the false branch.
            List<IntegerNegExactNode> coupledNodes = next.usages().filter(IntegerNegExactNode.class).filter(n -> value == ((IntegerNegExactNode) n).getValue()).snapshot();

            Stamp splitStamp = value.stamp(NodeView.DEFAULT).unrestricted();
            if (!coupledNodes.isEmpty()) {
                splitStamp = coupledNodes.iterator().next().stamp(NodeView.DEFAULT);
            }
            IntegerExactArithmeticSplitNode split = graph().add(new IntegerNegExactSplitNode(splitStamp, value, next, overflow));
            ifNode.replaceAndDelete(split);

            coupledNodes.forEach(n -> n.replaceAndDelete(split));
        }
    }
}

/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.replacements.nodes.arithmetic;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.List;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;

@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public abstract class IntegerExactOverflowNode extends LogicNode implements Canonicalizable.Binary<ValueNode>, Simplifiable {
    public static final NodeClass<IntegerExactOverflowNode> TYPE = NodeClass.create(IntegerExactOverflowNode.class);
    @Input protected ValueNode x;
    @Input protected ValueNode y;

    public IntegerExactOverflowNode(NodeClass<? extends IntegerExactOverflowNode> c, ValueNode x, ValueNode y) {
        super(c);
        this.x = x;
        this.y = y;
    }

    @Override
    public ValueNode getX() {
        return x;
    }

    @Override
    public ValueNode getY() {
        return y;
    }

    @Override
    protected boolean verifyNode() {
        for (Node usage : usages()) {
            assertTrue(usage instanceof GuardNode || usage instanceof FixedGuardNode || usage instanceof IfNode, "exact overflow node must only be used by guards or ifs, found: %s", usage);
        }
        return super.verifyNode();
    }

    /**
     * Make sure the overflow detection nodes have the same order of inputs as the exact arithmetic
     * nodes.
     *
     * @return the original node or another node with the same inputs, ignoring ordering.
     */
    @SuppressWarnings("deprecation")
    public LogicNode maybeCommuteInputs() {
        assert this instanceof BinaryCommutative : this;
        if (!y.isConstant() && (x.isConstant() || x.getId() > y.getId())) {
            ValueNode tmp = x;
            x = y;
            y = tmp;
            if (graph() != null) {
                // See if this node already exists
                LogicNode duplicate = graph().findDuplicate(this);
                if (duplicate != null) {
                    return duplicate;
                }
            }
        }
        return this;
    }

    protected abstract IntegerExactArithmeticSplitNode createSplit(Stamp splitStamp, AbstractBeginNode next, AbstractBeginNode overflow);

    protected abstract Class<? extends BinaryNode> getCoupledType();

    @Override
    public void simplify(SimplifierTool tool) {
        // Find all ifs that this node feeds into
        for (IfNode ifNode : usages().filter(IfNode.class).snapshot()) {
            // Replace the if with exact split
            AbstractBeginNode next = ifNode.falseSuccessor();
            AbstractBeginNode overflow = ifNode.trueSuccessor();
            ifNode.clearSuccessors();

            // Try to find corresponding exact nodes that could be combined with the split. They
            // would be directly
            // linked to the BeginNode of the false branch.
            List<? extends BinaryNode> coupledNodes = next.usages().filter(getCoupledType()).filter(n -> {
                BinaryNode exact = (BinaryNode) n;
                return exact.getX() == getX() && exact.getY() == getY();
            }).snapshot();

            Stamp splitStamp = x.stamp(NodeView.DEFAULT).unrestricted();
            if (!coupledNodes.isEmpty()) {
                splitStamp = coupledNodes.iterator().next().stamp(NodeView.DEFAULT);
            }
            IntegerExactArithmeticSplitNode split = graph().add(createSplit(splitStamp, next, overflow));
            ifNode.replaceAndDelete(split);

            coupledNodes.forEach(n -> n.replaceAndDelete(split));
        }
        if (hasNoUsages()) {
            /*
             * We don't want GVN during canonicalization to find this node and give it usages again,
             * since the canonicalizer would not call this simplify method again.
             */
            safeDelete();
        }
    }
}

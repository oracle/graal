/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;

/**
 * A node representing a conditional computation that could be represented by a
 * {@link ConditionalNode}, but where it is not yet known whether this would be beneficial. This
 * node allows users to delay the decision and temporarily represent two equivalent versions of the
 * same computation: as an {@link IfNode} with empty bodies and a {@link ValuePhiNode}, and as an
 * equivalent {@link ConditionalNode}.
 *
 * Users can {@link #commit()} to this conditional, destroying the original {@link ValuePhiNode}; or
 * {@link #revert()} it, removing this node and reverting its uses to the original
 * {@link ValuePhiNode}.
 */
// @formatter:off
@NodeInfo(cycles = CYCLES_1,
          cyclesRationale = "same as IfNode and ConditionalNode",
          size = SIZE_2,
          sizeRationale = "same as IfNode and ConditionalNode")
// @formatter:on
public class VirtualConditionalNode extends FloatingNode {

    public static final NodeClass<VirtualConditionalNode> TYPE = NodeClass.create(VirtualConditionalNode.class);
    @Input(InputType.Condition) LogicNode condition;
    @OptionalInput(InputType.Value) ValueNode originalPhi;
    @Input(InputType.Value) ValueNode conditional;

    protected VirtualConditionalNode(LogicNode condition, ValueNode originalPhi, ValueNode conditional) {
        super(TYPE, originalPhi.stamp(NodeView.DEFAULT));
        this.condition = condition;
        this.originalPhi = originalPhi;
        this.conditional = conditional;
    }

    /**
     * Constructs an instance for the given phi and a matching conditional node created for it. Adds
     * everything to the graph.
     */
    public static VirtualConditionalNode forPhi(ValuePhiNode originalPhi, StructuredGraph graph) {
        IfNode originalIf = ifNode(originalPhi);
        EndNode trueEnd = (EndNode) originalIf.trueSuccessor().successors().first();
        EndNode falseEnd = (EndNode) originalIf.falseSuccessor().successors().first();
        ValueNode conditional = ConditionalNode.create(originalIf.condition(), originalPhi.valueAt(trueEnd), originalPhi.valueAt(falseEnd), NodeView.DEFAULT);
        return graph.addOrUniqueWithInputs(new VirtualConditionalNode(originalIf.condition(), originalPhi, conditional));
    }

    public LogicNode condition() {
        return condition;
    }

    public ValueNode originalPhi() {
        return originalPhi;
    }

    public ValueNode conditional() {
        return conditional;
    }

    /**
     * The {@link IfNode} with empty true and false bodies corresponding to this phi's merge, or
     * {@code null} if this phi does not belong to such an empty {@link IfNode}.
     */
    private static IfNode ifNode(ValuePhiNode phi) {
        if (phi.valueCount() != 2 || !(phi.merge() instanceof MergeNode)) {
            return null;
        }
        IfNode ifNode = null;
        for (EndNode end : phi.merge().forwardEnds()) {
            if (end.predecessor() instanceof BeginNode && end.predecessor().predecessor() instanceof IfNode) {
                IfNode i = (IfNode) end.predecessor().predecessor();
                assert ifNode == null || ifNode == i : Assertions.errorMessage(ifNode);
                ifNode = i;
            } else {
                return null;
            }
        }
        return ifNode;
    }

    /**
     * Replace this node with the conditional expression, deleting the original phi node. Returns
     * the conditional node.
     */
    public ValueNode commit() {
        if (originalPhi != null) {
            assert originalPhi.hasExactlyOneUsage() && originalPhi.usages().first() == this : "This usages=" + originalPhi.usages().snapshot() + " this=" + this;
            originalPhi.removeUsage(this);
        }
        this.replaceAtUsagesAndDelete(conditional);
        return conditional;
    }

    /**
     * Replace this node with the original phi node, deleting the conditional expression. Returns
     * the original phi node
     */
    public ValueNode revert() {
        conditional.removeUsage(this);
        assert originalPhi != null;
        this.replaceAtUsagesAndDelete(originalPhi);
        return originalPhi;
    }
}

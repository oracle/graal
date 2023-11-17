/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.InputType.Anchor;
import static jdk.graal.compiler.nodeinfo.InputType.Guard;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * This node can be used for two different kinds of anchoring for non-CFG (floating) nodes: It can
 * 1) keep one node above a certain point in the graph by specifying that node as the
 * {@link #anchored} node; or 2) it can keep nodes below a certain point in the graph by using this
 * node as an Anchor or Guard input.
 *
 * This node must not move in the CFG, because that would change the anchor point. So optimizations
 * like de-duplication are not allowed and this node implements {@link NodeWithIdentity}. However,
 * duplication is allowed, i.e., it is on purpose that this node does not implement
 * {@link ControlFlowAnchored}.
 */
@NodeInfo(allowedUsageTypes = {Anchor, Guard}, cycles = CYCLES_0, size = SIZE_0)
public final class ValueAnchorNode extends FixedWithNextNode implements LIRLowerable, Simplifiable, Virtualizable, AnchoringNode, GuardingNode, NodeWithIdentity {

    public static final NodeClass<ValueAnchorNode> TYPE = NodeClass.create(ValueAnchorNode.class);
    @OptionalInput(Guard) ValueNode anchored;

    public ValueAnchorNode(ValueNode value) {
        super(TYPE, StampFactory.forVoid());
        this.anchored = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Nothing to emit, since this node is used for structural purposes only.
    }

    public ValueNode getAnchoredNode() {
        return anchored;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        while (next() instanceof ValueAnchorNode) {
            ValueAnchorNode nextAnchor = (ValueAnchorNode) next();
            if (nextAnchor.anchored == anchored || nextAnchor.anchored == null) {
                // two anchors for the same anchored -> coalesce
                // nothing anchored on the next anchor -> coalesce
                nextAnchor.replaceAtUsages(this);
                GraphUtil.removeFixedWithUnusedInputs(nextAnchor);
                /*
                 * Combining two anchors can allow the combining of two PiNode that are now anchored
                 * at the same place.
                 */
                tool.addToWorkList(usages());
            } else {
                break;
            }
        }
        if (tool.allUsagesAvailable() && hasNoUsages() && next() instanceof FixedAccessNode) {
            FixedAccessNode currentNext = (FixedAccessNode) next();
            if (currentNext.getGuard() == anchored) {
                GraphUtil.removeFixedWithUnusedInputs(this);
                return;
            }
        }

        if (anchored != null && (anchored.isConstant() || anchored instanceof FixedNode)) {
            // anchoring fixed nodes and constants is useless
            removeAnchoredNode();
        }

        if (anchored == null && hasNoUsages()) {
            // anchor is not necessary any more => remove.
            GraphUtil.removeFixedWithUnusedInputs(this);
        }
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (anchored == null || anchored instanceof AbstractBeginNode) {
            tool.delete();
        } else {
            ValueNode alias = tool.getAlias(anchored);
            if (alias instanceof VirtualObjectNode) {
                tool.delete();
            }
        }
    }

    public void removeAnchoredNode() {
        this.updateUsages(anchored, null);
        this.anchored = null;
    }
}

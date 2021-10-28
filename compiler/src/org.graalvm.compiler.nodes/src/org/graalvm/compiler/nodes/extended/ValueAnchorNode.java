/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.Anchor;
import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.NodeWithIdentity;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchored;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

/**
 * This node can be used for two different kinds of anchoring for non-CFG (floating) nodes: It can
 * 1) keep one node above a certain point in the graph by specifying that node as the
 * {@link #anchored} node; or 2) it can keep nodes below a certain point in the graph by using this
 * node as a Anchor or Guard input.
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

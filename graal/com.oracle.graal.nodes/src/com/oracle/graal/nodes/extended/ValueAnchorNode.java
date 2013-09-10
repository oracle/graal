/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.extended;

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The ValueAnchor instruction keeps non-CFG (floating) nodes above a certain point in the graph.
 */
public final class ValueAnchorNode extends FixedWithNextNode implements Canonicalizable, LIRLowerable, Node.IterableNodeType, Virtualizable, GuardingNode {

    @Input private final NodeInputList<ValueNode> anchored;

    public ValueAnchorNode(ValueNode... values) {
        this(false, values);
    }

    public ValueAnchorNode(boolean permanent, ValueNode... values) {
        super(StampFactory.dependency());
        this.permanent = permanent;
        this.anchored = new NodeInputList<>(this, values);
    }

    private boolean permanent;

    @Override
    public void generate(LIRGeneratorTool gen) {
        // Nothing to emit, since this node is used for structural purposes only.
    }

    public void addAnchoredNode(ValueNode value) {
        if (!anchored.contains(value)) {
            this.anchored.add(value);
        }
    }

    public void removeAnchoredNode(ValueNode value) {
        this.anchored.remove(value);
    }

    public NodeInputList<ValueNode> getAnchoredNodes() {
        return anchored;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (permanent) {
            return this;
        }
        if (this.predecessor() instanceof ValueAnchorNode) {
            ValueAnchorNode previousAnchor = (ValueAnchorNode) this.predecessor();
            if (previousAnchor.usages().isEmpty()) { // avoid creating cycles
                // transfer values and remove
                for (ValueNode node : anchored.nonNull().distinct()) {
                    previousAnchor.addAnchoredNode(node);
                }
                return previousAnchor;
            }
        }
        for (Node node : anchored.nonNull().and(isNotA(FixedNode.class))) {
            if (!(node instanceof ConstantNode)) {
                return this; // still necessary
            }
        }
        if (usages().isEmpty()) {
            return null; // no node which require an anchor found
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (permanent) {
            return;
        }
        for (ValueNode node : anchored.nonNull().and(isNotA(AbstractBeginNode.class))) {
            State state = tool.getObjectState(node);
            if (state == null || state.getState() != EscapeState.Virtual) {
                return;
            }
        }
        tool.delete();
    }
}

/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(allowedUsageTypes = {InputType.Guard, InputType.Anchor})
public abstract class AbstractBeginNode extends FixedWithNextNode implements LIRLowerable, GuardingNode, AnchoringNode, IterableNodeType {

    public static final NodeClass<AbstractBeginNode> TYPE = NodeClass.create(AbstractBeginNode.class);

    private boolean hasSpeculationFence;

    protected AbstractBeginNode(NodeClass<? extends AbstractBeginNode> c) {
        this(c, StampFactory.forVoid());
    }

    protected AbstractBeginNode(NodeClass<? extends AbstractBeginNode> c, Stamp stamp) {
        super(c, stamp);
    }

    public static AbstractBeginNode prevBegin(FixedNode from) {
        Node next = from;
        while (next != null) {
            if (next instanceof AbstractBeginNode) {
                return (AbstractBeginNode) next;
            }
            next = next.predecessor();
        }
        return null;
    }

    private void evacuateAnchored(FixedNode evacuateFrom) {
        if (!hasNoUsages()) {
            AbstractBeginNode prevBegin = prevBegin(evacuateFrom);
            assert prevBegin != null;
            replaceAtUsages(prevBegin, InputType.Anchor);
            replaceAtUsages(prevBegin, InputType.Guard);
            assert anchored().isEmpty() : anchored().snapshot();
        }
    }

    public void prepareDelete() {
        prepareDelete((FixedNode) predecessor());
    }

    public void prepareDelete(FixedNode evacuateFrom) {
        evacuateAnchored(evacuateFrom);
    }

    @Override
    public boolean verify() {
        assertTrue(predecessor() != null || this == graph().start() || this instanceof AbstractMergeNode, "begin nodes must be connected");
        return super.verify();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (hasSpeculationFence) {
            gen.getLIRGeneratorTool().emitSpeculationFence();
        }
    }

    public boolean isUsedAsGuardInput() {
        if (this.hasUsages()) {
            for (Node n : usages()) {
                for (Position inputPosition : n.inputPositions()) {
                    if (inputPosition.getInputType() == InputType.Guard && inputPosition.get(n) == this) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public NodeIterable<GuardNode> guards() {
        return usages().filter(GuardNode.class);
    }

    public NodeIterable<Node> anchored() {
        return usages();
    }

    public boolean hasAnchored() {
        return this.hasUsages();
    }

    public NodeIterable<FixedNode> getBlockNodes() {
        return new NodeIterable<>() {

            @Override
            public Iterator<FixedNode> iterator() {
                return new BlockNodeIterator(AbstractBeginNode.this);
            }
        };
    }

    /**
     * Set this begin node to be a speculation fence. This will prevent speculative execution of
     * this block.
     */
    public void setHasSpeculationFence() {
        this.hasSpeculationFence = true;
    }

    public boolean hasSpeculationFence() {
        return hasSpeculationFence;
    }

    private static class BlockNodeIterator implements Iterator<FixedNode> {

        private FixedNode current;

        BlockNodeIterator(FixedNode next) {
            this.current = next;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public FixedNode next() {
            FixedNode ret = current;
            if (ret == null) {
                throw new NoSuchElementException();
            }
            if (current instanceof FixedWithNextNode) {
                current = ((FixedWithNextNode) current).next();
                if (current instanceof AbstractBeginNode) {
                    current = null;
                }
            } else {
                current = null;
            }
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}

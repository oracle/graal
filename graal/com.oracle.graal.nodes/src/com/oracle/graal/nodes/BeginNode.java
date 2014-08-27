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
package com.oracle.graal.nodes;

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import java.util.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo(allowedUsageTypes = {InputType.Guard, InputType.Anchor})
public class BeginNode extends FixedWithNextNode implements LIRLowerable, Simplifiable, GuardingNode, AnchoringNode, IterableNodeType {

    public static BeginNode create() {
        return USE_GENERATED_NODES ? new BeginNodeGen() : new BeginNode();
    }

    protected BeginNode() {
        super(StampFactory.forVoid());
    }

    public static BeginNode create(Stamp stamp) {
        return USE_GENERATED_NODES ? new BeginNodeGen(stamp) : new BeginNode(stamp);
    }

    protected BeginNode(Stamp stamp) {
        super(stamp);
    }

    public static BeginNode begin(FixedNode with) {
        if (with instanceof BeginNode) {
            return (BeginNode) with;
        }
        BeginNode begin = with.graph().add(BeginNode.create());
        begin.setNext(with);
        return begin;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        FixedNode prev = (FixedNode) this.predecessor();
        if (prev == null) {
            // This is the start node.
        } else if (prev instanceof ControlSplitNode) {
            // This begin node is necessary.
        } else {
            // This begin node can be removed and all guards moved up to the preceding begin node.
            prepareDelete();
            tool.addToWorkList(next());
            graph().removeFixed(this);
        }
    }

    public static BeginNode prevBegin(FixedNode from) {
        for (BeginNode begin : GraphUtil.predecessorIterable(from).filter(BeginNode.class)) {
            return begin;
        }
        return null;
    }

    private void evacuateGuards(FixedNode evacuateFrom) {
        if (!usages().isEmpty()) {
            BeginNode prevBegin = prevBegin(evacuateFrom);
            assert prevBegin != null;
            for (Node anchored : anchored().snapshot()) {
                anchored.replaceFirstInput(this, prevBegin);
            }
        }
    }

    public void prepareDelete() {
        prepareDelete((FixedNode) predecessor());
    }

    public void prepareDelete(FixedNode evacuateFrom) {
        removeProxies();
        evacuateGuards(evacuateFrom);
    }

    public void removeProxies() {
        for (ProxyNode vpn : proxies().snapshot()) {
            // can not use graph.replaceFloating because vpn.value may be null during killCFG
            vpn.replaceAtUsages(vpn.value());
            vpn.safeDelete();
        }
    }

    @Override
    public boolean verify() {
        assertTrue(predecessor() != null || this == graph().start() || this instanceof MergeNode, "begin nodes must be connected");
        return super.verify();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // nop
    }

    public NodeIterable<GuardNode> guards() {
        return usages().filter(GuardNode.class);
    }

    public NodeIterable<Node> anchored() {
        return usages().filter(isNotA(ProxyNode.class));
    }

    public NodeIterable<ProxyNode> proxies() {
        return usages().filter(ProxyNode.class);
    }

    public NodeIterable<FixedNode> getBlockNodes() {
        return new NodeIterable<FixedNode>() {

            @Override
            public Iterator<FixedNode> iterator() {
                return new BlockNodeIterator(BeginNode.this);
            }
        };
    }

    private class BlockNodeIterator implements Iterator<FixedNode> {

        private FixedNode current;

        public BlockNodeIterator(FixedNode next) {
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
            if (!(current instanceof FixedWithNextNode) || (current instanceof BeginNode && current != BeginNode.this)) {
                current = null;
            } else {
                current = ((FixedWithNextNode) current).next();
            }
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}

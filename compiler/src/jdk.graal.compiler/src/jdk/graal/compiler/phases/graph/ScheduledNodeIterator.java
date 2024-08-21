/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.graph;

import java.util.List;
import java.util.ListIterator;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * Iterates over a list of nodes, which usually comes from
 * {@link ScheduleResult#getBlockToNodesMap()}.
 *
 * While iterating, it is possible to {@link #insert(FixedNode, FixedWithNextNode) insert} and
 * {@link #replaceCurrent(FixedWithNextNode) replace} nodes.
 */
public abstract class ScheduledNodeIterator {

    private FixedWithNextNode lastFixed;
    private FixedWithNextNode reconnect;
    private ListIterator<Node> iterator;

    protected final ScheduleResult schedule;

    public ScheduledNodeIterator(ScheduleResult schedule) {
        this.schedule = schedule;
    }

    public void processNodes(HIRBlock block) {
        lastFixed = block.getBeginNode();
        assert lastFixed != null;
        reconnect = null;
        iterator = schedule.nodesFor(block).listIterator();

        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (!node.isAlive()) {
                continue;
            }
            if (reconnect != null && node instanceof FixedNode) {
                reconnect.setNext((FixedNode) node);
                reconnect = null;
            }
            if (node instanceof FixedWithNextNode) {
                lastFixed = (FixedWithNextNode) node;
            }
            processNode(node, block, iterator);
        }
        if (reconnect != null) {
            assert block.getSuccessorCount() == 1 : Assertions.errorMessage(block);
            reconnect.setNext(block.getFirstSuccessor().getBeginNode());
        }
    }

    protected void insert(FixedNode start, FixedWithNextNode end) {
        this.lastFixed.setNext(start);
        this.lastFixed = end;
        this.reconnect = end;
    }

    protected void replaceCurrent(FixedWithNextNode newNode) {
        Node current = iterator.previous();
        iterator.next(); // needed because of the previous() call

        FixedNode lastFixedNode = this.lastFixed;

        if (GraphUtil.isFloatingNode(current) && lastFixedNode instanceof AbstractBeginNode) {
            HIRBlock bCurrent = schedule.blockFor(current, true);
            HIRBlock bLastFixed = schedule.blockFor(lastFixedNode, true);
            if (bCurrent == bLastFixed) {
                List<Node> nodes = schedule.nodesFor(bLastFixed);
                if (nodes.indexOf(current) < nodes.indexOf(lastFixedNode)) {
                    // We are inserting BEFORE a begin node. It is only possible that the begin
                    // has a dependency (data dependency) on the inserted node. In order to
                    // de-mangle this we insert a new dominator block.
                    BeginNode b = this.lastFixed.graph().add(new BeginNode());
                    lastFixedNode.replaceAtPredecessor(b);
                    b.setNext(lastFixedNode);
                    this.lastFixed = b;
                    b.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, b.graph(), "After adding new begin %s to establish proper dominance if we lower before", b);
                }
            }
        }

        current.replaceAndDelete(newNode);
        insert(newNode, newNode);
        iterator.set(newNode);
    }

    protected abstract void processNode(Node node, HIRBlock block, ListIterator<Node> iter);
}

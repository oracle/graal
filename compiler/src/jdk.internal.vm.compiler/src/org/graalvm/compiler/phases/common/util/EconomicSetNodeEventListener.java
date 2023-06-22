/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.util;

import java.util.EnumSet;
import java.util.Set;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventListener;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.IndirectCanonicalization;
import org.graalvm.compiler.nodes.AbstractBeginNode;

/**
 * A simple {@link NodeEventListener} implementation that accumulates event nodes in a
 * {@link EconomicSet}.
 */
public class EconomicSetNodeEventListener extends NodeEventListener {

    private final EconomicSet<Node> nodes;
    private final Set<NodeEvent> filter;

    /**
     * Creates a {@link NodeEventListener} that collects nodes from all events.
     */
    public EconomicSetNodeEventListener() {
        this.nodes = EconomicSet.create(Equivalence.IDENTITY);
        this.filter = EnumSet.of(NodeEvent.INPUT_CHANGED, NodeEvent.NODE_ADDED, NodeEvent.ZERO_USAGES);
    }

    /**
     * Creates a {@link NodeEventListener} that collects nodes from all events that match a given
     * filter.
     */
    public EconomicSetNodeEventListener(Set<NodeEvent> filter) {
        this.nodes = EconomicSet.create(Equivalence.IDENTITY);
        this.filter = filter;
    }

    /**
     * Excludes a given event from those for which nodes are collected.
     */
    public EconomicSetNodeEventListener exclude(NodeEvent e) {
        filter.remove(e);
        return this;
    }

    @Override
    public void changed(NodeEvent e, Node node) {
        if (filter.contains(e)) {
            add(node);
            if (node instanceof IndirectCanonicalization) {
                for (Node usage : node.usages()) {
                    add(usage);
                }
            }

            if (node instanceof AbstractBeginNode) {
                AbstractBeginNode abstractBeginNode = (AbstractBeginNode) node;
                add(abstractBeginNode.predecessor());
            }
        }
    }

    private void add(Node n) {
        if (n != null) {
            nodes.add(n);
        }
    }

    /**
     * Gets the set being used to accumulate the nodes communicated to this listener.
     */
    public EconomicSet<Node> getNodes() {
        return nodes;
    }
}

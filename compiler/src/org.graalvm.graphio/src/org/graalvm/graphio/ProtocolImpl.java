/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.graphio;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.Map;

public abstract class ProtocolImpl<Graph, Node, NodeClass, Port, Block, ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition>
                extends GraphProtocol<Graph, Node, NodeClass, Port, Block, ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition> {
    protected GraphStructure<Graph, Node, NodeClass, Port> structure;
    protected GraphEnums<?> enums = DefaultGraphEnums.DEFAULT;

    protected ProtocolImpl(WritableByteChannel channel) throws IOException {
        super(channel);
    }

    @Override
    protected final Graph findGraph(Graph current, Object obj) {
        return structure.graph(current, obj);
    }

    @Override
    protected final NodeClass findNodeClass(Object obj) {
        return structure.nodeClass(obj);
    }

    @Override
    protected final String findNameTemplate(NodeClass clazz) {
        return structure.nameTemplate(clazz);
    }

    @Override
    protected final int findNodeId(Node n) {
        return structure.nodeId(n);
    }

    @Override
    protected final boolean hasPredecessor(Node node) {
        return structure.nodeHasPredecessor(node);
    }

    @Override
    protected final int findNodesCount(Graph info) {
        return structure.nodesCount(info);
    }

    @Override
    protected final Iterable<? extends Node> findNodes(Graph info) {
        return structure.nodes(info);
    }

    @Override
    protected final void findNodeProperties(Node node, Map<String, Object> props, Graph info) {
        structure.nodeProperties(info, node, props);
    }

    @Override
    protected final Port findClassEdges(NodeClass nodeClass, boolean dumpInputs) {
        if (dumpInputs) {
            return structure.portInputs(nodeClass);
        } else {
            return structure.portOutputs(nodeClass);
        }
    }

    @Override
    protected final int findSize(Port edges) {
        return structure.portSize(edges);
    }

    @Override
    protected final boolean isDirect(Port edges, int i) {
        return structure.edgeDirect(edges, i);
    }

    @Override
    protected final String findName(Port edges, int i) {
        return structure.edgeName(edges, i);
    }

    @Override
    protected final Object findType(Port edges, int i) {
        return structure.edgeType(edges, i);
    }

    @Override
    protected final Collection<? extends Node> findNodes(Graph graph, Node node, Port port, int i) {
        return structure.edgeNodes(graph, node, port, i);
    }

    @Override
    protected final Object findEnumClass(Object enumValue) {
        return enums.findEnumClass(enumValue);
    }

    @Override
    protected final int findEnumOrdinal(Object obj) {
        return enums.findEnumOrdinal(obj);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final String[] findEnumTypeValues(Object clazz) {
        return ((GraphEnums) enums).findEnumTypeValues(clazz);
    }
}

/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * This node will be inserted at point specified by {@link StructuredGraph#getEntryBCI()}, usually
 * by the graph builder.
 */
@NodeInfo(allowedUsageTypes = Association, cycles = CYCLES_0, size = SIZE_0)
public final class EntryMarkerNode extends BeginStateSplitNode implements IterableNodeType, LIRLowerable {
    public static final NodeClass<EntryMarkerNode> TYPE = NodeClass.create(EntryMarkerNode.class);

    public EntryMarkerNode() {
        super(TYPE);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        throw new GraalError("OnStackReplacementNode should not survive");
    }

    @Override
    public NodeIterable<Node> anchored() {
        return super.anchored().filter(n -> {
            if (n instanceof EntryProxyNode) {
                EntryProxyNode proxyNode = (EntryProxyNode) n;
                return proxyNode.proxyPoint != this;
            }
            return true;
        });
    }

    @Override
    public void prepareDelete(FixedNode evacuateFrom) {
        for (Node usage : usages().filter(EntryProxyNode.class).snapshot()) {
            EntryProxyNode proxy = (EntryProxyNode) usage;
            proxy.replaceAndDelete(proxy.value());
        }
        super.prepareDelete(evacuateFrom);
    }
}

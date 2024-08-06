/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.virtual;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.GraalGraphError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class EnsureVirtualizedNode extends FixedWithNextNode implements Virtualizable, Lowerable {

    public static final NodeClass<EnsureVirtualizedNode> TYPE = NodeClass.create(EnsureVirtualizedNode.class);

    @Input ValueNode object;
    private final boolean localOnly;

    public EnsureVirtualizedNode(ValueNode object, boolean localOnly) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
        this.localOnly = localOnly;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object);
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            if (virtual instanceof VirtualBoxingNode) {
                Throwable exception = new GraalGraphError("ensureVirtual is not valid for boxing objects: %s", virtual.type().getName());
                throw GraphUtil.approxSourceException(this, exception);
            }
            if (!localOnly) {
                tool.setEnsureVirtualized(virtual, true);
            }
            tool.delete();
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        ensureVirtualFailure(this, object.stamp(NodeView.DEFAULT));
    }

    public static void ensureVirtualFailure(Node location, Stamp stamp) {
        String additionalReason = "";
        if (location instanceof FixedWithNextNode && !(location instanceof EnsureVirtualizedNode)) {
            FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) location;
            FixedNode next = fixedWithNextNode.next();
            if (next instanceof StoreFieldNode) {
                additionalReason = " (must not store virtual object into a field)";
            } else if (next instanceof Invoke) {
                additionalReason = " (must not pass virtual object into an invoke that cannot be inlined)";
            } else {
                additionalReason = " (must not let virtual object escape at node " + next + ")";
            }
        }
        Throwable exception = new GraalGraphError("Object of type %s should not be materialized%s:", StampTool.typeOrNull(stamp).getName(), additionalReason);

        Node pos;
        if (location instanceof FixedWithNextNode) {
            pos = ((FixedWithNextNode) location).next();
        } else if (location instanceof AbstractEndNode) {
            pos = ((AbstractEndNode) location).merge();
        } else {
            pos = location;
        }
        throw GraphUtil.approxSourceException(pos, exception);
    }
}

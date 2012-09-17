/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.virtual;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(nameTemplate = "Materialize {i#virtualObject}")
public final class MaterializeObjectNode extends FixedWithNextNode implements EscapeAnalyzable, Lowerable, Node.IterableNodeType, Canonicalizable {

    @Input private final NodeInputList<ValueNode> values;
    @Input private final VirtualObjectNode virtualObject;

    public MaterializeObjectNode(VirtualObjectNode virtualObject) {
        super(StampFactory.exactNonNull(virtualObject.type()));
        this.virtualObject = virtualObject;
        this.values = new NodeInputList<>(this, virtualObject.entryCount());
    }

    public NodeInputList<ValueNode> values() {
        return values;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) graph();
        if (virtualObject instanceof VirtualInstanceNode) {
            VirtualInstanceNode virtual = (VirtualInstanceNode) virtualObject;

            NewInstanceNode newInstance = graph.add(new NewInstanceNode(virtual.type(), false));
            this.replaceAtUsages(newInstance);
            graph.addAfterFixed(this, newInstance);

            FixedWithNextNode position = newInstance;
            for (int i = 0; i < virtual.entryCount(); i++) {
                StoreFieldNode store = graph.add(new StoreFieldNode(newInstance, virtual.field(i), values.get(i), -1));
                graph.addAfterFixed(position, store);
                position = store;
            }

            graph.removeFixed(this);
        } else {
            assert virtualObject instanceof VirtualArrayNode;
            VirtualArrayNode virtual = (VirtualArrayNode) virtualObject;

            ResolvedJavaType element = virtual.componentType();
            NewArrayNode newArray;
            if (element.kind() == Kind.Object) {
                newArray = graph.add(new NewObjectArrayNode(element, ConstantNode.forInt(virtual.entryCount(), graph), false));
            } else {
                newArray = graph.add(new NewPrimitiveArrayNode(element, ConstantNode.forInt(virtual.entryCount(), graph), false));
            }
            this.replaceAtUsages(newArray);
            graph.addAfterFixed(this, newArray);

            FixedWithNextNode position = newArray;
            for (int i = 0; i < virtual.entryCount(); i++) {
                StoreIndexedNode store = graph.add(new StoreIndexedNode(newArray, ConstantNode.forInt(i, graph), element.kind(), values.get(i), -1));
                graph.addAfterFixed(position, store);
                position = store;
            }

            graph.removeFixed(this);
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (usages().isEmpty()) {
            return null;
        } else {
            return this;
        }
    }

    @Override
    public EscapeOp getEscapeOp() {
        return new EscapeOp() {

            @Override
            public ValueNode[] fieldState() {
                return values.toArray(new ValueNode[values.size()]);
            }

            @Override
            public VirtualObjectNode virtualObject(int virtualId) {
                return virtualObject;
            }
        };
    }
}

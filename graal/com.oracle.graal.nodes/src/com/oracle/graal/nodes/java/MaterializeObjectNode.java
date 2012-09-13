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
package com.oracle.graal.nodes.java;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

@NodeInfo(nameTemplate = "Materialize {p#type/s}")
public final class MaterializeObjectNode extends FixedWithNextNode implements EscapeAnalyzable, Lowerable, Node.IterableNodeType, Canonicalizable {

    @Input private final NodeInputList<ValueNode> values;
    @Input private final VirtualObjectNode virtualObject;
    private final ResolvedJavaType type;

    public MaterializeObjectNode(ResolvedJavaType type, VirtualObjectNode virtualObject) {
        super(StampFactory.exactNonNull(type));
        this.type = type;
        this.virtualObject = virtualObject;
        this.values = new NodeInputList<>(this, virtualObject.fields().length);
    }

    public ResolvedJavaType type() {
        return type;
    }

    public NodeInputList<ValueNode> values() {
        return values;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) graph();
        EscapeField[] fields = virtualObject.fields();
        if (type.isArrayClass()) {
            ResolvedJavaType element = type.componentType();
            NewArrayNode newArray;
            if (element.kind() == Kind.Object) {
                newArray = graph.add(new NewObjectArrayNode(element, ConstantNode.forInt(fields.length, graph), false));
            } else {
                newArray = graph.add(new NewPrimitiveArrayNode(element, ConstantNode.forInt(fields.length, graph), false));
            }
            this.replaceAtUsages(newArray);
            graph.addAfterFixed(this, newArray);

            FixedWithNextNode position = newArray;
            for (int i = 0; i < fields.length; i++) {
                StoreIndexedNode store = graph.add(new StoreIndexedNode(newArray, ConstantNode.forInt(i, graph), element.kind(), values.get(i), -1));
                graph.addAfterFixed(position, store);
                position = store;
            }

            graph.removeFixed(this);
        } else {
            NewInstanceNode newInstance = graph.add(new NewInstanceNode(type, false));
            this.replaceAtUsages(newInstance);
            graph.addAfterFixed(this, newInstance);

            FixedWithNextNode position = newInstance;
            for (int i = 0; i < fields.length; i++) {
                StoreFieldNode store = graph.add(new StoreFieldNode(newInstance, (ResolvedJavaField) fields[i].representation(), values.get(i), -1));
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
        return new EscapeOpImpl();
    }

    private final class EscapeOpImpl extends EscapeOp {

        @Override
        public ResolvedJavaType type() {
            return type;
        }

        @Override
        public EscapeField[] fields() {
            return virtualObject.fields();
        }

        @Override
        public ValueNode[] fieldState() {
            return values.toArray(new ValueNode[values.size()]);
        }

        @Override
        public void beforeUpdate(Node usage) {
            throw new UnsupportedOperationException("MaterializeNode can only be escape analyzed using partial escape analysis");
        }

        @Override
        public int updateState(VirtualObjectNode node, Node current, Map<Object, Integer> fieldIndex, ValueNode[] fieldState) {
            throw new UnsupportedOperationException("MaterializeNode can only be escape analyzed using partial escape analysis");
        }
    }
}

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

/**
 * The {@code StoreFieldNode} represents a write to a static or instance field.
 */
@NodeInfo(nameTemplate = "MaterializeStore#{p#target/s}")
public final class CyclicMaterializeStoreNode extends FixedWithNextNode implements Lowerable {

    @Input private ValueNode object;
    @Input private ValueNode value;
    private final Object target;

    public ValueNode object() {
        return object;
    }

    public ValueNode value() {
        return value;
    }

    public ResolvedJavaField targetField() {
        return (ResolvedJavaField) target;
    }

    public int targetIndex() {
        return (int) target;
    }

    public CyclicMaterializeStoreNode(ValueNode object, ValueNode value, ResolvedJavaField field) {
        super(StampFactory.forVoid());
        this.object = object;
        this.value = value;
        this.target = field;
    }

    public CyclicMaterializeStoreNode(ValueNode object, ValueNode value, int index) {
        super(StampFactory.forVoid());
        this.object = object;
        this.value = value;
        this.target = index;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) graph();
        ResolvedJavaType type = object.objectStamp().type();
        FixedWithNextNode store;
        if (target instanceof Integer) {
            store = graph.add(new StoreIndexedNode(object, ConstantNode.forInt((int) target, graph), type.componentType().kind(), value, -1));
        } else {
            assert target instanceof ResolvedJavaField;
            store = graph.add(new StoreFieldNode(object, (ResolvedJavaField) target, value, -1));
        }
        graph.replaceFixedWithFixed(this, store);
    }
}

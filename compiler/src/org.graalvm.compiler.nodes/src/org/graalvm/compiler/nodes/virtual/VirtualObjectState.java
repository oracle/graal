/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.virtual;

import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;

/**
 * This class encapsulated the virtual state of an escape analyzed object.
 */
@NodeInfo
public final class VirtualObjectState extends EscapeObjectState implements Node.ValueNumberable {

    public static final NodeClass<VirtualObjectState> TYPE = NodeClass.create(VirtualObjectState.class);
    @OptionalInput NodeInputList<ValueNode> values;

    public NodeInputList<ValueNode> values() {
        return values;
    }

    public VirtualObjectState(VirtualObjectNode object, ValueNode[] values) {
        super(TYPE, object);
        assert object.entryCount() == values.length;
        this.values = new NodeInputList<>(this, values);
    }

    public VirtualObjectState(VirtualObjectNode object, List<ValueNode> values) {
        super(TYPE, object);
        assert object.entryCount() == values.size();
        this.values = new NodeInputList<>(this, values);
    }

    @Override
    public VirtualObjectState duplicateWithVirtualState() {
        return graph().addWithoutUnique(new VirtualObjectState(object(), values));
    }

}

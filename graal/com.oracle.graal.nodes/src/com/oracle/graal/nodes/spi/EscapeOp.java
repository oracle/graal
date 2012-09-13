/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.spi;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.virtual.*;

public abstract class EscapeOp {

    /**
     * Returns the type of object that is created by the associated node.
     */
    public abstract ResolvedJavaType type();

    /**
     * Returns all the fields that the objects create by the associated node have.
     */
    public abstract EscapeField[] fields();

    /**
     * Returns the initial value of all fields, in the same order as {@link #fields()}.
     */
    public abstract ValueNode[] fieldState();

    public abstract void beforeUpdate(Node usage);

    protected static void beforeUpdate(Node node, Node usage) {
        // IsNullNode and IsTypeNode should have been eliminated by the CanonicalizerPhase, but we can't rely on this
        if (usage instanceof IsNullNode) {
            IsNullNode x = (IsNullNode) usage;
            ((StructuredGraph) x.graph()).replaceFloating(x, ConstantNode.forBoolean(false, node.graph()));
        } else if (usage instanceof IsTypeNode) {
            IsTypeNode x = (IsTypeNode) usage;
            boolean result = ((ValueNode) node).objectStamp().type() == x.type();
            ((StructuredGraph) x.graph()).replaceFloating(x, ConstantNode.forBoolean(result, node.graph()));
        } else if (usage instanceof AccessMonitorNode) {
            ((AccessMonitorNode) usage).eliminate();
        }
    }

    public abstract int updateState(VirtualObjectNode virtualObject, Node current, Map<Object, Integer> fieldIndex, ValueNode[] fieldState);

}

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
package com.oracle.truffle.dsl.processor.node;

import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.node.NodeChildData.*;

public class NodeExecutionData {

    private final NodeChildData child;
    private final String name;
    private final int index;
    private final boolean shortCircuit;

    public NodeExecutionData(NodeChildData child, int index, boolean shortCircuit) {
        this.child = child;
        this.index = index;
        this.shortCircuit = shortCircuit;
        this.name = createName();
    }

    private String createName() {
        if (isIndexed()) {
            return child.getName() + index;
        }
        return child.getName();
    }

    public TypeMirror getNodeType() {
        TypeMirror type;
        if (child.getCardinality() == Cardinality.MANY && child.getNodeType().getKind() == TypeKind.ARRAY) {
            type = ((ArrayType) child.getNodeType()).getComponentType();
        } else {
            type = child.getNodeType();
        }
        return type;
    }

    public String getName() {
        return name;
    }

    public NodeChildData getChild() {
        return child;
    }

    public int getIndex() {
        return index;
    }

    public boolean isIndexed() {
        return index > -1;
    }

    public boolean isShortCircuit() {
        return shortCircuit;
    }

    public String getShortCircuitId() {
        return createShortCircuitId(child, index);
    }

    public static String createShortCircuitId(NodeChildData child, int varArgsIndex) {
        String shortCircuitName = child.getName();
        if (child.getCardinality().isMany()) {
            shortCircuitName = shortCircuitName + "[" + varArgsIndex + "]";
        }
        return shortCircuitName;
    }

}

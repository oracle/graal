/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph;

public class NodeStack {

    private static final int INITIAL_SIZE = 8;

    protected Node[] values;
    protected int tos;

    public NodeStack() {
        values = new Node[INITIAL_SIZE];
    }

    public void push(Node n) {
        int newIndex = tos++;
        int valuesLength = values.length;
        if (newIndex >= valuesLength) {
            Node[] newValues = new Node[valuesLength << 1];
            System.arraycopy(values, 0, newValues, 0, valuesLength);
            values = newValues;
        }
        values[newIndex] = n;
    }

    public Node pop() {
        assert tos > 0 : "stack must be non-empty";
        return values[--tos];
    }

    public Node peek() {
        assert tos > 0 : "stack must be non-empty";
        return values[tos - 1];
    }

    public boolean isEmpty() {
        return tos == 0;
    }
}

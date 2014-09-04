/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.graph.Graph.*;

import com.oracle.graal.graph.Node.*;

/**
 * An iterator over the references to a given {@link Node}'s {@linkplain Input inputs} or
 * {@linkplain Successor successors}.
 */
public class NodeRefIterable implements NodeClassIterable {

    protected final Node node;

    /**
     * Specifies if {@link #iterator()} and {@link #withNullIterator()} iterate over
     * {@linkplain Input inputs} or {@linkplain Successor successors}.
     */
    protected final boolean isInputs;

    public NodeRefIterable(Node node, boolean isInputs) {
        this.isInputs = isInputs;
        this.node = node;
    }

    @Override
    public NodePosIterator iterator() {
        int count = isInputs ? node.getInputsCount() : node.getSuccessorsCount();
        if (count == 0) {
            return NodeRefIterator.Empty;
        }
        int nodeFields = count & 0xFFFF;
        int nodeListFields = (count >> 16) & 0xFFFF;
        if (MODIFICATION_COUNTS_ENABLED) {
            return new NodeRefWithModCountIterator(node, nodeFields, nodeListFields, isInputs);
        } else {
            return new NodeRefIterator(node, nodeFields, nodeListFields, isInputs);
        }
    }

    public NodePosIterator withNullIterator() {
        int count = isInputs ? node.getInputsCount() : node.getSuccessorsCount();
        if (count == 0) {
            return NodeRefIterator.Empty;
        }
        int nodeFields = count & 0xFFFF;
        int nodeListFields = (count >> 16) & 0xFFFF;
        return new NodeAllRefsIterator(node, nodeFields, nodeListFields, isInputs);
    }

    @Override
    public boolean contains(Node other) {
        return isInputs ? node.inputsContains(other) : node.successorsContains(other);
    }
}

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

import java.util.*;

import com.oracle.graal.graph.Node.Input;
import com.oracle.graal.graph.Node.Successor;

/**
 * An iterator over the references to a {@link Node}'s {@linkplain Input inputs} and
 * {@linkplain Successor successors}.
 *
 * An iterator of this type will not return null values, unless the field values are modified
 * concurrently. Concurrent modifications are detected by an assertion on a best-effort basis.
 */
public class NodeRefIterator implements NodePosIterator {

    public static final NodeRefIterator Empty = new NodeRefIterator(null, 0, 0, false);

    protected final Node node;

    /**
     * The total number of {@link Node} and {@link NodeList} fields.
     */
    protected final int allNodeRefFields;

    /**
     * The number of {@link Node} fields.
     */
    protected final int nodeFields;

    /**
     * Specifies if this iterator iterates over {@linkplain Input inputs} or {@linkplain Successor
     * successors}.
     */
    protected final boolean isInputs;

    /**
     * Current field iteration index.
     */
    protected int index;

    /**
     * Current iteration index within a {@link NodeList} if {@link #index} denotes a
     * {@link NodeList} field.
     */
    protected int subIndex;

    protected NodeList<? extends Node> list;

    /**
     * Creates an iterator over a node's references (i.e., {@linkplain Input inputs} or
     * {@linkplain Successor successors}) to other nodes. The {@link Node} fields are iterated
     * before the {@link NodeList} fields. All elements of these NodeLists will be visited by the
     * iterator as well.
     *
     * @param nodeFields the number of {@link Node} fields in the class hierarchy of the node being
     *            iterated
     * @param nodeListFields the number of {@link NodeList} fields in the class hierarchy of the
     *            node being iterated
     */
    protected NodeRefIterator(Node node, int nodeFields, int nodeListFields, boolean isInputs) {
        this.node = node;
        this.allNodeRefFields = nodeListFields + nodeFields;
        this.nodeFields = nodeFields;
        this.isInputs = isInputs;
        index = Node.NOT_ITERABLE;
        subIndex = 0;
    }

    /**
     * Gets the value of a {@link Node} field in the node.
     *
     * @param at the index of the Node field whose value is being requested. This is guaranteed to
     *            be between 0 and the {@code nodeFields} value this iterator was constructed with
     */
    protected Node getNode(int at) {
        return isInputs ? node.getInputNodeAt(at) : node.getSuccessorNodeAt(at);
    }

    /**
     * Gets the value of a {@link NodeList} field in the node.
     *
     * @param at the index of the {@link NodeList} field whose value is being requested. This is
     *            guaranteed to be between 0 and the {@code nodeListFields} value this iterator was
     *            constructed with
     */
    protected NodeList<? extends Node> getNodeList(int at) {
        return isInputs ? node.getInputNodeListAt(at) : node.getSuccessorNodeListAt(at);
    }

    protected void forward() {
        if (index < nodeFields) {
            index++;
            while (index < nodeFields) {
                if (getNode(index) != null) {
                    return;
                }
                index++;
            }
        } else {
            subIndex++;
        }
        while (index < allNodeRefFields) {
            if (subIndex == 0) {
                list = getNodeList(index - nodeFields);
            }
            assert list == getNodeList(index - nodeFields);
            while (subIndex < list.size()) {
                if (list.get(subIndex) != null) {
                    return;
                }
                subIndex++;
            }
            subIndex = 0;
            index++;
        }
        return;
    }

    private Node nextElement() {
        if (index < nodeFields) {
            return getNode(index);
        } else if (index < allNodeRefFields) {
            assert getNodeList(index - nodeFields) == list;
            return list.get(subIndex);
        }
        throw new NoSuchElementException();
    }

    @Override
    public boolean hasNext() {
        return index >= 0 && index < allNodeRefFields;
    }

    @Override
    public Node next() {
        try {
            return nextElement();
        } finally {
            forward();
        }
    }

    public Position nextPosition() {
        try {
            if (index < nodeFields) {
                return new Position(isInputs, index, Node.NOT_ITERABLE);
            } else {
                return new Position(isInputs, index, subIndex);
            }
        } finally {
            forward();
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}

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

import static com.oracle.graal.graph.Node.*;

import java.util.*;

import com.oracle.graal.graph.Node.Input;
import com.oracle.graal.graph.Node.Successor;

/**
 * An iterator over the {@link Node} and {@link NodeList} fields in a node.
 */
public class FirstLevelPositionIterator implements Iterator<Position> {

    public static final FirstLevelPositionIterator Empty = new FirstLevelPositionIterator(0, 0, false);

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
    private final boolean isInputs;

    /**
     * Current field iteration index.
     */
    protected int index;

    /**
     * Creates an iterator over the {@link Node} and {@link NodeList} fields in a node.
     *
     * @param nodeFields the number of {@link Node} fields in the class hierarchy of the node being
     *            iterated
     * @param nodeListFields the number of {@link NodeList} fields in the class hierarchy of the
     *            node being iterated
     */
    protected FirstLevelPositionIterator(int nodeFields, int nodeListFields, boolean isInputs) {
        this.allNodeRefFields = nodeListFields + nodeFields;
        this.nodeFields = nodeFields;
        this.isInputs = isInputs;
        index = Node.NOT_ITERABLE;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public Position next() {
        Position pos = new Position(isInputs, index, index >= nodeFields ? NODE_LIST : NOT_ITERABLE);
        index++;
        return pos;
    }

    public boolean hasNext() {
        return index < allNodeRefFields;
    }

    public int size() {
        return allNodeRefFields;
    }
}

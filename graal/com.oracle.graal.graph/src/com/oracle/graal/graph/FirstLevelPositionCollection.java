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
 * A collection of the positions of the {@link Node} and {@link NodeList} fields in a node.
 */
public final class FirstLevelPositionCollection extends AbstractCollection<Position> implements Collection<Position> {

    public static final FirstLevelPositionCollection Empty = new FirstLevelPositionCollection(0, 0, false);

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
     * Creates a collection of the positions of the {@link Node} and {@link NodeList} fields in a
     * node.
     */
    public FirstLevelPositionCollection(int nodeFields, int nodeListFields, boolean isInputs) {
        this.allNodeRefFields = nodeListFields + nodeFields;
        this.nodeFields = nodeFields;
        this.isInputs = isInputs;
    }

    @Override
    public int size() {
        return allNodeRefFields;
    }

    @Override
    public Iterator<Position> iterator() {
        return new FirstLevelPositionIterator(nodeFields, allNodeRefFields - nodeFields, isInputs);
    }
}

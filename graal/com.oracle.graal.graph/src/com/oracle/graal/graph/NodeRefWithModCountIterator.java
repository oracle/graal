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

import com.oracle.graal.graph.Node.Input;

/**
 * An iterator over the references to a given {@link Node}'s {@linkplain Input inputs}.
 *
 * An iterator of this type will not return null values, unless the field values are modified
 * concurrently. Concurrent modifications are detected by an assertion on a best-effort basis.
 */
public final class NodeRefWithModCountIterator extends NodeRefIterator {

    private final int modCount;

    public NodeRefWithModCountIterator(Node node, int nodeFields, int nodeListFields, boolean isInputs) {
        super(node, nodeFields, nodeListFields, isInputs);
        assert MODIFICATION_COUNTS_ENABLED;
        this.modCount = node.modCount();
    }

    @Override
    public boolean hasNext() {
        try {
            return super.hasNext();
        } finally {
            assert modCount == node.modCount() : "must not be modified";
        }
    }

    @Override
    public Node next() {
        try {
            return super.next();
        } finally {
            assert modCount == node.modCount() : "must not be modified";
        }
    }

    @Override
    public Position nextPosition() {
        try {
            return super.nextPosition();
        } finally {
            assert modCount == node.modCount();
        }
    }
}

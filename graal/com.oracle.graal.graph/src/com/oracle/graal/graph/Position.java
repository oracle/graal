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

import com.oracle.graal.graph.Node.Input;
import com.oracle.graal.graph.Node.OptionalInput;
import com.oracle.graal.nodeinfo.*;

/**
 * Describes an edge slot for a {@link NodeClass}.
 *
 * @see NodeClass#getName(Position)
 */
public final class Position {

    /**
     * Specifies if this position denotes an {@link Input} or {@link OptionalInput} field.
     */
    private final boolean input;

    /**
     * Index of the {@link Node} or {@link NodeList} field denoted by this position.
     */
    private final int index;

    /**
     * Index within a {@link NodeList} if {@link #index} denotes a {@link NodeList} field otherwise
     * {@link Node#NOT_ITERABLE}.
     */
    private final int subIndex;

    public Position(boolean input, int index, int subIndex) {
        this.input = input;
        this.index = index;
        this.subIndex = subIndex;
    }

    public Node get(Node node) {
        if (Node.USE_GENERATED_NODES) {
            return node.getNodeAt(this);
        }
        return node.getNodeClass().get(node, this);
    }

    public InputType getInputType(Node node) {
        if (Node.USE_GENERATED_NODES) {
            return node.getInputTypeAt(this);
        }
        return node.getNodeClass().getInputType(this);
    }

    public String getInputName(Node node) {
        if (Node.USE_GENERATED_NODES) {
            return node.getNameOf(this);
        }
        return node.getNodeClass().getName(this);
    }

    public boolean isInputOptional(Node node) {
        if (Node.USE_GENERATED_NODES) {
            return node.isOptionalInputAt(this);
        }
        return node.getNodeClass().isInputOptional(this);
    }

    public void set(Node node, Node value) {
        if (Node.USE_GENERATED_NODES) {
            node.updateNodeAt(this, value);
        } else {
            node.getNodeClass().set(node, this, value);
        }
    }

    public void initialize(Node node, Node value) {
        if (Node.USE_GENERATED_NODES) {
            node.initializeNodeAt(this, value);
        } else {
            node.getNodeClass().initializePosition(node, this, value);
        }
    }

    @Override
    public String toString() {
        return (input ? "input " : "successor ") + index + "/" + subIndex;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + index;
        result = prime * result + (input ? 1231 : 1237);
        result = prime * result + subIndex;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Position other = (Position) obj;
        if (index != other.index) {
            return false;
        }
        if (input != other.input) {
            return false;
        }
        if (subIndex != other.subIndex) {
            return false;
        }
        return true;
    }

    /**
     * Gets the index within a {@link NodeList} if {@link #getIndex()} denotes a {@link NodeList}
     * field otherwise {@link Node#NOT_ITERABLE}.
     */
    public int getSubIndex() {
        return subIndex;
    }

    /**
     * Gets the index of the {@link Node} or {@link NodeList} field denoted by this position.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns true if this position denotes an {@link Input} or {@link OptionalInput} field, false
     * otherwise.
     */
    public boolean isInput() {
        return input;
    }
}

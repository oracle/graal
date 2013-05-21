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
package com.oracle.truffle.api.nodes;

import java.lang.annotation.*;
import java.util.*;

import com.oracle.truffle.api.*;

/**
 * Abstract base class for all Truffle nodes.
 */
public abstract class Node implements Cloneable {

    /**
     * Utility constant representing an empty node array.
     */
    public static final Node[] EMPTY_ARRAY = new Node[0];

    private Node parent;

    private SourceSection sourceSection;

    /**
     * Marks array fields that are children of this node.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Children {
    }

    /**
     * Marks fields that represent child nodes of this node.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Child {
    }

    /**
     * Assigns a link to a guest language source section to this node.
     * 
     * @param section the object representing a section in guest language source code
     */
    public final void assignSourceSection(SourceSection section) {
        if (sourceSection != null) {
            throw new IllegalStateException("Source section is already assigned.");
        }
        this.sourceSection = section;
    }

    /**
     * Clears any previously assigned guest language source code from this node.
     */
    public final void clearSourceSection() {
        this.sourceSection = null;
    }

    /**
     * Retrieves the guest language source code section that is currently assigned to this node.
     * 
     * @return the assigned source code section
     */
    public final SourceSection getSourceSection() {
        return sourceSection;
    }

    /**
     * Method that updates the link to the parent in the array of specified new child nodes to this
     * node.
     * 
     * @param newChildren the array of new children whose parent should be updated
     * @return the array of new children
     */
    protected final <T extends Node> T[] adoptChildren(T[] newChildren) {
        if (newChildren != null) {
            for (T n : newChildren) {
                adoptChild(n);
            }
        }
        return newChildren;
    }

    /**
     * Method that updates the link to the parent in the specified new child node to this node.
     * 
     * @param newChild the new child whose parent should be updated
     * @return the new child
     */
    protected final <T extends Node> T adoptChild(T newChild) {
        if (newChild != null) {
            ((Node) newChild).parent = this;
        }
        return newChild;
    }

    /**
     * Returns properties of this node interesting for debugging and can be overwritten by
     * subclasses to add their own custom properties.
     * 
     * @return the properties as a key/value hash map
     */
    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new HashMap<>();
        return properties;
    }

    /**
     * The current parent node of this node.
     * 
     * @return the parent node
     */
    public final Node getParent() {
        return parent;
    }

    /**
     * Replaces this node with another node. If there is a source section (see
     * {@link #getSourceSection()}) associated with this node, it is transferred to the new node.
     * 
     * @param newNode the new node that is the replacement
     * @param reason a description of the reason for the replacement
     * @return the new node
     */
    @SuppressWarnings({"unchecked"})
    public final <T extends Node> T replace(T newNode, String reason) {
        assert this.getParent() != null;
        if (sourceSection != null) {
            // Pass on the source section to the new node.
            newNode.assignSourceSection(sourceSection);
        }
        onReplace(newNode, reason);
        return (T) this.getParent().replaceChild(this, newNode);
    }

    private <T extends Node> T replaceChild(T oldChild, T newChild) {
        NodeUtil.replaceChild(this, oldChild, newChild);
        adoptChild(newChild);
        return newChild;
    }

    /**
     * Replaces this node with another node. If there is a source section (see
     * {@link #getSourceSection()}) associated with this node, it is transferred to the new node.
     * 
     * @param newNode the new node that is the replacement
     * @return the new node
     */
    public final <T extends Node> T replace(T newNode) {
        return replace(newNode, "");
    }

    /**
     * Intended to be implemented by subclasses of {@link Node} to receive a notification when the
     * node is rewritten. This method is invoked before the actual replace has happened.
     * 
     * @param newNode the replacement node
     * @param reason the reason the replace supplied
     */
    protected void onReplace(Node newNode, String reason) {
    }

    /**
     * Invokes the {@link NodeVisitor#visit(Node)} method for this node and recursively also for all
     * child nodes.
     * 
     * @param nodeVisitor the visitor
     */
    public final void accept(NodeVisitor nodeVisitor) {
        if (nodeVisitor.visit(this)) {
            for (Node child : this.getChildren()) {
                if (child != null) {
                    child.accept(nodeVisitor);
                }
            }
        }
    }

    /**
     * Iterator over the children of this node.
     * 
     * @return the iterator
     */
    public final Iterable<Node> getChildren() {
        final Node node = this;
        return new Iterable<Node>() {

            public Iterator<Node> iterator() {
                return new NodeUtil.NodeIterator(node);
            }
        };
    }

    /**
     * Creates a shallow copy of this node.
     * 
     * @return the new copy
     */
    public Node copy() {
        try {
            return (Node) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    /**
     * This method must never be called. It enforces that {@link Object#clone} is not directly
     * called by subclasses. Use the {@link #copy()} method instead.
     */
    @Override
    @Deprecated
    protected final Object clone() throws CloneNotSupportedException {
        throw new IllegalStateException("This method should never be called, use the copy method instead!");
    }

    /**
     * Converts this node to a textual representation useful for debugging.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        Map<String, Object> properties = getDebugProperties();
        boolean hasProperties = false;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            sb.append(hasProperties ? "," : "<");
            hasProperties = true;
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        if (hasProperties) {
            sb.append(">");
        }
        sb.append("@").append(Integer.toHexString(hashCode()));
        return sb.toString();
    }
}

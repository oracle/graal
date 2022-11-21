/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.profdiff.core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.collections.Pair;
import org.graalvm.profdiff.util.Writer;

/**
 * Represents a named node in a tree.
 */
public abstract class TreeNode<T extends TreeNode<T>> {
    /**
     * A string to be used instead of the name of the node when the name is {@code null}.
     */
    public static final String NULL_NAME = "null";

    /**
     * The name of this node.
     */
    private final String name;

    /**
     * The parent node of this node in the tree.
     */
    public T parent;

    /**
     * The children of this node in the tree.
     */
    private final List<T> children;

    protected TreeNode(String name) {
        this.name = name;
        this.parent = null;
        this.children = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private T asT() {
        return (T) this;
    }

    /**
     * Gets the children of this node.
     */
    public List<T> getChildren() {
        return children;
    }

    /**
     * Gets the name of this node.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the name of this node or {@link #NULL_NAME} if the name is {@code null}.
     */
    public String getNameOrNull() {
        return name == null ? NULL_NAME : name;
    }

    /**
     * Appends a child node to the list of children of this node. Removes the child from its parent
     * if it already has a different parent.
     *
     * @param child the child node to be added
     */
    public void addChild(T child) {
        if (child.parent != null) {
            child.removeFromParent();
        }
        child.parent = asT();
        children.add(child);
    }

    /**
     * Performs an operation on each node of the subtree of this node. The subtree is traversed
     * depth-first and the operation is performed when the node is first visited.
     *
     * @param consumer the operation to be performed on each node
     */
    public void forEach(Consumer<T> consumer) {
        LinkedList<T> stack = new LinkedList<>();
        stack.add(asT());
        while (!stack.isEmpty()) {
            T node = stack.removeLast();
            consumer.accept(node);
            for (int i = node.getChildren().size() - 1; i >= 0; i--) {
                stack.add(node.getChildren().get(i));
            }
        }
    }

    /**
     * Performs operations on each node of the subtree of this node. The subtree is traversed
     * depth-first. An operation is performed when the node is first visited and then another
     * operation is performed after its whole subtree has been visited.
     *
     * @param pre the operation that is performed on the first visit
     * @param post the operation that is performed after the subtree has been visited
     */
    public void forEach(Consumer<T> pre, Consumer<T> post) {
        LinkedList<Pair<T, Boolean>> stack = new LinkedList<>();
        stack.add(Pair.create(asT(), true));
        while (!stack.isEmpty()) {
            Pair<T, Boolean> item = stack.removeLast();
            T node = item.getLeft();
            if (item.getRight()) {
                pre.accept(node);
                stack.add(Pair.create(node, false));
                for (int i = node.getChildren().size() - 1; i >= 0; i--) {
                    T child = node.getChildren().get(i);
                    stack.add(Pair.create(child, true));
                }
            } else {
                post.accept(node);
            }
        }
    }

    /**
     * Removes this node from the list of children of its parent, making this node a root.
     */
    public void removeFromParent() {
        if (parent == null) {
            throw new RuntimeException("Cannot remove the root from parent.");
        }
        parent.getChildren().remove(asT());
        parent = null;
    }

    /**
     * Traverses the subtree (except this node) depth-first and removes each subtree where the
     * predicate returns {@code true}.
     *
     * @param predicate remove the subtree when the predicate returns {@code true}
     */
    public void removeIf(Function<T, Boolean> predicate) {
        LinkedList<T> stack = new LinkedList<>();
        stack.add(asT());
        while (!stack.isEmpty()) {
            T node = stack.removeLast();
            if (node != this && predicate.apply(node)) {
                node.removeFromParent();
            } else if (!node.getChildren().isEmpty()) {
                stack.addAll(node.getChildren());
            }
        }
    }

    /**
     * Writes the subtree starting from this node in dfs preorder to the destination writer.
     *
     * @param writer the destination writer
     */
    public void writeRecursive(Writer writer) {
        forEach(node -> {
            node.writeHead(writer);
            writer.increaseIndent();
        }, node -> writer.decreaseIndent());
    }

    /**
     * Writes the representation of this node without its subtree to the destination writer.
     *
     * @param writer the destination writer
     */
    public void writeHead(Writer writer) {
        writer.writeln(getNameOrNull());
    }
}

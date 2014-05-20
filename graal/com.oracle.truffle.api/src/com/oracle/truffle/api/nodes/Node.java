/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.io.*;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.truffle.api.*;

/**
 * Abstract base class for all Truffle nodes.
 */
public abstract class Node implements Cloneable {

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

    protected Node() {
        CompilerAsserts.neverPartOfCompilation();
    }

    protected Node(SourceSection sourceSection) {
        CompilerAsserts.neverPartOfCompilation();
        this.sourceSection = sourceSection;
    }

    /**
     * Assigns a link to a guest language source section to this node.
     *
     * @param section the object representing a section in guest language source code
     */
    public final void assignSourceSection(SourceSection section) {
        if (sourceSection != null) {
            // Patch this test during the transition to constructor-based
            // source attribution, which would otherwise trigger this
            // exception. This method will eventually be deprecated.
            if (getSourceSection() != section) {
                throw new IllegalStateException("Source section is already assigned. Old: " + getSourceSection() + ", new: " + section);
            }
        }
        this.sourceSection = section;
    }

    /**
     * Returns a rough estimate for the cost of this {@link Node}. This estimate can be used by
     * runtime systems or guest languages to implement heuristics based on Truffle ASTs. This method
     * is intended to be overridden by subclasses. The default implementation returns the value of
     * {@link NodeInfo#cost()} of the {@link NodeInfo} annotation declared at the subclass. If no
     * {@link NodeInfo} annotation is declared the method returns {@link NodeCost#MONOMORPHIC} as a
     * default value.
     */
    public NodeCost getCost() {
        NodeInfo info = getClass().getAnnotation(NodeInfo.class);
        if (info != null) {
            return info.cost();
        }
        return NodeCost.MONOMORPHIC;
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
     * Retrieves the guest language source code section that is currently assigned to this node.
     *
     * @return the assigned source code section
     */
    @CompilerDirectives.SlowPath
    public final SourceSection getEncapsulatingSourceSection() {
        if (sourceSection == null && getParent() != null) {
            return getParent().getEncapsulatingSourceSection();
        }
        return sourceSection;
    }

    /**
     * Method that updates the link to the parent in the array of specified new child nodes to this
     * node.
     *
     * @param newChildren the array of new children whose parent should be updated
     * @return the array of new children
     */
    protected final <T extends Node> T[] insert(final T[] newChildren) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert newChildren != null;
        for (Node newChild : newChildren) {
            adoptHelper(newChild);
        }
        return newChildren;
    }

    /**
     * Method that updates the link to the parent in the specified new child node to this node.
     *
     * @param newChild the new child whose parent should be updated
     * @return the new child
     */
    protected final <T extends Node> T insert(final T newChild) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert newChild != null;
        adoptHelper(newChild);
        return newChild;
    }

    public final void adoptChildren() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        adoptHelper();
    }

    private void adoptHelper(final Node newChild) {
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        boolean isInserted = newChild.parent == null;
        newChild.parent = this;
        newChild.adoptHelper();
        if (isInserted) {
            newChild.onAdopt();
        }
    }

    private void adoptHelper() {
        Iterable<Node> children = this.getChildren();
        for (Node child : children) {
            if (child != null && child.getParent() != this) {
                this.adoptHelper(child);
            }
        }
    }

    private void adoptUnadoptedHelper(final Node newChild) {
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        boolean isInserted = newChild.parent == null;
        newChild.parent = this;
        newChild.adoptUnadoptedHelper();
        if (isInserted) {
            newChild.onAdopt();
        }
    }

    private void adoptUnadoptedHelper() {
        Iterable<Node> children = this.getChildren();
        for (Node child : children) {
            if (child != null && child.getParent() == null) {
                this.adoptUnadoptedHelper(child);
            }
        }
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
    public final <T extends Node> T replace(final T newNode, final CharSequence reason) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        atomic(new Runnable() {
            public void run() {
                replaceHelper(newNode, reason);
            }
        });
        return newNode;
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

    private void replaceHelper(Node newNode, CharSequence reason) {
        CompilerAsserts.neverPartOfCompilation();
        if (this.getParent() == null) {
            throw new IllegalStateException("This node cannot be replaced, because it does not yet have a parent.");
        }
        if (sourceSection != null && newNode.getSourceSection() == null) {
            // Pass on the source section to the new node.
            newNode.assignSourceSection(sourceSection);
        }
        // (aw) need to set parent *before* replace, so that (unsynchronized) getRootNode()
        // will always find the root node
        newNode.parent = this.parent;
        if (NodeUtil.replaceChild(this.parent, this, newNode)) {
            this.parent.adoptHelper(newNode);
        } else {
            this.parent.adoptUnadoptedHelper(newNode);
        }
        reportReplace(this, newNode, reason);
        onReplace(newNode, reason);
    }

    /**
     * Checks if this node is properly adopted by a parent and can be replaced.
     *
     * @return {@code true} if it is safe to replace this node.
     */
    public final boolean isReplaceable() {
        if (getParent() != null) {
            for (Node sibling : getParent().getChildren()) {
                if (sibling == this) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reportReplace(Node oldNode, Node newNode, CharSequence reason) {
        RootNode rootNode = getRootNode();
        if (rootNode != null) {
            CallTarget target = rootNode.getCallTarget();
            if (target instanceof ReplaceObserver) {
                ((ReplaceObserver) target).nodeReplaced(oldNode, newNode, reason);
            }
        }
    }

    /**
     * Intended to be implemented by subclasses of {@link Node} to receive a notification when the
     * node is rewritten. This method is invoked before the actual replace has happened.
     *
     * @param newNode the replacement node
     * @param reason the reason the replace supplied
     */
    protected void onReplace(Node newNode, CharSequence reason) {
        if (TruffleOptions.TraceRewrites) {
            traceRewrite(newNode, reason);
        }
    }

    private void traceRewrite(Node newNode, CharSequence reason) {
        if (TruffleOptions.TraceRewritesFilterFromCost != null) {
            if (filterByKind(this, TruffleOptions.TraceRewritesFilterFromCost)) {
                return;
            }
        }

        if (TruffleOptions.TraceRewritesFilterToCost != null) {
            if (filterByKind(newNode, TruffleOptions.TraceRewritesFilterToCost)) {
                return;
            }
        }

        String filter = TruffleOptions.TraceRewritesFilterClass;
        Class<? extends Node> from = getClass();
        Class<? extends Node> to = newNode.getClass();
        if (filter != null && (filterByContainsClassName(from, filter) || filterByContainsClassName(to, filter))) {
            return;
        }

        final SourceSection reportedSourceSection = getEncapsulatingSourceSection();

        PrintStream out = System.out;
        out.printf("[truffle]   rewrite %-50s |From %-40s |To %-40s |Reason %s%s%n", this.toString(), formatNodeInfo(this), formatNodeInfo(newNode), reason != null && reason.length() > 0 ? reason
                        : "unknown", reportedSourceSection != null ? " at " + reportedSourceSection.getShortDescription() : "");
    }

    /**
     * Subclasses of {@link Node} can implement this method to execute extra functionality when a
     * node is effectively inserted into the AST. The {@code onAdopt} callback is called after the
     * node has been effectively inserted, and it is guaranteed to be called only once for any given
     * node.
     */
    protected void onAdopt() {
        // empty default
    }

    private static String formatNodeInfo(Node node) {
        String cost = "?";
        switch (node.getCost()) {
            case NONE:
                cost = "G";
                break;
            case MONOMORPHIC:
                cost = "M";
                break;
            case POLYMORPHIC:
                cost = "P";
                break;
            case MEGAMORPHIC:
                cost = "G";
                break;
            default:
                cost = "?";
                break;
        }
        return cost + " " + node.getClass().getSimpleName();
    }

    private static boolean filterByKind(Node node, NodeCost cost) {
        return node.getCost() == cost;
    }

    private static boolean filterByContainsClassName(Class<? extends Node> from, String filter) {
        Class<?> currentFrom = from;
        while (currentFrom != null) {
            if (currentFrom.getName().contains(filter)) {
                return false;
            }
            currentFrom = currentFrom.getSuperclass();
        }
        return true;
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
     * Get the root node of the tree a node belongs to.
     *
     * @return the {@link RootNode} or {@code null} if there is none.
     */
    public final RootNode getRootNode() {
        Node rootNode = this;
        while (rootNode.getParent() != null) {
            assert !(rootNode instanceof RootNode) : "root node must not have a parent";
            rootNode = rootNode.getParent();
        }
        if (rootNode instanceof RootNode) {
            return (RootNode) rootNode;
        }
        return null;
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

    public final void atomic(Runnable closure) {
        RootNode rootNode = getRootNode();
        if (rootNode != null) {
            synchronized (rootNode) {
                closure.run();
            }
        } else {
            closure.run();
        }
    }

    public final <T> T atomic(Callable<T> closure) {
        try {
            RootNode rootNode = getRootNode();
            if (rootNode != null) {
                synchronized (rootNode) {
                    return closure.call();
                }
            } else {
                return closure.call();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a user-readable description of the purpose of the Node, or "" if no description is
     * available.
     */
    public String getDescription() {
        NodeInfo info = getClass().getAnnotation(NodeInfo.class);
        if (info != null) {
            return info.description();
        }
        return "";
    }

    /**
     * Returns a string representing the language this node has been implemented for. If the
     * language is unknown, returns "".
     */
    public String getLanguage() {
        NodeInfo info = getClass().getAnnotation(NodeInfo.class);
        if (info != null && info.language() != null && info.language().length() > 0) {
            return info.language();
        }
        if (parent != null) {
            return parent.getLanguage();
        }
        return "";
    }
}

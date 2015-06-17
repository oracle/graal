/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;

/**
 * Abstract base class for all Truffle nodes.
 */
public abstract class Node implements NodeInterface, Cloneable {

    private final NodeClass nodeClass;
    @CompilationFinal private Node parent;
    @CompilationFinal private SourceSection sourceSection;

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
        this(null);
    }

    protected Node(SourceSection sourceSection) {
        CompilerAsserts.neverPartOfCompilation();
        this.sourceSection = sourceSection;
        this.nodeClass = NodeClass.get(getClass());
        if (TruffleOptions.TraceASTJSON) {
            JSONHelper.dumpNewNode(this);
        }
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

    NodeClass getNodeClass() {
        return nodeClass;
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
     * Retrieves the segment of guest language source code that is represented by this Node.
     *
     * @return the source code represented by this Node
     */
    public final SourceSection getSourceSection() {
        return sourceSection;
    }

    /**
     * Retrieves the segment of guest language source code that is represented by this Node, if
     * present; otherwise retrieves the segment represented by the nearest AST ancestor that has
     * this information.
     *
     * @return an approximation of the source code represented by this Node
     */
    @ExplodeLoop
    public final SourceSection getEncapsulatingSourceSection() {
        Node current = this;
        while (current != null) {
            if (current.sourceSection != null) {
                return current.sourceSection;
            }
            current = current.parent;
        }
        return null;
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
        newChild.parent = this;
        if (TruffleOptions.TraceASTJSON) {
            JSONHelper.dumpNewChild(this, newChild);
        }
        newChild.adoptHelper();
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
        newChild.parent = this;
        newChild.adoptUnadoptedHelper();
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

    final void replaceHelper(Node newNode, CharSequence reason) {
        CompilerAsserts.neverPartOfCompilation();
        assert inAtomicBlock();
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
     * Checks if this node can be replaced by another node: tree structure & type.
     */
    public final boolean isSafelyReplaceableBy(Node newNode) {
        return NodeUtil.isReplacementSafe(getParent(), this, newNode);
    }

    private void reportReplace(Node oldNode, Node newNode, CharSequence reason) {
        Node node = this;
        while (node != null) {
            boolean consumed = false;
            if (node instanceof ReplaceObserver) {
                consumed = ((ReplaceObserver) node).nodeReplaced(oldNode, newNode, reason);
            } else if (node instanceof RootNode) {
                CallTarget target = ((RootNode) node).getCallTarget();
                if (target instanceof ReplaceObserver) {
                    consumed = ((ReplaceObserver) target).nodeReplaced(oldNode, newNode, reason);
                }
            }
            if (consumed) {
                break;
            }
            node = node.getParent();
        }
        if (TruffleOptions.TraceRewrites) {
            NodeUtil.traceRewrite(this, newNode, reason);
        }
        if (TruffleOptions.TraceASTJSON) {
            JSONHelper.dumpReplaceChild(this, newNode, reason);
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
        // empty default
    }

    /**
     * Invokes the {@link NodeVisitor#visit(Node)} method for this node and recursively also for all
     * child nodes.
     *
     * @param nodeVisitor the visitor
     */
    public final void accept(NodeVisitor nodeVisitor) {
        if (nodeVisitor.visit(this)) {
            NodeUtil.forEachChildRecursive(this, nodeVisitor);
        }
    }

    /**
     * Iterator over the children of this node.
     *
     * @return the iterator
     */
    public final Iterable<Node> getChildren() {
        return new Iterable<Node>() {
            public Iterator<Node> iterator() {
                return getNodeClass().makeIterator(Node.this);
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
            throw new AssertionError(e);
        }
    }

    /**
     * Creates a deep copy of this node.
     *
     * @return the new deep copy
     */
    public Node deepCopy() {
        return NodeUtil.deepCopyImpl(this);
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
     * Any node for which this is {@code true} can be "instrumented" by installing a {@link Probe}
     * that intercepts execution events at the node and routes them to any {@link Instrument}s that
     * have been attached to the {@link Probe}. Only one {@link Probe} may be installed at each
     * node; subsequent calls return the one already installed.
     * <p>
     * <b>Note:</b> instrumentation requires a appropriate {@link WrapperNode}, which must be
     * provided by {@link #createWrapperNode()}.
     *
     * @see Instrument
     */
    public boolean isInstrumentable() {
        return false;
    }

    /**
     * For any node that {@link #isInstrumentable()}, this method must return a {@link Node} that:
     * <ol>
     * <li>implements {@link WrapperNode}</li>
     * <li>has {@code this} as it's child, and</li>
     * <li>whose type is safe for replacement of {@code this} in the parent.</li>
     * </ol>
     *
     * @return an appropriately typed {@link WrapperNode} if {@link #isInstrumentable()}.
     */
    public WrapperNode createWrapperNode() {
        return null;
    }

    /**
     * Enables {@linkplain Instrument instrumentation} of a node, where the node is presumed to be
     * part of a well-formed Truffle AST that is not being executed. If this node has not already
     * been probed, modifies the AST by inserting a {@linkplain WrapperNode wrapper node} between
     * the node and its parent; the wrapper node must be provided by implementations of
     * {@link #createWrapperNode()}. No more than one {@link Probe} may be associated with a node,
     * so a {@linkplain WrapperNode wrapper} may not wrap another {@linkplain WrapperNode wrapper}.
     *
     * @return a (possibly newly created) {@link Probe} associated with this node.
     * @throws ProbeException (unchecked) when a probe cannot be created, leaving the AST unchanged
     */
    public final Probe probe() {

        if (this instanceof WrapperNode) {
            throw new ProbeException(ProbeFailure.Reason.WRAPPER_NODE, null, this, null);
        }

        if (parent == null) {
            throw new ProbeException(ProbeFailure.Reason.NO_PARENT, null, this, null);
        }

        if (parent instanceof WrapperNode) {
            return ((WrapperNode) parent).getProbe();
        }

        if (!isInstrumentable()) {
            throw new ProbeException(ProbeFailure.Reason.NOT_INSTRUMENTABLE, parent, this, null);
        }

        // Create a new wrapper/probe with this node as its child.
        final WrapperNode wrapper = createWrapperNode();

        if (wrapper == null || !(wrapper instanceof Node)) {
            throw new ProbeException(ProbeFailure.Reason.NO_WRAPPER, parent, this, wrapper);
        }

        final Node wrapperNode = (Node) wrapper;

        if (!this.isSafelyReplaceableBy(wrapperNode)) {
            throw new ProbeException(ProbeFailure.Reason.WRAPPER_TYPE, parent, this, wrapper);
        }

        // Connect it to a Probe
        final Probe probe = ProbeNode.insertProbe(wrapper);

        // Replace this node in the AST with the wrapper
        this.replace(wrapperNode);

        return probe;
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
        synchronized (rootNode != null ? rootNode : GIL) {
            assert enterAtomic();
            try {
                closure.run();
            } finally {
                assert exitAtomic();
            }
        }
    }

    public final <T> T atomic(Callable<T> closure) {
        try {
            RootNode rootNode = getRootNode();
            synchronized (rootNode != null ? rootNode : GIL) {
                assert enterAtomic();
                try {
                    return closure.call();
                } finally {
                    assert exitAtomic();
                }
            }
        } catch (RuntimeException | Error e) {
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

    private static final Object GIL = new Object();

    private static final ThreadLocal<Integer> IN_ATOMIC_BLOCK = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private static boolean inAtomicBlock() {
        return IN_ATOMIC_BLOCK.get() > 0;
    }

    private static boolean enterAtomic() {
        IN_ATOMIC_BLOCK.set(IN_ATOMIC_BLOCK.get() + 1);
        return true;
    }

    private static boolean exitAtomic() {
        IN_ATOMIC_BLOCK.set(IN_ATOMIC_BLOCK.get() - 1);
        return true;
    }
}

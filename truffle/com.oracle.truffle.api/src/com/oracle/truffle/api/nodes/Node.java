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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;

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
    public void assignSourceSection(SourceSection section) {
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
    public void clearSourceSection() {
        this.sourceSection = null;
    }

    /**
     * Retrieves the segment of guest language source code that is represented by this Node.
     *
     * @return the source code represented by this Node
     */
    public SourceSection getSourceSection() {
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
    public SourceSection getEncapsulatingSourceSection() {
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

    void adoptHelper(final Node newChild) {
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
        NodeUtil.forEachChild(this, new NodeVisitor() {
            public boolean visit(Node child) {
                if (child != null && child.getParent() != Node.this) {
                    Node.this.adoptHelper(child);
                }
                return true;
            }
        });
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
        NodeUtil.forEachChild(this, new NodeVisitor() {
            public boolean visit(Node child) {
                if (child != null && child.getParent() == null) {
                    Node.this.adoptUnadoptedHelper(child);
                }
                return true;
            }
        });
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
        if (!NodeUtil.replaceChild(this.parent, this, newNode, true)) {
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
        CompilerAsserts.neverPartOfCompilation();

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
        // Major Assumption: parent is never null after a node got adopted
        // it is never reset to null, and thus, rootNode is always reachable.
        // GIL: used for nodes that are replace in ASTs that are not yet adopted
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
            // Major Assumption: parent is never null after a node got adopted
            // it is never reset to null, and thus, rootNode is always reachable.
            // GIL: used for nodes that are replace in ASTs that are not yet adopted
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

    static final class AccessorNodes extends Accessor {
        @SuppressWarnings("rawtypes")
        @Override
        protected Class<? extends TruffleLanguage> findLanguage(RootNode n) {
            return n.language;
        }

        @Override
        protected boolean isInstrumentable(RootNode rootNode) {
            return rootNode.isInstrumentable();
        }

        @Override
        protected void probeAST(RootNode rootNode) {
            super.probeAST(rootNode);
        }
    }

    // registers into Accessor.NODES
    static final AccessorNodes ACCESSOR = new AccessorNodes();
}

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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.Accessor.InstrumentSupport;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Abstract base class for all Truffle nodes.
 *
 * @since 0.8 or earlier
 */
public abstract class Node implements NodeInterface, Cloneable {

    @CompilationFinal private volatile Node parent;

    /**
     * Marks array fields that are children of this node.
     *
     * This annotation implies the semantics of @{@link CompilationFinal}(dimensions = 1).
     *
     * @since 0.8 or earlier
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Children {
    }

    /**
     * Marks fields that represent child nodes of this node.
     *
     * This annotation implies the semantics of {@link CompilationFinal}.
     *
     * @since 0.8 or earlier
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Child {
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    protected Node() {
        CompilerAsserts.neverPartOfCompilation("do not create a Node from compiled code");
        assert NodeClass.get(getClass()) != null; // ensure NodeClass constructor does not throw
        if (TruffleOptions.TraceASTJSON) {
            dump(this, null, null);
        }
    }

    NodeClass getNodeClass() {
        return NodeClass.get(getClass());
    }

    void setParent(Node parent) {
        this.parent = parent;
    }

    /**
     * Returns a rough estimate for the cost of this {@link Node}. This estimate can be used by
     * runtime systems or guest languages to implement heuristics based on Truffle ASTs. This method
     * is intended to be overridden by subclasses. The default implementation returns the value of
     * {@link NodeInfo#cost()} of the {@link NodeInfo} annotation declared at the subclass. If no
     * {@link NodeInfo} annotation is declared the method returns {@link NodeCost#MONOMORPHIC} as a
     * default value.
     *
     * @since 0.8 or earlier
     */
    public NodeCost getCost() {
        NodeInfo info = getClass().getAnnotation(NodeInfo.class);
        if (info != null) {
            return info.cost();
        }
        return NodeCost.MONOMORPHIC;
    }

    /**
     * Retrieves the segment of guest language source code that is represented by this Node. The
     * default implementation of this method returns <code>null</code>. If your node represents a
     * segment of the source code, override this method and return a eagerly or lazily computed
     * source section value. This method is not designed to be invoked on compiled code paths. May
     * be called on any thread and without a language context being active.
     * <p>
     * Simple example implementation using a simple implementation using a field:
     * {@link com.oracle.truffle.api.nodes.NodeSnippets.SimpleNode}
     * <p>
     * Recommended implementation computing the source section lazily from primitive fields:
     * {@link com.oracle.truffle.api.nodes.NodeSnippets.RecommendedNode}
     *
     * @return the source code represented by this Node
     * @since 0.8 or earlier
     */
    public SourceSection getSourceSection() {
        return null;
    }

    /**
     * Retrieves the segment of guest language source code that is represented by this Node, if
     * present; otherwise retrieves the segment represented by the nearest AST ancestor that has
     * this information. Can be called on any thread and without a language context.
     *
     * @return an approximation of the source code represented by this Node
     * @since 0.8 or earlier
     */
    @ExplodeLoop
    public SourceSection getEncapsulatingSourceSection() {
        Node current = this;
        while (current != null) {
            final SourceSection currentSection = current.getSourceSection();
            if (currentSection != null) {
                return currentSection;
            }
            current = current.parent;
        }
        return null;
    }

    /**
     * Inserts new node children into an AST that was already {@link #adoptChildren() adopted} by a
     * {@link #getParent() parent}. The new children need to be assigned to its {@link Children
     * children} field after insert was called.
     *
     * @param newChildren the array of new children whose parent should be updated
     * @return the array of new children
     * @since 0.8 or earlier
     */
    protected final <T extends Node> T[] insert(final T[] newChildren) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (newChildren != null) {
            for (Node newChild : newChildren) {
                adoptHelper(newChild);
            }
        }
        return newChildren;
    }

    /**
     * Inserts an new node into an AST that was already {@link #adoptChildren() adopted} by a
     * {@link #getParent() parent}. The new child needs to be assigned to its {@link Child child}
     * field after insert was called.
     *
     * @param newChild the new child whose parent should be updated
     * @return the new child
     * @since 0.8 or earlier
     */
    protected final <T extends Node> T insert(final T newChild) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (newChild != null) {
            adoptHelper(newChild);
        }
        return newChild;
    }

    /**
     * Notifies the framework about the insertion of one or more nodes during execution. Otherwise,
     * the framework assumes that {@link com.oracle.truffle.api.instrumentation.Instrumentable
     * instrumentable} nodes remain unchanged after their root node is first
     * {@link RootNode#execute(com.oracle.truffle.api.frame.VirtualFrame) executed}. Insertions
     * don't need to be notified if it is known that none of the inserted nodes are
     * {@link com.oracle.truffle.api.instrumentation.InstrumentableNode instrumentable}.
     * <p>
     * The provided {@link Node} and its children must be {@link #adoptChildren() adopted} in the
     * AST before invoking this method. The caller must ensure that this method is invoked only once
     * for a given node and its children.
     * <p>
     * Example usage: {@link com.oracle.truffle.api.nodes.NodeSnippets#notifyInserted}
     *
     * @param node the node tree that got inserted.
     * @since 0.27
     */
    @SuppressWarnings("static-method")
    protected final void notifyInserted(Node node) {
        RootNode rootNode = node.getRootNode();
        if (rootNode == null) {
            throw new IllegalStateException("Node is not yet adopted and cannot be updated.");
        }
        InstrumentSupport support = ACCESSOR.instrumentSupport();
        if (support != null) {
            support.onNodeInserted(rootNode, node);
        }
    }

    /** @since 0.8 or earlier */
    public final void adoptChildren() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        NodeUtil.adoptChildrenHelper(this);
    }

    @SuppressWarnings("deprecation")
    final void adoptHelper(final Node newChild) {
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        assert checkSameLanguages(newChild);
        newChild.parent = this;
        if (TruffleOptions.TraceASTJSON) {
            dump(this, newChild, null);
        }
        NodeUtil.adoptChildrenHelper(newChild);
    }

    int adoptChildrenAndCount() {
        CompilerAsserts.neverPartOfCompilation();
        return 1 + NodeUtil.adoptChildrenAndCountHelper(this);
    }

    @SuppressWarnings("deprecation")
    int adoptAndCountHelper(Node newChild) {
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        assert checkSameLanguages(newChild);
        newChild.parent = this;
        if (TruffleOptions.TraceASTJSON) {
            dump(this, newChild, null);
        }
        return 1 + NodeUtil.adoptChildrenAndCountHelper(newChild);
    }

    private boolean checkSameLanguages(final Node newChild) {
        if (newChild instanceof ExecutableNode && !(newChild instanceof RootNode)) {
            RootNode root = getRootNode();
            if (root == null) {
                throw new IllegalStateException("Cannot adopt ExecutableNode " + newChild + " as a child of node without a root.");
            }
            LanguageInfo pl = root.getLanguageInfo();
            LanguageInfo cl = ((ExecutableNode) newChild).getLanguageInfo();
            if (cl != pl) {
                throw new IllegalArgumentException("Can not adopt ExecutableNode under a different language." +
                                " Parent " + this + " is of " + langId(pl) + ", child " + newChild + " is of " + langId(cl));
            }
        }
        return true;
    }

    private static String langId(LanguageInfo languageInfo) {
        if (languageInfo == null) {
            return null;
        } else {
            return languageInfo.getId();
        }
    }

    private void adoptUnadoptedHelper(final Node newChild) {
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        newChild.parent = this;
        NodeUtil.forEachChild(newChild, new NodeVisitor() {
            public boolean visit(Node child) {
                if (child != null && child.getParent() == null) {
                    newChild.adoptUnadoptedHelper(child);
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
     * @since 0.8 or earlier
     */
    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new HashMap<>();
        return properties;
    }

    /**
     * The current parent node of this node.
     *
     * @return the parent node
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    public final <T extends Node> T replace(T newNode) {
        return replace(newNode, "");
    }

    final void replaceHelper(Node newNode, CharSequence reason) {
        CompilerAsserts.neverPartOfCompilation("do not call Node.replaceHelper from compiled code");
        assert inAtomicBlock();
        if (this.getParent() == null) {
            throw new IllegalStateException("This node cannot be replaced, because it does not yet have a parent.");
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
     *
     * @since 0.8 or earlier
     */
    public final boolean isSafelyReplaceableBy(Node newNode) {
        return NodeUtil.isReplacementSafe(getParent(), this, newNode);
    }

    @SuppressWarnings("deprecation")
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
            dump(this, newNode, reason);
        }
    }

    private static void dump(Node node, Node newChild, CharSequence reason) {
        if (ACCESSOR != null) {
            Accessor.DumpSupport dumpSupport = ACCESSOR.dumpSupport();
            if (dumpSupport != null) {
                dumpSupport.dump(node, newChild, reason);
            }
        }
    }

    /**
     * Intended to be implemented by subclasses of {@link Node} to receive a notification when the
     * node is rewritten. This method is invoked before the actual replace has happened.
     *
     * @param newNode the replacement node
     * @param reason the reason the replace supplied
     * @since 0.8 or earlier
     */
    protected void onReplace(Node newNode, CharSequence reason) {
        // empty default
    }

    /**
     * Invokes the {@link NodeVisitor#visit(Node)} method for this node and recursively also for all
     * child nodes.
     *
     * @param nodeVisitor the visitor
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    public Node copy() {
        CompilerAsserts.neverPartOfCompilation("do not call Node.copy from compiled code");

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
     * @since 0.8 or earlier
     */
    public Node deepCopy() {
        return NodeUtil.deepCopyImpl(this);
    }

    /**
     * Get the root node of the tree a node belongs to.
     *
     * @return the {@link RootNode} or {@code null} if there is none.
     * @since 0.8 or earlier
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
     * Notifies the runtime that this node specialized to a polymorphic state. This includes
     * specializations that increase "level" of polymorphism (e.g. Adding another element to an
     * existing inline cache). The runtime can use this information to, if
     * {@link RootNode#isCloningAllowed() allowed}, create a deep copy of the {@link RootNode}
     * hosting this node and gather context sensitive profiling feedback.
     *
     * @since 0.33
     */
    protected final void reportPolymorphicSpecialize() {
        CompilerAsserts.neverPartOfCompilation();
        Node.ACCESSOR.nodes().reportPolymorphicSpecialize(this);
    }

    /**
     * Converts this node to a textual representation useful for debugging.
     *
     * @since 0.8 or earlier
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

    /** @since 0.8 or earlier */
    public final void atomic(Runnable closure) {
        Lock lock = getLock();
        try {
            lock.lock();
            closure.run();
        } finally {
            lock.unlock();
        }
    }

    /** @since 0.8 or earlier */
    public final <T> T atomic(Callable<T> closure) {
        Lock lock = getLock();
        try {
            lock.lock();
            return closure.call();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a lock object that can be used to synchronize modifications to the AST. Only use it
     * as part of a synchronized block, do not call {@link Object#wait()} or {@link Object#notify()}
     * manually.
     *
     * @since 0.17
     * @deprecated replaced with {@link #getLock()}
     */
    @Deprecated
    protected final Object getAtomicLock() {
        // Major Assumption: parent is never null after a node got adopted
        // it is never reset to null, and thus, rootNode is always reachable.
        // GIL: used for nodes that are replace in ASTs that are not yet adopted
        RootNode root = getRootNode();
        return root == null ? GIL : root;
    }

    /**
     * Returns a lock object that can be used to synchronize modifications to the AST. Don't lock if
     * you call into foreign code with potential recursions to avoid deadlocks. Use responsibly.
     *
     * @since 0.19
     */
    protected final Lock getLock() {
        // Major Assumption: parent is never null after a node got adopted
        // it is never reset to null, and thus, rootNode is always reachable.
        // GIL: used for nodes that are replace in ASTs that are not yet adopted
        RootNode root = getRootNode();
        return root == null ? GIL_LOCK : root.lock;
    }

    /**
     * @since 0.12
     * @see com.oracle.truffle.api.instrumentation.InstrumentableNode
     * @deprecated in 0.33 implement InstrumentableNode#hasTag instead.
     */
    @Deprecated
    protected boolean isTaggedWith(@SuppressWarnings("unused") Class<?> tag) {
        return false;
    }

    /**
     * Returns a user-readable description of the purpose of the Node, or "" if no description is
     * available. Can be called on any thread and without a language context.
     *
     * @since 0.8 or earlier
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
     *
     * @since 0.8 or earlier
     * @deprecated in 0.25 use {@link #getRootNode() getRootNode()}.
     *             {@link RootNode#getLanguageInfo() getLanguageInfo()}.
     *             {@link LanguageInfo#getName() getName()} instead
     */
    @Deprecated
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
    private static final ReentrantLock GIL_LOCK = new ReentrantLock(false);

    private boolean inAtomicBlock() {
        return ((ReentrantLock) getLock()).isHeldByCurrentThread();
    }

    static final class AccessorNodes extends Accessor {

        @Override
        protected void onLoopCount(Node source, int iterations) {
            super.onLoopCount(source, iterations);
        }

        @Override
        protected EngineSupport engineSupport() {
            return super.engineSupport();
        }

        @Override
        protected Accessor.Nodes nodes() {
            return new AccessNodes();
        }

        @Override
        protected LanguageSupport languageSupport() {
            return super.languageSupport();
        }

        @Override
        protected DumpSupport dumpSupport() {
            return super.dumpSupport();
        }

        @Override
        protected InstrumentSupport instrumentSupport() {
            return super.instrumentSupport();
        }

        @Override
        protected Frames framesSupport() {
            return super.framesSupport();
        }

        static final class AccessNodes extends Accessor.Nodes {

            @Override
            public boolean isInstrumentable(RootNode rootNode) {
                return rootNode.isInstrumentable();
            }

            @Override
            public boolean isTaggedWith(Node node, Class<?> tag) {
                return node.isTaggedWith(tag);
            }

            @Override
            public boolean isCloneUninitializedSupported(RootNode rootNode) {
                return rootNode.isCloneUninitializedSupported();
            }

            @Override
            public RootNode cloneUninitialized(RootNode rootNode) {
                return rootNode.cloneUninitialized();
            }

            @Override
            public int adoptChildrenAndCount(RootNode rootNode) {
                return rootNode.adoptChildrenAndCount();
            }

            @Override
            public Object getEngineObject(LanguageInfo languageInfo) {
                return languageInfo.getEngineObject();
            }

            @Override
            public LanguageInfo createLanguage(Object vmObject, String id, String name, String version, String defaultMimeType, Set<String> mimeTypes, boolean internal, boolean interactive) {
                return new LanguageInfo(vmObject, id, name, version, defaultMimeType, mimeTypes, internal, interactive);
            }

            @Override
            public Object getSourceVM(RootNode rootNode) {
                return rootNode.sourceVM;
            }

            @Override
            public TruffleLanguage<?> getLanguage(RootNode rootNode) {
                return rootNode.language;
            }

            @Override
            public int getRootNodeBits(RootNode root) {
                return root.instrumentationBits;
            }

            @Override
            public void setRootNodeBits(RootNode root, int bits) {
                assert ((byte) bits) == bits : "root bits currently limit to a byte";
                root.instrumentationBits = (byte) bits;
            }

            @Override
            public Lock getLock(Node node) {
                return node.getLock();
            }

        }
    }

    // registers into Accessor.NODES
    static final AccessorNodes ACCESSOR = new AccessorNodes();

}

@SuppressWarnings("unused")
class NodeSnippets {

    // BEGIN: com.oracle.truffle.api.nodes.NodeSnippets.SimpleNode
    abstract class SimpleNode extends Node {

        private SourceSection sourceSection;

        void setSourceSection(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }
    }
    // END: com.oracle.truffle.api.nodes.NodeSnippets.SimpleNode

    // BEGIN:com.oracle.truffle.api.nodes.NodeSnippets.RecommendedNode
    abstract class RecommendedNode extends Node {

        private static final int NO_SOURCE = -1;

        private int sourceCharIndex = NO_SOURCE;
        private int sourceLength;

        public abstract Object execute(VirtualFrame frame);

        // invoked by the parser to set the source
        void setSourceSection(int charIndex, int length) {
            assert sourceCharIndex == NO_SOURCE : "source should only be set once";
            this.sourceCharIndex = charIndex;
            this.sourceLength = length;
        }

        @Override
        public final SourceSection getSourceSection() {
            if (sourceCharIndex == NO_SOURCE) {
                // AST node without source
                return null;
            }
            RootNode rootNode = getRootNode();
            if (rootNode == null) {
                // not yet adopted yet
                return null;
            }
            Source source = rootNode.getSourceSection().getSource();
            return source.createSection(sourceCharIndex, sourceLength);
        }

    }
    // END: com.oracle.truffle.api.nodes.NodeSnippets.RecommendedNode

    public static void notifyInserted() {
        class InstrumentableLanguageNode extends Node {
            Object execute(VirtualFrame frame) {
                return null;
            }
        }

        class MyLanguage extends TruffleLanguage<Object> {
            @Override
            protected Object createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
                return null;
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return false;
            }
        }

        // @formatter:off
        // BEGIN: com.oracle.truffle.api.nodes.NodeSnippets#notifyInserted
        class MyRootNode extends RootNode {

            protected MyRootNode(MyLanguage language) {
                super(language);
            }

            @Child InstrumentableLanguageNode child;

            @Override
            public Object execute(VirtualFrame frame) {
                if (child == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    child = insert(new InstrumentableLanguageNode());
                    notifyInserted(child);
                }
                return child.execute(frame);
            }

        }
        // END: com.oracle.truffle.api.nodes.NodeSnippets#notifyInserted
        // @formatter:on
    }
}

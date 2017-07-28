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
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.Accessor.InstrumentSupport;
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
     * segment of the source code, override this method and return a <code>final</code> or
     * {@link CompilationFinal} field in your node to the caller.
     *
     * To define node with <em>fixed</em> {@link SourceSection} that doesn't change after node
     * construction use:
     *
     * {@link com.oracle.truffle.api.nodes.NodeSnippets.NodeWithFixedSourceSection#section}
     *
     * To create a node which can associate and change the {@link SourceSection} later at any point
     * of time use:
     *
     * {@link com.oracle.truffle.api.nodes.NodeSnippets.MutableSourceSectionNode#section}
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
     * this information.
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
        assert newChildren != null;
        for (Node newChild : newChildren) {
            adoptHelper(newChild);
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
        assert newChild != null;
        adoptHelper(newChild);
        return newChild;
    }

    /**
     * Notifies the framework about the insertion of one or more nodes during execution. Otherwise,
     * the framework assumes that {@link com.oracle.truffle.api.instrumentation.Instrumentable
     * instrumentable} nodes remain unchanged after their root node is first
     * {@link RootNode#execute(com.oracle.truffle.api.frame.VirtualFrame) executed}. Insertions
     * don't need to be notified if it is known that none of the inserted nodes are instrumentable.
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

    final void adoptHelper(final Node newChild) {
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        newChild.parent = this;
        if (TruffleOptions.TraceASTJSON) {
            dump(this, newChild, null);
        }
        NodeUtil.adoptChildrenHelper(newChild);
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
     * Returns <code>true</code> if this node should be considered tagged by a given tag else
     * <code>false</code>. The method is only invoked for tags which are explicitly declared as
     * {@link com.oracle.truffle.api.instrumentation.ProvidedTags provided} by the
     * {@link TruffleLanguage language}. If the {@link #getSourceSection() source section} of the
     * node returns <code>null</code> then this node is considered to be not tagged by any tag.
     * <p>
     * Tags are used by guest languages to indicate that a {@link Node node} is a member of a
     * certain category of nodes. For example a debugger
     * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument instrument} might require a
     * guest language to tag all nodes as halt locations that should be considered as such.
     * <p>
     * The node implementor may decide how to implement tagging for nodes. The simplest way to
     * implement tagging using Java types is by overriding the {@link #isTaggedWith(Class)} method.
     * This example shows how to tag a node subclass and all its subclasses as expression and
     * statement:
     *
     * {@link com.oracle.truffle.api.nodes.NodeSnippets.ExpressionNode}
     *
     * <p>
     * Often it is impossible to just rely on the node's Java type to implement tagging. This
     * example shows how to use local state to implement tagging for a node.
     *
     * {@link com.oracle.truffle.api.nodes.NodeSnippets.StatementNode#isDebuggerHalt}
     *
     * <p>
     * The implementation of isTaggedWith method must ensure that its result is stable after the
     * parent {@link RootNode root node} was wrapped in a {@link CallTarget} using
     * {@link TruffleRuntime#createCallTarget(RootNode)}. The result is stable if the result of
     * calling this method for a particular tag remains always the same.
     *
     * @param tag the class {@link com.oracle.truffle.api.instrumentation.ProvidedTags provided} by
     *            the {@link TruffleLanguage language}
     * @return <code>true</code> if the node should be considered tagged by a tag else
     *         <code>false</code>.
     * @since 0.12
     */
    protected boolean isTaggedWith(Class<?> tag) {
        return false;
    }

    /**
     * Returns a user-readable description of the purpose of the Node, or "" if no description is
     * available.
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
            public Object getEngineObject(LanguageInfo languageInfo) {
                return languageInfo.getEngineObject();
            }

            @Override
            public TruffleLanguage<?> getLanguageSpi(LanguageInfo languageInfo) {
                return languageInfo.getSpi();
            }

            @Override
            public void setLanguageSpi(LanguageInfo languageInfo, TruffleLanguage<?> spi) {
                languageInfo.setSpi(spi);
            }

            @Override
            public LanguageInfo createLanguage(Object vmObject, String id, String name, String version, Set<String> mimeTypes) {
                return new LanguageInfo(vmObject, id, name, version, mimeTypes);
            }

            @Override
            public Object getSourceVM(RootNode rootNode) {
                return rootNode.sourceVM;
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

        }
    }

    // registers into Accessor.NODES
    static final AccessorNodes ACCESSOR = new AccessorNodes();

}

class NodeSnippets {
    static class NodeWithFixedSourceSection extends Node {
        // BEGIN: com.oracle.truffle.api.nodes.NodeSnippets.NodeWithFixedSourceSection#section
        private final SourceSection section;

        NodeWithFixedSourceSection(SourceSection section) {
            this.section = section;
        }

        @Override
        public SourceSection getSourceSection() {
            return section;
        }
        // END: com.oracle.truffle.api.nodes.NodeSnippets.NodeWithFixedSourceSection#section
    }

    static class MutableSourceSectionNode extends Node {
        // BEGIN: com.oracle.truffle.api.nodes.NodeSnippets.MutableSourceSectionNode#section
        @CompilerDirectives.CompilationFinal private SourceSection section;

        final void changeSourceSection(SourceSection sourceSection) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.section = sourceSection;
        }

        @Override
        public SourceSection getSourceSection() {
            return section;
        }
        // END: com.oracle.truffle.api.nodes.NodeSnippets.MutableSourceSectionNode#section
    }

    private static final class Debugger {
        static class HaltTag {
        }
    }

    // BEGIN: com.oracle.truffle.api.nodes.NodeSnippets.StatementNode#isDebuggerHalt
    class StatementNode extends Node {
        private boolean isDebuggerHalt;

        public void setDebuggerHalt(boolean isDebuggerHalt) {
            this.isDebuggerHalt = isDebuggerHalt;
        }

        @Override
        protected boolean isTaggedWith(Class<?> tag) {
            if (tag == Debugger.HaltTag.class) {
                return isDebuggerHalt;
            }
            return super.isTaggedWith(tag);
        }
    }

    // END: com.oracle.truffle.api.nodes.NodeSnippets.StatementNode#isDebuggerHalt

    static class ExpressionTag {
    }

    // BEGIN: com.oracle.truffle.api.nodes.NodeSnippets.ExpressionNode
    class ExpressionNode extends Node {

        @Override
        protected boolean isTaggedWith(Class<?> tag) {
            if (tag == ExpressionTag.class) {
                return true;
            }
            return super.isTaggedWith(tag);
        }
    }

    // END: com.oracle.truffle.api.nodes.NodeSnippets.ExpressionNode

    public static void notifyInserted() {
        class InstrumentableNode extends Node {
            Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
                return null;
            }
        }

        class MyLanguage extends TruffleLanguage<Object> {
            @Override
            protected Object createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
                return null;
            }

            @Override
            protected Object getLanguageGlobal(Object context) {
                return null;
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return false;
            }
        }

        @SuppressWarnings("unused")
        // @formatter:off
        // BEGIN: com.oracle.truffle.api.nodes.NodeSnippets#notifyInserted
        class MyRootNode extends RootNode {

            protected MyRootNode(MyLanguage language) {
                super(language);
            }

            @Child InstrumentableNode child;

            @Override
            public Object execute(VirtualFrame frame) {
                if (child == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    child = insert(new InstrumentableNode());
                    notifyInserted(child);
                }
                return child.execute(frame);
            }

        }
        // END: com.oracle.truffle.api.nodes.NodeSnippets#notifyInserted
        // @formatter:on
    }
}

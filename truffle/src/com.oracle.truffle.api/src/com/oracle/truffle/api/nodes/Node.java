/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.nodes;

import static com.oracle.truffle.api.nodes.NodeAccessor.INSTRUMENT;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
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
    protected Node() {
        CompilerAsserts.neverPartOfCompilation("do not create a Node from compiled code");
        assert NodeClass.get(getClass()) != null; // ensure NodeClass constructor does not throw
    }

    final NodeClass getNodeClass() {
        return NodeClass.get(getClass());
    }

    final void setParentForClone(Node parent) {
        // for clones we do not need to validate the parent
        // because the object did not yet escape so it is safe to set to null
        this.parent = parent;
    }

    final void setParent(Node parent) {
        assert validateNewParent(parent);
        this.parent = parent;
    }

    /**
     * Validates that any new parent is adopted if the previous parent was adopted.
     */
    private boolean validateNewParent(Node newParent) {
        Node oldParent = this.parent;
        if (oldParent == null) {
            return true;
        }
        RootNode oldRoot = oldParent.getRootNode();
        if (oldRoot != null) {
            if (newParent == null) {
                throw CompilerDirectives.shouldNotReachHere("Old parent was adopted, but new insertion parent be non-null.");
            } else {
                try {
                    return NodeUtil.assertAdopted(newParent);
                } catch (AssertionError e) {
                    throw CompilerDirectives.shouldNotReachHere("Old parent was adopted, but new insertion parent is not adopted. Old root was: " + oldRoot, e);
                }
            }
        }
        return true;
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
     * @deprecated in 24.1 without replacement
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public com.oracle.truffle.api.nodes.NodeCost getCost() {
        NodeInfo info = getClass().getAnnotation(NodeInfo.class);
        if (info != null) {
            return info.cost();
        }
        return com.oracle.truffle.api.nodes.NodeCost.MONOMORPHIC;
    }

    /**
     * Retrieves the segment of guest language source code that is represented by this Node. The
     * default implementation of this method returns <code>null</code>. If your node represents a
     * segment of the source code, override this method and return a eagerly or lazily computed
     * source section value. This method is not designed to be invoked on compiled code paths. May
     * be called on any thread and without a language context being active.
     * <p>
     * Simple example implementation using a simple implementation using a field:
     *
     * {@snippet file = "com/oracle/truffle/api/nodes/Node.java" region =
     * "com.oracle.truffle.api.nodes.NodeSnippets.SimpleNode"}
     * <p>
     * Recommended implementation computing the source section lazily from primitive fields:
     *
     * {@snippet file = "com/oracle/truffle/api/nodes/Node.java" region =
     * "com.oracle.truffle.api.nodes.NodeSnippets.RecommendedNode"}
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
    @TruffleBoundary
    public SourceSection getEncapsulatingSourceSection() {
        Node current = this;
        while (current != null) {
            final SourceSection currentSection = current.getSourceSection();
            if (currentSection != null) {
                return currentSection;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Returns <code>true</code> if this node can be adopted by a parent. This method is intended to
     * be overriden by subclasses. If nodes need to be statically shared that they must not be
     * adoptable, because otherwise the parent reference might cause a memory leak. If a node is not
     * adoptable then then it is guaranteed that the {@link #getParent() parent} pointer remains
     * <code>null</code> at all times, even if the node is tried to be adopted by a parent.
     * <p>
     * If the result of this method is statically known then it is recommended to make the node
     * implement {@link UnadoptableNode} instead.
     * <p>
     * Implementations of {@link #isAdoptable()} are required to fold to a constant result when
     * compiled with a constant receiver.
     *
     * @since 19.0
     */
    public boolean isAdoptable() {
        return !(this instanceof UnadoptableNode);
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
    public final <T extends Node> T[] insert(final T[] newChildren) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (newChildren != null) {
            for (Node newChild : newChildren) {
                adoptHelperInsert(newChild);
                assert checkSameLanguages(newChild) && newChild.checkSameLanguageOfChildren();
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
    public final <T extends Node> T insert(final T newChild) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        adoptHelperInsert(newChild);
        return newChild;
    }

    private void adoptHelperInsert(final Node newChild) {
        if (newChild != null) {
            adoptHelper(newChild);
            assert checkSameLanguages(newChild) && newChild.checkSameLanguageOfChildren();
        }
    }

    /**
     * Notifies the framework about the insertion of one or more nodes during execution. Otherwise,
     * the framework assumes that {@link com.oracle.truffle.api.instrumentation.InstrumentableNode
     * instrumentable} nodes remain unchanged after their root node is first
     * {@link RootNode#execute(com.oracle.truffle.api.frame.VirtualFrame) executed}. Insertions
     * don't need to be notified if it is known that none of the inserted nodes are
     * {@link com.oracle.truffle.api.instrumentation.InstrumentableNode instrumentable}.
     * <p>
     * The provided {@link Node} and its children must be {@link #adoptChildren() adopted} in the
     * AST before invoking this method. The caller must ensure that this method is invoked only once
     * for a given node and its children.
     * <p>
     * {@snippet file = "com/oracle/truffle/api/nodes/Node.java" region =
     * "com.oracle.truffle.api.nodes.NodeSnippets#notifyInserted"}
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
        INSTRUMENT.onNodeInserted(rootNode, node);
    }

    /**
     * @since 0.8 or earlier
     */
    public final void adoptChildren() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        NodeUtil.adoptChildrenHelper(this);
        assert checkSameLanguageOfChildren();
    }

    final void adoptHelper(final Node newChild) {
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        if (newChild.isAdoptable()) {
            Node oldParent = newChild.getParent();
            try {
                newChild.setParent(this);
                NodeUtil.adoptChildrenHelper(newChild);
            } catch (Throwable t) {
                // anything goes wrong adoption (e.g. stackoverflow) restore the old value.
                // important: keep this handle as simple as possible to minimize stack use.
                newChild.parent = oldParent;
                throw t;
            }
        }
    }

    int adoptChildrenAndCount() {
        CompilerAsserts.neverPartOfCompilation();
        int count = 1 + NodeUtil.adoptChildrenAndCountHelper(this);
        assert checkSameLanguageOfChildren();
        return count;
    }

    int adoptAndCountHelper(Node newChild) {
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        int count = 1;
        if (newChild.isAdoptable()) {
            Node oldParent = newChild.getParent();
            try {
                newChild.setParent(this);
                count += NodeUtil.adoptChildrenAndCountHelper(newChild);
            } catch (Throwable t) {
                // anything goes wrong adoption (e.g. stackoverflow) restore the old value.
                // important: keep this handle as simple as possible to minimize stack use.
                newChild.parent = oldParent;
                throw t;
            }
        }
        return count;
    }

    private static final NodeVisitor SAME_LANGUAGE_CHECK_VISITOR = new NodeVisitor() {
        @Override
        public boolean visit(Node node) {
            if (node.isAdoptable()) {
                assert node.parent.checkSameLanguages(node);
                return true;
            } else {
                return false;
            }
        }
    };

    boolean checkSameLanguageOfChildren() {
        NodeUtil.forEachChild(this, SAME_LANGUAGE_CHECK_VISITOR);
        return true;
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
        assert isAdoptable();
        assert newChild != null;
        if (newChild == this) {
            throw new IllegalStateException("The parent of a node can never be the node itself.");
        }
        newChild.setParent(this);
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
        if (newNode.isAdoptable()) {
            newNode.setParent(this.parent);
        }
        if (!NodeUtil.replaceChild(this.parent, this, newNode, true)) {
            this.parent.adoptUnadoptedHelper(newNode);
        }
        reportReplace(this, newNode, reason);
        onReplace(newNode, reason);
    }

    /**
     * Checks if this node can be replaced by another node: tree structure &amp; type.
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
                // Avoid creating a CallTarget here if replace() is called before this RootNode has
                // a CallTarget
                CallTarget target = ((RootNode) node).getCallTargetWithoutInitialization();
                if (target instanceof ReplaceObserver) {
                    consumed = ((ReplaceObserver) target).nodeReplaced(oldNode, newNode, reason);
                }
            }

            if (node instanceof BytecodeOSRNode) {
                NodeAccessor.RUNTIME.onOSRNodeReplaced((BytecodeOSRNode) node, oldNode, newNode, reason);
            }

            if (consumed) {
                break;
            }
            node = node.getParent();
        }
        if (TruffleOptions.TraceRewrites) {
            NodeUtil.traceRewrite(this, newNode, reason);
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
        return new Iterable<>() {
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
        if (CompilerDirectives.isPartialEvaluationConstant(this)) {
            return getRootNodeImpl();
        } else {
            return getRootBoundary();
        }
    }

    @TruffleBoundary
    private RootNode getRootBoundary() {
        return getRootNodeImpl();
    }

    /**
     * Protect against parent cycles and extremely long parent chains.
     */
    static final int PARENT_LIMIT = 100000;

    @ExplodeLoop
    private RootNode getRootNodeImpl() {
        Node node = this;
        Node prev;
        int parentsVisited = 0;
        do {
            if (parentsVisited++ > PARENT_LIMIT) {
                assert false : "getRootNode() did not terminate in " + PARENT_LIMIT + " iterations.";
                return null;
            }
            prev = node;
            node = node.parent;
        } while (node != null);

        if (prev instanceof RootNode) {
            return (RootNode) prev;
        } else {
            return null;
        }
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
    public final void reportPolymorphicSpecialize() {
        CompilerAsserts.neverPartOfCompilation();
        NodeAccessor.RUNTIME.reportPolymorphicSpecialize(this);
    }

    /**
     * Converts this node to a textual representation useful for debugging.
     *
     * @since 0.8 or earlier
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        Class<?> enclosing = getClass().getEnclosingClass();
        while (enclosing != null) {
            sb.insert(0, enclosing.getSimpleName() + ".");
            enclosing = enclosing.getEnclosingClass();
        }
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

    /**
     * @since 0.8 or earlier
     */
    public final void atomic(Runnable closure) {
        Lock lock = getLock();
        try {
            lock.lock();
            closure.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @since 0.8 or earlier
     */
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
        if (root == null) {
            return GIL_LOCK;
        } else {
            return root.getLazyLock();
        }
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

    @ExplodeLoop
    private ExecutableNode getExecutableNode() {
        Node node = this;
        while (node != null) {
            if (node instanceof ExecutableNode) {
                return (ExecutableNode) node;
            }
            node = node.getParent();
        }
        if (node == null) {
            checkAdoptable();
        }
        return null;
    }

    /*
     * Better to call this on a boundary to not pull in more methods.
     */
    @TruffleBoundary
    private void checkAdoptable() {
        if (isAdoptable()) {
            throw new IllegalStateException("Node must be adopted before a reference can be looked up.");
        }
    }

    private static final ReentrantLock GIL_LOCK = new ReentrantLock(false);

    private boolean inAtomicBlock() {
        return ((ReentrantLock) getLock()).isHeldByCurrentThread();
    }

    static Lookup lookup() {
        return MethodHandles.lookup();
    }

}

@SuppressWarnings("unused")
class NodeSnippets {

    // @start region = "com.oracle.truffle.api.nodes.NodeSnippets.SimpleNode"
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
    // @end region = "com.oracle.truffle.api.nodes.NodeSnippets.SimpleNode"

    // @start region = "com.oracle.truffle.api.nodes.NodeSnippets.RecommendedNode"
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
    // @end region = "com.oracle.truffle.api.nodes.NodeSnippets.RecommendedNode"

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
        }

        // @formatter:off // @replace regex='.*' replacement=''
        // @start region="com.oracle.truffle.api.nodes.NodeSnippets#notifyInserted"
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
        // @end region="com.oracle.truffle.api.nodes.NodeSnippets#notifyInserted"
        // @formatter:on // @replace regex='.*' replacement=''
    }
}

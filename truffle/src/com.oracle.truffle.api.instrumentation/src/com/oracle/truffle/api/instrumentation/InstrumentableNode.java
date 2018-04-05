/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Interface implemented by AST {@link Node nodes} that may be <em>instrumentable</em>: an AST
 * location where {@linkplain com.oracle.truffle.api.instrumentation.TruffleInstrument Truffle
 * instruments} are permitted to listen to before and after using execution event listeners.
 * <p>
 * If a node is instrumentable depends on the return value of {@link #isInstrumentable()}. All
 * instrumentable nodes must also extend {@link Node node}. All other member methods of this
 * interface are only allowed to be invoked if {@link #isInstrumentable()} returns <code>true</code>
 * .
 * <p>
 * Every instrumentable node is required to create a wrapper for this instrumentable node in
 * {@link #createWrapper(ProbeNode)}. The instrumentation framework will, when needed during
 * execution, {@link Node#replace(Node) replace} the instrumentable node with a {@link WrapperNode
 * wrapper} and delegate to the original node. After the replacement of an instrumentable node with
 * a wrapper we refer to the original node as an instrumented node.
 * <p>
 * Wrappers can be generated automatically using an annotation processor by annotating the class
 * with @{@link GenerateWrapper}. If an instrumentable node subclass has additional declared methods
 * than its instrumentable base class that are used by other nodes, then a new wrapper should be
 * generated or implemented for the subclass, otherwise the replacement of the wrapper will fail.
 * <p>
 * Instrumentable nodes may return <code>true</code> to indicate that they were tagged by {@link Tag
 * tag}. Tags are used by guest languages to indicate that a {@link Node node} is a member of a
 * certain category of nodes. For example a debugger
 * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument instrument} might require a guest
 * language to tag all nodes as {@link StandardTags.StatementTag statements} that should be
 * considered as such. See {@link #hasTag(Class)} for further details on how to use tags.
 * <p>
 * <b>Example minimal implementation of an instrumentable node:</b>
 *
 * {@link com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.SimpleNode}
 *
 * <p>
 * Example for a typical implementation of an instrumentable node with support for source
 * sections:</b>
 * {@link com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.RecommendedNode}
 * <p>
 *
 * @see #isInstrumentable() to decide whether node is instrumentable.
 * @see #hasTag(Class) Implement hasTag to decide whether an instrumentable node is tagged with a
 *      tag.
 * @see GenerateWrapper Use an annotation processor to generate the wrapper class.
 * @see Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
 * @since 0.33
 */
public interface InstrumentableNode extends NodeInterface {

    /**
     * Returns <code>true</code> if this node is instrumentable. Instrumentable nodes are points
     * where instrumenters can attach execution events. The return values of instrumentable nodes
     * must always be interop capable values.
     * <p>
     * The implementation of this method must ensure that its result is stable after the parent
     * {@link RootNode root node} was wrapped in a {@link CallTarget} using
     * {@link TruffleRuntime#createCallTarget(RootNode)}. The result is stable if the result of
     * calling this method remains always the same.
     * <p>
     * This method might be called in parallel from multiple threads even if the language is single
     * threaded. The method may be invoked without a {@link TruffleLanguage#getContextReference()
     * language context} currently being active.
     *
     * @since 0.33
     */
    boolean isInstrumentable();

    /**
     * Returns a new, never adopted, unshared {@link WrapperNode wrapper} node implementation for
     * this {@link InstrumentableNode instrumentable} node. The returned wrapper implementation must
     * extend the same type that implements {@link InstrumentableNode}.
     * <p>
     * The instrumentation framework will, when needed during execution, {@link Node#replace(Node)
     * replace} the instrumentable node with a {@link WrapperNode wrapper} and delegate to the
     * original node. After the replacement of an instrumentable node with a wrapper we refer to the
     * original node as an instrumented node. Wrappers can be generated automatically using an
     * annotation processor by annotating the class with @{@link GenerateWrapper}. Please note that
     * if an instrumetnable node subclass has additional execute methods then a new wrapper must be
     * generated or implemented. Otherwise the {@link Node#replace(Node) replacement} of the
     * instrumentable node with the wrapper will fail if the subtype is used as static type in nodes
     * {@link Child children}.
     * <p>
     * A wrapper forwards the following events concerning the delegate to the given {@link ProbeNode
     * probe} for propagation through the instrumentation framework, e.g. to
     * {@linkplain ExecutionEventListener event listeners} bound to this guest language program
     * location:
     * <ul>
     * <li>{@linkplain ProbeNode#onEnter(com.oracle.truffle.api.frame.VirtualFrame) onEnter(Frame)}:
     * an <em>execute</em> method on the delegate is ready to be called;</li>
     * <li>{@linkplain ProbeNode#onReturnValue(com.oracle.truffle.api.frame.VirtualFrame, Object)
     * onReturnValue(Frame,Object)}: an <em>execute</em> method on the delegate has just returned a
     * (possibly <code>null</code>) value;</li>
     * <li>{@linkplain ProbeNode#onReturnExceptionalOrUnwind(VirtualFrame, Throwable, boolean)
     * onReturnExceptionalOrUnwind(Frame,Throwable, boolean)}: an <em>execute</em> method on the
     * delegate has just thrown an exception.</li>
     * </ul>
     * <p>
     * This method is always invoked on an interpreter thread. The method is always invoked with a
     * {@link TruffleLanguage#getContextReference() language context} currently being active.
     *
     * @param probe the {@link ProbeNode probe node} to be adopted and sent execution events by the
     *            wrapper
     * @return a {@link WrapperNode wrapper} implementation
     * @since 0.33
     */
    WrapperNode createWrapper(ProbeNode probe);

    /**
     * Returns <code>true</code> if this node should be considered tagged by a given tag else
     * <code>false</code>. In order for a Truffle language to support a particular tag, the tag must
     * also be marked as {@link ProvidedTags provided} by the language.
     * <p>
     * Tags are used by guest languages to indicate that a {@link Node node} is a member of a
     * certain category of nodes. For example a debugger {@link TruffleInstrument instrument} might
     * require a guest language to tag all nodes as statements that should be considered as such.
     * <p>
     * The node implementor may decide how to implement tagging for nodes. The simplest way to
     * implement tagging using Java types is by overriding the {@link #hasTag(Class)} method. This
     * example shows how to tag a node subclass and all its subclasses as statement:
     *
     * {@link com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.StatementNode}
     *
     * <p>
     * Often it is impossible to just rely on the node's Java type to implement tagging. This
     * example shows how to use local state to implement tagging for a node.
     *
     * {@link com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.HaltNode}
     *
     * <p>
     * The implementation of hasTag method must ensure that its result is stable after the parent
     * {@link RootNode root node} was wrapped in a {@link CallTarget} using
     * {@link TruffleRuntime#createCallTarget(RootNode)}. The result is stable if the result of
     * calling this method for a particular tag remains always the same.
     * <p>
     * This method might be called in parallel from multiple threads even if the language is single
     * threaded. The method may be invoked without a {@link TruffleLanguage#getContextReference()
     * language context} currently being active.
     *
     * @param tag the class {@link com.oracle.truffle.api.instrumentation.ProvidedTags provided} by
     *            the {@link TruffleLanguage language}
     * @return <code>true</code> if the node should be considered tagged by a tag else
     *         <code>false</code>.
     * @since 0.33
     */
    default boolean hasTag(Class<? extends Tag> tag) {
        return false;
    }

    /**
     * Returns an interop capable object that contains all keys and values of attributes associated
     * with this node. The returned object must return <code>true</code> in response to the
     * {@link com.oracle.truffle.api.interop.Message#HAS_KEYS has keys} message. If
     * <code>null</code> is returned then an empty tag object without any readable keys will be
     * assumed. Multiple calls to {@link #getNodeObject()} for a particular node may return the same
     * or objects with different identity. The returned object must not support any write operation.
     * The returned object must not support execution or instantiation and must not have a size.
     * <p>
     * For performance reasons it is not recommended to eagerly collect all properties of the node
     * object when {@link #getNodeObject()} is invoked. Instead, the language should lazily compute
     * them when they are read. If the node object contains dynamic properties, that change during
     * the execution of the AST, then the node must return an updated value for each key when it is
     * read repeatedly. In other words the node object must always represent the current state of
     * this AST {@link Node node}. The implementer should not cache the node instance in the AST.
     * The instrumentation framework will take care of caching node object instances when they are
     * requested by tools.
     * <p>
     * <b>Compatibility:</b> In addition to the expected keys by the tag specification, the language
     * implementation may provide any set of additional keys and values. Tools might depend on these
     * language specific tags and might break if keys or values are changed without notice.
     * <p>
     * For a memory efficient implementation the language might make the instrumentable {@link Node}
     * a TruffleObject and return this instance.
     * <p>
     * This method might be called in parallel from multiple threads even if the language is single
     * threaded. The method may be invoked without a {@link TruffleLanguage#getContextReference()
     * language context} currently being active. The {@link Node#getLock() AST lock} is held while
     * {@link #getNodeObject()} object is invoked. There is no lock held when the object is read.
     *
     * @return the node object as TruffleObject or <code>null</code> if no node object properties
     *         are available for this instrumented node
     * @since 0.33
     */
    default Object getNodeObject() {
        return null;
    }

    /**
     * Removes optimizations performed in this AST node to restore the syntactic AST structure.
     * Guest languages may decide to group multiple nodes together into a single node. This is
     * useful to reduce the memory consumed by the AST representation and it can also improve the
     * execution performance when interpreting the AST. Performing such optimizations often modify
     * the syntactic AST structure, leading to invalid execution events reported to the
     * instrumentation framework. Implementing this method allows the instrumented node to restore
     * the syntactic AST structure when needed. It provides a list of tags that were requested by
     * all current execution event bindings to allow the language to do the materialization
     * selectively for instrumentable nodes with certain tags only.
     * <p>
     * The returned instrumentable nodes must return themselves when this method is called on them
     * with the same tags. Materialized nodes should not be re-materialized again. Instrumentation
     * relies on the stability of materialized nodes. Use {@link Node#notifyInserted(Node)} when you
     * need to change the structure of instrumentable nodes.
     * <p>
     * The AST lock is acquired while this method is invoked. Therefore it is not allowed to run
     * guest language code while this method is invoked. This method might be called in parallel
     * from multiple threads even if the language is single threaded. The method may be invoked
     * without a {@link TruffleLanguage#getContextReference() language context} currently being
     * active.
     * <p>
     * In the example below, we show how the <code>IncrementNode</code> with a
     * <code>ConstantNode</code> child is optimized into a <code>ConstantIncrementNode</code> and
     * how it can implement <code>materializeSyntaxNodes</code> to restore the syntactic structure
     * of the AST:
     * <p>
     * {@link com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.ExpressionNode}
     *
     * @param materializedTags a set of tags that requested to be materialized
     * @since 0.33
     */
    default InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
        return this;
    }

    /**
     * Find the nearest {@link Node node} to the given source character index according to the guest
     * language control flow, that is tagged with some of the given tags. The source character index
     * is in this node's source. The nearest node will preferably be in the same block/function as
     * the character index. This node acts as a context node - either a node containing the
     * character index if such node exists, or node following the character index if exists, or node
     * preceding the character index otherwise.
     * <p>
     * Return an instrumentable node that is tagged with some of the tags and containing the
     * character index, if such exists and there is not a more suitable sibling node inside the
     * container source section. Return the next sibling tagged node otherwise, or the previous one
     * when the next one does not exist.
     * <p>
     * <u>Use Case</u><br>
     * The current use-case of this method is a relocation of breakpoint position, for instance.
     * When a user submits a breakpoint at the source character index, a nearest logical
     * instrumentable node that has suitable tags needs to be found to move the breakpoint
     * accordingly.
     * <p>
     * <u>Default Implementation</u><br>
     * This method has a default implementation, which assumes that the materialized Truffle
     * {@link Node} hierarchy corresponds with the logical guest language AST structure. If this is
     * not the case for a particular guest language, this method needs to be implemented, possibly
     * with the help of language specific AST node classes.
     * <p>
     * The default algorithm is following:<br>
     * <ol>
     * <li>If the character index is smaller than the start index of this node's source section,
     * return the first tagged child of this node.</li>
     * <li>If the character index is larger than the end index of this node's source section, return
     * the last tagged child of this node.</li>
     * <li>Otherwise, this node's source section contains the character index. Use following steps
     * to find the nearest tagged node in this node's hierarchy:
     * <ol type="a">
     * <li>Find the nearest tagged parent node, remember it's existence as an
     * <code>outerCandidate</code> boolean.</li>
     * <li>Traverse the node children in declaration order (AST breadth-first order). For every
     * child do:
     * <ol>
     * <li>When the child is not instrumentable, include its children into the traversal.</li>
     * <li>When the child does not have a source section assigned, ignore it.</li>
     * <li>When the <code>sourceCharIndex</code> is inside the child's source section, find if it's
     * tagged with one of the tags (store as <code>isTagged</code>) and repeat recursively from
     * <b>3.b.</b> using this child as the node and passing the current
     * <code>outerCandidate || isTagged</code> as the new <code>outerCandidate</code> value.</li>
     * <li>When the child is above the character index, remember the lowest such child (store in
     * <code>lowestHigherNode</code> and <code>lowestHigherTaggedNode</code> if the child is
     * tagged).</li>
     * <li>When the child is below the character index, remember the highest such child (store in
     * <code>highestLowerNode</code> and <code>highestLowerTaggedNode</code> if the child is
     * tagged).</li>
     * </ol>
     * </li>
     * <li>If a tagged child node was found in <b>3.b</b> with source section matching the
     * <code>sourceCharIndex</code>, return it.</li>
     * <li>Otherwise, we check the lowest/highest nodes:
     * <ol>
     * <li>Prefer the node after the character index, unless we're in a nested recursive call, in
     * which case prefer the first instrumentable node in the current scope. The motivation is to
     * prefer the next tagged code location, but when the next node is not tagged, we search for the
     * first tagged child in the next node.</li>
     * <li>Create a <code>primaryNode</code> alias to the <code>lowestHigherNode</code> if not in a
     * recursive call and to <code>highestLowerNode</code> otherwise.</li>
     * <li>Create a <code>secondaryNode</code> alias to the <code>highestLowerNode</code> if not in
     * a recursive call and to <code>lowestHigherNode</code> otherwise.</li>
     * <li>Create an analogous aliases for highest/lowest tagged nodes.</li>
     * <li>If <code>primaryNode</code> is tagged, return it.</li>
     * <li>Otherwise, if <code>secondaryNode</code> is tagged, return it.</li>
     * <li>If <code>outerCandidate</code> is false, repeat <b>3.b.</b> on <code>primaryNode</code>
     * and if it provides a tagged child, return it, otherwise return <code>primaryTaggedNode</code>
     * if it's not <code>null</code>.</li>
     * <li>Otherwise, if <code>outerCandidate</code> is false, repeat <b>3.b.</b> on
     * <code>secondaryNode</code> and if it provides a tagged child, return it, otherwise return
     * <code>secondaryTaggedNode</code> if it's not <code>null</code>.</li>
     * <li>When nothing was found in the steps above, return <code>null</code>.</li>
     * </ol>
     * </li>
     * <li>If <b>d.</b> didn't provide a tagged node, apply this algorithm recursively to a parent
     * of this node, if exists. If you encounter the nearest tagged parent node found in <b>3.a</b>,
     * return it. Otherwise, return a tagged child found in the steps above, if any.</li>
     * </ol>
     * </li>
     * </ol>
     *
     * @param sourceCharIndex the 0-based character index in this node's source, to find the nearest
     *            tagged node from
     * @param tags a set of tags, the nearest node needs to be tagged with at least one tag from
     *            this set
     * @return the nearest instrumentable node according to the execution flow and tagged with some
     *         of the tags, or <code>null</code> when none was found
     * @since 0.33
     */
    default Node findNearestNodeAt(int sourceCharIndex, Set<Class<? extends Tag>> tags) {
        return DefaultNearestNodeSearch.findNearestNodeAt(sourceCharIndex, (Node) this, tags);
    }

    /**
     * Nodes that the instrumentation framework inserts into guest language ASTs (between
     * {@link Instrumentable} guest language nodes and their parents) for the purpose of interposing
     * on execution events and reporting them via the instrumentation framework.
     *
     * @see #createWrapper(Node, ProbeNode)
     * @since 0.33
     */
    @SuppressWarnings("deprecation")
    public interface WrapperNode extends NodeInterface, InstrumentableFactory.WrapperNode {

        /**
         * The {@link InstrumentableNode instrumentable} guest language node, adopted as a child,
         * whose execution events the wrapper reports to the instrumentation framework.
         * <p>
         * This method might be called in parallel from multiple threads. The method may be invoked
         * without a {@link TruffleLanguage#getContextReference() language context} currently being
         * active.
         *
         * @since 0.33
         */
        Node getDelegateNode();

        /**
         * A child of the wrapper, through which the wrapper reports execution events related to the
         * guest language <em>delegate</em> node.
         * <p>
         * This method might be called in parallel from multiple threads. The method may be invoked
         * without a {@link TruffleLanguage#getContextReference() language context} currently being
         * active.
         *
         * @since 0.33
         */
        ProbeNode getProbeNode();

    }

}

class InstrumentableNodeSnippets {

    static class SimpleNodeWrapper implements WrapperNode {

        @SuppressWarnings("unused")
        SimpleNodeWrapper(SimpleNode delegate, ProbeNode probe) {
        }

        public Node getDelegateNode() {
            return null;
        }

        public ProbeNode getProbeNode() {
            return null;
        }
    }

    // BEGIN: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.SimpleNode
    @GenerateWrapper
    abstract class SimpleNode extends Node implements InstrumentableNode {

        public abstract Object execute(VirtualFrame frame);

        public final boolean isInstrumentable() {
            return true;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            // ASTNodeWrapper is generated by @GenerateWrapper
            return new SimpleNodeWrapper(this, probe);
        }
    }
    // END: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.SimpleNode

    static class RecommendedNodeWrapper implements WrapperNode {

        @SuppressWarnings("unused")
        RecommendedNodeWrapper(RecommendedNode delegate, ProbeNode probe) {
        }

        public Node getDelegateNode() {
            return null;
        }

        public ProbeNode getProbeNode() {
            return null;
        }
    }

    // BEGIN: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.RecommendedNode
    @GenerateWrapper
    abstract class RecommendedNode extends Node implements InstrumentableNode {

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

        public final boolean isInstrumentable() {
            // all AST nodes with source are instrumentable
            return sourceCharIndex != NO_SOURCE;
        }

        @Override
        @TruffleBoundary
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

        public WrapperNode createWrapper(ProbeNode probe) {
            // ASTNodeWrapper is generated by @GenerateWrapper
            return new RecommendedNodeWrapper(this, probe);
        }
    }
    // END: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.RecommendedNode

    abstract static class StatementNodeWrapper implements WrapperNode {

        @SuppressWarnings("unused")
        static StatementNodeWrapper create(StatementNode statementNode, ProbeNode probe) {
            return null;
        }
    }

    // BEGIN: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.StatementNode
    @GenerateWrapper
    abstract class StatementNode extends SimpleNode implements InstrumentableNode {

        @Override
        public final Object execute(VirtualFrame frame) {
            executeVoid(frame);
            return null;
        }

        public abstract void executeVoid(VirtualFrame frame);

        @Override
        public final WrapperNode createWrapper(ProbeNode probe) {
            return StatementNodeWrapper.create(this, probe);
        }

        public boolean hasTag(Class<? extends Tag> tag) {
            if (tag == StandardTags.StatementTag.class) {
                return true;
            }
            return false;
        }
    }
    // END: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.StatementNode

    private static final class Debugger {
        static class HaltTag extends Tag {
        }
    }

    @SuppressWarnings("unused")
    class HaltNodeWrapper implements WrapperNode {
        HaltNodeWrapper(Node node, ProbeNode probe) {

        }

        public Node getDelegateNode() {
            return null;
        }

        public ProbeNode getProbeNode() {
            return null;
        }
    }

    // BEGIN: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.HaltNode
    @GenerateWrapper
    class HaltNode extends Node implements InstrumentableNode {
        private boolean isDebuggerHalt;

        public void setDebuggerHalt(boolean isDebuggerHalt) {
            this.isDebuggerHalt = isDebuggerHalt;
        }

        public boolean isInstrumentable() {
            return true;
        }

        public boolean hasTag(Class<? extends Tag> tag) {
            if (tag == Debugger.HaltTag.class) {
                return isDebuggerHalt;
            }
            return false;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            return new HaltNodeWrapper(this, probe);
        }

    }
    // END: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.HaltNode

    @SuppressWarnings("unused")
    class ExpressionNodeWrapper implements WrapperNode {
        ExpressionNodeWrapper(Node node, ProbeNode probe) {
        }

        public Node getDelegateNode() {
            return null;
        }

        public ProbeNode getProbeNode() {
            return null;
        }
    }

    // BEGIN: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.ExpressionNode
    @GenerateWrapper
    abstract class ExpressionNode extends Node implements InstrumentableNode {
        abstract int execute(VirtualFrame frame);

        public boolean isInstrumentable() {
            return true;
        }

        public boolean hasTag(Class<? extends Tag> tag) {
            return tag == StandardTags.ExpressionTag.class;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            return new ExpressionNodeWrapper(this, probe);
        }
    }

    class ConstantNode extends ExpressionNode {

        private final int constant;

        ConstantNode(int constant) {
            this.constant = constant;
        }

        @Override
        int execute(VirtualFrame frame) {
            return constant;
        }

    }

    // node with constant folded operation
    class ConstantIncrementNode extends ExpressionNode {
        final int constantIncremented;

        ConstantIncrementNode(int constant) {
            this.constantIncremented = constant + 1;
        }

        // desguar to restore syntactic structure of the AST
        public InstrumentableNode materializeInstrumentableNodes(
                        Set<Class<? extends Tag>> tags) {
            if (tags.contains(StandardTags.ExpressionTag.class)) {
                return new IncrementNode(
                                new ConstantNode(constantIncremented - 1));
            }
            return this;
        }

        @Override
        int execute(VirtualFrame frame) {
            return constantIncremented;
        }

    }

    // node with full semantics of the node.
    class IncrementNode extends ExpressionNode {
        @Child ExpressionNode child;

        IncrementNode(ExpressionNode child) {
            this.child = child;
        }

        @Override
        int execute(VirtualFrame frame) {
            return child.execute(frame) + 1;
        }
    }
    // END: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.ExpressionNode
}

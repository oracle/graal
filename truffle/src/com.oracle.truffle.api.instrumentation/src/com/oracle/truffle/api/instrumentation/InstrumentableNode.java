package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
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
 * Every instrumentable node is required to return a wrapper for this instrumentable node in
 * {@link #createWrapper(ProbeNode)}. The instrumentation framework will, when needed during
 * execution, {@link Node#replace(Node) replace} the instrumentable node with a {@link WrapperNode
 * wrapper} and delegate to the original node. After the replacement of an instrumentable node with
 * a wrapper we refer to the original node as an instrumented node. Wrappers can be generated
 * automatically using an annotation processor by annotating the class with @{@link GenerateWrapper}
 * . If an instrumentable node subclass has additional declared execute methods then a new wrapper
 * must be generated or implemented. Otherwise the {@link Node#replace(Node) replacement} of the
 * instrumentable node with the wrapper will fail if the subtype is used in a node {@link Child
 * child}.
 * <p>
 * Instrumentable nodes may return <code>true</code> to indicate that they were tagged by {@link Tag
 * tag}. Tags are used by guest languages to indicate that a {@link Node node} is a member of a
 * certain category of nodes. For example a debugger
 * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument instrument} might require a guest
 * language to tag all nodes as {@link StandardTags.StatementTag statements} that should be
 * considered as such. See {@link #hasTag(Class)} for further details on how to use implement tags.
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
 *
 * @see #isInstrumentable()
 * @see #hasTag(Class)
 * @see #createWrapper(ProbeNode)
 * @see GenerateWrapper
 * @see Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
 * @since 0.32
 */
public interface InstrumentableNode extends NodeInterface {

    /**
     * Returns whether this node is instrumentable.
     * <p>
     * The implementation of this method must ensure that its result is stable after the parent
     * {@link RootNode root node} was wrapped in a {@link CallTarget} using
     * {@link TruffleRuntime#createCallTarget(RootNode)}. The result is stable if the result of
     * calling this method remains always the same.
     *
     * @return
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
     * <li>
     * {@linkplain ProbeNode#onReturnExceptional(com.oracle.truffle.api.frame.VirtualFrame, Throwable)
     * onReturnExceptional(Frame,Throwable)}: an <em>execute</em> method on the delegate has just
     * thrown an exception.</li>
     * </ul>
     * </p>
     *
     * @param probe the {@link ProbeNode probe node} to be adopted and sent execution events by the
     *            wrapper
     * @return a {@link WrapperNode wrapper} implementation
     * @since 0.32
     */
    WrapperNode createWrapper(ProbeNode probe);

    /**
     * Returns <code>true</code> if this node should be considered tagged by a given tag else
     * <code>false</code>. In order for a Truffle language to support a particular tag, the tag must
     * also be marked as {@link ProvidedTags provided} by the language.
     * <p>
     * Tags are used by guest languages to indicate that a {@link Node node} is a member of a
     * certain category of nodes. For example a debugger
     * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument instrument} might require a
     * guest language to tag all nodes as halt locations that should be considered as such.
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
     * {@link com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.StatementNode#isDebuggerHalt}
     *
     * <p>
     * The implementation of hasTag method must ensure that its result is stable after the parent
     * {@link RootNode root node} was wrapped in a {@link CallTarget} using
     * {@link TruffleRuntime#createCallTarget(RootNode)}. The result is stable if the result of
     * calling this method for a particular tag remains always the same.
     *
     * @param tag the class {@link com.oracle.truffle.api.instrumentation.ProvidedTags provided} by
     *            the {@link TruffleLanguage language}
     * @return <code>true</code> if the node should be considered tagged by a tag else
     *         <code>false</code>.
     * @since 0.32
     */
    default boolean hasTag(Class<? extends Tag> tag) {
        return false;
    }

    @SuppressWarnings("unused")
    default Object getTagAttribute(Tag.Attribute<?> attribute) {
        return null;
    }

    /**
     * Nodes that the instrumentation framework inserts into guest language ASTs (between
     * {@link Instrumentable} guest language nodes and their parents) for the purpose of interposing
     * on execution events and reporting them via the instrumentation framework.
     *
     * @see #createWrapper(Node, ProbeNode)
     * @since 0.32
     */
    @SuppressWarnings("deprecation")
    public interface WrapperNode extends NodeInterface, InstrumentableFactory.WrapperNode {

        /**
         * The {@link InstrumentableNode instrumentable} guest language node, adopted as a child,
         * whose execution events the wrapper reports to the instrumentation framework.
         *
         * @since 0.32
         */
        Node getDelegateNode();

        /**
         * A child of the wrapper, through which the wrapper reports execution events related to the
         * guest language <em>delegate</em> node.
         *
         * @since 0.32
         */
        ProbeNode getProbeNode();

    }

}

class InstrumentableNodeSnippets {

    static abstract class SimpleNodeWrapper implements WrapperNode {

        @SuppressWarnings("unused")
        static SimpleNodeWrapper create(SimpleNode delegate, ProbeNode probe) {
            return null;
        }
    }

    static
    // BEGIN: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.SimpleNode
    @GenerateWrapper abstract class SimpleNode extends Node implements InstrumentableNode {

        public abstract Object execute(VirtualFrame frame);

        public final boolean isInstrumentable() {
            return true;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            // ASTNodeWrapper is generated by @GenerateWrapper
            return SimpleNodeWrapper.create(this, probe);
        }
    }
    // END: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.SimpleNode

    static abstract class RecommendedNodeWrapper implements WrapperNode {

        @SuppressWarnings("unused")
        static SimpleNodeWrapper create(RecommendedNode delegate, ProbeNode probe) {
            return null;
        }
    }

    static
    // BEGIN: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.RecommendedNode
    @GenerateWrapper abstract class RecommendedNode extends Node implements InstrumentableNode {

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
            return RecommendedNodeWrapper.create(this, probe);
        }
    }
    // END: com.oracle.truffle.api.instrumentation.InstrumentableNodeSnippets.RecommendedNode

    static abstract class StatementNodeWrapper implements WrapperNode {

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

    // BEGIN: com.oracle.truffle.api.nodes.NodeSnippets.StatementNode#isDebuggerHalt
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
    // END: com.oracle.truffle.api.nodes.NodeSnippets.StatementNode#isDebuggerHalt

}

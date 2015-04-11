/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.InstrumentationNode.TruffleEvents;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

// TODO (mlvdv) migrate some of this to external documentation.
// TODO (mlvdv) move all this to a factory implemented in .impl (together with Probe),
// then break out some of the nested classes into package privates.
/**
 * A dynamically added/removed binding between a {@link Probe}, which provides notification of
 * <em>execution events</em> taking place at a {@link Node} in a Guest Language (GL) Truffle AST,
 * and a <em>listener</em>, which consumes notifications on behalf of an external tool. There are at
 * present two kinds of listeners that be used:
 * <ol>
 * <li>{@link SimpleInstrumentListener} is the simplest and is intended for tools that require no access
 * to the <em>internal execution state</em> of the Truffle execution, only that execution has passed
 * through a particular location in the program. Information about that location is made available
 * via the {@link Probe} argument in notification methods, including the {@linkplain SourceSection
 * source location} of the node and any {@linkplain SyntaxTag tags} that have been applied to the
 * node.</li>
 * <li>{@link ASTInstrumentListener} reports the same events and {@link Probe} argument, but
 * additionally provides access to the execution state via the explicit {@link Node} and
 * {@link Frame} at the current execution site.</li>
 * </ol>
 * <p>
 * <h4>Summary: How to "instrument" an AST location:</h4>
 * <p>
 * <ol>
 * <li>Create an implementation of a <em>listener</em> interface.</li>
 * <li>Create an Instrument via factory methods
 * {@link Instrument#create(SimpleInstrumentListener, String)} or
 * {@link Instrument#create(ASTInstrumentListener, String)}.</li>
 * <li>"Attach" the Instrument to a Probe via {@link Probe#attach(Instrument)}, at which point event
 * notifications begin to arrive at the listener.</li>
 * <li>When no longer needed, "detach" the Instrument via {@link ASTInstrument#dispose()}, at which
 * point event notifications to the listener cease, and the Instrument becomes unusable.</li>
 * </ol>
 * <p>
 * <h4>Options for creating listeners:</h4>
 * <p>
 * <ol>
 * <li>Implement one of the <em>listener interfaces</em>: {@link SimpleInstrumentListener} or
 * {@link ASTInstrumentListener} . Their event handling methods account for both the entry into an
 * AST node (about to call) and three possible kinds of <em>execution return</em> from an AST node.</li>
 * <li>Extend one of the <em>helper implementations</em>: {@link DefaultSimpleInstrumentListener} or
 * {@link DefaultASTInstrumentListener}. These provide no-op implementation of every listener
 * method, so only the methods of interest need to be overridden.</li>
 * </ol>
 * <p>
 * <h4>General guidelines for {@link ASTInstrumentListener} implementation:</h4>
 * <p>
 * Unlike the listener interface {@link SimpleInstrumentListener}, which isolates implementations
 * completely from Truffle internals (and is thus <em>Truffle-safe</em>), implementations of
 * {@link ASTInstrumentListener} can interact directly with (and potentially affect) Truffle
 * execution in general and Truffle optimization in particular. For example, it is possible to
 * implement a debugger with this interface.
 * </p>
 * <p>
 * As a consequence, implementations of {@link ASTInstrumentListener} effectively become part of the
 * Truffle execution and must be coded according to general guidelines for Truffle implementations.
 * For example:
 * <ul>
 * <li>Do not store {@link Frame} or {@link Node} references in fields.</li>
 * <li>Prefer {@code final} fields and (where performance is important) short methods.</li>
 * <li>If needed, pass along the {@link VirtualFrame} reference from an event notification as far as
 * possible through code that is expected to be inlined, since this incurs no runtime overhead. When
 * access to frame data is needed, substitute a more expensive {@linkplain Frame#materialize()
 * materialized} representation of the frame.</li>
 * <li>If a listener calls back to its tool during event handling, and if performance is an issue,
 * then this should be through a final "callback" field in the instrument, and the called methods
 * should be minimal.</li>
 * <li>On the other hand, implementations should prevent Truffle from inlining beyond a reasonable
 * point with the method annotation {@link TruffleBoundary}.</li>
 * <li>The implicit "outer" pointer in a non-static inner class is a useful (and
 * Truffle-optimizable) way to implement callbacks to owner tools.</li>
 * <li>Primitive-valued return events are boxed for event notification, but Truffle will eliminate
 * the boxing if they are cast back to their primitive values quickly (in particular before crossing
 * any {@link TruffleBoundary} annotations).
 * </ul>
 * <p>
 * <h4>Allowing for AST cloning:</h4>
 * <p>
 * Truffle routinely <em>clones</em> ASTs, which has consequences for implementations of
 * {@link ASTInstrumentListener} (but not for implementations of {@link SimpleInstrumentListener}, from
 * which cloning is hidden).
 * <ul>
 * <li>Even though a {@link Probe} is uniquely associated with a particular location in the
 * executing Guest Language program, execution events at that location will in general be
 * implemented by different {@link Node} instances, i.e. <em>clones</em> of the originally probed
 * node.</li>
 * <li>Because of <em>cloning</em> the {@link Node} supplied with notifications to a particular
 * listener will vary, but because they all represent the same GL program location the events should
 * be treated as equivalent for most purposes.</li>
 * </ul>
 * <p>
 * <h4>Access to execution state via {@link ASTInstrumentListener}:</h4>
 * <p>
 * <ul>
 * <li>Notification arguments provide primary access to the GL program's execution states:
 * <ul>
 * <li>{@link Node}: the concrete node (in one of the AST's clones) from which the event originated.
 * </li>
 * <li>{@link VirtualFrame}: the current execution frame.
 * </ul>
 * <li>Truffle global information is available, for example the execution
 * {@linkplain TruffleRuntime#iterateFrames(FrameInstanceVisitor) stack}.</li>
 * <li>Additional API access to execution state may be added in the future.</li>
 * </ul>
 * <p>
 * <h4>Activating and deactivating Instruments:</h4>
 * <p>
 * Instruments are <em>single-use</em>:
 * <ul>
 * <li>An instrument becomes active only when <em>attached</em> to a Probe via
 * {@link Probe#attach(Instrument)}, and it may only be attached to a single Probe. It is a runtime
 * error to attempt attaching a previously attached instrument.</li>
 * <li>Attaching an instrument modifies every existing clone of the AST to which it is being
 * attached, which can trigger deoptimization.</li>
 * <li>The method {@link Instrument#dispose()} makes an instrument inactive by removing it from the
 * Probe to which it was attached and rendering it permanently inert.</li>
 * <li>Disposal removes the implementation of an instrument from all ASTs to which it was attached,
 * which can trigger deoptimization.</li>
 * </ul>
 * <p>
 * <h4>Sharing listeners:</h4>
 * <p>
 * Although an Instrument may only be attached to a single Probe, a listener can be shared among
 * multiple Instruments. This can be useful for observing events that might happen at different
 * locations in a single AST, for example all assignments to a particular variable. In this case a
 * new Instrument would be created and attached at each assignment node, but all the Instruments
 * would be created with the same listener.
 * <p>
 * <strong>Disclaimer:</strong> experimental; under development.
 *
 * @see Probe
 * @see TruffleEvents
 */
public abstract class Instrument {

    /**
     * Creates an instrument that will route execution events to a listener.
     *
     * @param listener a minimal listener for event generated by the instrument.
     * @param instrumentInfo optional description of the instrument's role, useful for debugging.
     * @return a new instrument, ready for attachment at a probe
     */
    public static Instrument create(SimpleInstrumentListener listener, String instrumentInfo) {
        return new SimpleInstrument(listener, instrumentInfo);
    }

    /**
     * Creates an instrument that will route execution events to a listener, along with access to
     * internal execution state.
     *
     * @param astListener a listener for event generated by the instrument that provides access to
     *            internal execution state
     * @param instrumentInfo optional description of the instrument's role, useful for debugging.
     * @return a new instrument, ready for attachment at a probe
     */
    public static Instrument create(ASTInstrumentListener astListener, String instrumentInfo) {
        return new ASTInstrument(astListener, instrumentInfo);
    }

    /**
     * Creates an instrument that, when executed the first time in any particular AST location,
     * invites the tool to provide an AST fragment for attachment/adoption into the running AST.
     *
     * @param toolNodeListener a listener for the tool that can request an AST fragment
     * @param instrumentInfo instrumentInfo optional description of the instrument's role, useful
     *            for debugging.
     * @return a new instrument, ready for attachment at a probe.
     */
    public static Instrument create(ToolNodeInstrumentListener toolNodeListener, String instrumentInfo) {
        return new ToolNodeInstrument(toolNodeListener, instrumentInfo);
    }

    // TODO (mlvdv) experimental
    /**
     * For implementation testing.
     */
    public static Instrument create(TruffleOptListener listener) {
        return new TruffleOptInstrument(listener, null);
    }

    /**
     * Has this instrument been disposed? stays true once set.
     */
    private boolean isDisposed = false;

    protected Probe probe = null;

    /**
     * Optional documentation, mainly for debugging.
     */
    private final String instrumentInfo;

    private Instrument(String instrumentInfo) {
        this.instrumentInfo = instrumentInfo;
    }

    /**
     * Removes this instrument (and any clones) from the probe to which it attached and renders the
     * instrument inert.
     *
     * @throws IllegalStateException if this instrument has already been disposed
     */
    public void dispose() throws IllegalStateException {
        if (isDisposed) {
            throw new IllegalStateException("Attempt to dispose an already disposed Instrumennt");
        }
        if (probe != null) {
            // It's attached
            probe.disposeInstrument(this);
        }
        this.isDisposed = true;
    }

    Probe getProbe() {
        return probe;
    }

    void setAttachedTo(Probe probe) {
        this.probe = probe;
    }

    /**
     * Has this instrument been disposed and rendered unusable?
     */
    boolean isDisposed() {
        return isDisposed;
    }

    abstract AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode);

    /**
     * An instrument that propagates events to an instance of {@link SimpleInstrumentListener}.
     */
    private static final class SimpleInstrument extends Instrument {

        /**
         * Tool-supplied listener for events.
         */
        private final SimpleInstrumentListener simpleListener;

        private SimpleInstrument(SimpleInstrumentListener simpleListener, String instrumentInfo) {
            super(instrumentInfo);
            this.simpleListener = simpleListener;
        }

        @Override
        AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode) {
            return new SimpleInstrumentNode(nextNode);
        }

        @Override
        AbstractInstrumentNode removeFromChain(AbstractInstrumentNode instrumentNode) {
            boolean found = false;
            if (instrumentNode != null) {
                if (instrumentNode.getInstrument() == this) {
                    // Found the match at the head of the chain
                    return instrumentNode.nextInstrumentNode;
                }
                // Match not at the head of the chain; remove it.
                found = instrumentNode.removeFromChain(SimpleInstrument.this);
            }
            if (!found) {
                throw new IllegalStateException("Couldn't find instrument node to remove: " + this);
            }
            return instrumentNode;
        }

        @NodeInfo(cost = NodeCost.NONE)
        private final class SimpleInstrumentNode extends AbstractInstrumentNode {

            private SimpleInstrumentNode(AbstractInstrumentNode nextNode) {
                super(nextNode);
            }

            public void enter(Node node, VirtualFrame vFrame) {
                SimpleInstrument.this.simpleListener.enter(SimpleInstrument.this.probe);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, vFrame);
                }
            }

            public void returnVoid(Node node, VirtualFrame vFrame) {
                SimpleInstrument.this.simpleListener.returnVoid(SimpleInstrument.this.probe);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, vFrame);
                }
            }

            public void returnValue(Node node, VirtualFrame vFrame, Object result) {
                SimpleInstrument.this.simpleListener.returnValue(SimpleInstrument.this.probe, result);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, vFrame, result);
                }
            }

            public void returnExceptional(Node node, VirtualFrame vFrame, Exception exception) {
                SimpleInstrument.this.simpleListener.returnExceptional(SimpleInstrument.this.probe, exception);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, vFrame, exception);
                }
            }

            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : simpleListener.getClass().getSimpleName();
            }
        }
    }

    /**
     * Removes this instrument from an instrument chain.
     */
    abstract AbstractInstrumentNode removeFromChain(AbstractInstrumentNode instrumentNode);

    /**
     * An instrument that propagates events to an instance of {@link ASTInstrumentListener}.
     */
    private static final class ASTInstrument extends Instrument {

        /**
         * Tool-supplied listener for AST events.
         */
        private final ASTInstrumentListener astListener;

        private ASTInstrument(ASTInstrumentListener astListener, String instrumentInfo) {
            super(instrumentInfo);
            this.astListener = astListener;
        }

        @Override
        AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode) {
            return new ASTInstrumentNode(nextNode);
        }

        @Override
        AbstractInstrumentNode removeFromChain(AbstractInstrumentNode instrumentNode) {
            boolean found = false;
            if (instrumentNode != null) {
                if (instrumentNode.getInstrument() == this) {
                    // Found the match at the head of the chain
                    return instrumentNode.nextInstrumentNode;
                }
                // Match not at the head of the chain; remove it.
                found = instrumentNode.removeFromChain(ASTInstrument.this);
            }
            if (!found) {
                throw new IllegalStateException("Couldn't find instrument node to remove: " + this);
            }
            return instrumentNode;
        }

        @NodeInfo(cost = NodeCost.NONE)
        private final class ASTInstrumentNode extends AbstractInstrumentNode {

            private ASTInstrumentNode(AbstractInstrumentNode nextNode) {
                super(nextNode);
            }

            public void enter(Node node, VirtualFrame vFrame) {
                ASTInstrument.this.astListener.enter(ASTInstrument.this.probe, node, vFrame);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, vFrame);
                }
            }

            public void returnVoid(Node node, VirtualFrame vFrame) {
                ASTInstrument.this.astListener.returnVoid(ASTInstrument.this.probe, node, vFrame);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, vFrame);
                }
            }

            public void returnValue(Node node, VirtualFrame vFrame, Object result) {
                ASTInstrument.this.astListener.returnValue(ASTInstrument.this.probe, node, vFrame, result);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, vFrame, result);
                }
            }

            public void returnExceptional(Node node, VirtualFrame vFrame, Exception exception) {
                ASTInstrument.this.astListener.returnExceptional(ASTInstrument.this.probe, node, vFrame, exception);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, vFrame, exception);
                }
            }

            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : astListener.getClass().getSimpleName();
            }
        }

    }

    /**
     * An instrument that propagates events to an instance of {@link ASTInstrumentListener}.
     */
    private static final class ToolNodeInstrument extends Instrument {

        /**
         * Tool-supplied listener for AST events.
         */
        private final ToolNodeInstrumentListener toolNodeListener;

        private ToolNodeInstrument(ToolNodeInstrumentListener toolNodeListener, String instrumentInfo) {
            super(instrumentInfo);
            this.toolNodeListener = toolNodeListener;
        }

        @Override
        AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode) {
            return new ToolInstrumentNode(nextNode);
        }

        @Override
        AbstractInstrumentNode removeFromChain(AbstractInstrumentNode instrumentNode) {
            boolean found = false;
            if (instrumentNode != null) {
                if (instrumentNode.getInstrument() == this) {
                    // Found the match at the head of the chain
                    return instrumentNode.nextInstrumentNode;
                }
                // Match not at the head of the chain; remove it.
                found = instrumentNode.removeFromChain(ToolNodeInstrument.this);
            }
            if (!found) {
                throw new IllegalStateException("Couldn't find instrument node to remove: " + this);
            }
            return instrumentNode;
        }

        @NodeInfo(cost = NodeCost.NONE)
        private final class ToolInstrumentNode extends AbstractInstrumentNode {

            @Child ToolNode toolNode;

            private ToolInstrumentNode(AbstractInstrumentNode nextNode) {
                super(nextNode);
            }

            public void enter(Node node, VirtualFrame vFrame) {
                if (toolNode == null) {
                    final ToolNode newToolNode = ToolNodeInstrument.this.toolNodeListener.getToolNode(ToolNodeInstrument.this.probe);
                    if (newToolNode != null) {
                        toolNode = newToolNode;
                        adoptChildren();
                        ToolNodeInstrument.this.probe.invalidateProbeUnchanged();
                    }
                }
                if (toolNode != null) {
                    toolNode.enter(node, vFrame);
                }
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, vFrame);
                }
            }

            public void returnVoid(Node node, VirtualFrame vFrame) {
                if (toolNode != null) {
                    toolNode.returnVoid(node, vFrame);
                }
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, vFrame);
                }
            }

            public void returnValue(Node node, VirtualFrame vFrame, Object result) {
                if (toolNode != null) {
                    toolNode.returnValue(node, vFrame, result);
                }
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, vFrame, result);
                }
            }

            public void returnExceptional(Node node, VirtualFrame vFrame, Exception exception) {
                if (toolNode != null) {
                    toolNode.returnExceptional(node, vFrame, exception);
                }
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, vFrame, exception);
                }
            }

            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : toolNodeListener.getClass().getSimpleName();
            }
        }

    }

    public interface TruffleOptListener {
        void notifyIsCompiled(boolean isCompiled);
    }

    private static final class TruffleOptInstrument extends Instrument {

        private final TruffleOptListener toolOptListener;

        private TruffleOptInstrument(TruffleOptListener listener, String instrumentInfo) {
            super(instrumentInfo);
            this.toolOptListener = listener;
        }

        @Override
        AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode) {
            return new TruffleOptInstrumentNode(nextNode);
        }

        @Override
        AbstractInstrumentNode removeFromChain(AbstractInstrumentNode instrumentNode) {
            boolean found = false;
            if (instrumentNode != null) {
                if (instrumentNode.getInstrument() == this) {
                    // Found the match at the head of the chain
                    return instrumentNode.nextInstrumentNode;
                }
                // Match not at the head of the chain; remove it.
                found = instrumentNode.removeFromChain(TruffleOptInstrument.this);
            }
            if (!found) {
                throw new IllegalStateException("Couldn't find instrument node to remove: " + this);
            }
            return instrumentNode;
        }

        @NodeInfo(cost = NodeCost.NONE)
        private final class TruffleOptInstrumentNode extends AbstractInstrumentNode {

            private boolean isCompiled;

            private TruffleOptInstrumentNode(AbstractInstrumentNode nextNode) {
                super(nextNode);
                this.isCompiled = CompilerDirectives.inCompiledCode();
            }

            public void enter(Node node, VirtualFrame vFrame) {
                if (this.isCompiled != CompilerDirectives.inCompiledCode()) {
                    this.isCompiled = CompilerDirectives.inCompiledCode();
                    TruffleOptInstrument.this.toolOptListener.notifyIsCompiled(this.isCompiled);
                }
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, vFrame);
                }
            }

            public void returnVoid(Node node, VirtualFrame vFrame) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, vFrame);
                }
            }

            public void returnValue(Node node, VirtualFrame vFrame, Object result) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, vFrame, result);
                }
            }

            public void returnExceptional(Node node, VirtualFrame vFrame, Exception exception) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, vFrame, exception);
                }
            }

            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : toolOptListener.getClass().getSimpleName();
            }
        }

    }

    @NodeInfo(cost = NodeCost.NONE)
    abstract class AbstractInstrumentNode extends Node implements TruffleEvents, InstrumentationNode {

        @Child protected AbstractInstrumentNode nextInstrumentNode;

        protected AbstractInstrumentNode(AbstractInstrumentNode nextNode) {
            this.nextInstrumentNode = nextNode;
        }

        @Override
        public boolean isInstrumentable() {
            return false;
        }

        /**
         * Gets the instrument that created this node.
         */
        private Instrument getInstrument() {
            return Instrument.this;
        }

        /**
         * Removes the node from this chain that was added by a particular instrument, assuming that
         * the head of the chain is not the one to be replaced. This is awkward, but is required
         * because {@link Node#replace(Node)} won't take a {@code null} argument. This doesn't work
         * for the tail of the list, which would be replacing itself with null. So the replacement
         * must be directed the parent of the node being removed.
         */
        private boolean removeFromChain(Instrument instrument) {
            assert getInstrument() != instrument;
            if (nextInstrumentNode == null) {
                return false;
            }
            if (nextInstrumentNode.getInstrument() == instrument) {
                // Next is the one to remove
                if (nextInstrumentNode.nextInstrumentNode == null) {
                    // Next is at the tail; just forget
                    nextInstrumentNode = null;
                } else {
                    // Replace next with its successor
                    nextInstrumentNode.replace(nextInstrumentNode.nextInstrumentNode);
                }
                return true;
            }
            return nextInstrumentNode.removeFromChain(instrument);
        }

        protected String getInstrumentInfo() {
            return Instrument.this.instrumentInfo;
        }

    }

}

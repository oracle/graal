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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.InstrumentationNode.TruffleEvents;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

// TODO (mlvdv) these statics should not be global.  Move them to some kind of context.
// TODO (mlvdv) migrate factory (together with Probe)? break out nested classes?

/**
 * A <em>binding</em> between:
 * <ol>
 * <li>A {@link Probe}: a source of <em>execution events</em> taking place at a program location in
 * an executing Truffle AST, and</li>
 * <li>A <em>listener</em>: a consumer of execution events on behalf of an external client.
 * </ol>
 * <p>
 * Client-oriented documentation for the use of Instruments is available online at <a
 * HREF="https://wiki.openjdk.java.net/display/Graal/Listening+for+Execution+Events"
 * >https://wiki.openjdk.java.net/display/Graal/Listening+for+Execution+Events</a>
 * <p>
 * The implementation of Instruments is complicated by the requirement that Truffle be able to clone
 * ASTs at any time. In particular, any instrumentation-supporting Nodes that have been attached to
 * an AST must be cloned along with the AST: AST clones are not permitted to share Nodes.
 * <p>
 * AST cloning is intended to be as <em>transparent</em> as possible to clients. This is encouraged
 * by providing the {@link SimpleInstrumentListener} for clients that need know nothing more than
 * the properties associated with a Probe: it's {@link SourceSection} and any associated instances
 * of {@link SyntaxTag}.
 * <p>
 * AST cloning is <em>not transparent</em> to clients that use the
 * {@link StandardInstrumentListener}, since those event methods identify the concrete Node instance
 * (and thus the AST instance) where the event takes place.
 * <p>
 * <h4>Implementation Notes: the Life Cycle of an {@link Instrument} at a {@link Probe}</h4>
 * <p>
 * <ul>
 * <li>A new Instrument is created in permanent association with a client-provided
 * <em>listener.</em></li>
 *
 * <li>Multiple Instruments may share a single listener.</li>
 *
 * <li>An Instrument does nothing until it is {@linkplain Probe#attach(Instrument) attached} to a
 * Probe, at which time the Instrument begins routing execution events from the Probe's AST location
 * to the Instrument's listener.</li>
 *
 * <li>Neither Instruments nor Probes are {@link Node}s.</li>
 *
 * <li>A Probe has a single source-based location in an AST, but manages a separate
 * <em>instrumentation chain</em> of Nodes at the equivalent location in each clone of the AST.</li>
 * <li>When a probed AST is cloned, the instrumentation chain associated with each Probe is cloned
 * along with the rest of the AST.</li>
 *
 * <li>When a new Instrument (for example an instance of {@link SimpleInstrument} is attached to a
 * Probe, the Instrument inserts a new instance of its private Node type,
 * {@link SimpleInstrument.SimpleInstrumentNode}, into <em>each of the instrument chains</em>
 * managed by the Probe, i.e. one node instance per existing clone of the AST.</li>
 *
 * <li>If an Instrument is attached to a Probe in an AST that subsequently gets cloned, then the
 * Instrument's private Node type will be cloned along with the rest of the the AST.</li>
 * <li>Each Instrument's private Node type is a dynamic inner class whose only state is in the
 * shared (outer) Instrument instance; that state includes a reference to the Instrument's listener.
 * </li>
 *
 * <li>When an Instrument that has been attached to a Probe is {@linkplain #dispose() disposed}, the
 * Instrument searches every instrument chain associated with the Probe and removes the instance of
 * its private Node type.</li>
 *
 * <li>Attaching and disposing an Instrument at a Probe <em>deoptimizes</em> any compilations of the
 * AST.</li>
 *
 * </ul>
 *
 * @see Probe
 * @see TruffleEvents
 */
public abstract class Instrument {

    /**
     * Creates a <em>Simple Instrument</em>: this Instrument routes execution events to a
     * client-provided listener.
     *
     * @param listener a listener for execution events
     * @param instrumentInfo optional description of the instrument's role, intended for debugging.
     * @return a new instrument, ready for attachment at a probe
     */
    public static Instrument create(SimpleInstrumentListener listener, String instrumentInfo) {
        return new SimpleInstrument(listener, instrumentInfo);
    }

    /**
     * Creates a <em>Standard Instrument</em>: this Instrument routes execution events, together
     * with access to Truffle execution state, to a client-provided listener.
     *
     * @param standardListener a listener for execution events and execution state
     * @param instrumentInfo optional description of the instrument's role, intended for debugging.
     * @return a new instrument, ready for attachment at a probe
     */
    public static Instrument create(StandardInstrumentListener standardListener, String instrumentInfo) {
        return new StandardInstrument(standardListener, instrumentInfo);
    }

    /**
     * Creates an <em>Advanced Instrument</em>: this Instrument executes efficiently, subject to
     * full Truffle optimization, a client-provided AST fragment every time the Probed node is
     * entered.
     *
     * @param resultListener optional client callback for results/failure notification
     * @param rootFactory provider of AST fragments on behalf of the client
     * @param requiredResultType optional requirement, any non-assignable result is reported to the
     *            the listener, if any, as a failure
     * @param instrumentInfo optional description of the instrument's role, intended for debugging.
     * @return a new instrument, ready for attachment at a probe
     */
    public static Instrument create(AdvancedInstrumentResultListener resultListener, AdvancedInstrumentRootFactory rootFactory, Class<?> requiredResultType, String instrumentInfo) {
        return new AdvancedInstrument(resultListener, rootFactory, requiredResultType, instrumentInfo);
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
     * Gets the {@link Probe} to which this Instrument is currently attached: {@code null} if not
     * yet attached to a Probe or if this Instrument has been {@linkplain #dispose() disposed}.
     */
    public Probe getProbe() {
        return probe;
    }

    /**
     * Removes this Instrument from the Probe to which it attached and renders this Instrument
     * inert.
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
            probe = null;
        }
        this.isDisposed = true;
    }

    /**
     * For internal implementation only.
     */
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
     * Removes this instrument from an instrument chain.
     */
    abstract AbstractInstrumentNode removeFromChain(AbstractInstrumentNode instrumentNode);

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

        /**
         * Node that implements a {@link SimpleInstrument} in a particular AST.
         */
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
     * An instrument that propagates events to an instance of {@link StandardInstrumentListener}.
     */
    private static final class StandardInstrument extends Instrument {

        /**
         * Tool-supplied listener for AST events.
         */
        private final StandardInstrumentListener standardListener;

        private StandardInstrument(StandardInstrumentListener standardListener, String instrumentInfo) {
            super(instrumentInfo);
            this.standardListener = standardListener;
        }

        @Override
        AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode) {
            return new StandardInstrumentNode(nextNode);
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
                found = instrumentNode.removeFromChain(StandardInstrument.this);
            }
            if (!found) {
                throw new IllegalStateException("Couldn't find instrument node to remove: " + this);
            }
            return instrumentNode;
        }

        /**
         * Node that implements a {@link StandardInstrument} in a particular AST.
         */
        @NodeInfo(cost = NodeCost.NONE)
        private final class StandardInstrumentNode extends AbstractInstrumentNode {

            private StandardInstrumentNode(AbstractInstrumentNode nextNode) {
                super(nextNode);
            }

            public void enter(Node node, VirtualFrame vFrame) {
                standardListener.enter(StandardInstrument.this.probe, node, vFrame);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, vFrame);
                }
            }

            public void returnVoid(Node node, VirtualFrame vFrame) {
                standardListener.returnVoid(StandardInstrument.this.probe, node, vFrame);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, vFrame);
                }
            }

            public void returnValue(Node node, VirtualFrame vFrame, Object result) {
                standardListener.returnValue(StandardInstrument.this.probe, node, vFrame, result);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, vFrame, result);
                }
            }

            public void returnExceptional(Node node, VirtualFrame vFrame, Exception exception) {
                standardListener.returnExceptional(StandardInstrument.this.probe, node, vFrame, exception);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, vFrame, exception);
                }
            }

            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : standardListener.getClass().getSimpleName();
            }
        }
    }

    /**
     * An instrument that allows clients to provide an AST fragment to be executed directly from
     * within a Probe's <em>instrumentation chain</em>, and thus directly in the executing Truffle
     * AST with potential for full optimization.
     */
    private static final class AdvancedInstrument extends Instrument {

        private final AdvancedInstrumentResultListener resultListener;
        private final AdvancedInstrumentRootFactory rootFactory;
        private final Class<?> requiredResultType;

        private AdvancedInstrument(AdvancedInstrumentResultListener resultListener, AdvancedInstrumentRootFactory rootFactory, Class<?> requiredResultType, String instrumentInfo) {
            super(instrumentInfo);
            this.resultListener = resultListener;
            this.rootFactory = rootFactory;
            this.requiredResultType = requiredResultType;
        }

        @Override
        AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode) {
            return new AdvancedInstrumentNode(nextNode);
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
                found = instrumentNode.removeFromChain(AdvancedInstrument.this);
            }
            if (!found) {
                throw new IllegalStateException("Couldn't find instrument node to remove: " + this);
            }
            return instrumentNode;
        }

        /**
         * Node that implements a {@link AdvancedInstrument} in a particular AST.
         */
        @NodeInfo(cost = NodeCost.NONE)
        private final class AdvancedInstrumentNode extends AbstractInstrumentNode {

            @Child private AdvancedInstrumentRoot instrumentRoot;

            private AdvancedInstrumentNode(AbstractInstrumentNode nextNode) {
                super(nextNode);
            }

            public void enter(Node node, VirtualFrame vFrame) {
                if (instrumentRoot == null) {
                    try {
                        final AdvancedInstrumentRoot newInstrumentRoot = AdvancedInstrument.this.rootFactory.createInstrumentRoot(AdvancedInstrument.this.probe, node);
                        if (newInstrumentRoot != null) {
                            instrumentRoot = newInstrumentRoot;
                            adoptChildren();
                            AdvancedInstrument.this.probe.invalidateProbeUnchanged();
                        }
                    } catch (RuntimeException ex) {
                        if (resultListener != null) {
                            resultListener.notifyFailure(node, vFrame, ex);
                        }
                    }
                }
                if (instrumentRoot != null) {
                    try {
                        final Object result = instrumentRoot.executeRoot(node, vFrame);
                        if (resultListener != null) {
                            checkResultType(result);
                            resultListener.notifyResult(node, vFrame, result);
                        }
                    } catch (RuntimeException ex) {
                        if (resultListener != null) {
                            resultListener.notifyFailure(node, vFrame, ex);
                        }
                    }
                }
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, vFrame);
                }
            }

            private void checkResultType(Object result) {
                if (requiredResultType == null) {
                    return;
                }
                if (result == null) {
                    throw new RuntimeException("Instrument result null: " + requiredResultType.getSimpleName() + " is required");
                }
                if (!(requiredResultType.isAssignableFrom(result.getClass()))) {
                    throw new RuntimeException("Instrument result " + result.toString() + " not assignable to " + requiredResultType.getSimpleName());
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
                return info != null ? info : rootFactory.getClass().getSimpleName();
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

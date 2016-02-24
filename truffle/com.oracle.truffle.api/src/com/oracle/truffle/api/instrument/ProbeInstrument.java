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

import java.io.IOException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A <em>binding</em> between:
 * <ol>
 * <li>A {@link Probe}: a source of <em>execution events</em> taking place at a program location in
 * an executing Truffle AST, and</li>
 * <li>A <em>listener</em>: a consumer of execution events on behalf of an external client.
 * </ol>
 * <p>
 * Client-oriented documentation for the use of Instruments is available online at <a
 * HREF="https://wiki.openjdk.java.net/display/Graal/Listening+for+Execution+Events" >https://
 * wiki.openjdk.java.net/display/Graal/Listening+for+Execution+Events</a>
 *
 * @see Probe
 * @see Instrumenter
 */
public abstract class ProbeInstrument extends Instrument {
    Probe probe = null;

    /**
     * <h4>Implementation Notes</h4>
     * <p>
     * The implementation of Instruments is complicated by the requirement that Truffle be able to
     * clone ASTs at any time. In particular, any instrumentation-supporting Nodes that have been
     * attached to an AST must be cloned along with the AST: AST clones are not permitted to share
     * Nodes.
     * <p>
     * AST cloning is intended to be as <em>transparent</em> as possible to clients. This is
     * encouraged by providing the {@link SimpleInstrumentListener} for clients that need know
     * nothing more than the properties associated with a Probe: its {@link SourceSection} and any
     * associated instances of {@link SyntaxTag}.
     * <p>
     * AST cloning is <em>not transparent</em> to clients that use the
     * {@link StandardInstrumentListener}, since those event methods identify the concrete Node
     * instance (and thus the AST instance) where the event takes place.
     * <p>
     * <h4>Implementation Notes: the Life Cycle of an {@link ProbeInstrument} at a {@link Probe}</h4>
     * <p>
     * <ul>
     * <li>A new Instrument is created in permanent association with a client-provided
     * <em>listener.</em></li>
     *
     * <li>Multiple Instruments may share a single listener.</li>
     *
     * <li>An Instrument does nothing until it is {@linkplain Probe#attach(ProbeInstrument)
     * attached} to a Probe, at which time the Instrument begins routing execution events from the
     * Probe's AST location to the Instrument's listener.</li>
     *
     * <li>Neither Instruments nor Probes are {@link Node}s.</li>
     *
     * <li>A Probe has a single source-based location in an AST, but manages a separate
     * <em>instrumentation chain</em> of Nodes at the equivalent location in each clone of the AST.</li>
     * <li>When a probed AST is cloned, the instrumentation chain associated with each Probe is
     * cloned along with the rest of the AST.</li>
     *
     * <li>When a new Instrument is attached to a Probe, the Instrument inserts a new instance of
     * its private Node type into <em>each of the instrument chains</em> managed by the Probe, i.e.
     * one node instance per existing clone of the AST.</li>
     *
     * <li>If an Instrument is attached to a Probe in an AST that subsequently gets cloned, then the
     * Instrument's private Node type will be cloned along with the rest of the the AST.</li>
     * <li>Each Instrument's private Node type is a dynamic inner class whose only state is in the
     * shared (outer) Instrument instance; that state includes a reference to the Instrument's
     * listener.</li>
     *
     * <li>When an Instrument that has been attached to a Probe is {@linkplain #dispose() disposed},
     * the Instrument searches every instrument chain associated with the Probe and removes the
     * instance of its private Node type.</li>
     *
     * <li>Attaching or disposing an Instrument at a Probe <em>deoptimizes</em> any compilations of
     * the AST.</li>
     *
     * </ul>
     */
    private ProbeInstrument(String instrumentInfo) {
        super(instrumentInfo);
    }

    /**
     * Removes this Instrument from the Probe to which it attached and renders this Instrument
     * inert.
     */
    @Override
    protected void innerDispose() {
        if (probe != null) {
            // It's attached
            probe.disposeInstrument(this);
            probe = null;
        }
    }

    /**
     * Gets the {@link Probe} to which this {@link Instrument} is currently attached: {@code null}
     * if not yet attached to a Probe or if this Instrument has been {@linkplain #dispose()
     * disposed}.
     */
    public Probe getProbe() {
        return probe;
    }

    void setAttachedTo(Probe probe) {
        this.probe = probe;
    }

    abstract AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode);

    /**
     * Removes this instrument from an instrument chain.
     */
    abstract AbstractInstrumentNode removeFromChain(AbstractInstrumentNode instrumentNode);

    /**
     * An instrument that propagates events to an instance of {@link SimpleInstrumentListener}.
     */
    static final class SimpleInstrument extends ProbeInstrument {

        /**
         * Tool-supplied listener for events.
         */
        private final SimpleInstrumentListener simpleListener;

        SimpleInstrument(SimpleInstrumentListener simpleListener, String instrumentInfo) {
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

            @Override
            public void enter(Node node, VirtualFrame frame) {
                SimpleInstrument.this.simpleListener.onEnter(SimpleInstrument.this.probe);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, frame);
                }
            }

            @Override
            public void returnVoid(Node node, VirtualFrame frame) {
                SimpleInstrument.this.simpleListener.onReturnVoid(SimpleInstrument.this.probe);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, frame);
                }
            }

            @Override
            public void returnValue(Node node, VirtualFrame frame, Object result) {
                SimpleInstrument.this.simpleListener.onReturnValue(SimpleInstrument.this.probe, result);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, frame, result);
                }
            }

            @Override
            public void returnExceptional(Node node, VirtualFrame frame, Throwable exception) {
                SimpleInstrument.this.simpleListener.onReturnExceptional(SimpleInstrument.this.probe, exception);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, frame, exception);
                }
            }

            @Override
            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : simpleListener.getClass().getSimpleName();
            }

            @Override
            public Probe getProbe() {
                return SimpleInstrument.this.probe;
            }
        }
    }

    /**
     * An instrument that propagates events to an instance of {@link StandardInstrumentListener}.
     */
    static final class StandardInstrument extends ProbeInstrument {

        /**
         * Tool-supplied listener for AST events.
         */
        private final StandardInstrumentListener standardListener;

        StandardInstrument(StandardInstrumentListener standardListener, String instrumentInfo) {
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

            @Override
            public void enter(Node node, VirtualFrame frame) {
                standardListener.onEnter(StandardInstrument.this.probe, node, frame);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, frame);
                }
            }

            @Override
            public void returnVoid(Node node, VirtualFrame frame) {
                standardListener.onReturnVoid(StandardInstrument.this.probe, node, frame);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, frame);
                }
            }

            @Override
            public void returnValue(Node node, VirtualFrame frame, Object result) {
                standardListener.onReturnValue(StandardInstrument.this.probe, node, frame, result);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, frame, result);
                }
            }

            @Override
            public void returnExceptional(Node node, VirtualFrame frame, Throwable exception) {
                standardListener.onReturnExceptional(StandardInstrument.this.probe, node, frame, exception);
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, frame, exception);
                }
            }

            @Override
            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : standardListener.getClass().getSimpleName();
            }

            @Override
            public Probe getProbe() {
                return StandardInstrument.this.probe;
            }
        }
    }

    /**
     * An instrument that allows clients to provide guest language code to be executed directly from
     * within a Probe's <em>instrumentation chain</em>, and thus directly in the executing Truffle
     * AST with potential for full optimization.
     */
    static final class EvalInstrument extends ProbeInstrument {

        @SuppressWarnings("rawtypes") private final Class<? extends TruffleLanguage> languageClass;
        private final Source source;
        private final EvalInstrumentListener evalListener;
        private final String[] names;
        private final Object[] params;

        @SuppressWarnings("rawtypes")
        EvalInstrument(Class<? extends TruffleLanguage> languageClass, Source source, EvalInstrumentListener evalListener, String instrumentInfo, String[] argumentNames, Object[] parameters) {
            super(instrumentInfo);
            this.languageClass = languageClass;
            this.source = source;
            this.evalListener = evalListener;
            this.names = argumentNames;
            this.params = parameters;
        }

        @Override
        AbstractInstrumentNode addToChain(AbstractInstrumentNode nextNode) {
            return new EvalInstrumentNode(nextNode);
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
                found = instrumentNode.removeFromChain(EvalInstrument.this);
            }
            if (!found) {
                throw new IllegalStateException("Couldn't find instrument node to remove: " + this);
            }
            return instrumentNode;
        }

        /**
         * Node that implements an {@link EvalInstrument} in a particular AST.
         */
        @NodeInfo(cost = NodeCost.NONE)
        private final class EvalInstrumentNode extends AbstractInstrumentNode {

            @Child private DirectCallNode callNode;

            private EvalInstrumentNode(AbstractInstrumentNode nextNode) {
                super(nextNode);
            }

            @Override
            public void enter(Node node, VirtualFrame frame) {
                if (callNode == null) {
                    try {
                        final CallTarget callTarget = Instrumenter.ACCESSOR.parse(languageClass, source, node, names);
                        if (callTarget != null) {
                            callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
                            callNode.forceInlining();
                            adoptChildren();
                            EvalInstrument.this.probe.invalidateProbeUnchanged();
                        }
                    } catch (RuntimeException | IOException ex) {
                        if (evalListener != null) {
                            evalListener.onFailure(node, frame, ex);
                        }
                    }
                }
                if (callNode != null) {
                    try {
                        final Object result = callNode.call(frame, params);
                        if (evalListener != null) {
                            evalListener.onExecution(node, frame, result);
                        }
                    } catch (RuntimeException ex) {
                        if (evalListener != null) {
                            evalListener.onFailure(node, frame, ex);
                        }
                    }
                }
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, frame);
                }
            }

            @Override
            public void returnVoid(Node node, VirtualFrame frame) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, frame);
                }
            }

            @Override
            public void returnValue(Node node, VirtualFrame frame, Object result) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, frame, result);
                }
            }

            @Override
            public void returnExceptional(Node node, VirtualFrame frame, Throwable exception) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, frame, exception);
                }
            }

            @Override
            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : getInstrumentInfo();
            }
        }
    }

    // Experimental
    public interface TruffleOptListener {
        void notifyIsCompiled(boolean isCompiled);
    }

    @SuppressWarnings("unused")
    private static final class TruffleOptInstrument extends ProbeInstrument {

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

            @Override
            public void enter(Node node, VirtualFrame frame) {
                if (this.isCompiled != CompilerDirectives.inCompiledCode()) {
                    this.isCompiled = CompilerDirectives.inCompiledCode();
                    TruffleOptInstrument.this.toolOptListener.notifyIsCompiled(this.isCompiled);
                }
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.enter(node, frame);
                }
            }

            @Override
            public void returnVoid(Node node, VirtualFrame frame) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnVoid(node, frame);
                }
            }

            @Override
            public void returnValue(Node node, VirtualFrame frame, Object result) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnValue(node, frame, result);
                }
            }

            @Override
            public void returnExceptional(Node node, VirtualFrame frame, Throwable exception) {
                if (nextInstrumentNode != null) {
                    nextInstrumentNode.returnExceptional(node, frame, exception);
                }
            }

            @Override
            public String instrumentationInfo() {
                final String info = getInstrumentInfo();
                return info != null ? info : toolOptListener.getClass().getSimpleName();
            }
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    abstract class AbstractInstrumentNode extends EventHandlerNode {

        @Child protected AbstractInstrumentNode nextInstrumentNode;

        protected AbstractInstrumentNode(AbstractInstrumentNode nextNode) {
            this.nextInstrumentNode = nextNode;
        }

        @Override
        public Probe getProbe() {
            return probe;
        }

        /**
         * Gets the instrument that created this node.
         */
        private ProbeInstrument getInstrument() {
            return ProbeInstrument.this;
        }

        /**
         * Removes the node from this chain that was added by a particular instrument, assuming that
         * the head of the chain is not the one to be replaced. This is awkward, but is required
         * because {@link Node#replace(Node)} won't take a {@code null} argument. This doesn't work
         * for the tail of the list, which would be replacing itself with null. So the replacement
         * must be directed the parent of the node being removed.
         */
        private boolean removeFromChain(ProbeInstrument instrument) {
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
            return ProbeInstrument.this.getInstrumentInfo();
        }
    }
}

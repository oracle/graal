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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.Instrument.InstrumentNode;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Implementation interfaces and classes for attaching {@link Probe}s to {@link WrapperNode}s.
 */
public abstract class ProbeNode extends Node implements TruffleEventListener, InstrumentationNode {

    /**
     * A node that can be inserted into a Truffle AST, and which enables {@linkplain Instrument
     * instrumentation} at a particular Guest Language (GL) node. Implementations must extend
     * {@link Node} and should override {@link Node#isInstrumentable()} to return {@code false}.
     * <p>
     * The implementation must be GL-specific. A wrapper <em>decorates</em> a GL AST node (the
     * wrapper's <em>child</em>) by acting as a transparent <em>proxy</em> with respect to the GL's
     * execution semantics.
     * <p>
     * Instrumentation at the wrapped node is implemented by an instance of {@link ProbeNode}
     * attached as a second child of the {@link WrapperNode}.
     * <p>
     * A wrapper is obliged to notify its attached {@link ProbeNode} when execution events occur at
     * the wrapped AST node during program execution.
     * <p>
     * When a GL AST is cloned, the {@link WrapperNode}, its {@link ProbeNode} and any
     * {@linkplain Instrument instrumentation} are also cloned; they are in effect part of the GL
     * AST. An instance of {@link Probe} represents abstractly the instrumentation at a particular
     * location in a GL AST; it tracks all the copies of the Wrapper and attached instrumentation,
     * and acts as a single point of access for tools.
     * <p>
     * This interface is not intended to be visible as part of the API for tools (instrumentation
     * clients).
     * <p>
     * Implementation guidelines:
     * <ol>
     * <li>Each GL implementation should include a WrapperNode implementation; usually only one is
     * needed.</li>
     * <li>The wrapper type should descend from the <em>GL-specific node class</em>.</li>
     * <li>Must have a field: {@code @Child private <GL>Node child;}</li>
     * <li>Must have a field: {@code @Child private ProbeNode probeNode;}</li>
     * <li>The wrapper must act as a <em>proxy</em> for its child, which means implementing every
     * possible <em>execute-</em> method that gets called on guest language AST node types by their
     * parents, and passing along each call to its child.</li>
     * <li>Method {@code Probe getProbe()} should be implemented as {@code probeNode.getProbe();}
     * <li>Method {@code insertProbe(ProbeNode)} should be implemented as
     * {@code this.probeNode=insert(newProbeNode);}</li>
     * <li>Most importantly, Wrappers must be implemented so that Truffle optimization will reduce
     * their runtime overhead to zero when there are no attached {@link Instrument}s.</li>
     * </ol>
     * <p>
     *
     * @see Instrument
     */
    public interface WrapperNode extends InstrumentationNode {

        /**
         * Gets the node being "wrapped", i.e. the AST node for which
         * {@linkplain TruffleEventListener execution events} will be reported through the
         * Instrumentation Framework.
         */
        Node getChild();

        /**
         * Gets the {@link Probe} responsible for installing this wrapper; none if the wrapper
         * installed via {@linkplain Node#probeLite(TruffleEventListener) "lite-Probing"}.
         */
        Probe getProbe();

        /**
         * Implementation support for completing a newly created wrapper node.
         */
        void insertProbe(ProbeNode probeNode);

    }

    /**
     * Create a new {@link Probe} associated with, and attached to, a Guest Language specific
     * instance of {@link WrapperNode}.
     */
    public static Probe insertProbe(WrapperNode wrapper) {
        final SourceSection sourceSection = wrapper.getChild().getSourceSection();
        final ProbeFullNode probeFullNode = new ProbeFullNode(); // private constructor
        final Probe probe = new Probe(probeFullNode, sourceSection);  // package private access
        probeFullNode.setProbe(probe);
        wrapper.insertProbe(probeFullNode);
        return probe;
    }

    /**
     * Creates a new {@link ProbeLiteNode} associated with, and attached to, a Guest Language
     * specific instance of {@link WrapperNode}.
     */
    public static void insertProbeLite(WrapperNode wrapper, TruffleEventListener eventListener) {
        final ProbeLiteNode probeLiteNode = new ProbeLiteNode(eventListener);
        wrapper.insertProbe(probeLiteNode);
    }

    @Override
    public boolean isInstrumentable() {
        return false;
    }

    /**
     * @return the {@link Probe} permanently associated with this {@link ProbeNode}.
     *
     * @throws IllegalStateException if this location was "lite-Probed"
     */
    public abstract Probe getProbe() throws IllegalStateException;

    /**
     * Adds an {@link InstrumentNode} to this chain.
     *
     * @throws IllegalStateException if at a "lite-Probed" location.
     */
    abstract void addInstrument(Instrument instrument);

    /**
     * Removes an instrument from this chain of instruments.
     *
     * @throws IllegalStateException if at a "lite-Probed" location.
     * @throws RuntimeException if no matching instrument is found,
     */
    abstract void removeInstrument(Instrument instrument);

    /**
     * Implementation class & interfaces for enabling the attachment of {@linkplain Probe Probes} to
     * Truffle ASTs.
     * <p>
     * Head of a chain of nodes acting on behalf of {@linkplain Instrument instruments}, attached to
     * a Guest Language (GL) AST as a child of a GL-specific {@link WrapperNode} node.
     * <p>
     * When Truffle clones an AST, the chain, including all attached {@linkplain Instrument
     * instruments} will be cloned along with the {@link WrapperNode} to which it is attached. An
     * instance of {@link Probe} represents abstractly the instrumentation at a particular location
     * in a GL AST, tracks the clones of the chain, and keeps the instrumentation attached to the
     * clones consistent.
     */
    @NodeInfo(cost = NodeCost.NONE)
    private static final class ProbeFullNode extends ProbeNode {

        /**
         * First {@link InstrumentNode} node in chain; {@code null} of no instruments in chain.
         */
        @Child protected InstrumentNode firstInstrument;

        // Never changed once set.
        @CompilationFinal private Probe probe = null;

        /**
         * An assumption that the state of the {@link Probe} with which this chain is associated has
         * not changed since the last time checking such an assumption failed and a reference to a
         * new assumption (associated with a new state of the {@link Probe} was retrieved.
         */
        @CompilationFinal private Assumption probeUnchangedAssumption;

        private ProbeFullNode() {
            this.firstInstrument = null;
        }

        @Override
        public Probe getProbe() throws IllegalStateException {
            return probe;
        }

        @Override
        public Node copy() {
            Node node = super.copy();
            probe.registerProbeNodeClone((ProbeNode) node);
            return node;
        }

        private void setProbe(Probe probe) {
            this.probe = probe;
            this.probeUnchangedAssumption = probe.getUnchangedAssumption();
        }

        private void checkProbeUnchangedAssumption() {
            try {
                probeUnchangedAssumption.check();
            } catch (InvalidAssumptionException ex) {
                // Failure creates an implicit deoptimization
                // Get the assumption associated with the new probe state
                this.probeUnchangedAssumption = probe.getUnchangedAssumption();
            }
        }

        @Override
        @TruffleBoundary
        void addInstrument(Instrument instrument) {
            assert instrument.getProbe() == probe;
            // The existing chain of nodes may be empty
            // Attach the modified chain.
            firstInstrument = insert(instrument.addToChain(firstInstrument));
        }

        @Override
        @TruffleBoundary
        void removeInstrument(Instrument instrument) {
            assert instrument.getProbe() == probe;
            final InstrumentNode modifiedChain = instrument.removeFromChain(firstInstrument);
            if (modifiedChain == null) {
                firstInstrument = null;
            } else {
                firstInstrument = insert(modifiedChain);
            }
        }

        public void enter(Node node, VirtualFrame frame) {
            final SyntaxTagTrap trap = probe.getTrap();
            if (trap != null) {
                checkProbeUnchangedAssumption();
                trap.tagTrappedAt(((WrapperNode) this.getParent()).getChild(), frame.materialize());
            }
            if (firstInstrument != null) {
                checkProbeUnchangedAssumption();
                firstInstrument.enter(node, frame);
            }
        }

        public void returnVoid(Node node, VirtualFrame frame) {
            if (firstInstrument != null) {
                checkProbeUnchangedAssumption();
                firstInstrument.returnVoid(node, frame);
            }
        }

        public void returnValue(Node node, VirtualFrame frame, Object result) {
            if (firstInstrument != null) {
                checkProbeUnchangedAssumption();
                firstInstrument.returnValue(node, frame, result);
            }
        }

        public void returnExceptional(Node node, VirtualFrame frame, Exception exception) {
            if (firstInstrument != null) {
                checkProbeUnchangedAssumption();
                firstInstrument.returnExceptional(node, frame, exception);
            }
        }

        public String instrumentationInfo() {
            return "Standard probe";
        }

    }

    /**
     * Implementation of a probe that only ever has a single "instrument" associated with it. No
     * {@link Instrument} is ever created; instead this method simply delegates the various enter
     * and return events to a {@link TruffleEventListener} passed in during construction.
     */
    @NodeInfo(cost = NodeCost.NONE)
    private static final class ProbeLiteNode extends ProbeNode {

        private final TruffleEventListener eventListener;

        private ProbeLiteNode(TruffleEventListener eventListener) {
            this.eventListener = eventListener;
        }

        @Override
        public Probe getProbe() throws IllegalStateException {
            throw new IllegalStateException("\"lite-Probed\" nodes have no explicit Probe");
        }

        @Override
        @TruffleBoundary
        void addInstrument(Instrument instrument) {
            throw new IllegalStateException("Instruments may not be added at a \"lite-probed\" location");
        }

        @Override
        @TruffleBoundary
        void removeInstrument(Instrument instrument) {
            throw new IllegalStateException("Instruments may not be removed at a \"lite-probed\" location");
        }

        public void enter(Node node, VirtualFrame frame) {
            eventListener.enter(node, frame);
        }

        public void returnVoid(Node node, VirtualFrame frame) {
            eventListener.returnVoid(node, frame);
        }

        public void returnValue(Node node, VirtualFrame frame, Object result) {
            eventListener.returnValue(node, frame, result);
        }

        public void returnExceptional(Node node, VirtualFrame frame, Exception exception) {
            eventListener.returnExceptional(node, frame, exception);
        }

        public String instrumentationInfo() {
            return "\"Lite\" probe";
        }

    }
}

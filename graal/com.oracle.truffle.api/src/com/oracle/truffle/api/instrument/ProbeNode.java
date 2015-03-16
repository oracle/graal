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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.Instrument.AbstractInstrumentNode;
import com.oracle.truffle.api.instrument.InstrumentationNode.TruffleEvents;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Implementation class & interface for enabling the attachment of {@linkplain Probe Probes} to
 * Truffle ASTs.
 * <p>
 * A {@link ProbeNode} is the head of a chain of nodes acting on behalf of {@linkplain Instrument
 * instruments}. It is attached to an AST as a child of a guest-language-specific
 * {@link WrapperNode} node.
 * <p>
 * When Truffle clones an AST, the chain, including all attached {@linkplain Instrument instruments}
 * will be cloned along with the {@link WrapperNode} to which it is attached. An instance of
 * {@link Probe} represents abstractly the instrumentation at a particular location in a GL AST,
 * tracks the clones of the chain, and keeps the instrumentation attached to the clones consistent.
 */
@NodeInfo(cost = NodeCost.NONE)
public final class ProbeNode extends Node implements TruffleEvents, InstrumentationNode {

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
         * {@linkplain InstrumentationNode.TruffleEvents execution events} will be reported through
         * the Instrumentation Framework.
         */
        Node getChild();

        /**
         * Gets the {@link Probe} responsible for installing this wrapper.
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
        final ProbeNode probeNode = new ProbeNode(); // private constructor
        probeNode.probe = new Probe(probeNode, sourceSection);  // package private access
        wrapper.insertProbe(probeNode);
        return probeNode.probe;
    }

    // Never changed once set.
    @CompilationFinal Probe probe = null;
    /**
     * First {@link AbstractInstrumentNode} node in chain; {@code null} of no instruments in chain.
     */
    @Child protected AbstractInstrumentNode firstInstrument;

    @Override
    public boolean isInstrumentable() {
        return false;
    }

    @Override
    public Node copy() {
        Node node = super.copy();
        probe.registerProbeNodeClone((ProbeNode) node);
        return node;
    }

    /**
     * @return the {@link Probe} permanently associated with this {@link ProbeNode}.
     */
    public Probe getProbe() {
        return probe;
    }

    public void enter(Node node, VirtualFrame vFrame) {
        this.probe.checkProbeUnchanged();
        final SyntaxTagTrap trap = probe.getTrap();
        if (trap != null) {
            trap.tagTrappedAt(((WrapperNode) this.getParent()).getChild(), vFrame.materialize());
        }
        if (firstInstrument != null) {
            firstInstrument.enter(node, vFrame);
        }
    }

    public void returnVoid(Node node, VirtualFrame vFrame) {
        this.probe.checkProbeUnchanged();
        if (firstInstrument != null) {
            firstInstrument.returnVoid(node, vFrame);
        }
    }

    public void returnValue(Node node, VirtualFrame vFrame, Object result) {
        this.probe.checkProbeUnchanged();
        if (firstInstrument != null) {
            firstInstrument.returnValue(node, vFrame, result);
        }
    }

    public void returnExceptional(Node node, VirtualFrame vFrame, Exception exception) {
        this.probe.checkProbeUnchanged();
        if (firstInstrument != null) {
            firstInstrument.returnExceptional(node, vFrame, exception);
        }
    }

    public String instrumentationInfo() {
        return "Standard probe";
    }

    /**
     * Adds an {@link AbstractInstrumentNode} to this chain.
     */
    @TruffleBoundary
    void addInstrument(Instrument instrument) {
        assert instrument.getProbe() == probe;
        // The existing chain of nodes may be empty
        // Attach the modified chain.
        firstInstrument = insert(instrument.addToChain(firstInstrument));
    }

    /**
     * Removes an instrument from this chain of instruments.
     *
     * @throws RuntimeException if no matching instrument is found,
     */
    @TruffleBoundary
    void removeInstrument(Instrument instrument) {
        assert instrument.getProbe() == probe;
        final AbstractInstrumentNode modifiedChain = instrument.removeFromChain(firstInstrument);
        if (modifiedChain == null) {
            firstInstrument = null;
        } else {
            firstInstrument = insert(modifiedChain);
        }
    }

}

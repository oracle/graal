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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.ProbeInstrument.AbstractInstrumentNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * Implementation class & interface for enabling the attachment of {@linkplain Probe Probes} to
 * Truffle ASTs.
 * <p>
 * A {@link ProbeNode} is the head of a chain of nodes acting on behalf of {@linkplain ProbeInstrument
 * instruments}. It is attached to an AST as a child of a guest-language-specific
 * {@link WrapperNode} node.
 * <p>
 * When Truffle clones an AST, the chain, including all attached {@linkplain ProbeInstrument instruments}
 * will be cloned along with the {@link WrapperNode} to which it is attached. An instance of
 * {@link Probe} represents abstractly the instrumentation at a particular location in a Guest
 * Language AST, tracks the clones of the chain, and keeps the instrumentation attached to the
 * clones consistent.
 */
@NodeInfo(cost = NodeCost.NONE)
final class ProbeNode extends EventHandlerNode {

    // Never changed once set.
    @CompilationFinal Probe probe = null;
    /**
     * First {@link AbstractInstrumentNode} node in chain; {@code null} of no instruments in chain.
     */
    @Child protected AbstractInstrumentNode firstInstrumentNode;

    @Override
    public Node copy() {
        Node node = super.copy();
        probe.registerProbeNodeClone((ProbeNode) node);
        return node;
    }

    /**
     * @return the {@link Probe} permanently associated with this {@link ProbeNode}.
     */
    @Override
    public Probe getProbe() {
        return probe;
    }

    @Override
    public void enter(Node node, VirtualFrame vFrame) {
        this.probe.checkProbeUnchanged();
        final SyntaxTagTrap beforeTagTrap = probe.getBeforeTrap();
        if (beforeTagTrap != null) {
            beforeTagTrap.tagTrappedAt(((WrapperNode) this.getParent()).getChild(), vFrame.materialize());
        }
        if (firstInstrumentNode != null) {
            firstInstrumentNode.enter(node, vFrame);
        }
    }

    @Override
    public void returnVoid(Node node, VirtualFrame vFrame) {
        this.probe.checkProbeUnchanged();
        if (firstInstrumentNode != null) {
            firstInstrumentNode.returnVoid(node, vFrame);
        }
        final SyntaxTagTrap afterTagTrap = probe.getAfterTrap();
        if (afterTagTrap != null) {
            afterTagTrap.tagTrappedAt(((WrapperNode) this.getParent()).getChild(), vFrame.materialize());
        }
    }

    @Override
    public void returnValue(Node node, VirtualFrame vFrame, Object result) {
        this.probe.checkProbeUnchanged();
        if (firstInstrumentNode != null) {
            firstInstrumentNode.returnValue(node, vFrame, result);
        }
        final SyntaxTagTrap afterTagTrap = probe.getAfterTrap();
        if (afterTagTrap != null) {
            afterTagTrap.tagTrappedAt(((WrapperNode) this.getParent()).getChild(), vFrame.materialize());
        }
    }

    @Override
    public void returnExceptional(Node node, VirtualFrame vFrame, Exception exception) {
        this.probe.checkProbeUnchanged();
        if (firstInstrumentNode != null) {
            firstInstrumentNode.returnExceptional(node, vFrame, exception);
        }
        final SyntaxTagTrap afterTagTrap = probe.getAfterTrap();
        if (afterTagTrap != null) {
            afterTagTrap.tagTrappedAt(((WrapperNode) this.getParent()).getChild(), vFrame.materialize());
        }
    }

    @Override
    public String instrumentationInfo() {
        return "Standard probe";
    }

    /**
     * Gets the guest-language AST node to which this Probe is attached.
     */
    Node getProbedNode() {
        return ((WrapperNode) this.getParent()).getChild();
    }

    /**
     * Adds an {@link AbstractInstrumentNode} to this chain.
     */
    @TruffleBoundary
    void addInstrument(ProbeInstrument instrument) {
        assert instrument.getProbe() == probe;
        // The existing chain of nodes may be empty
        // Attach the modified chain.
        firstInstrumentNode = insert(instrument.addToChain(firstInstrumentNode));
    }

    /**
     * Removes an instrument from this chain of instruments.
     *
     * @throws RuntimeException if no matching instrument is found,
     */
    @TruffleBoundary
    void removeInstrument(ProbeInstrument instrument) {
        assert instrument.getProbe() == probe;
        final AbstractInstrumentNode modifiedChain = instrument.removeFromChain(firstInstrumentNode);
        if (modifiedChain == null) {
            firstInstrumentNode = null;
        } else {
            firstInstrumentNode = insert(modifiedChain);
        }
    }

}

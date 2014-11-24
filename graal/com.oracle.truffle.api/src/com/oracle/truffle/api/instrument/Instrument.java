/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.nodes.*;

// TODO (mlvdv) migrate some of this to external documentation.
/**
 * A dynamically added/removed binding between a {@link Probe}, which provides notification of
 * {@linkplain TruffleEventReceiver execution events} taking place at a {@link Node} in a Guest
 * Language (GL) Truffle AST, and a {@linkplain TruffleEventReceiver receiver}, which consumes
 * notifications on behalf of an external tool.
 * <p>
 * <h4>Summary: How to "instrument" an AST location:</h4>
 * <ol>
 * <li>Create an implementation of {@link TruffleEventReceiver} that responds to events on behalf of
 * a tool.</li>
 * <li>Create an Instrument via factory method {@link Instrument#create(TruffleEventReceiver)}.</li>
 * <li>"Attach" the Instrument to a Probe via {@link Probe#attach(Instrument)}, at which point event
 * notifications begin to arrive at the receiver.</li>
 * <li>When no longer needed, "detach" the Instrument via {@link Instrument#dispose()}, at which
 * point event notifications to the receiver cease, and the Instrument becomes unusable.</li>
 * </ol>
 * <p>
 * <h4>Options for creating receivers:</h4>
 * <p>
 * <ol>
 * <li>Implement the interface {@link TruffleEventReceiver}. The event handling methods account for
 * both the entry into an AST node (about to call) and several possible kinds of exit from an AST
 * node (just returned).</li>
 * <li>Extend {@link DefaultEventReceiver}, which provides a no-op implementation of every
 * {@link TruffleEventReceiver} method; override the methods of interest.</li>
 * <li>Extend {@link SimpleEventReceiver}, where return values are ignored so only two methods (for
 * "enter" and "return") will notify all events.</li>
 * </ol>
 * <p>
 * <h4>General guidelines for receiver implementation:</h4>
 * <p>
 * When an Instrument is attached to a Probe, the receiver effectively becomes part of the executing
 * GL program; performance can be affected by the receiver's implementation.
 * <ul>
 * <li>Do not store {@link Frame} or {@link Node} references in fields.</li>
 * <li>Prefer {@code final} fields and (where performance is important) short methods.</li>
 * <li>If needed, pass along the {@link VirtualFrame} reference from an event notification as far as
 * possible through code that is expected to be inlined, since this incurs no runtime overhead. When
 * access to frame data is needed, substitute a more expensive {@linkplain Frame#materialize()
 * materialized} representation of the frame.</li>
 * <li>If a receiver calls back to its tool during event handling, and if performance is an issue,
 * then this should be through a final "callback" field in the instrument, and the called methods
 * should be minimal.</li>
 * <li>On the other hand, implementations should prevent Truffle from inlining beyond a reasonable
 * point with the method annotation {@link TruffleBoundary}.</li>
 * <li>The implicit "outer" pointer in a non-static inner class is a useful way to implement
 * callbacks to owner tools.</li>
 * <li>Primitive-valued return events are boxed for notification, but Truffle will eliminate the
 * boxing if they are cast back to their primitive values quickly (in particular before crossing any
 * {@link TruffleBoundary} annotations).
 * </ul>
 * <p>
 * <h4>Allowing for AST cloning:</h4>
 * <p>
 * Truffle routinely <em>clones</em> ASTs, which has consequences for receiver implementation.
 * <ul>
 * <li>Even though a {@link Probe} is uniquely associated with a particular location in the
 * executing Guest Language program, execution events at that location will in general be
 * implemented by different {@link Node} instances, i.e. <em>clones</em> of the originally probed
 * node.</li>
 * <li>Because of <em>cloning</em> the {@link Node} supplied with notifications to a particular
 * receiver will vary, but because they all represent the same GL program location the events should
 * be treated as equivalent for most purposes.</li>
 * </ul>
 * <p>
 * <h4>Access to execution state:</h4>
 * <p>
 * <ul>
 * <li>Event notification arguments provide primary access to the GL program's execution states:
 * <ul>
 * <li>{@link Node}: the concrete node (in one of the AST's clones) from which the event originated.
 * </li>
 * <li>{@link VirtualFrame}: the current execution frame.
 * </ul>
 * <li>Some global information is available, for example the execution
 * {@linkplain TruffleRuntime#iterateFrames(FrameInstanceVisitor) stack}.</li>
 * <li>Additional information needed by a receiver could be stored when created, preferably
 * {@code final} of course. For example, a reference to the {@link Probe} to which the receiver's
 * Instrument has been attached would give access to its corresponding
 * {@linkplain Probe#getProbedSourceSection() source location} or to the collection of
 * {@linkplain SyntaxTag tags} currently applied to the Probe.</li>
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
 * <h4>Sharing receivers:</h4>
 * <p>
 * Although an Instrument may only be attached to a single Probe, a receiver can be shared among
 * multiple Instruments. This can be useful for observing events that might happen at different
 * locations in a single AST, for example all assignments to a particular variable. In this case a
 * new Instrument would be created and attached at each assignment node, but all the Instruments
 * would be created with the same receiver.
 * <p>
 * <strong>Disclaimer:</strong> experimental; under development.
 *
 * @see Probe
 * @see TruffleEventReceiver
 */
public final class Instrument {

    /**
     * Creates an instrument that will route execution events to a receiver.
     *
     * @param receiver a receiver for event generated by the instrument
     * @param instrumentInfo optional description of the instrument's role
     * @return a new instrument, ready for attachment at a probe
     */
    public static Instrument create(TruffleEventReceiver receiver, String instrumentInfo) {
        return new Instrument(receiver, instrumentInfo);
    }

    /**
     * Creates an instrument that will route execution events to a receiver.
     */
    public static Instrument create(TruffleEventReceiver receiver) {
        return new Instrument(receiver, null);
    }

    /**
     * Tool-supplied receiver of events.
     */
    private final TruffleEventReceiver toolEventreceiver;

    /**
     * Optional documentation, mainly for debugging.
     */
    private final String instrumentInfo;

    /**
     * Has this instrument been disposed? stays true once set.
     */
    private boolean isDisposed = false;

    private Probe probe = null;

    private Instrument(TruffleEventReceiver receiver, String instrumentInfo) {
        this.toolEventreceiver = receiver;
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

    InstrumentNode addToChain(InstrumentNode nextNode) {
        return new InstrumentNode(nextNode);
    }

    /**
     * Removes this instrument from an instrument chain.
     */
    InstrumentNode removeFromChain(InstrumentNode instrumentNode) {
        boolean found = false;
        if (instrumentNode != null) {
            if (instrumentNode.getInstrument() == this) {
                // Found the match at the head of the chain
                return instrumentNode.nextInstrument;
            }
            // Match not at the head of the chain; remove it.
            found = instrumentNode.removeFromChain(Instrument.this);
        }
        if (!found) {
            throw new IllegalStateException("Couldn't find instrument node to remove: " + this);
        }
        return instrumentNode;
    }

    @NodeInfo(cost = NodeCost.NONE)
    final class InstrumentNode extends Node implements TruffleEventReceiver, InstrumentationNode {

        @Child private InstrumentNode nextInstrument;

        private InstrumentNode(InstrumentNode nextNode) {
            this.nextInstrument = nextNode;
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
            if (nextInstrument == null) {
                return false;
            }
            if (nextInstrument.getInstrument() == instrument) {
                // Next is the one to remove
                if (nextInstrument.nextInstrument == null) {
                    // Next is at the tail; just forget
                    nextInstrument = null;
                } else {
                    // Replace next with its successor
                    nextInstrument.replace(nextInstrument.nextInstrument);
                }
                return true;
            }
            return nextInstrument.removeFromChain(instrument);
        }

        public void enter(Node node, VirtualFrame frame) {
            Instrument.this.toolEventreceiver.enter(node, frame);
            if (nextInstrument != null) {
                nextInstrument.enter(node, frame);
            }
        }

        public void returnVoid(Node node, VirtualFrame frame) {
            Instrument.this.toolEventreceiver.returnVoid(node, frame);
            if (nextInstrument != null) {
                nextInstrument.returnVoid(node, frame);
            }
        }

        public void returnValue(Node node, VirtualFrame frame, Object result) {
            Instrument.this.toolEventreceiver.returnValue(node, frame, result);
            if (nextInstrument != null) {
                nextInstrument.returnValue(node, frame, result);
            }
        }

        public void returnExceptional(Node node, VirtualFrame frame, Exception exception) {
            Instrument.this.toolEventreceiver.returnExceptional(node, frame, exception);
            if (nextInstrument != null) {
                nextInstrument.returnExceptional(node, frame, exception);
            }
        }

        public String instrumentationInfo() {
            if (Instrument.this.instrumentInfo != null) {
                return Instrument.this.instrumentInfo;
            }
            return toolEventreceiver.getClass().getSimpleName();
        }
    }

}

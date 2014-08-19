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
package com.oracle.truffle.api.instrument.impl;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Abstract implementation of Truffle {@link Node}s used as AST {@link Probe}s and
 * {@link Instrument}s. A {@link Probe} manages its attached {@link Instrument}s by appending them
 * to a chain through which {@link ExecutionEvents} are propagated.
 */
public abstract class InstrumentationNode extends Node implements ExecutionEvents {

    interface ProbeCallback {
        void newTagAdded(ProbeImpl probe, SyntaxTag tag);
    }

    static ProbeImpl createProbe(SourceSection source, ProbeCallback probeCallback) {
        return new ProbeImpl(source, probeCallback);
    }

    /**
     * Next instrumentation node in chain.
     */
    @Child protected InstrumentationNode next;

    protected InstrumentationNode() {
    }

    /**
     * Gets the {@link Probe} to which this instrument is attached; {@code null} if not attached.
     */
    protected Probe getProbe() {
        final InstrumentationNode parent = (InstrumentationNode) getParent();
        return parent == null ? null : parent.getProbe();
    }

    /**
     * Add an instrument to the end of this instrument chain.
     */
    private void internalAddInstrument(Instrument newInstrument) {
        if (next == null) {
            this.next = insert(newInstrument);
        } else {
            next.internalAddInstrument(newInstrument);
        }
    }

    /**
     * Remove an instrument from this instrument chain. If no matching instrument is found, a
     * {@link RuntimeException} is thrown.
     *
     * @param oldInstrument The {@link Instrument} to remove.
     */
    private void internalRemoveInstrument(Instrument oldInstrument) {
        if (next == null) {
            throw new RuntimeException("Couldn't find probe to remove: " + oldInstrument);
        } else if (next == oldInstrument) {
            if (oldInstrument.next == null) {
                this.next = null;
            } else {
                this.next = insert(oldInstrument.next);
                oldInstrument.next = null;
            }
        } else {
            next.internalRemoveInstrument(oldInstrument);
        }
    }

    /**
     * Reports to the instance of {@link Probe} holding this instrument, if any, that some essential
     * state has changed that requires deoptimization.
     */
    @CompilerDirectives.SlowPath
    protected void notifyProbeChanged(Instrument instrument) {
        Probe probe = getProbe();
        if (probe != null) {
            final ProbeImpl probeImpl = (ProbeImpl) probe;
            probeImpl.notifyProbeChanged(instrument);
        }
    }

    /**
     * Informs the instrument that execution is just about to enter an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalEnter(Node, VirtualFrame)} to inform all instruments in the chain.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of entry
     */
    protected void internalEnter(Node astNode, VirtualFrame frame) {
        enter(astNode, frame);
        if (next != null) {
            next.internalEnter(astNode, frame);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalEnter(Node, VirtualFrame)} to inform all instruments in the chain. In this
     * case, there is no return value.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     */
    protected void internalLeave(Node astNode, VirtualFrame frame) {
        leave(astNode, frame);
        if (next != null) {
            next.internalLeave(astNode, frame);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, boolean)} to inform all instruments in the chain.
     * In this case, a boolean value was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The boolean result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, boolean result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, byte)} to inform all instruments in the chain. In
     * this case, a byte value was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The byte result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, byte result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, short)} to inform all instruments in the chain. In
     * this case, a short value was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The short result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, short result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, int)} to inform all instruments in the chain. In
     * this case, a int value was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The int result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, int result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, long)} to inform all instruments in the chain. In
     * this case, a long value was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The long result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, long result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, char)} to inform all instruments in the chain. In
     * this case, a char value was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The char result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, char result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, float)} to inform all instruments in the chain. In
     * this case, a float value was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The float result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, float result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, double)} to inform all instruments in the chain. In
     * this case, a double value was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The double result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, double result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeave(Node, VirtualFrame, Object)} to inform all instruments in the chain. In
     * this case, an Object was returned.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param result The Object result
     */
    protected void internalLeave(Node astNode, VirtualFrame frame, Object result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    /**
     * Informs the instrument that execution has just returned from an AST node with which this
     * instrumentation node is associated. This will continue to call
     * {@link #internalLeaveExceptional(Node, VirtualFrame, Exception)} to inform all instruments in
     * the chain. In this case, a exception (sometimes containing a value) was thrown.
     *
     * @param astNode The {@link Node} that was entered
     * @param frame The {@link VirtualFrame} at the time of exit
     * @param e The exception
     */
    protected void internalLeaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        leaveExceptional(astNode, frame, null);
        if (next != null) {
            next.internalLeaveExceptional(astNode, frame, e);
        }
    }

    /**
     * Holder of a chain of {@linkplain InstrumentationNode instruments}: manages the
     * {@link Assumption} that no {@link Instrument}s have been added or removed and that none of
     * the attached instruments have changed state in a way that would require deopt.
     * <p>
     * An instance is intended to be shared by every clone of the AST node with which it is
     * originally attached, so it holds no parent pointer.
     * <p>
     * Each probe is associated with a {@link SourceSection}, not necessarily uniquely, although
     * such a policy could be enforced for some uses.
     * <p>
     * Each {@link Probe} be categorized by one or more {@linkplain SyntaxTag tags}, signifying
     * information useful for instrumentation about its AST location(s).
     */
    static final class ProbeImpl extends InstrumentationNode implements Probe {

        private final ProbeCallback probeCallback;

        // TODO (mlvdv) assumption model broken
        @CompilerDirectives.CompilationFinal private Assumption probeUnchanged;

        @CompilerDirectives.CompilationFinal private SyntaxTagTrap trap = null;

        /**
         * The collection of tags for this instrumentation node
         */
        private final ArrayList<SyntaxTag> tags = new ArrayList<>();

        /**
         * The region of source code associated with this probe. Note that this is distinct from
         * {@link Node#getSourceSection()}, which is {@code null} for all instances of
         * {@link InstrumentationNode} since they have no corresponding source of their own.
         */
        private final SourceSection source;

        /**
         * Constructor.
         *
         * @param source The {@link SourceSection} associated with this probe.
         * @param probeCallback The {@link ProbeCallback} to inform when tags have been added to
         *            this probe.
         */
        private ProbeImpl(SourceSection source, ProbeCallback probeCallback) {
            this.probeCallback = probeCallback;
            this.source = source;
            this.probeUnchanged = Truffle.getRuntime().createAssumption();
            this.next = null;
        }

        public SourceSection getSourceLocation() {
            return source;
        }

        @SlowPath
        public void tagAs(SyntaxTag tag) {
            assert tag != null;
            if (!tags.contains(tag)) {
                tags.add(tag);
                probeCallback.newTagAdded(this, tag);
            }
        }

        public boolean isTaggedAs(SyntaxTag tag) {
            assert tag != null;
            return tags.contains(tag);
        }

        public Iterable<SyntaxTag> getSyntaxTags() {
            return tags;
        }

        /**
         * Adds the given {@link Instrument} to this probe's chain of instruments. This method does
         * not check to see if the same instrument has already been added.
         *
         * @param instrument The instrument to add to this probe.
         */
        @SlowPath
        public void addInstrument(Instrument instrument) {
            probeUnchanged.invalidate();
            super.internalAddInstrument(instrument);
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        /**
         * Removes the given instrument from the chain of instruments. If no matching instrument is
         * found, a {@link RuntimeException} is thrown.
         *
         * @param instrument The instrument to remove from this probe.
         */
        @SlowPath
        public void removeInstrument(Instrument instrument) {
            probeUnchanged.invalidate();
            super.internalRemoveInstrument(instrument);
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        /**
         * Returns this probe.
         */
        @Override
        protected Probe getProbe() {
            return this;
        }

        @Override
        @SlowPath
        protected void notifyProbeChanged(Instrument instrument) {
            probeUnchanged.invalidate();
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        @SlowPath
        void setTrap(SyntaxTagTrap trap) {
            assert trap == null || isTaggedAs(trap.getTag());
            probeUnchanged.invalidate();
            this.trap = trap;
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        public void enter(Node astNode, VirtualFrame frame) {
            if (trap != null || next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                if (trap != null) {
                    trap.tagTrappedAt(astNode, frame.materialize());
                }
                if (next != null) {
                    next.internalEnter(astNode, frame);
                }
            }
        }

        public void leave(Node astNode, VirtualFrame frame) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, boolean result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, byte result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, short result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, int result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, long result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, char result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, float result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, double result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leave(Node astNode, VirtualFrame frame, Object result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeaveExceptional(astNode, frame, e);
            }
        }

    }

}

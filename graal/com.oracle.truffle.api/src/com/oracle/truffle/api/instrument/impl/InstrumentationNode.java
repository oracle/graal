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

/**
 * Abstract implementation of Truffle {@link Node} to be used for AST probes and instruments.
 * <p>
 * Coordinates propagation of Truffle AST {@link ExecutionEvents}.
 */
public abstract class InstrumentationNode extends Node implements ExecutionEvents {

    interface ProbeCallback {
        void newTagAdded(ProbeImpl probe, PhylumTag tag);
    }

    /**
     * Creates a new {@link Probe}, presumed to be unique to a particular {@linkplain SourceSection}
     * extent of guest language source code.
     *
     * @return a new probe
     */
    static ProbeImpl createProbe(SourceSection sourceSection, ProbeCallback probeCallback) {
        return new ProbeImpl(sourceSection, probeCallback);
    }

    /**
     * Next in chain.
     */
    @Child protected InstrumentationNode next;

    protected InstrumentationNode() {
    }

    /**
     * @return the instance of {@link Probe} to which this instrument is attached.
     */
    protected Probe getProbe() {
        final InstrumentationNode parent = (InstrumentationNode) getParent();
        return parent == null ? null : parent.getProbe();
    }

    /**
     * Add a probe to the end of this probe chain.
     */
    private void internalAddInstrument(Instrument newInstrument) {
        if (next == null) {
            this.next = insert(newInstrument);
        } else {
            next.internalAddInstrument(newInstrument);
        }
    }

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
     * Reports to the instance of {@link Probe} holding this instrument that some essential state
     * has changed that requires deoptimization.
     */
    @CompilerDirectives.SlowPath
    protected void notifyProbeChanged(Instrument instrument) {
        final ProbeImpl probe = (ProbeImpl) getProbe();
        probe.notifyProbeChanged(instrument);
    }

    protected void internalEnter(Node astNode, VirtualFrame frame) {
        enter(astNode, frame);
        if (next != null) {
            next.internalEnter(astNode, frame);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame) {
        leave(astNode, frame);
        if (next != null) {
            next.internalLeave(astNode, frame);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, boolean result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, byte result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, short result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, int result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, long result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, char result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, float result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, double result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeave(Node astNode, VirtualFrame frame, Object result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    protected void internalLeaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        leaveExceptional(astNode, frame, null);
        if (next != null) {
            next.internalLeaveExceptional(astNode, frame, e);
        }
    }

    /**
     * Holder of a chain of {@linkplain InstrumentationNode instruments}: manages the
     * {@link Assumption} that none of the instruments have changed since last checked.
     * <p>
     * An instance is intended to be shared by every clone of the AST node with which it is
     * originally attached, so it holds no parent pointer.
     * <p>
     * May be categorized by one or more {@linkplain PhylumTag tags}, signifying information useful
     * for instrumentation about its AST location(s).
     */
    static final class ProbeImpl extends InstrumentationNode implements Probe {

        private final ProbeCallback probeCallback;

        /**
         * Source information about the AST node (and its clones) to which this probe is attached.
         */
        private final SourceSection probedSourceSection;

        // TODO (mlvdv) assumption model broken
        @CompilerDirectives.CompilationFinal private Assumption probeUnchanged;

        @CompilerDirectives.CompilationFinal private PhylumTrap trap = null;

        private final ArrayList<PhylumTag> tags = new ArrayList<>();

        private ProbeImpl(SourceSection sourceSection, ProbeCallback probeCallback) {
            this.probeCallback = probeCallback;
            this.probedSourceSection = sourceSection;
            this.probeUnchanged = Truffle.getRuntime().createAssumption();
            this.next = null;
        }

        public SourceSection getSourceLocation() {
            return probedSourceSection;
        }

        @SlowPath
        public void tagAs(PhylumTag tag) {
            assert tag != null;
            if (!tags.contains(tag)) {
                tags.add(tag);
                probeCallback.newTagAdded(this, tag);
            }
        }

        public boolean isTaggedAs(PhylumTag tag) {
            assert tag != null;
            return tags.contains(tag);
        }

        public Iterable<PhylumTag> getPhylumTags() {
            return tags;
        }

        @SlowPath
        public void addInstrument(Instrument instrument) {
            probeUnchanged.invalidate();
            super.internalAddInstrument(instrument);
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        @SlowPath
        public void removeInstrument(Instrument instrument) {
            probeUnchanged.invalidate();
            super.internalRemoveInstrument(instrument);
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

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
        void setTrap(PhylumTrap trap) {
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
                    trap.phylumTrappedAt(astNode, frame.materialize());
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

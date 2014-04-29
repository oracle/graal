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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Abstract implementation of Truffle {@link Node} to be used for AST probes and instruments.
 * <p>
 * Coordinates propagation of Truffle AST {@link ExecutionEvents}.
 */
public abstract class InstrumentationNode extends Node implements ExecutionEvents {

    // TODO (mlvdv) This is a pretty awkward design; it is a priority to revise it.

    /**
     * Creates a new {@link Probe}, presumed to be unique to a particular {@linkplain SourceSection}
     * extent of guest language source code.
     *
     * @param eventListener an optional listener for certain instrumentation-related events.
     * @return a new probe
     */
    static Probe createProbe(InstrumentationImpl instrumentation, SourceSection sourceSection, InstrumentEventListener eventListener) {
        return new ProbeImpl(instrumentation, sourceSection, eventListener);
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
    public Probe getProbe() {
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

    private void internalEnter(Node astNode, VirtualFrame frame) {
        enter(astNode, frame);
        if (next != null) {
            next.internalEnter(astNode, frame);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame) {
        leave(astNode, frame);
        if (next != null) {
            next.internalLeave(astNode, frame);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, boolean result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, byte result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, short result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, int result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, long result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, char result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, float result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, double result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeave(Node astNode, VirtualFrame frame, Object result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    private void internalLeaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
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
    private static final class ProbeImpl extends InstrumentationNode implements Probe {

        final InstrumentationImpl instrumentation;

        final InstrumentEventListener eventListener;

        @CompilerDirectives.CompilationFinal private Assumption probeUnchanged;

        /**
         * When in stepping mode, ordinary line breakpoints are ignored, but every entry at a line
         * will cause a halt.
         */
        @CompilerDirectives.CompilationFinal private boolean stepping;

        /**
         * Source information about the AST node to which this instrumentation is attached.
         */
        private final SourceSection probedSourceSection;

        private final Set<PhylumTag> tags = EnumSet.noneOf(PhylumTag.class);

        private ProbeImpl(InstrumentationImpl instrumentation, SourceSection sourceSection, InstrumentEventListener eventListener) {
            this.instrumentation = instrumentation;
            this.probedSourceSection = sourceSection;
            this.eventListener = eventListener == null ? NullInstrumentEventListener.INSTANCE : eventListener;
            this.probeUnchanged = Truffle.getRuntime().createAssumption();
            this.next = null;
        }

        @Override
        public Probe getProbe() {
            return this;
        }

        @Override
        protected void notifyProbeChanged(Instrument instrument) {
            probeUnchanged.invalidate();
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        public SourceSection getSourceLocation() {
            return probedSourceSection;
        }

        public void tagAs(PhylumTag tag) {
            assert tag != null;
            if (!tags.contains(tag)) {
                tags.add(tag);
                instrumentation.newTagAdded(this, tag);
            }
        }

        public boolean isTaggedAs(PhylumTag tag) {
            assert tag != null;
            return tags.contains(tag);
        }

        public Set<PhylumTag> getPhylumTags() {
            return tags;
        }

        public void setStepping(boolean stepping) {
            if (this.stepping != stepping) {
                this.stepping = stepping;
                probeUnchanged.invalidate();
                probeUnchanged = Truffle.getRuntime().createAssumption();
            }
        }

        public boolean isStepping() {
            return stepping;
        }

        @CompilerDirectives.SlowPath
        public void addInstrument(Instrument instrument) {
            probeUnchanged.invalidate();
            super.internalAddInstrument(instrument);
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        @CompilerDirectives.SlowPath
        public void removeInstrument(Instrument instrument) {
            probeUnchanged.invalidate();
            super.internalRemoveInstrument(instrument);
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        public void notifyEnter(Node astNode, VirtualFrame frame) {
            if (stepping || next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                if (stepping) {
                    eventListener.haltedAt(astNode, frame.materialize());
                }
                if (next != null) {
                    next.internalEnter(astNode, frame);
                }
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, boolean result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, byte result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, short result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, int result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, long result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, char result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, float result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, double result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeave(Node astNode, VirtualFrame frame, Object result) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeave(astNode, frame, result);
            }
        }

        public void notifyLeaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalLeaveExceptional(astNode, frame, e);
            }
        }

        public void enter(Node astNode, VirtualFrame frame) {
        }

        public void leave(Node astNode, VirtualFrame frame) {
        }

        public void leave(Node astNode, VirtualFrame frame, boolean result) {
            leave(astNode, frame, (Object) result);
        }

        public void leave(Node astNode, VirtualFrame frame, byte result) {
            leave(astNode, frame, (Object) result);
        }

        public void leave(Node astNode, VirtualFrame frame, short result) {
            leave(astNode, frame, (Object) result);
        }

        public void leave(Node astNode, VirtualFrame frame, int result) {
            leave(astNode, frame, (Object) result);
        }

        public void leave(Node astNode, VirtualFrame frame, long result) {
            leave(astNode, frame, (Object) result);
        }

        public void leave(Node astNode, VirtualFrame frame, char result) {
            leave(astNode, frame, (Object) result);
        }

        public void leave(Node astNode, VirtualFrame frame, float result) {
            leave(astNode, frame, (Object) result);
        }

        public void leave(Node astNode, VirtualFrame frame, double result) {
            leave(astNode, frame, (Object) result);
        }

        public void leave(Node astNode, VirtualFrame frame, Object result) {
        }

        public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        }

    }

}

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
package com.oracle.truffle.api.nodes.instrument;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * A <strong>probe</strong>: a Truffle instrumentation node that holds code to perform some action
 * when notified (via a {@linkplain InstrumentationProxyNode proxy node} in the AST) of a
 * {@linkplain InstrumentationProbeEvents probe event} taking place at the AST node.
 * <p>
 * Probes are only active when attached to a {@linkplain ProbeChain "probe chain"} that is referred
 * to by one or more {@linkplain InstrumentationProxyNode proxy nodes} in an AST.
 */
public abstract class InstrumentationProbeNode extends Node implements InstrumentationNode, InstrumentationProbeEvents {

    /**
     * Next in chain.
     */
    @Child protected InstrumentationProbeNode next;

    protected InstrumentationProbeNode() {
    }

    protected int countProbes() {
        return next == null ? 0 : next.countProbes() + 1;
    }

    protected boolean isStepping() {
        final InstrumentationProbeNode parent = (InstrumentationProbeNode) getParent();
        return parent.isStepping();
    }

    /**
     * Add a probe to the end of this probe chain.
     */
    protected void internalAppendProbe(InstrumentationProbeNode newProbeNode) {
        if (next == null) {
            this.next = adoptChild(newProbeNode);
        } else {
            next.internalAppendProbe(newProbeNode);
        }
    }

    protected void internalRemoveProbe(InstrumentationProbeNode oldProbeNode) {
        if (next == null) {
            throw new RuntimeException("Couldn't find probe to remove: " + oldProbeNode);
        } else if (next == oldProbeNode) {
            if (oldProbeNode.next == null) {
                this.next = null;
            } else {
                this.next = adoptChild(oldProbeNode.next);
                oldProbeNode.next = null;
            }
        } else {
            next.internalRemoveProbe(oldProbeNode);
        }
    }

    /**
     * Passes up the chain notification that a probe has changed its execution state in a way that
     * invalidates fast path code. Assumes that there is an instance of {@link ProbeChain} at the
     * head of the chain.
     */
    @CompilerDirectives.SlowPath
    protected void notifyProbeChanged(InstrumentationProbeNode probeNode) {
        final InstrumentationProbeNode parent = (InstrumentationProbeNode) getParent();
        parent.notifyProbeChanged(probeNode);
    }

    // TODO (mlvdv) making the internal*() methods public is a workaround for a bug/limitation in
    // the Truffle compiler; they are intended to be private.

    public void internalEnter(Node astNode, VirtualFrame frame) {
        enter(astNode, frame);
        if (next != null) {
            next.internalEnter(astNode, frame);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame) {
        leave(astNode, frame);
        if (next != null) {
            next.internalLeave(astNode, frame);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, boolean result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, byte result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, short result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, int result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, long result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, char result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, float result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, double result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeave(Node astNode, VirtualFrame frame, Object result) {
        leave(astNode, frame, result);
        if (next != null) {
            next.internalLeave(astNode, frame, result);
        }
    }

    public void internalLeaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
        leaveExceptional(astNode, frame, null);
        if (next != null) {
            next.internalLeaveExceptional(astNode, frame, e);
        }
    }

    public void internalReplace(Node oldAstNode, Node newAstNode, String reason) {
        replace(oldAstNode, newAstNode, reason);
        if (next != null) {
            next.internalReplace(oldAstNode, newAstNode, reason);
        }
    }

    /**
     * A probe implementation that implements all of {@link InstrumentationProbeEvents} with empty
     * methods; concrete subclasses can override only the methods for which something is to be done.
     */
    public static class DefaultProbeNode extends InstrumentationProbeNode {

        private final ExecutionContext executionContext;

        protected DefaultProbeNode(ExecutionContext context) {
            this.executionContext = context;
        }

        public ExecutionContext getContext() {
            return executionContext;
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

        public void replace(Node oldAstNode, Node newAstNode, String reason) {
        }

    }

    /**
     * Holder of a chain of {@linkplain InstrumentationProbeNode probes}: manages the
     * {@link Assumption} that the chain has not changed since checked checked.
     * <p>
     * May be categorized by one or more {@linkplain NodePhylum node phyla}, signifying information
     * useful for instrumentation about its AST location(s).
     */
    public static final class ProbeChain extends DefaultProbeNode implements PhylumMarked {

        @CompilerDirectives.CompilationFinal private Assumption probeUnchanged;

        /**
         * When in stepping mode, ordinary line breakpoints are ignored, but every entry at a line
         * will cause a halt.
         */
        @CompilerDirectives.CompilationFinal private boolean stepping;

        /**
         * Source information about the node to which this probe chain is attached; it isn't
         * otherwise available. A probe chain is shared by every copy made during runtime, so there
         * is no parent pointer.
         */
        private final SourceSection probedSourceSection;

        private final Set<NodePhylum> phyla = EnumSet.noneOf(NodePhylum.class);

        private final String description; // for debugging

        /**
         * Creates a new, empty chain of {@linkplain InstrumentationProbeNode probes}, to which
         * probes can be added/removed, and all of which will be notified of
         * {@linkplain InstrumentationProbeEvents events} when the chain is notified.
         */
        public ProbeChain(ExecutionContext context, SourceSection sourceSection, String description) {
            super(context);
            this.probeUnchanged = Truffle.getRuntime().createAssumption();
            this.probedSourceSection = sourceSection;
            this.description = description;
            this.next = null;
        }

        public int probeCount() {
            return countProbes();
        }

        public String getDescription() {
            return description;
        }

        public SourceSection getProbedSourceSection() {
            return probedSourceSection;
        }

        /**
         * Mark this probe chain as being associated with an AST node in some category useful for
         * debugging and other tools.
         */
        public void markAs(NodePhylum phylum) {
            assert phylum != null;
            phyla.add(phylum);
        }

        /**
         * Is this probe chain as being associated with an AST node in some category useful for
         * debugging and other tools.
         */
        public boolean isMarkedAs(NodePhylum phylum) {
            assert phylum != null;
            return phyla.contains(phylum);
        }

        /**
         * In which categories is the AST (with which this probe is associated) marked?
         */
        public Set<NodePhylum> getPhylumMarks() {
            return phyla;
        }

        /**
         * Change <em>stepping mode</em> for statements.
         */
        public void setStepping(boolean stepping) {
            if (this.stepping != stepping) {
                this.stepping = stepping;
                probeUnchanged.invalidate();
                probeUnchanged = Truffle.getRuntime().createAssumption();
            }
        }

        @Override
        protected boolean isStepping() {
            return stepping;
        }

        @Override
        protected int countProbes() {
            // The head of the chain does not itself hold a probe
            return next == null ? 0 : next.countProbes();
        }

        @Override
        protected void notifyProbeChanged(InstrumentationProbeNode probeNode) {
            probeUnchanged.invalidate();
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        @CompilerDirectives.SlowPath
        public void appendProbe(InstrumentationProbeNode newProbeNode) {
            probeUnchanged.invalidate();
            super.internalAppendProbe(newProbeNode);
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        @CompilerDirectives.SlowPath
        public void removeProbe(InstrumentationProbeNode oldProbeNode) {
            probeUnchanged.invalidate();
            super.internalRemoveProbe(oldProbeNode);
            probeUnchanged = Truffle.getRuntime().createAssumption();
        }

        public void notifyEnter(Node astNode, VirtualFrame frame) {
            if (stepping || next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                if (stepping) {
                    getContext().getDebugManager().haltedAt(astNode, frame.materialize());
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

        public void notifyReplace(Node oldAstNode, Node newAstNode, String reason) {
            if (next != null) {
                if (!probeUnchanged.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                }
                next.internalReplace(oldAstNode, newAstNode, reason);
            }
        }

    }

}

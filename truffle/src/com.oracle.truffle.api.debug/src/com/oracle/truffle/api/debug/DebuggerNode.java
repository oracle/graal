/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.Set;
import java.util.concurrent.Callable;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

/**
 * Base class for all execution event nodes that were inserted by the debugger.
 */
abstract class DebuggerNode extends ExecutionEventNode implements InsertableNode {

    abstract EventBinding<?> getBinding();

    protected final EventContext context;

    /*
     * Fields for the duplication check. If multiple debugger nodes are installed at the same source
     * location, only the first should be triggered and the other ones should be processed in a
     * batch to avoid sending duplicated events. Only the execution of debugger nodes of the same
     * thread and the same session should be marked as duplicates.
     */
    private volatile boolean singleThreadSession = true;
    private volatile Thread cachedThread;
    private DebuggerSession cachedSessionDuplicate;
    // A map of threads and sessions in which this node is a duplicate to others
    private volatile EconomicMap<Thread, Object> duplicateInThreads;
    private volatile Assumption noDuplicateAssumption = Truffle.getRuntime().createAssumption("No duplicate node assumption");

    DebuggerNode(EventContext context) {
        this.context = context;
    }

    Breakpoint getBreakpoint() {
        return null;
    }

    abstract boolean isStepNode();

    abstract Set<SuspendAnchor> getSuspendAnchors();

    abstract boolean isActiveAt(SuspendAnchor anchor);

    final EventContext getContext() {
        return context;
    }

    @Override
    public void setParentOf(Node child) {
        insert(child);
    }

    void markAsDuplicate(DebuggerSession session) {
        CompilerAsserts.neverPartOfCompilation();
        noDuplicateAssumption.invalidate();
        if (singleThreadSession) {
            Thread thread = Thread.currentThread();
            if (cachedThread == thread && cachedSessionDuplicate == null) {
                cachedSessionDuplicate = session;
                return;
            } else if (cachedThread == null) {
                Boolean marked = atomic(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        if (cachedThread == null) {
                            cachedThread = thread;
                            cachedSessionDuplicate = session;
                            return true;
                        }
                        return false;
                    }
                });
                if (marked) {
                    return;
                }
                // fall through to slowpath
            }
        }
        singleThreadSession = false;
        markAsDuplicateSlowPath(session);
    }

    @SuppressWarnings("unchecked")
    private void markAsDuplicateSlowPath(DebuggerSession session) {
        atomic(new Runnable() {
            @Override
            public void run() {
                if (duplicateInThreads == null) {
                    duplicateInThreads = EconomicMap.create();
                }
                Thread thread = Thread.currentThread();
                Object sessions = duplicateInThreads.get(thread);
                if (sessions == null) {
                    duplicateInThreads.put(thread, session);
                } else if (sessions instanceof DebuggerSession) {
                    EconomicSet<DebuggerSession> set = EconomicSet.create();
                    set.add((DebuggerSession) sessions);
                    set.add(session);
                    duplicateInThreads.put(thread, set);
                } else {
                    ((EconomicSet<DebuggerSession>) sessions).add(session);
                }
            }
        });
    }

    boolean consumeIsDuplicate(DebuggerSession session) {
        if (noDuplicateAssumption.isValid()) {
            return false;
        }
        if (cachedThread == Thread.currentThread()) {
            // optimized version for single thread only
            if (cachedSessionDuplicate == session) {
                cachedSessionDuplicate = null;
                if (singleThreadSession) {
                    noDuplicateAssumption = Truffle.getRuntime().createAssumption("No duplicate node assumption");
                }
                return true;
            }
        }
        if (singleThreadSession) {
            return false;
        }
        // version for multiple threads and sessions
        return isDuplicateSlowPath(session);
    }

    @TruffleBoundary
    @SuppressWarnings("unchecked")
    private boolean isDuplicateSlowPath(DebuggerSession session) {
        return atomic(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                if (duplicateInThreads != null) {
                    try {
                        Thread thread = Thread.currentThread();
                        Object sessions = duplicateInThreads.get(thread);
                        if (sessions == session) {
                            duplicateInThreads.removeKey(thread);
                            return true;
                        } else if (sessions instanceof EconomicSet) {
                            EconomicSet<DebuggerSession> set = (EconomicSet<DebuggerSession>) sessions;
                            boolean contains = set.contains(session);
                            if (contains) {
                                set.remove(session);
                                if (set.isEmpty()) {
                                    duplicateInThreads.removeKey(thread);
                                }
                            }
                            return contains;
                        }
                    } finally {
                        if (duplicateInThreads.isEmpty()) {
                            duplicateInThreads = null;
                            singleThreadSession = true;
                            if (cachedSessionDuplicate == null) {
                                cachedThread = null;
                                noDuplicateAssumption = Truffle.getRuntime().createAssumption("No duplicate node assumption");
                            }
                        }
                    }
                }
                return false;
            }
        });
    }

    /** Implemented by nodes that provide input values lazily to the {@link SuspendedEvent}. */
    interface InputValuesProvider {

        Object[] getDebugInputValues(MaterializedFrame frame);

    }
}

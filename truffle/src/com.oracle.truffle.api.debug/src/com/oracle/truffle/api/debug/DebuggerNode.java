/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.debug;

import java.util.Set;
import java.util.concurrent.Callable;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
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
    private volatile long cachedThreadId;
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

    @Override
    protected Object onUnwind(VirtualFrame frame, Object info) {
        if (info instanceof ChangedReturnInfo) {
            return ((ChangedReturnInfo) info).returnValue;
        }
        return super.onUnwind(frame, info);
    }

    void markAsDuplicate(DebuggerSession session) {
        CompilerAsserts.neverPartOfCompilation();
        noDuplicateAssumption.invalidate();
        if (singleThreadSession) {
            Thread thread = Thread.currentThread();
            if (cachedThreadId == thread.getId() && cachedSessionDuplicate == null) {
                cachedSessionDuplicate = session;
                return;
            } else if (cachedThreadId == 0) {
                Boolean marked = atomic(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        if (cachedThreadId == 0) {
                            cachedThreadId = thread.getId();
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
        if (cachedThreadId == Thread.currentThread().getId()) {
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
                                cachedThreadId = 0;
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

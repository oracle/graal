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

import com.oracle.truffle.api.debug.DebuggerSession.SteppingLocation;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

/**
 * Base class for all execution event nodes that were inserted by the debugger.
 */
abstract class DebuggerNode extends ExecutionEventNode {

    abstract EventBinding<?> getBinding();

    protected final EventContext context;

    /*
     * Fields for the duplication check. If multiple debugger nodes are installed at the same source
     * location, only the first should be triggered and the other ones should be ignored to avoid
     * sending duplicated events. Only the execution of debugger nodes of the same thread should be
     * ignored.
     */
    private volatile Thread cachedThread;
    private boolean cachedThreadDuplicate;
    private volatile ThreadLocal<Boolean> duplicateThreadLocal;

    DebuggerNode(EventContext context) {
        this.context = context;
    }

    Breakpoint getBreakpoint() {
        return null;
    }

    abstract boolean isStepNode();

    abstract SteppingLocation getSteppingLocation();

    final EventContext getContext() {
        return context;
    }

    void markAsDuplicate() {
        Thread thread = Thread.currentThread();
        if (cachedThread == thread) {
            cachedThreadDuplicate = true;
        } else if (cachedThread == null) {
            synchronized (getRootNode()) {
                if (cachedThread == null) {
                    cachedThread = thread;
                    cachedThreadDuplicate = true;
                    return;
                }
            }
            // fall through to slowpath
        }
        getDuplicateThreadLocal().set(Boolean.FALSE);
    }

    boolean consumeIsDuplicate() {
        if (cachedThread == Thread.currentThread()) {
            // optimized version for single thread only
            if (cachedThreadDuplicate) {
                cachedThreadDuplicate = false;
                return true;
            }
            return false;
        } else if (cachedThread == null) {
            // node was never consumed so its not a duplicate
            return false;
        } else {
            // version for multiple threads
            return isDuplicateSlowPath();
        }
    }

    private ThreadLocal<Boolean> getDuplicateThreadLocal() {
        if (duplicateThreadLocal == null) {
            synchronized (getRootNode()) {
                if (duplicateThreadLocal == null) {
                    duplicateThreadLocal = new ThreadLocal<>();
                }
            }
        }
        return duplicateThreadLocal;
    }

    private boolean isDuplicateSlowPath() {
        ThreadLocal<Boolean> suspend = getDuplicateThreadLocal();
        Boolean b = suspend.get();
        if (b == null) {
            return true;
        } else {
            boolean value = b.booleanValue();
            if (!value) {
                duplicateThreadLocal.set(Boolean.TRUE);
            }
            return value;
        }
    }
}

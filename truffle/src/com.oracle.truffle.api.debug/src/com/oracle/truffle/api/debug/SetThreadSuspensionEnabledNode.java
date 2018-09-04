/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.debug.DebuggerSession.ThreadSuspension;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

/**
 * This node sets thread-local enabled suspension flag. It uses {@link DebuggerSession}'s
 * {@link ThreadLocal} field, which is cached in 10 threads for fast access.
 */
abstract class SetThreadSuspensionEnabledNode extends Node {

    static final int CACHE_LIMIT = 10;

    public final void execute(boolean suspensionEnabled, DebuggerSession[] sessions) {
        execute(suspensionEnabled, sessions, Thread.currentThread().getId());
    }

    protected abstract void execute(boolean suspensionEnabled, DebuggerSession[] sessions, long threadId);

    @Specialization(guards = {"sessions.length == 1", "threadId == currentThreadId"}, limit = "CACHE_LIMIT")
    protected void executeCached(boolean suspensionEnabled,
                    @SuppressWarnings("unused") DebuggerSession[] sessions,
                    @SuppressWarnings("unused") long threadId,
                    @SuppressWarnings("unused") @Cached("currentThreadId()") long currentThreadId,
                    @Cached("getThreadSuspension(sessions)") ThreadSuspension threadSuspension) {
        threadSuspension.enabled = suspensionEnabled;
    }

    @ExplodeLoop
    @Specialization(replaces = "executeCached")
    protected void executeGeneric(boolean suspensionEnabled,
                    DebuggerSession[] sessions,
                    @SuppressWarnings("unused") long threadId) {
        for (DebuggerSession session : sessions) {
            session.setThreadSuspendEnabled(suspensionEnabled);
        }
    }

    static long currentThreadId() {
        return Thread.currentThread().getId();
    }

    @TruffleBoundary
    protected ThreadSuspension getThreadSuspension(DebuggerSession[] sessions) {
        assert sessions.length == 1;
        ThreadSuspension threadSuspension = new ThreadSuspension(true);
        sessions[0].threadSuspensions.set(threadSuspension);
        return threadSuspension;
    }

}

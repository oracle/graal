/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.tools.dap.types.ContinuedEvent;
import com.oracle.truffle.tools.dap.types.DebugProtocolClient;
import com.oracle.truffle.tools.dap.types.StoppedEvent;
import com.oracle.truffle.tools.dap.types.ThreadEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ThreadsHandler implements ThreadsListener {

    private static final Pattern LOGMESSAGE_VARIABLE_REGEXP = Pattern.compile("\\{(.*?)\\}");

    private final ExecutionContext context;
    private final DebuggerSession debuggerSession;
    private final Map<Thread, Integer> thread2Ids = new HashMap<>();
    private final Map<Integer, Thread> id2threads = new HashMap<>();
    private final Map<Integer, SuspendedThreadInfo> suspendedThreads = new HashMap<>();

    private int lastId = 0;

    public ThreadsHandler(ExecutionContext context, DebuggerSession debuggerSession) {
        this.context = context;
        this.debuggerSession = debuggerSession;
    }

    @Override
    public void onThreadInitialized(TruffleContext ctx, Thread thread) {
        Integer id;
        synchronized (thread2Ids) {
            id = ++lastId;
            thread2Ids.put(thread, id);
            id2threads.put(id, thread);
        }
        DebugProtocolClient client = context.getClient();
        if (client != null) {
            client.thread(ThreadEvent.EventBody.create("started", id));
        }
    }

    @Override
    public void onThreadDisposed(TruffleContext ctx, Thread thread) {
        Integer id;
        synchronized (thread2Ids) {
            id = thread2Ids.remove(thread);
            id2threads.remove(id);
        }
        if (id != null) {
            DebugProtocolClient client = context.getClient();
            if (client != null) {
                client.thread(ThreadEvent.EventBody.create("exited", id));
            }
        }
    }

    public List<com.oracle.truffle.tools.dap.types.Thread> getThreads() {
        synchronized (thread2Ids) {
            final List<com.oracle.truffle.tools.dap.types.Thread> ret = new ArrayList<>(thread2Ids.size());
            for (Map.Entry<Thread, Integer> entry : thread2Ids.entrySet()) {
                ret.add(com.oracle.truffle.tools.dap.types.Thread.create(entry.getValue(), entry.getKey().getName()));
            }
            return ret;
        }
    }

    public void threadSuspended(Thread thread, SuspendedEvent event) {
        SuspendedThreadInfo info = null;
        synchronized (thread2Ids) {
            Integer id = thread2Ids.get(thread);
            if (id != null) {
                info = new SuspendedThreadInfo(id, event);
                suspendedThreads.put(id, info);
            }
        }
        if (info != null) {
            String reason = null;
            String description = null;
            List<String> logMessages = new ArrayList<>();
            boolean stop = true;
            for (Breakpoint bp : event.getBreakpoints()) {
                String logMessage = context.getBreakpointsHandler().getLogMessage(bp);
                if (logMessage != null) {
                    logMessages.add(logMessage);
                }
                stop &= context.getBreakpointsHandler().checkConditions(bp, event.getTopStackFrame());
                switch (bp.getKind()) {
                    case EXCEPTION:
                        reason = "exception";
                        description = "Paused on exception";
                        break;
                    case HALT_INSTRUCTION:
                        reason = "debugger_statement";
                        description = "Paused on debugger statement";
                        break;
                    case SOURCE_LOCATION:
                        reason = "breakpoint";
                        description = "Paused on breakpoint";
                }
            }
            if (stop) {
                if (logMessages.isEmpty()) {
                    DebugException exception = event.getException();
                    if (exception != null) {
                        boolean uncaught = exception.getCatchLocation() == null;
                        description = uncaught ? "Paused on uncaught exception" : "Paused on caught exception";
                    }
                    if (reason == null) {
                        reason = "debugger_statement";
                        description = "Paused on debugger statement";
                    }
                    DebugProtocolClient client = context.getClient();
                    if (client != null) {
                        client.stopped(StoppedEvent.EventBody.create(reason).setThreadId(info.getThreadId()).setDescription(description));
                    }
                } else {
                    info.executables.add(i -> {
                        for (String logMessage : logMessages) {
                            Matcher matcher = LOGMESSAGE_VARIABLE_REGEXP.matcher(logMessage);
                            StringBuilder sb = new StringBuilder();
                            int idx = 0;
                            while (matcher.find()) {
                                String expression = matcher.group(1);
                                DebugValue value = VariablesHandler.getDebugValue(event.getTopStackFrame(), expression);
                                sb.append(logMessage.substring(idx, matcher.start())).append(value.toDisplayString());
                                idx = matcher.end();
                            }
                            sb.append(logMessage.substring(idx));
                            context.getInfo().println(sb.toString());
                        }
                        return true;
                    });
                }
                info.runExecutables();
            }
        }
    }

    public void executeInSuspendedThread(int id, Function<SuspendedThreadInfo, Boolean> task) {
        synchronized (thread2Ids) {
            SuspendedThreadInfo info = suspendedThreads.get(id);
            if (info == null) {
                for (SuspendedThreadInfo sti : suspendedThreads.values()) {
                    if (sti.id2Refs.containsKey(id)) {
                        info = sti;
                        break;
                    }
                }
            }
            if (info != null) {
                info.executables.add(task);
                return;
            }
        }
        task.apply(null);
    }

    public void threadResumed(int threadId) {
        synchronized (thread2Ids) {
            suspendedThreads.remove(threadId);
            if (suspendedThreads.isEmpty()) {
                lastId = 0;
            }
        }
        DebugProtocolClient client = context.getClient();
        if (client != null) {
            client.continued(ContinuedEvent.EventBody.create(threadId));
        }
    }

    public boolean pause(int threadId) {
        Thread t;
        synchronized (thread2Ids) {
            t = id2threads.get(threadId);
        }
        if (t != null) {
            debuggerSession.suspend(t);
            return true;
        }
        return false;
    }

    public final class SuspendedThreadInfo {

        private final int threadId;
        private final SuspendedEvent event;
        private Map<Object, Integer> ref2Ids = new HashMap<>(100);
        private Map<Integer, Object> id2Refs = new HashMap<>(100);
        private final BlockingQueue<Function<SuspendedThreadInfo, Boolean>> executables = new LinkedBlockingQueue<>();

        private SuspendedThreadInfo(int threadId, SuspendedEvent event) {
            this.threadId = threadId;
            this.event = event;
        }

        public int getThreadId() {
            return threadId;
        }

        public SuspendedEvent getSuspendedEvent() {
            return event;
        }

        public int getId(Object ref) {
            Integer id;
            synchronized (thread2Ids) {
                id = ref2Ids.get(ref);
                if (id == null) {
                    id = ++lastId;
                    ref2Ids.put(ref, id);
                    id2Refs.put(id, ref);
                }
            }
            return id;
        }

        public <T> T getById(Class<T> cls, int id) {
            Object ref;
            synchronized (thread2Ids) {
                ref = id2Refs.get(id);
            }
            return cls.isInstance(ref) ? cls.cast(ref) : null;
        }

        private void runExecutables() {
            boolean resume = false;
            while (!resume) {
                try {
                    Function<SuspendedThreadInfo, Boolean> task = executables.take();
                    resume = task.apply(this);
                } catch (InterruptedException ex) {
                }
            }
            threadResumed(threadId);
            Function<SuspendedThreadInfo, Boolean> task;
            while ((task = executables.poll()) != null) {
                task.apply(null);
            }
        }
    }
}

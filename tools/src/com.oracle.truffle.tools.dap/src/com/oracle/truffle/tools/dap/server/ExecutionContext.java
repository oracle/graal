/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.dap.types.DebugProtocolClient;
import java.io.PrintWriter;
import org.graalvm.collections.EconomicSet;

/**
 * The execution context.
 */
public final class ExecutionContext {

    private final TruffleInstrument.Env env;
    private final PrintWriter info;
    private final PrintWriter err;
    private final boolean inspectInternal;
    private final boolean inspectInitialization;
    private final TruffleLogger logger;
    private final boolean[] runPermission = new boolean[]{false};

    private volatile DebugProtocolClient client;
    private volatile EventBinding<ContextsListener> contextsBinding;
    private volatile LoadedSourcesHandler loadedSourcesHandler;
    private volatile EventBinding<LoadSourceListener> srcBinding;
    private volatile ThreadsHandler threadsHandler;
    private volatile EventBinding<ThreadsListener> thrBinding;
    private volatile BreakpointsHandler breakpointsHandler;
    private volatile StackFramesHandler stackFramesHandler;
    private volatile VariablesHandler variablesHandler;
    private boolean linesStartAt1 = true;
    private boolean columnsStartAt1 = true;

    private final EconomicSet<TruffleContext> contexts = EconomicSet.create();

    public ExecutionContext(TruffleInstrument.Env env, PrintWriter info, PrintWriter err, boolean inspectInternal, boolean inspectInitialization) {
        this.env = env;
        this.err = err;
        this.info = info;
        this.inspectInternal = inspectInternal;
        this.inspectInitialization = inspectInitialization;
        this.logger = env.getLogger("");
    }

    public void initSession(DebuggerSession debuggerSession) {
        this.loadedSourcesHandler = new LoadedSourcesHandler(this, debuggerSession);
        this.contextsBinding = env.getInstrumenter().attachContextsListener(new ContextTracker(), true);
        this.srcBinding = env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, loadedSourcesHandler, true);
        this.threadsHandler = new ThreadsHandler(this, debuggerSession);
        this.thrBinding = env.getInstrumenter().attachThreadsListener(threadsHandler, true);
        this.breakpointsHandler = new BreakpointsHandler(this, debuggerSession);
        this.stackFramesHandler = new StackFramesHandler(this, debuggerSession);
        this.variablesHandler = new VariablesHandler(this);
    }

    public void initClient(DebugProtocolClient dpClient) {
        this.client = dpClient;
    }

    public TruffleInstrument.Env getEnv() {
        return env;
    }

    public TruffleContext getATruffleContext() {
        synchronized (contexts) {
            if (contexts.isEmpty()) {
                return null;
            } else {
                return contexts.iterator().next();
            }
        }
    }

    public PrintWriter getInfo() {
        return info;
    }

    public PrintWriter getErr() {
        return err;
    }

    public boolean isInspectInternal() {
        return inspectInternal;
    }

    public boolean isInspectInitialization() {
        return inspectInitialization;
    }

    public TruffleLogger getLogger() {
        return logger;
    }

    public DebugProtocolClient getClient() {
        return client;
    }

    public LoadedSourcesHandler getLoadedSourcesHandler() {
        return loadedSourcesHandler;
    }

    public ThreadsHandler getThreadsHandler() {
        return threadsHandler;
    }

    public BreakpointsHandler getBreakpointsHandler() {
        return breakpointsHandler;
    }

    public VariablesHandler getVariablesHandler() {
        return variablesHandler;
    }

    public StackFramesHandler getStackFramesHandler() {
        return stackFramesHandler;
    }

    public void setLinesStartAt1(Boolean value) {
        if (value != null && !value) {
            linesStartAt1 = false;
        }
    }

    void setColumnsStartAt1(Boolean value) {
        if (value != null && !value) {
            columnsStartAt1 = false;
        }
    }

    public int clientToDebuggerLine(int line) {
        return linesStartAt1 ? line : line + 1;
    }

    public int clientToDebuggerColumn(int col) {
        return columnsStartAt1 ? col : col + 1;
    }

    public int debuggerToClientLine(int line) {
        return linesStartAt1 ? line : line - 1;
    }

    public int debuggerToClientColumn(int col) {
        return columnsStartAt1 ? col : col - 1;
    }

    public void doRunIfWaitingForDebugger() {
        synchronized (runPermission) {
            runPermission[0] = true;
            runPermission.notifyAll();
        }
    }

    public void waitForRunPermission() throws InterruptedException {
        synchronized (runPermission) {
            while (!runPermission[0]) {
                runPermission.wait();
            }
        }
    }

    public void dispose() {
        disposeBinding(srcBinding);
        disposeBinding(thrBinding);
        if (threadsHandler != null) {
            threadsHandler.dispose();
        }
        disposeBinding(contextsBinding);
        doRunIfWaitingForDebugger();
        client = null; // Do not call the client after dispose.
    }

    private static void disposeBinding(EventBinding<?> binding) {
        if (binding != null && !binding.isDisposed()) {
            binding.dispose();
        }
    }

    private final class ContextTracker implements ContextsListener {

        @Override
        public void onContextCreated(TruffleContext context) {
            synchronized (contexts) {
                contexts.add(context);
            }
            loadedSourcesHandler.notifyNewTruffleContext(context);
        }

        @Override
        public void onContextClosed(TruffleContext context) {
            synchronized (contexts) {
                contexts.remove(context);
            }
        }

        @Override
        public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
        }
    }
}

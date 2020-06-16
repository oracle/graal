/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector;

import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.types.CallArgument;
import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;

/**
 * The Truffle engine execution context.
 */
public final class InspectorExecutionContext {

    public static final String VALUE_NOT_READABLE = "<not readable>";
    private static final AtomicLong LAST_ID = new AtomicLong(0);

    private final String name;
    private final TruffleInstrument.Env env;
    private final PrintWriter err;
    private final List<Listener> listeners = Collections.synchronizedList(new ArrayList<>(3));
    private final long id = LAST_ID.incrementAndGet();
    private final boolean[] runPermission = new boolean[]{false};
    private final boolean inspectInternal;
    private final boolean inspectInitialization;
    private final List<URI> sourceRoots;
    private final TruffleLogger log;
    // Till the legacy TruffleLanguage.toString() is around, we must keep this as true
    private final boolean allowToStringSideEffects = true;

    private volatile DebuggerSuspendedInfo suspendedInfo;
    private volatile SuspendedThreadExecutor suspendThreadExecutor;
    private RemoteObjectsHandler roh;
    private volatile ScriptsHandler scriptsHandler;
    private volatile EventBinding<ScriptsHandler> schBinding;
    private int schCounter;
    private volatile String lastMimeType = "text/javascript";   // Default JS
    private volatile String lastLanguage = "js";
    private boolean synchronous = false;
    private boolean customObjectFormatterEnabled = false;

    public InspectorExecutionContext(String name, boolean inspectInternal, boolean inspectInitialization, TruffleInstrument.Env env, List<URI> sourceRoots, PrintWriter err) {
        this.name = name;
        this.inspectInternal = inspectInternal;
        this.inspectInitialization = inspectInitialization;
        this.env = env;
        this.sourceRoots = sourceRoots;
        this.err = err;
        this.log = env.getLogger("");
    }

    public boolean isInspectInternal() {
        return inspectInternal;
    }

    public boolean isInspectInitialization() {
        return inspectInitialization;
    }

    public boolean areToStringSideEffectsAllowed() {
        return allowToStringSideEffects;
    }

    public TruffleInstrument.Env getEnv() {
        return env;
    }

    public long getId() {
        return id;
    }

    public PrintWriter getErr() {
        return err;
    }

    public void logMessage(String prefix, Object message) {
        if (log.isLoggable(Level.FINE)) {
            log.fine("CONTEXT " + id + " " + prefix + message);
        }
    }

    public void logException(Throwable ex) {
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "CONTEXT " + id, ex);
        }
    }

    public void logException(String prefix, Throwable ex) {
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "CONTEXT " + id + " " + prefix, ex);
        }
    }

    Iterable<URI> getSourcePath() {
        return sourceRoots;
    }

    public void doRunIfWaitingForDebugger() {
        fireContextCreated();
        synchronized (runPermission) {
            runPermission[0] = true;
            runPermission.notifyAll();
        }
    }

    public boolean canRun() {
        synchronized (runPermission) {
            return runPermission[0];
        }
    }

    public ScriptsHandler acquireScriptsHandler() {
        ScriptsHandler sh;
        boolean attachListener = false;
        synchronized (this) {
            sh = scriptsHandler;
            if (sh == null) {
                scriptsHandler = sh = new ScriptsHandler(inspectInternal);
                attachListener = true;
                schCounter = 0;
            }
            schCounter++;
        }
        if (attachListener) {
            schBinding = env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, sh, true);
        }
        return sh;
    }

    public synchronized void releaseScriptsHandler() {
        if (--schCounter == 0) {
            schBinding.dispose();
            schBinding = null;
            scriptsHandler = null;
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fireContextCreated() {
        for (Listener l : listeners) {
            l.contextCreated(id, name);
        }
    }

    public void waitForRunPermission() throws InterruptedException {
        if (synchronous) {
            return;
        }
        synchronized (runPermission) {
            while (!runPermission[0]) {
                runPermission.wait();
            }
        }
    }

    public synchronized RemoteObjectsHandler getRemoteObjectsHandler() {
        if (roh == null) {
            roh = new RemoteObjectsHandler(this);
        }
        return roh;
    }

    public RemoteObject createAndRegister(DebugValue value, boolean generatePreview) {
        RemoteObject ro = new RemoteObject(value, generatePreview, this);
        if (ro.getId() != null) {
            getRemoteObjectsHandler().register(ro);
        }
        return ro;
    }

    void setValue(DebugValue debugValue, CallArgument newValue) {
        String objectId = newValue.getObjectId();
        if (objectId != null) {
            RemoteObject obj = getRemoteObjectsHandler().getRemote(objectId);
            debugValue.set(obj.getDebugValue());
        } else {
            debugValue.set(newValue.getPrimitiveValue());
        }
    }

    void setSuspendThreadExecutor(SuspendedThreadExecutor suspendThreadExecutor) {
        this.suspendThreadExecutor = suspendThreadExecutor;
    }

    <T> T executeInSuspendThread(SuspendThreadExecutable<T> executable) throws NoSuspendedThreadException, CommandProcessException {
        if (synchronous) {
            try {
                return executable.executeCommand();
            } catch (ThreadDeath td) {
                throw td;
            } catch (DebugException dex) {
                return executable.processException(dex);
            }
        }
        CompletableFuture<T> cf = new CompletableFuture<>();
        suspendThreadExecutor.execute(new CancellableRunnable() {
            @Override
            public void run() {
                T params = null;
                try {
                    params = executable.executeCommand();
                    cf.complete(params);
                } catch (ThreadDeath td) {
                    cf.completeExceptionally(td);
                    throw td;
                } catch (DebugException dex) {
                    cf.complete(executable.processException(dex));
                } catch (Throwable t) {
                    cf.completeExceptionally(t);
                }
            }

            @Override
            public void cancel() {
                cf.completeExceptionally(new NoSuspendedThreadException("Resuming..."));
            }
        });
        T params;
        try {
            params = cf.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof CommandProcessException) {
                throw (CommandProcessException) cause;
            }
            if (cause instanceof NoSuspendedThreadException) {
                throw (NoSuspendedThreadException) cause;
            }
            if (err != null) {
                cause.printStackTrace(err);
            }
            throw new CommandProcessException(ex.getLocalizedMessage());
        } catch (InterruptedException ex) {
            throw new CommandProcessException(ex.getLocalizedMessage());
        }
        return params;
    }

    void setLastLanguage(String language, String mimeType) {
        this.lastLanguage = language;
        this.lastMimeType = mimeType;
    }

    String getLastLanguage() {
        return lastLanguage;
    }

    String getLastMimeType() {
        return lastMimeType;
    }

    void setSuspendedInfo(DebuggerSuspendedInfo suspendedInfo) {
        this.suspendedInfo = suspendedInfo;
        if (suspendedInfo == null) {
            // not suspended, clear variables
            synchronized (this) {
                if (roh != null) {
                    roh.reset();
                }
            }
        }
    }

    DebuggerSuspendedInfo getSuspendedInfo() {
        return suspendedInfo;
    }

    /**
     * Returns the current debugger session if debugging is on.
     *
     * @return the current debugger session, or <code>null</code>.
     */
    public DebuggerSession getDebuggerSession() {
        ScriptsHandler handler = this.scriptsHandler;
        return (handler != null) ? handler.getDebuggerSession() : null;
    }

    /**
     * For test purposes only. Do not call from production code.
     */
    public static void resetIDs() {
        LAST_ID.set(0);
    }

    public void reset() {
        this.suspendedInfo = null;
        this.suspendThreadExecutor = null;
        this.roh = null;
        assert scriptsHandler == null;
        synchronized (runPermission) {
            runPermission[0] = true;
            runPermission.notifyAll();
        }
    }

    public void setSynchronous(boolean synchronousExecution) {
        this.synchronous = synchronousExecution;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    void setCustomObjectFormatterEnabled(boolean enabled) {
        this.customObjectFormatterEnabled = enabled;
    }

    public boolean isCustomObjectFormatterEnabled() {
        return this.customObjectFormatterEnabled;
    }

    public interface Listener {

        void contextCreated(long id, String name);

        void contextDestroyed(long id, String name);
    }

    interface SuspendedThreadExecutor {

        void execute(CancellableRunnable run) throws NoSuspendedThreadException;
    }

    interface CancellableRunnable extends Runnable {

        void cancel();
    }

    static final class NoSuspendedThreadException extends Exception {

        private static final long serialVersionUID = 2834058024185219386L;

        private NoSuspendedThreadException(String message) {
            super(message);
        }

        static void raise() throws NoSuspendedThreadException {
            throw new NoSuspendedThreadException("<Not suspended>");
        }

        static void raiseResuming() throws NoSuspendedThreadException {
            throw new NoSuspendedThreadException("<Resuming...>");
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

}

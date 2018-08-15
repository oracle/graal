/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

import com.oracle.truffle.tools.chromeinspector.instrument.SourceLoadInstrument;
import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.types.CallArgument;
import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;

/**
 * The Truffle engine execution context.
 */
public final class TruffleExecutionContext {

    private static final AtomicLong LAST_ID = new AtomicLong(0);

    private final String name;
    private final TruffleInstrument.Env env;
    private final PrintWriter err;
    private final List<Listener> listeners = Collections.synchronizedList(new ArrayList<>(3));
    private final long id = LAST_ID.incrementAndGet();
    private final boolean[] runPermission = new boolean[]{false};
    private final boolean inspectInternal;
    private final boolean inspectInitialization;

    private volatile DebuggerSuspendedInfo suspendedInfo;
    private volatile SuspendedThreadExecutor suspendThreadExecutor;
    private RemoteObjectsHandler roh;
    private ScriptsHandler sch;
    private AtomicInteger schCounter;
    private volatile String lastMimeType = "text/javascript";   // Default JS
    private volatile String lastLanguage = "js";

    public TruffleExecutionContext(String name, boolean inspectInternal, boolean inspectInitialization, TruffleInstrument.Env env, PrintWriter err) {
        this.name = name;
        this.inspectInternal = inspectInternal;
        this.inspectInitialization = inspectInitialization;
        this.env = env;
        this.err = err;
    }

    public boolean isInspectInternal() {
        return inspectInternal;
    }

    public boolean isInspectInitialization() {
        return inspectInitialization;
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

    public void doRunIfWaitingForDebugger() {
        fireContextCreated();
        synchronized (runPermission) {
            runPermission[0] = true;
            runPermission.notifyAll();
        }
    }

    public ScriptsHandler acquireScriptsHandler() {
        if (sch == null) {
            InstrumentInfo instrumentInfo = getEnv().getInstruments().get(SourceLoadInstrument.ID);
            SourceLoadInstrument sli = getEnv().lookup(instrumentInfo, SourceLoadInstrument.class);
            sli.enable(inspectInternal);
            sch = sli.getScriptsHandler();
            schCounter = new AtomicInteger(0);
        }
        schCounter.incrementAndGet();
        return sch;
    }

    public void releaseScriptsHandler() {
        if (schCounter.decrementAndGet() == 0) {
            InstrumentInfo instrumentInfo = getEnv().getInstruments().get(SourceLoadInstrument.ID);
            getEnv().lookup(instrumentInfo, SourceLoadInstrument.class).disable();
            sch = null;
            schCounter = null;
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
        synchronized (runPermission) {
            while (!runPermission[0]) {
                runPermission.wait();
            }
        }
    }

    synchronized RemoteObjectsHandler getRemoteObjectsHandler() {
        if (roh == null) {
            roh = new RemoteObjectsHandler(err);
        }
        return roh;
    }

    public RemoteObject createAndRegister(DebugValue value) {
        RemoteObject ro = new RemoteObject(value, getErr());
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
    }

    DebuggerSuspendedInfo getSuspendedInfo() {
        return suspendedInfo;
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
        assert sch == null;
        synchronized (runPermission) {
            runPermission[0] = false;
        }
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

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

}

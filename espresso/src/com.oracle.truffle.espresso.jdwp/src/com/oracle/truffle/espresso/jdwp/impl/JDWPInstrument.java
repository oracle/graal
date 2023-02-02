/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.impl;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

@Registration(id = JDWPInstrument.ID, name = "Java debug wire protocol", services = DebuggerController.class)
public final class JDWPInstrument extends TruffleInstrument implements Runnable {

    public static final String ID = "jdwp";

    private DebuggerController controller;
    private TruffleInstrument.Env env;
    private JDWPContext context;
    private DebuggerConnection connection;
    private Collection<Thread> activeThreads = new ArrayList<>();
    private PrintStream err;
    private volatile HandshakeController hsController = null;
    private final Semaphore resetting = new Semaphore(1);

    @Override
    protected void onCreate(TruffleInstrument.Env instrumentEnv) {
        assert controller == null;
        controller = new DebuggerController(this);
        this.env = instrumentEnv;
        this.env.registerService(controller);
        this.env.getInstrumenter().attachContextsListener(controller, false);
        this.err = new PrintStream(env.err());
    }

    public void reset(boolean prepareForReconnect) {
        if (!resetting.tryAcquire()) {
            return;
        }
        // stop all running jdwp threads in an orderly fashion
        for (Thread activeThread : activeThreads) {
            activeThread.interrupt();
        }
        // close the server socket used to listen for transport dt_socket
        HandshakeController hsc = hsController;
        if (hsc != null) {
            hsc.close();
        }
        // close the connection to the debugger
        if (connection != null) {
            connection.close();
        }
        // wait for threads to fully stop
        boolean stillRunning = true;
        while (stillRunning) {
            stillRunning = false;
            for (Thread activeThread : activeThreads) {
                if (activeThread.isAlive()) {
                    stillRunning = true;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        // re-enable GC for all objects
        controller.getGCPrevention().clearAll();

        // end the current debugger session to avoid hitting any further breakpoints
        // when resuming all threads
        controller.endSession();

        // resume all threads
        controller.resumeAll(true);

        if (prepareForReconnect) {
            // replace the controller instance
            controller.reInitialize();
            resetting.release();
        }
    }

    public boolean isResetting() {
        return resetting.availablePermits() == 0;
    }

    public void printStackTrace(Throwable e) {
        e.printStackTrace(err);
    }

    public void printError(String message) {
        err.println(message);
    }

    @CompilerDirectives.TruffleBoundary
    public void init(JDWPContext jdwpContext) {
        this.context = jdwpContext;
        try {
            if (controller.isSuspend()) {
                try {
                    doConnect(true, controller.isServer());
                } catch (ConnectException ex) {
                    handleConnectException(ex, false);
                }
            } else {
                // don't suspend until debugger attaches, so fire up deamon thread
                Thread handshakeThread = new Thread(this, "jdwp-handshake-thread");
                handshakeThread.setDaemon(true);
                handshakeThread.start();
            }
        } catch (IOException e) {
            printError("Critical failure in establishing jdwp connection: " + e.getLocalizedMessage());
            printStackTrace(e);
        }
    }

    private void handleConnectException(ConnectException ex, boolean swallowExitException) {
        System.err.println("ERROR: transport error 202: connect failed: " + ex.getMessage());
        System.err.println("ERROR: JDWP Transport dt_socket failed to initialize, TRANSPORT_INIT(510)");
        System.err.println("JDWP exit error AGENT_ERROR_TRANSPORT_INIT(197): No transports initialized");
        try {
            context.abort(197);
        } catch (Throwable t) {
            if (swallowExitException) {
                // swallow exit exception if thread will exit anyway
                if (t instanceof AbstractTruffleException) {
                    try {
                        if ((InteropLibrary.getUncached().getExceptionType(t)) != ExceptionType.EXIT) {
                            throw t;
                        }
                    } catch (UnsupportedMessageException e) {
                        throw t;
                    }
                }
            } else {
                throw t;
            }
        }
    }

    void doConnect(boolean suspend, boolean server) throws IOException {
        SocketConnection socketConnection;

        hsController = new HandshakeController();
        socketConnection = hsController.createSocketConnection(server, controller.getHost(), controller.getListeningPort(), activeThreads);
        hsController.close();
        hsController = null;

        // connection established with handshake. Prepare to process commands from debugger
        connection = new DebuggerConnection(socketConnection, controller);
        controller.getEventListener().setConnection(socketConnection);
        // The VM started event must be sent when we're ready to process commands
        // doProcessCommands method will control when events can be fired without
        // causing races, so pass on a Callable
        Callable<Void> vmStartedJob = new Callable<>() {
            @Override
            public Void call() {
                controller.getEventListener().vmStarted(suspend);
                return null;
            }
        };
        connection.doProcessCommands(suspend, activeThreads, vmStartedJob);
    }

    @Override
    public void run() {
        try {
            doConnect(false, controller.isServer());
        } catch (ConnectException ex) {
            handleConnectException(ex, true);
        } catch (IOException e) {
            if (!isResetting()) {
                printError("Critical failure in establishing jdwp connection: " + e.getLocalizedMessage());
                printStackTrace(e);
            }
        }
    }

    public JDWPContext getContext() {
        return context;
    }

    TruffleInstrument.Env getEnv() {
        return env;
    }
}

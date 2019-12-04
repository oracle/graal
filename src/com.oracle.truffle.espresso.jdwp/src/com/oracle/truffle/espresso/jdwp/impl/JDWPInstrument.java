/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

@TruffleInstrument.Registration(id = JDWPInstrument.ID, name = "Java debug wire protocol", services = DebuggerController.class)
public final class JDWPInstrument extends TruffleInstrument implements Runnable {

    public static final String ID = "jdwp";

    public final Object suspendStartupLock = new Object();
    private DebuggerController controller;
    private TruffleInstrument.Env env;
    private JDWPContext context;
    private DebuggerConnection connection;
    private Collection<Thread> activeThreads = new ArrayList<>();
    private boolean isStarted;

    @Override
    protected void onCreate(TruffleInstrument.Env instrumentEnv) {
        assert controller == null;
        controller = new Controller(this);
        this.env = instrumentEnv;
        this.env.registerService(controller);
    }

    public void reset(boolean prepareForReconnect) {
        // stop all running jdwp threads in an orderly fashion
        for (Thread activeThread : activeThreads) {
            activeThread.interrupt();
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

        // close the connection to the debugger
        connection.close();

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

            // prepare to accept a new debugger connection
            try {
                doConnect();
            } catch (IOException e) {
                throw new RuntimeException("Failed to prepare for a new JDWP connection", e);
            }
        }
    }

    public void setStarted() {
        this.isStarted = true;
    }

    @CompilerDirectives.TruffleBoundary
    public void init(JDWPContext jdwpContext) {
        this.context = jdwpContext;
        try {
            if (controller.shouldWaitForAttach()) {
                doConnect();
                // take all initial commands from the debugger before resuming to main thread
                synchronized (suspendStartupLock) {
                    try {
                        while (!isStarted) {
                            suspendStartupLock.wait();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException("JDWP connection interrupted");
                    }
                }
            } else {
                // don't suspend until debugger attaches, so fire up deamon thread
                Thread handshakeThread = new Thread(this, "jdwp-handshake-thread");
                handshakeThread.setDaemon(true);
                handshakeThread.start();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed JDWP transsport setup", e);
        }
    }

    void doConnect() throws IOException {
        SocketConnection socketConnection = HandshakeController.createSocketConnection(controller.getListeningPort(), activeThreads);
        // connection established with handshake. Prepare to process commands from debugger
        connection = new DebuggerConnection(socketConnection, controller);
        connection.doProcessCommands(controller.shouldWaitForAttach(), activeThreads);
    }

    @Override
    public void run() {
        try {
            doConnect();
        } catch (IOException e) {
            throw new RuntimeException("JDWP connection setup failed", e);
        }
    }

    public JDWPContext getContext() {
        return context;
    }

    public Object getSuspendStartupLock() {
        return suspendStartupLock;
    }

    private static final class Controller extends DebuggerController {

        Controller(JDWPInstrument instrument) {
            super(instrument);
        }
    }
}

/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

@TruffleInstrument.Registration(id = JDWPInstrument.ID, name = "Java debug wire protocol", services = DebuggerController.class)
public final class JDWPInstrument extends TruffleInstrument implements Runnable {

    public static final String ID = "jdwp";

    private DebuggerController controller;
    private TruffleInstrument.Env env;
    private JDWPContext context;
    private DebuggerConnection connection;
    private Collection<Thread> activeThreads = new ArrayList<>();
    private PrintStream err;

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
        // close the connection to the debugger
        connection.close();

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
        }
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
            if (controller.shouldWaitForAttach()) {
                doConnect(true);
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

    void doConnect(boolean suspend) throws IOException {
        SocketConnection socketConnection = HandshakeController.createSocketConnection(controller.getListeningPort(), activeThreads);
        // connection established with handshake. Prepare to process commands from debugger
        connection = new DebuggerConnection(socketConnection, controller);
        controller.getEventListener().setConnection(socketConnection);
        controller.getEventListener().vmStarted(suspend);
        connection.doProcessCommands(suspend, activeThreads);
    }

    @Override
    public void run() {
        try {
            doConnect(false);
        } catch (IOException e) {
            printError("Critical failure in establishing jdwp connection: " + e.getLocalizedMessage());
            printStackTrace(e);
        }
    }

    public JDWPContext getContext() {
        return context;
    }

    TruffleInstrument.Env getEnv() {
        return env;
    }
}

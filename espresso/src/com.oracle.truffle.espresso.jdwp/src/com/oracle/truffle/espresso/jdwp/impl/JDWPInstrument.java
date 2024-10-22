/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Semaphore;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

@Registration(id = JDWPInstrument.ID, name = "Java debug wire protocol", services = DebuggerController.class)
public final class JDWPInstrument extends TruffleInstrument {

    public static final String ID = "jdwp";

    private DebuggerController controller;
    private JDWPContext context;
    private final Set<Thread> debuggerThreads = new HashSet<>();
    private volatile HandshakeController hsController = null;
    private final Semaphore resetting = new Semaphore(1);

    @Override
    protected void onCreate(TruffleInstrument.Env instrumentEnv) {
        assert controller == null;
        controller = new DebuggerController(this, instrumentEnv.getLogger(ID));
        instrumentEnv.registerService(controller);
        instrumentEnv.getInstrumenter().attachContextsListener(controller, false);
    }

    public void reset(boolean prepareForReconnect) {
        if (!resetting.tryAcquire()) {
            return;
        }
        try {
            // stop all running jdwp threads in an orderly fashion
            synchronized (debuggerThreads) {
                for (Thread activeThread : debuggerThreads) {
                    activeThread.interrupt();
                }
            }
            // close the server socket used to listen for transport dt_socket
            HandshakeController hsc = hsController;
            if (hsc != null) {
                hsc.close();
            }
            // close the connection to the debugger
            controller.closeConnection();

            // wait for threads to fully stop
            boolean stillRunning = true;
            while (stillRunning) {
                stillRunning = false;
                synchronized (debuggerThreads) {
                    Iterator<Thread> it = debuggerThreads.iterator();
                    while (it.hasNext()) {
                        Thread activeThread = it.next();
                        if (activeThread.isAlive()) {
                            stillRunning = true;
                        } else {
                            // thread is done, so clean up from set
                            it.remove();
                        }
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
            controller.forceResumeAll();

            if (prepareForReconnect) {
                // replace the controller instance
                controller.reInitialize();
            }
        } finally {
            resetting.release();
        }
    }

    @TruffleBoundary
    public void init(JDWPContext jdwpContext) {
        this.context = jdwpContext;

        // Do all the non-blocking connection setup on the main thread.
        // If we need to suspend on startup, or we need to exit the context due to fatal connection
        // errors, we do this later when the context initialization is finalizing.
        try {
            hsController = new HandshakeController();
            hsController.setupInitialConnection(controller);
        } catch (IOException e) {
            System.err.println("ERROR: transport error 202: connect failed: " + e.getMessage());
            System.err.println("ERROR: JDWP Transport dt_socket failed to initialize, TRANSPORT_INIT(510)");
            controller.setSetupState(new DebuggerController.SetupState(null, null, true));
        }
    }

    public JDWPContext getContext() {
        return context;
    }

    public void addDebuggerThread(Thread thread) {
        synchronized (debuggerThreads) {
            debuggerThreads.add(thread);
        }
    }

    public boolean isDebuggerThread(Thread hostThread) {
        synchronized (debuggerThreads) {
            return debuggerThreads.contains(hostThread);
        }
    }
}

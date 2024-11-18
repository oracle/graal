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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

@Registration(id = JDWPInstrument.ID, name = "Java debug wire protocol", services = DebuggerController.class)
public final class JDWPInstrument extends TruffleInstrument {

    public static final String ID = "jdwp";

    private DebuggerController controller;
    private JDWPContext context;
    private Thread senderThread;
    private Thread receiverThread;
    private volatile HandshakeController hsController = null;
    private final Lock resetting = new ReentrantLock();
    private volatile boolean isClosing;

    @Override
    protected void onCreate(Env instrumentEnv) {
        assert controller == null;
        controller = new DebuggerController(this, instrumentEnv.getLogger(ID));
        instrumentEnv.registerService(controller);
        instrumentEnv.getInstrumenter().attachContextsListener(controller, false);
    }

    public void reset(boolean prepareForReconnect) {
        if (isClosing) {
            // already done closing, so don't attempt anything further
            return;
        }
        if (!prepareForReconnect) {
            // mark that we're closing down the whole context
            isClosing = true;
        }
        Thread currentReceiverThread = null;
        try {
            // begin section that needs to be synchronized with establishing a new connection and
            // starting the threads. The logic within the locked part, must be written in a way that
            // it can run on any current state in the debugger connection and in any debugger thread
            // existence state.
            resetting.lockInterruptibly();

            currentReceiverThread = receiverThread;

            // Close the server socket used to listen for transport dt_socket.
            // This will unblock the accept call on a server socket.
            HandshakeController hsc = hsController;
            if (hsc != null) {
                hsc.close();
            }
            // Tell the controller to dispose the underlying connection by adding a special dispose
            // packet to the sender thread queue. This will force the sender to complete work.
            controller.dispose();

            // we know the sender can finish work, so wait for it to complete
            joinThread(senderThread);

            // clear our current state of the threads
            senderThread = null;

            // re-enable GC for all objects
            controller.getGCPrevention().clearAll();

            // end the current debugger session to avoid hitting any further breakpoints
            // when resuming all threads
            controller.endSession();

            // resume all threads
            controller.forceResumeAll();

            // Now, close the socket, which will force the receiver thread to complete eventually.
            // Note that we might run this code in the receiver thread, so we can't simply join.
            controller.closeSocket();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            resetting.unlock();
        }

        // If we're not running in the receiver thread we should join
        if (Thread.currentThread() != currentReceiverThread) {
            joinThread(currentReceiverThread);
        }

        if (prepareForReconnect && !isClosing) {
            // replace the controller instance
            controller.reInitialize();
        }
        // At this point the receiver thread field has either been replaced with a fresh thread from
        // the above reInitialize call, or we're closing down. Either way, we don't need to worry
        // about leaking the receiverThread field.
    }

    private void joinThread(Thread thread) {
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                controller.warning(() -> "jdwp thread " + thread.getName() + " didn't finish naturally");
                Thread.currentThread().interrupt();
            }
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

    public void addDebuggerSenderThread(Thread thread) {
        senderThread = thread;
    }

    public void addDebuggerReceiverThread(Thread thread) {
        receiverThread = thread;
    }

    public boolean isDebuggerThread(Thread hostThread) {
        // only the receiver thread enters the context
        return hostThread == receiverThread;
    }

    public Lock getResettingLock() {
        return resetting;
    }

    public boolean isClosing() {
        return isClosing;
    }
}

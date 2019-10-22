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
package com.oracle.truffle.espresso.debugger.jdwp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.espresso.runtime.EspressoContext;

import java.io.IOException;

@TruffleInstrument.Registration(id = JDWPInstrument.ID, name = "Java debug wire protocol", services = JDWPDebuggerController.class)
public class JDWPInstrument extends TruffleInstrument implements Runnable {

    public static final String ID = "jdwp";

    private JDWPDebuggerController controller;
    private EspressoContext context;

    @Override
    protected void onCreate(TruffleInstrument.Env env) {
        assert this.controller == null;
        this.controller = new JDWPController(this);
        env.registerService(controller);
    }

    @CompilerDirectives.TruffleBoundary
    public void init(EspressoContext context) {
        this.context = context;
        try {
            if (controller.shouldWaitForAttach()) {
                doConnect();
                // take all initial commands from the debugger before resuming to main thread
                synchronized (JDWP.suspendStartupLock) {
                    try {
                        JDWP.suspendStartupLock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("JDWP connection interrupted");
                    }
                }
            }
            else {
                // don't suspend until debugger attaches, so fire up deamon thread
                Thread handshakeThread = new Thread(this, "jdwp-handshake-thread");
                handshakeThread.setDaemon(true);
                handshakeThread.start();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed JDWP transsport setup", e);
        }
    }

    private void doConnect() throws IOException {
        SocketConnection socketConnection = JDWPHandshakeController.createSocketConnection(controller.getListeningPort());
        // connection established with handshake. Prepare to process commands from debugger
        new DebuggerConnection(socketConnection, controller).doProcessCommands(controller.shouldWaitForAttach());
    }

    @Override
    public void run() {
        try {
            doConnect();
        } catch (IOException e) {
            throw new RuntimeException("JDWP connection setup failed" , e);
        }
    }

    public EspressoContext getContext() {
        return context;
    }

    private static final class JDWPController extends JDWPDebuggerController {

        JDWPController(JDWPInstrument instrument) {
            super(instrument);
        }
    }

}

/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collection;

import com.oracle.svm.jdwp.bridge.DebugOptions;
import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.server.api.ConnectionController;
import com.oracle.svm.jdwp.server.impl.DebuggerConnection;
import com.oracle.svm.jdwp.server.impl.DebuggerController;
import com.oracle.svm.jdwp.server.impl.HandshakeController;
import com.oracle.svm.jdwp.server.impl.SocketConnection;

public final class JDWPHandler implements Runnable {

    static final String HOST = "*";
    static final int PORT = 8000;

    private final ConnectionControllerImpl connectionController;
    private volatile DebuggerController debuggerController;

    JDWPHandler(long initialThreadId) {
        connectionController = new ConnectionControllerImpl(initialThreadId);
    }

    public DebuggerController getController() {
        return debuggerController;
    }

    void doConnect(DebugOptions.Options options) {
        connectionController.doConnect(options);
    }

    @Override
    public void run() {
        DebugOptions.Options dummyOptions = DebugOptions.parse("transport=dt_socket,server=y,suspend=n,address=" + HOST + ":" + PORT, false);
        doConnect(dummyOptions);
    }

    private static void handleConnectException(ConnectException ex) {
        System.err.println("ERROR: transport error 202: connect failed: " + ex.getMessage());
        System.err.println("ERROR: JDWP Transport dt_socket failed to initialize, TRANSPORT_INIT(510)");
        System.err.println("JDWP exit error AGENT_ERROR_TRANSPORT_INIT(197): No transports initialized");
    }

    private final class ConnectionControllerImpl implements ConnectionController {

        private long initialThreadId;
        private DebugOptions.Options lastOptions;
        private volatile DebuggerConnection lastConnection;

        private ConnectionControllerImpl(long initialThreadId) {
            this.initialThreadId = initialThreadId;
        }

        @Override
        public void dispose(Packet replyPacket) {
            DebuggerConnection connection = lastConnection;
            lastConnection = null; // no sync, dispose() and restart() do not run in parallel
            if (connection != null) {
                connection.queuePacket(replyPacket);
                connection.close();
            }
        }

        @Override
        public void restart() {
            if (lastOptions != null && lastOptions.server()) {
                doConnect(lastOptions);
            }
        }

        void doConnect(DebugOptions.Options options) {
            lastOptions = options;

            SocketConnection socketConnection;

            Collection<Thread> activeThreads = new ArrayList<>();
            try (HandshakeController hsController = new HandshakeController(options)) {
                socketConnection = hsController.createSocketConnection(activeThreads);
            } catch (ConnectException ex) {
                handleConnectException(ex);
                return;
            } catch (IOException ioex) {
                System.err.println("Critical failure in establishing JDWP connection: " + ioex.getLocalizedMessage());
                return;
            }

            // connection established with handshake. Prepare to process commands from debugger
            DebuggerController controller = new DebuggerController(initialThreadId, connectionController);
            debuggerController = controller;
            DebuggerConnection connection = new DebuggerConnection(socketConnection, controller, activeThreads);
            controller.getEventListener().setConnection(socketConnection);
            // The VM started event must be sent when we're ready to process commands
            // doProcessCommands method will control when events can be fired without
            // causing races, so pass on a Callable
            Runnable vmStartedJob = () -> controller.getEventListener().vmStarted(options.suspend());
            lastConnection = connection;
            connection.doProcessCommands(options.suspend(), vmStartedJob);
            initialThreadId = 0; // no initial thread for further connections
        }
    }
}

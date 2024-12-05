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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

final class HandshakeController {

    private static final String JDWP_HANDSHAKE = "JDWP-Handshake";
    private ServerSocket currentServerSocket;

    /**
     * Initializes a Socket connection which serves as a transport for jdwp communication.
     *
     * @throws IOException
     */
    void setupInitialConnection(DebuggerController controller) throws IOException {
        DebuggerController.SetupState result;
        String connectionHost = controller.getHost();
        int port = controller.getListeningPort();
        if (controller.isServer()) {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(0); // no timeout
            if (port != 0) {
                serverSocket.setReuseAddress(true);
            }
            if (connectionHost == null) {
                // localhost only
                serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            } else if ("*".equals(connectionHost)) {
                // allow any host to bind
                serverSocket.bind(new InetSocketAddress((InetAddress) null, port));
            } else {
                // allow specific host to bind
                serverSocket.bind(new InetSocketAddress(connectionHost, port));
            }
            // print to console that we're listening
            String address = controller.getHost() != null ? controller.getHost() + ":" + serverSocket.getLocalPort() : "" + serverSocket.getLocalPort();
            System.out.println("Listening for transport dt_socket at address: " + address);

            synchronized (this) {
                assert currentServerSocket == null;
                currentServerSocket = serverSocket;
            }
            result = new DebuggerController.SetupState(null, serverSocket, false);
        } else {
            try {
                controller.getResettingLock().lockInterruptibly();
                if (!controller.isClosing()) {
                    result = new DebuggerController.SetupState(new Socket(connectionHost, port), null, false);
                } else {
                    // if we're already closing down, simply mark as fatal connection error
                    result = new DebuggerController.SetupState(null, null, true);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                controller.getResettingLock().unlock();
            }
        }
        controller.setSetupState(result);
    }

    synchronized void close() {
        if (currentServerSocket != null) {
            if (!currentServerSocket.isClosed()) {
                try {
                    currentServerSocket.close();
                } catch (IOException e) {
                    throw new RuntimeException("Unable to close the server socket used to listen for transport dt_socket", e);
                }
            }
            currentServerSocket = null;
        }
    }

    /**
     * Handshake with the debugger.
     */
    static boolean handshake(Socket s) throws IOException {

        byte[] hello = JDWP_HANDSHAKE.getBytes(StandardCharsets.UTF_8);

        byte[] b = new byte[hello.length];
        int received = 0;
        while (received < hello.length) {
            int n;
            try {
                n = s.getInputStream().read(b, received, hello.length - received);
            } catch (SocketTimeoutException x) {
                throw new IOException("handshake timeout");
            }
            if (n < 0) {
                s.close();
                throw new IOException("handshake failed - connection prematurely closed");
            }
            received += n;
        }
        for (int i = 0; i < hello.length; i++) {
            if (b[i] != hello[i]) {
                throw new IOException("handshake failed - unrecognized message from the debugger");
            }
        }

        // handshake received, so return the gesture to establish the jdwp transport
        s.getOutputStream().write(hello);
        return true;
    }
}

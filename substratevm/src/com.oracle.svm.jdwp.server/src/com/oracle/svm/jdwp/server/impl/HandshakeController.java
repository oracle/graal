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
package com.oracle.svm.jdwp.server.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;

import com.oracle.svm.jdwp.bridge.DebugOptions;

public final class HandshakeController implements AutoCloseable {

    private static final String JDWP_HANDSHAKE = "JDWP-Handshake";
    private ServerSocket currentServerSocket;

    private final DebugOptions.Options options;

    public HandshakeController(DebugOptions.Options options) {
        this.options = Objects.requireNonNull(options);
    }

    /**
     * Initializes a Socket connection which serves as transport for JDWP communication.
     * 
     */
    public SocketConnection createSocketConnection(Collection<Thread> activeThreads) throws IOException {
        String connectionHost = options.host();
        if (connectionHost == null) {
            // only allow local host if nothing specified
            connectionHost = "localhost";
        }
        Socket connectionSocket;
        if (options.server()) {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(options.timeout()); // use 0 for no timeout

            // inspired by
            // https://github.com/openjdk/jdk/blob/20d8f58c92009a46dfb91b951e7d87b4cb8e8b41/src/jdk.jdwp.agent/share/native/libdt_socket/socketTransport.c#L635-L645
            serverSocket.setReuseAddress(options.port() != 0);

            if ("*".equals(options.host())) {
                // allow any host to bind
                serverSocket.bind(new InetSocketAddress((InetAddress) null, options.port()));
            } else {
                // allow specific host to bind
                serverSocket.bind(new InetSocketAddress(connectionHost, options.port()));
            }
            // print to console that we're listening
            // String address = host != null ? host + ":" + port : "" + port;
            if (!options.quiet()) {
                System.out.println("Listening for transport " + options.transport() + " at address: " + serverSocket.getLocalPort());
            }

            synchronized (this) {
                assert currentServerSocket == null;
                currentServerSocket = serverSocket;
            }
            // block until a debugger has accepted the socket
            connectionSocket = serverSocket.accept();
        } else {
            connectionSocket = new Socket();
            connectionSocket.connect(new InetSocketAddress(connectionHost, options.port()), options.timeout());
        }

        if (!handshake(connectionSocket)) {
            throw new IOException("Unable to handshake with debugger");
        }
        SocketConnection connection = new SocketConnection(connectionSocket);
        Thread jdwpSender = connection.startSenderThread();
        activeThreads.add(jdwpSender);
        return connection;
    }

    @Override
    public synchronized void close() {
        if (currentServerSocket != null) {
            if (!currentServerSocket.isClosed()) {
                try {
                    currentServerSocket.close();
                } catch (IOException e) {
                    throw new RuntimeException("Unable to close the server socket used to listen for transport " + options.transport(), e);
                }
            }
            currentServerSocket = null;
        }
    }

    /**
     * Handshake with the debugger.
     */
    private static boolean handshake(Socket s) throws IOException {

        byte[] hello = JDWP_HANDSHAKE.getBytes(StandardCharsets.UTF_8);

        byte[] b = new byte[hello.length];
        int received = 0;
        while (received < hello.length) {
            int n;
            try {
                n = s.getInputStream().read(b, received, hello.length - received);
            } catch (SocketTimeoutException x) {
                throw new IOException("Handshake timeout");
            }
            if (n < 0) {
                s.close();
                throw new IOException("Handshake failed - connection prematurely closed");
            }
            received += n;
        }
        for (int i = 0; i < hello.length; i++) {
            if (b[i] != hello[i]) {
                throw new IOException("Handshake failed - unrecognized message from the debugger");
            }
        }

        // handshake received, so return the gesture to establish the jdwp transport
        s.getOutputStream().write(hello);
        return true;
    }
}

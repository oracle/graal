/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.dap.types.DebugProtocolServer.Session;

/**
 * Owns an Engine's listening socket and serves one debugger client at a time.
 * Each connection gets fresh debugger and protocol state.
 */
public final class DebugProtocolServerListener {

    private final TruffleInstrument.Env env;
    private final PrintWriter info;
    private final PrintWriter err;
    private final boolean inspectInternal;
    private final boolean inspectInitialization;
    private final List<URI> sourcePath;
    private DebugProtocolServerImpl session;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private Thread thread;
    private boolean disposed;

    /**
     * Installs startup debugger state before any connection is accepted.
     *
     * @param context the first session's execution context, owned by this listener
     * @param debugBreak suspends the first guest execution
     * @param waitAttached delays language initialization until configuration is complete
     * @param inspectInitialization includes language initialization in debugging
     * @param sourcePath source lookup locations shared by successive sessions
     */
    public DebugProtocolServerListener(ExecutionContext context, boolean debugBreak, boolean waitAttached, boolean inspectInitialization, List<URI> sourcePath) {
        this.env = context.getEnv();
        this.info = context.getInfo();
        this.err = context.getErr();
        this.inspectInternal = context.isInspectInternal();
        this.inspectInitialization = context.isInspectInitialization();
        this.sourcePath = sourcePath;
        // Startup suspension must be installed before the first client is accepted.
        this.session = DebugProtocolServerImpl.create(context, debugBreak, waitAttached, inspectInitialization, sourcePath);
    }

    /**
     * Takes ownership of the listening socket until this listener is disposed.
     *
     * @param socket the bound listening socket
     * @return completion of the accept loop and all session cleanup
     */
    public synchronized CompletableFuture<Void> start(ServerSocket socket) {
        serverSocket = socket;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        thread = env.createSystemThread(() -> {
            try {
                run();
                completion.complete(null);
            } catch (Throwable error) {
                completion.completeExceptionally(error);
            }
        });
        thread.setName("DAP client connection thread");
        thread.start();
        return completion;
    }

    private void run() throws IOException, InterruptedException, ExecutionException {
        info.println("[Graal DAP] Starting server and listening on " + serverSocket.getLocalSocketAddress());
        try (ServerSocket listener = serverSocket) {
            while (!listener.isClosed()) {
                try (Socket socket = listener.accept()) {
                    DebugProtocolServerImpl server;
                    synchronized (this) {
                        if (disposed) {
                            break;
                        }
                        clientSocket = socket;
                        if (session == null) {
                            ExecutionContext context = new ExecutionContext(env, info, err, inspectInternal, inspectInitialization);
                            // Suspend/WaitAttached are startup options, not reconnect options.
                            session = DebugProtocolServerImpl.create(context, false, false, context.isInspectInitialization(), sourcePath);
                        }
                        server = session;
                    }
                    info.println("[Graal DAP] Client connected on " + socket.getRemoteSocketAddress());
                    try {
                        // The accept thread also reads this client's requests. Cleanup and pending
                        // request completion finish before another client is accepted.
                        Session.connect(server, socket.getInputStream(), socket.getOutputStream(), Runnable::run, () -> close(socket), server::dispose).get();
                    } finally {
                        server.dispose();
                        synchronized (this) {
                            session = null;
                            clientSocket = null;
                        }
                    }
                }
            }
        } catch (IOException error) {
            synchronized (this) {
                if (!disposed) {
                    throw error;
                }
            }
        } finally {
            DebugProtocolServerImpl server;
            synchronized (this) {
                disposed = true;
                server = session;
                session = null;
            }
            if (server != null) {
                server.dispose();
            }
        }
    }

    /**
     * Closes the listener and active transport and joins the client thread.
     */
    public void dispose() {
        Thread clientThread;
        DebugProtocolServerImpl server;
        Socket socket;
        synchronized (this) {
            disposed = true;
            close(serverSocket);
            clientThread = thread;
            server = session;
            socket = clientSocket;
        }
        try {
            if (server != null) {
                // Engine shutdown reports termination before closing the active transport.
                // Client disconnect/EOF cleanup disposes the session without this event.
                server.dispose(true);
            }
        } finally {
            close(socket);
            if (clientThread != null && clientThread != Thread.currentThread()) {
                boolean interrupted = false;
                while (clientThread.isAlive()) {
                    try {
                        clientThread.join();
                    } catch (InterruptedException error) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void close(Closeable socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException error) {
                err.println("[Graal DAP] Error while closing socket: " + error.getLocalizedMessage());
            }
        }
    }
}

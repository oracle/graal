/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.server;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.net.*;

import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.logging.*;

/**
 * Server side of the client/server compilation model. The server listens for connections on the
 * hardcoded port 1199.
 */
public class CompilationServer implements Runnable {

    public static void main(String[] args) throws Exception {
        new CompilationServer(false).run();
    }

    public interface ConnectionObserver {

        void connectionStarted(HotSpotGraalRuntime compiler);

        void connectionFinished(HotSpotGraalRuntime compiler);
    }

    private final boolean multiple;
    private final ArrayList<ConnectionObserver> observers = new ArrayList<>();

    /**
     * Creates a new Compilation server. The server is activated by calling {@link #run()} directly
     * or via a new {@link Thread}.
     * 
     * @param multiple true if the server should server should serve an infinite amount of
     *            consecutive connections, false if it should terminate after the first connection
     *            ends.
     */
    public CompilationServer(boolean multiple) {
        this.multiple = multiple;
    }

    public void addConnectionObserver(ConnectionObserver observer) {
        observers.add(observer);
    }

    public void removeConnectionObserver(ConnectionObserver observer) {
        observers.remove(observer);
    }

    public void run() {
        final ServerSocket serverSocket;
        try {
            serverSocket = ServerSocketFactory.getDefault().createServerSocket(1199);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't create compilation server", e);
        }
        do {
            Socket socket = null;
            try {
                Logger.log("Compilation server ready, waiting for client to connect...");
                socket = serverSocket.accept();
                Logger.log("Connected to " + socket.getRemoteSocketAddress());

                ReplacingStreams streams = new ReplacingStreams(socket.getOutputStream(), socket.getInputStream());

                // get the CompilerToVM proxy from the client
                CompilerToVM toVM = (CompilerToVM) streams.getInvocation().waitForResult(false);

                // return the initialized compiler to the client
                HotSpotGraalRuntime compiler = initializeServer(toVM);
                streams.getInvocation().sendResult(compiler);

                for (ConnectionObserver observer : observers) {
                    observer.connectionStarted(compiler);
                }

                streams.getInvocation().waitForResult(true);

                for (ConnectionObserver observer : observers) {
                    observer.connectionFinished(compiler);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                    }
                }
            }
        } while (multiple);
    }

    @SuppressWarnings("unused")
    private static HotSpotGraalRuntime initializeServer(CompilerToVM toVM) {
        // TODO(thomaswue): Fix creation of compiler instances on server side.
        return null;
    }
}

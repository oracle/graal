/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime.server;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.net.*;

import com.oracle.graal.runtime.*;
import com.oracle.graal.runtime.Compiler;
import com.oracle.graal.runtime.logging.*;

/**
 * Server side of the client/server compilation model. The server listens for connections on the hardcoded port 1199.
 *
 * @author Lukas Stadler
 */
public class CompilationServer implements Runnable {

    public static void main(String[] args) throws Exception {
        new CompilationServer(false).run();
    }

    public static interface ConnectionObserver {

        public void connectionStarted(Compiler compiler);

        public void connectionFinished(Compiler compiler);
    }

    private final boolean multiple;
    private final ArrayList<ConnectionObserver> observers = new ArrayList<ConnectionObserver>();

    /**
     * Creates a new Compilation server. The server is activated by calling {@link #run()} directly or via a new
     * {@link Thread}.
     *
     * @param multiple true if the server should server should serve an infinite amount of consecutive connections,
     *            false if it should terminate after the first connection ends.
     */
    public CompilationServer(boolean multiple) {
        this.multiple = multiple;
        HotSpotOptions.setDefaultOptions();
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

                // get the VMEntries proxy from the client
                VMEntries entries = (VMEntries) streams.getInvocation().waitForResult(false);

                // return the initialized compiler to the client
                Compiler compiler = CompilerImpl.initializeServer(entries);
                compiler.getCompiler();
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
}

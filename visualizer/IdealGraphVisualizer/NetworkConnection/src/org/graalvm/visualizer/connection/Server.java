/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.connection;

import java.io.File;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.settings.graal.GraalSettings;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import org.graalvm.visualizer.data.serialization.DocumentFactory;
import org.graalvm.visualizer.data.serialization.ParseMonitor;
import static org.graalvm.visualizer.settings.graal.GraalSettings.ACCEPT_NETWORK;
import static org.graalvm.visualizer.settings.graal.GraalSettings.CLEAN_CACHES;
import static org.graalvm.visualizer.settings.graal.GraalSettings.PORT_BINARY;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

public class Server implements PreferenceChangeListener {
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    /**
     * Maximum parallel network reading threads.
     */
    private static final int MAX_PARALLEL_READS = 50;

    private ServerSocketChannel serverSocket;
    private final DocumentFactory rootDocumentFactory;
    private final ParseMonitor monitor;
    private int port;
    private boolean network;
    private Runnable serverRunnable;
    private int clientCount;

    /**
     * Request processor which reads network data and stores to disk files.
     */
    private static final RequestProcessor NETWORK_RP = new RequestProcessor(Server.class.getName(), MAX_PARALLEL_READS);
    
    private final RequestProcessor streamLoader = new RequestProcessor(Client.class.getName(), 10);

    public Server(DocumentFactory rootDocumentFactory, ParseMonitor monitor) {
        this.rootDocumentFactory = rootDocumentFactory;
        this.monitor = monitor;
        initializeNetwork();
        
        GraalSettings s = GraalSettings.obtain();
        s.addPreferenceChangeListener(this);
        if (s.get(Boolean.class, CLEAN_CACHES)) {
            cleanCacheDir();
        }
    }
    
    private void cleanCacheDir() {
        File f = Places.getCacheSubdirectory("igv"); // NOI18N
        if (!f.exists() || !f.isDirectory()) {
            return;
        }
        Path rootPath = f.toPath();
        try {
            Files.list(rootPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .peek((n) -> {
                        LOG.log(Level.FINE, "Deleting {0}", n); // NOI18N
                    })
                    .forEach(File::delete);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Could not delete caches", // NOI18N
                    Exceptions.attachSeverity(ex, Level.INFO));
        }
    }
    
    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        GraalSettings set = GraalSettings.obtain();
        int curPort = set.get(Integer.class, PORT_BINARY);
        boolean net = set.get(Boolean.class, ACCEPT_NETWORK);
        synchronized (this) {
            if (curPort == port && net == network) {
                return;
            }
        }
        // batch several potential subsequent changes
        refreshNetwork();
    }
    
    private RequestProcessor.Task refreshTask;
    
    private synchronized void refreshNetwork() {
        if (refreshTask == null) {
            refreshTask = NETWORK_RP.post(this::initializeNetwork, 200);
        }
    }
    

    @NbBundle.Messages({
        "ERR_CannotListen=Could not create server. Listening for incoming binary data is disabled.",
        "# {0} - error description",
        "ERR_ProcessingAccept=Error listening for connections: {0}"
    })
    private void initializeNetwork() {
        synchronized (this) {
            refreshTask = null;
        }
        GraalSettings set = GraalSettings.obtain(); 
        int curPort = set.get(Integer.class, PORT_BINARY);
        boolean net = set.get(Boolean.class, ACCEPT_NETWORK);
        ServerSocketChannel ss;
        synchronized (this) {
            ss = this.serverSocket;
            this.port = curPort;
            this.network = set.get(Boolean.class, ACCEPT_NETWORK);
        }
        try {
            if (ss != null) {
                ss.close();
                this.serverSocket = null;
            }
            InetAddress bindTo;
            if (net) {
                bindTo = null;
            } else {
                bindTo = InetAddress.getLoopbackAddress();
            }
            synchronized (this) {
                serverSocket = ServerSocketChannel.open();
                serverSocket.bind(new InetSocketAddress(bindTo, curPort));
            }
        } catch (IOException ex) {
            NotifyDescriptor message = new NotifyDescriptor.Message(
                            Bundle.ERR_CannotListen(), NotifyDescriptor.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notifyLater(message);
            return;
        }

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                ServerSocketChannel ss;
                
                synchronized (Server.this) {
                    ss = serverSocket;
                }
                if (ss == null) {
                    return;
                }
                SocketAddress sa = null;
                try {
                    while (true) {
                        try {
                            if (sa == null) {
                                sa = ss.getLocalAddress();
                                LOG.log(Level.INFO, "Listening starts on {0}", sa);
                            }
                            SocketChannel clientSocket = ss.accept();
                            synchronized (Server.this) {
                                if (serverRunnable != this) {
                                    clientSocket.close();
                                    return;
                                }
                                clientCount++;
                            }
                            DocumentFactory f = onNewClient();
                            if (f == null) {
                                f = rootDocumentFactory;
                            }
                            NETWORK_RP.post(new Client(clientSocket, f, monitor, streamLoader) {
                                public void run() {
                                    try {
                                        super.run();
                                    } finally {
                                        notifyClientTerminated();
                                    }
                                }
                            }, 0, Thread.MAX_PRIORITY);
                        } catch (ClosedChannelException ex) {
                            LOG.log(Level.INFO, "Listening terminated on {0}", sa);
                            return;
                        } catch (IOException ex) {
                            NotifyDescriptor message = new NotifyDescriptor.Message(
                                            Bundle.ERR_ProcessingAccept(ex.toString()), NotifyDescriptor.ERROR_MESSAGE);
                            DialogDisplayer.getDefault().notifyLater(message);
                            return;
                        }
                        // break;
                    }
                } finally {
                    synchronized (Server.this) {
                        if (serverRunnable == this) {
                            if (serverSocket != null && serverSocket.isOpen()) {
                                try {
                                    serverSocket.close();
                                } catch (IOException ex) {
                                    LOG.log(Level.WARNING, "Could not close server for {0}", sa);
                                }
                            }
                            serverRunnable = null;
                            serverSocket = null;
                        }
                    }
                }
            }
        };

        serverRunnable = runnable;

        RequestProcessor.getDefault().post(runnable, 0, Thread.MAX_PRIORITY);
    }
    
    protected DocumentFactory onNewClient() {
        return null;
    }
    
    /**
     * Overridable method that informs that all clients
     * are closed at the moment. The method is invoked under a lock -
     * while executing, the IGV server cannot accept new connections.
     */
    protected void onAllClientsClosed() {
        // no op
    }
    
    private synchronized void notifyClientTerminated() {
        if (--clientCount == 0) {
            onAllClientsClosed();
        }
    }
}

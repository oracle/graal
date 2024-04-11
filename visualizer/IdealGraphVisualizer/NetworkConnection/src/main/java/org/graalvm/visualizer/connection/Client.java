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

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;

import org.graalvm.visualizer.data.serialization.lazy.NetworkStreamContent;
import org.graalvm.visualizer.data.serialization.lazy.ScanningModelBuilder;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.DocumentFactory;
import jdk.graal.compiler.graphio.parsing.ParseMonitor;

public class Client implements Runnable {
    private static final Logger LOG = Logger.getLogger(Client.class.getName());

    private final SocketChannel socket;
    private final DocumentFactory rootDocumentFactory;
    private final RequestProcessor loader;
    private final ParseMonitor monitor;

    public Client(SocketChannel socket,
                  DocumentFactory rootDocumentFactory, ParseMonitor monitor, RequestProcessor loadProcessor) {
        this.socket = socket;
        this.rootDocumentFactory = rootDocumentFactory;
        this.loader = loadProcessor;
        this.monitor = monitor;
    }

    /**
     * Model operations should happen in a dedicated thread, AWT right now.
     *
     * @param r
     */
    private void runInAWT(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    private static final AtomicInteger clientId = new AtomicInteger();

    @Override
    public void run() {
        int id = clientId.incrementAndGet();
        try {
            LOG.log(Level.FINE, "Client {0} starting for remote {1}", new Object[]{id, socket.getRemoteAddress()});
            final SocketChannel channel = socket;
            channel.configureBlocking(true);
            try (NetworkStreamContent captureChannel = new NetworkStreamContent(channel, Places.getCacheSubdirectory("igv"))) { // NOI18N
                Object docId = captureChannel.getDumpFile();
                BinarySource bs = new BinarySource(docId, captureChannel);
                ScanningModelBuilder mb = new ScanningModelBuilder(
                        bs, captureChannel, rootDocumentFactory,
                        monitor,
                        loader);
                mb.setDocumentId(docId);
                BinaryReader reader = new BinaryReader(bs, mb);
                reader.parse();
            }
        } catch (EOFException ex) {
            LOG.log(Level.INFO, "Client {0} encountered end-of-file", id);
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Error during processing the stream",
                    Exceptions.attachSeverity(ex, Level.INFO));
        } finally {
            try {
                socket.close();
                LOG.log(Level.FINE, "Client {0} terminated", id);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
}

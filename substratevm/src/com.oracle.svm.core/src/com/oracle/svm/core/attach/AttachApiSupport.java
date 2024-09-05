/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.attach;

import org.graalvm.nativeimage.ImageSingletons;

import java.io.File;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import com.oracle.svm.core.log.Log;

import jdk.graal.compiler.api.replacements.Fold;

import static java.net.StandardProtocolFamily.UNIX;

/**
 * This class is responsible for initialization/shutdown of the Attach-API. This includes performing
 * the initialization handshake and setting up the UNIX domain sockets. Similar to Hotspot, once
 * initialized, it will dispatch a dedicated thread to handle new connections.
 */
public class AttachApiSupport {
    private boolean initialized;
    private ServerSocketChannel serverChannel;
    AttachListenerThread attachListenerThread;
    private Path socketFile;

    @Fold
    public static AttachApiSupport singleton() {
        return ImageSingletons.lookup(AttachApiSupport.class);
    }

    synchronized void maybeInitialize() {
        if (isInitTrigger()) {
            if (!initialized) {
                init();
            } else if (!Files.exists(getSocketFilePath())) {
                // If socket file is missing, but we're already initialized, restart.
                teardown();
                init();
            }
        }
    }

    private synchronized void init() {
        assert (!initialized);
        // Set up Attach API unix domain socket
        serverChannel = createServerSocket();
        if (serverChannel != null) {
            attachListenerThread = new AttachListenerThread(serverChannel);
            attachListenerThread.start();
            initialized = true;
        }
    }

    /** Stop dedicated thread. Close socket. Uninitialized. Can be initialized again. */
    public synchronized void teardown() {
        if (initialized) {
            try {
                attachListenerThread.shutdown();
                attachListenerThread.join();
                serverChannel.close();
                socketFile = null;
                initialized = false;
                // .close() does not delete the file.
                Files.deleteIfExists(getSocketFilePath());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * This method determines whether the SIGQUIT we've received is actually a signal to start the
     * Attach API handshake. It is loosely based on AttachListener::is_init_trigger() in
     * attachListener.cpp in jdk-24+2.
     */
    private static boolean isInitTrigger() {
        // Determine whether an attempt to use the Attach API is being made.
        File attachFile = new File(".attach_pid" + ProcessHandle.current().pid());

        if (!attachFile.exists()) {
            // Check the alternate location.
            String tempDir = System.getProperty("java.io.tmpdir");
            attachFile = new File(tempDir + "/.attach_pid" + ProcessHandle.current().pid());
            if (!attachFile.exists()) {
                Log.log().string("Attach-API could not find .attach_pid file").newline();
                return false;
            }
        }
        return true;
    }

    private Path getSocketFilePath() {
        if (socketFile == null) {
            socketFile = Paths.get(getSocketPathString());
        }
        return socketFile;
    }

    private static String getSocketPathString() {
        long pid = ProcessHandle.current().pid();
        String tempDir = System.getProperty("java.io.tmpdir");
        if (tempDir == null) {
            tempDir = "/tmp";
        }
        if (!Files.isDirectory(Paths.get(tempDir))) {
            throw new RuntimeException("Could not find temporary directory.");
        }
        return tempDir + "/.java_pid" + pid;
    }

    /**
     * This method creates the server socket channel that will be handed over to the dedicated
     * attach API listener thread. We must set specific permissions on the socket file, otherwise
     * the client will not accept it. It is important that the permissions are set before the
     * filename is set to the correct name the client is polling for.
     */
    private ServerSocketChannel createServerSocket() {
        String socketPathString = getSocketPathString();
        Path initialPath = Paths.get(socketPathString + "_tmp");
        Path finalPath = getSocketFilePath();
        var address = UnixDomainSocketAddress.of(initialPath);
        ServerSocketChannel sc = null;
        try {
            sc = ServerSocketChannel.open(UNIX);
            // Create the socket file
            sc.bind(address);
            // Change the file permissions
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(initialPath, permissions);

            // Rename socket file so it can begin being used.
            Files.move(initialPath, finalPath);
            Files.deleteIfExists(initialPath);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(initialPath);
                Files.deleteIfExists(finalPath);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            Log.log().string("Unable to create server socket. " + e).newline();
        }
        return sc;
    }
}

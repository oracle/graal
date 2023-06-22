/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import static org.graalvm.compiler.debug.DebugOptions.PrintGraphHost;
import static org.graalvm.compiler.debug.DebugOptions.PrintGraphPort;
import static org.graalvm.compiler.debug.PathUtilities.getParent;
import static org.graalvm.compiler.debug.PathUtilities.isDirectory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;

import org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.common.NativeImageReinitialize;

final class IgvDumpChannel implements WritableByteChannel {
    private final Supplier<String> pathProvider;
    private final OptionValues options;
    private WritableByteChannel sharedChannel;
    private boolean closed;

    IgvDumpChannel(Supplier<String> pathProvider, OptionValues options) {
        this.pathProvider = pathProvider;
        this.options = options;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        WritableByteChannel channel = channel();
        return channel == null ? 0 : channel.write(src);
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
    }

    void realClose() throws IOException {
        closed = true;
        if (sharedChannel != null) {
            sharedChannel.close();
            sharedChannel = null;
        }
    }

    WritableByteChannel channel() throws IOException {
        if (closed) {
            throw new IOException("already closed");
        }
        if (sharedChannel == null) {
            PrintGraphTarget target = DebugOptions.PrintGraph.getValue(options);
            if (target == PrintGraphTarget.File) {
                sharedChannel = createFileChannel(pathProvider, null);
            } else if (target == PrintGraphTarget.Network) {
                sharedChannel = createNetworkChannel(pathProvider, options);
            } else {
                TTY.println("WARNING: Graph dumping requested but value of %s option is %s", DebugOptions.PrintGraph.getName(), PrintGraphTarget.Disable);
            }
        }
        return sharedChannel;
    }

    private static WritableByteChannel createNetworkChannel(Supplier<String> pathProvider, OptionValues options) throws IOException {
        String host = PrintGraphHost.getValue(options);
        int port = PrintGraphPort.getValue(options);
        try {
            WritableByteChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
            String targetAnnouncement = String.format("Connected to the IGV on %s:%d", host, port);
            maybeAnnounceTarget(targetAnnouncement);
            return channel;
        } catch (ClosedByInterruptException | InterruptedIOException e) {
            /*
             * Interrupts should not count as errors because they may be caused by a cancelled Graal
             * compilation. ClosedByInterruptException occurs if the SocketChannel could not be
             * opened. InterruptedIOException occurs if new Socket(..) was interrupted.
             */
            return null;
        } catch (IOException e) {
            String networkFailure = String.format("Could not connect to the IGV on %s:%d", host, port);
            if (pathProvider != null) {
                return createFileChannel(pathProvider, networkFailure);
            } else {
                throw new IOException(networkFailure, e);
            }
        }
    }

    @NativeImageReinitialize private static String lastTargetAnnouncement;

    private static void maybeAnnounceTarget(String targetAnnouncement) {
        if (!targetAnnouncement.equals(lastTargetAnnouncement)) {
            // Ignore races - an extra announcement is ok
            lastTargetAnnouncement = targetAnnouncement;
            TTY.println(targetAnnouncement);
        }
    }

    private static WritableByteChannel createFileChannel(Supplier<String> pathProvider, String networkFailure) throws IOException {
        String path = pathProvider.get();
        try {
            WritableByteChannel channel = PathUtilities.openFileChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            String dir = isDirectory(path, false) ? path : getParent(path);
            if (networkFailure == null) {
                maybeAnnounceTarget("Dumping IGV graphs in " + dir);
            } else {
                maybeAnnounceTarget(networkFailure + ". Dumping IGV graphs in " + dir);
            }
            return channel;
        } catch (IOException e) {
            throw new IOException(String.format("Failed to open %s to dump IGV graphs", path), e);
        }
    }

}

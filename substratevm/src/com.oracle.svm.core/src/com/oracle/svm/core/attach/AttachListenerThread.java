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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.ImageSingletons;
import com.oracle.svm.core.dcmd.DcmdSupport;
import com.oracle.svm.core.dcmd.DcmdParseException;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.BasedOnJDKFile;

/**
 * This class is responsible for receiving connections and dispatching to the appropriate tool (jcmd
 * etc).
 */
public final class AttachListenerThread extends Thread {
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/hotspot/share/services/attachListener.hpp#L143") //
    private static final int ARG_LENGTH_MAX = 1024;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/hotspot/share/services/attachListener.hpp#L144") //
    private static final int ARG_COUNT_MAX = 3;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/hotspot/os/posix/attachListener_posix.cpp#L84") //
    private static final char VERSION = '1';
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/hotspot/os/posix/attachListener_posix.cpp#L259") //
    private static final int VERSION_SIZE = 8;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/hotspot/os/posix/attachListener_posix.cpp#L87") //
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.attach/share/classes/sun/tools/attach/HotSpotVirtualMachine.java#L401") //
    private static final String ATTACH_ERROR_BAD_VERSION = "101";
    private static final String RESPONSE_CODE_OK = "0";
    private static final String RESPONSE_CODE_BAD = "1";
    private static final String JCMD_COMMAND_STRING = "jcmd";

    private ServerSocketChannel serverSocketChannel;
    private volatile boolean shutdown = false;

    public AttachListenerThread(ServerSocketChannel serverSocketChannel) {
        super("AttachListener");
        this.serverSocketChannel = serverSocketChannel;
        setDaemon(true);
    }

    /**
     * The method is the main loop where the dedicated listener thread accepts client connections
     * and handles commands. It is loosely based on AttachListenerThread::thread_entry in
     * attachListener.cpp in jdk-24+2.
     */
    @Override
    public void run() {
        AttachRequest request = null;
        while (true) {
            try {
                // Dequeue a connection from socket. May block inside here waiting for connections.
                request = dequeue(serverSocketChannel);

                // Check if this thread been signalled to finish executing.
                if (shutdown) {
                    return;
                }

                // Find the correct handler to dispatch too.
                if (request.name.equals(JCMD_COMMAND_STRING)) {
                    String response = jcmd(request.arguments);
                    sendResponse(request.clientChannel, response, RESPONSE_CODE_OK);
                } else {
                    sendResponse(request.clientChannel, "Invalid Operation. Only JCMD is supported currently.", RESPONSE_CODE_BAD);
                }

                request.clientChannel.close();

            } catch (IOException e) {
                request.closeConnection();
                AttachApiSupport.singleton().teardown();
            } catch (DcmdParseException e) {
                sendResponse(request.clientChannel, e.getMessage(), RESPONSE_CODE_BAD);
                request.closeConnection();
            }
        }
    }

    /**
     * This method will loop or block until a valid actionable request is received. It is loosely
     * based on PosixAttachListener::dequeue() in attachListener_posix.cpp in jdk-24+2.
     */
    private AttachRequest dequeue(ServerSocketChannel serverChannel) throws IOException {
        AttachRequest request = new AttachRequest();
        while (true) {

            if (shutdown) {
                return null;
            }

            try {
                // Block waiting for a connection
                request.clientChannel = serverChannel.accept();
            } catch (ClosedByInterruptException e) {
                // Allow unblocking if a teardown has been signalled.
                return null;
            }

            readRequest(request);
            if (request.error != null) {
                sendResponse(request.clientChannel, null, request.error);
                request.reset();
            } else if (request.name == null || request.arguments == null) {
                // Didn't get any usable request data. Try again.
                request.reset();
            } else {
                return request;
            }
        }
    }

    /**
     * This method reads and processes a single request from the socket. It is loosely based on
     * PosixAttachListener::read_request in attachListener_posix.cpp in jdk-24+2.
     */
    private static void readRequest(AttachRequest request) throws IOException {
        int expectedStringCount = 2 + ARG_COUNT_MAX;
        int maxLen = (VERSION_SIZE + 1) + (ARG_LENGTH_MAX + 1) + (ARG_COUNT_MAX * (ARG_LENGTH_MAX + 1));
        int strCount = 0;
        long left = maxLen;
        ByteBuffer buf = ByteBuffer.allocate(maxLen);

        // The current position to inspect.
        int bufIdx = 0;
        // The start of the arguments.
        int argIdx = 0;
        // The start of the command type name.
        int nameIdx = 0;

        // Start reading messages
        while (strCount < expectedStringCount && left > 0) {

            // Do a single read.
            int bytesRead = request.clientChannel.read(buf);

            // Check if finished or error.
            if (bytesRead < 0) {
                break;
            }

            // Process data from a single read.
            for (int i = 0; i < bytesRead; i++) {
                if (buf.get(bufIdx) == 0) {
                    if (strCount == 0) {
                        // The first string should be the version identifier.
                        if ((char) buf.get(bufIdx - 1) == VERSION) {
                            nameIdx = bufIdx + 1;
                        } else {
                            /*
                             * Version is no good. Drain reads to avoid "Connection reset by peer"
                             * before sending error code and starting again.
                             */
                            request.error = ATTACH_ERROR_BAD_VERSION;
                        }
                    } else if (strCount == 1) {
                        // The second string specifies the command type.
                        argIdx = bufIdx + 1;
                        request.name = StandardCharsets.UTF_8.decode(buf.slice(nameIdx, bufIdx - nameIdx)).toString();
                    }
                    strCount++;
                }
                bufIdx++;
            }
            left -= bytesRead;
        }

        // Only set arguments if we read real data.
        if (argIdx > 0 && bufIdx > 0) {
            // Remove non-printable characters from the result.
            request.arguments = StandardCharsets.UTF_8.decode(buf.slice(argIdx, bufIdx - 1 - argIdx)).toString().replaceAll("\\P{Print}", "");
        }
    }

    private static String jcmd(String arguments) throws DcmdParseException {
        return ImageSingletons.lookup(DcmdSupport.class).parseAndExecute(arguments);
    }

    /**
     * This method sends response data, or error data back to the client. It is loosely based on
     * PosixAttachOperation::complete in in attachListener_posix.cpp in jdk-24+2.
     */
    private static void sendResponse(SocketChannel clientChannel, String response, String code) {
        try {
            // Send result
            ByteBuffer buffer = ByteBuffer.allocate(32);
            buffer.clear();
            buffer.put((code + "\n").getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            clientChannel.write(buffer);

            if (response != null && !response.isEmpty()) {
                // Send data
                byte[] responseBytes = response.getBytes();
                buffer = ByteBuffer.allocate(responseBytes.length);
                buffer.clear();
                buffer.put(responseBytes);
                buffer.flip();
                clientChannel.write(buffer);
            }
        } catch (IOException e) {
            Log.log().string("Unable to send Attach API response: " + e).newline();
        }
    }

    /** This method is called to notify the listener thread that it should finish. */
    void shutdown() {
        shutdown = true;
        this.interrupt();
    }

    /** This represents one individual connection/command request. */
    static class AttachRequest {
        public String name;
        public String arguments;
        public SocketChannel clientChannel;
        public String error;

        public void reset() {
            closeConnection();
            clientChannel = null;
            error = null;
            name = null;
            arguments = null;
        }

        public void closeConnection() {
            if (clientChannel != null && clientChannel.isConnected()) {
                try {
                    clientChannel.close();
                } catch (IOException e) {
                    // Do nothing.
                }
            }
        }
    }
}

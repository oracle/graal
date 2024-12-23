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

package com.oracle.svm.core.posix.attach;

import java.nio.charset.StandardCharsets;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.attach.AttachListenerThread;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.BasedOnJDKFile;

public final class PosixAttachListenerThread extends AttachListenerThread {
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+3/src/hotspot/os/aix/attachListener_aix.cpp#L82") //
    private static final String PROTOCOL_VERSION = "1";
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+3/src/hotspot/os/aix/attachListener_aix.cpp#L269") //
    private static final int VERSION_SIZE = 8;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+3/src/hotspot/os/aix/attachListener_aix.cpp#L85") //
    private static final int ATTACH_ERROR_BAD_VERSION = 101;

    /**
     * Each attach request consists of a fixed number of zero-terminated UTF-8 strings.
     * {@code <version><commandName><arg0><arg1><arg2>}
     */
    private static final int EXPECTED_STRING_COUNT = 2 + ARG_COUNT_MAX;
    private static final int MAX_REQUEST_LEN = (VERSION_SIZE + 1) + (NAME_LENGTH_MAX + 1) + (ARG_COUNT_MAX * (ARG_LENGTH_MAX + 1));

    private final int listener;

    public PosixAttachListenerThread(int listener) {
        this.listener = listener;
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+3/src/hotspot/os/posix/attachListener_posix.cpp#L254-L311")
    protected PosixAttachOperation dequeue() {
        while (true) {
            int socket = AttachHelper.waitForRequest(listener);
            if (socket == -1) {
                return null;
            }

            PosixAttachOperation op = readRequest(socket);
            if (op == null) {
                /* Close the socket and try again. */
                Unistd.NoTransitions.close(socket);
            } else {
                return op;
            }
        }
    }

    /** This method reads and processes a single request from the socket. */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+3/src/hotspot/os/aix/attachListener_aix.cpp#L268-L359")
    private static PosixAttachOperation readRequest(int socket) {
        int strCount = 0;
        int[] stringEnds = new int[EXPECTED_STRING_COUNT];
        Pointer buf = StackValue.get(MAX_REQUEST_LEN);

        /* Read until all expected strings have been read, the buffer is full, or EOF. */
        int offset = 0;
        do {
            int n = PosixUtils.readUninterruptibly(socket, buf, MAX_REQUEST_LEN, offset);
            if (n == -1) {
                return null;
            } else if (n == 0) {
                break;
            }

            int end = offset + n;
            while (offset < end) {
                if (buf.readByte(offset) == 0) {
                    /* End-of-string found. */
                    stringEnds[strCount] = offset;
                    strCount++;
                }
                offset++;
            }
        } while (offset < MAX_REQUEST_LEN && strCount < EXPECTED_STRING_COUNT);

        if (strCount != EXPECTED_STRING_COUNT) {
            /* Incomplete or invalid request. */
            return null;
        }

        String version = decodeString(buf, stringEnds, 0);
        if (!PROTOCOL_VERSION.equals(version)) {
            complete(socket, ATTACH_ERROR_BAD_VERSION, null);
            return null;
        }

        String name = decodeString(buf, stringEnds, 1);
        if (name.length() > NAME_LENGTH_MAX) {
            return null;
        }

        String arg0 = decodeString(buf, stringEnds, 2);
        if (arg0.length() > ARG_LENGTH_MAX) {
            return null;
        }

        String arg1 = decodeString(buf, stringEnds, 3);
        if (arg1.length() > ARG_LENGTH_MAX) {
            return null;
        }

        String arg2 = decodeString(buf, stringEnds, 4);
        if (arg2.length() > ARG_LENGTH_MAX) {
            return null;
        }

        return new PosixAttachOperation(name, arg0, arg1, arg2, socket);
    }

    private static String decodeString(Pointer buf, int[] stringEnds, int index) {
        int start = index == 0 ? 0 : stringEnds[index - 1] + 1;
        int length = stringEnds[index] - start;
        assert length >= 0;
        return CTypeConversion.toJavaString((CCharPointer) buf.add(start), Word.unsigned(length), StandardCharsets.UTF_8);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+3/src/hotspot/os/posix/attachListener_posix.cpp#L321-L328")
    private static void complete(int socket, int code, String response) {
        /* Send the return code. */
        byte[] returnCodeData = Integer.toString(code).getBytes(StandardCharsets.UTF_8);
        sendData(socket, returnCodeData);

        byte[] lineBreak = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
        sendData(socket, lineBreak);

        /* Send the actual response message. */
        if (response != null && !response.isEmpty()) {
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            sendData(socket, responseBytes);
            sendData(socket, lineBreak);
        }

        AttachHelper.shutdownSocket(socket);
        Unistd.NoTransitions.close(socket);
    }

    private static void sendData(int socket, byte[] data) {
        PosixUtils.writeUninterruptibly(socket, data);
    }

    private static class PosixAttachOperation extends AttachOperation {
        private final int socket;

        PosixAttachOperation(String name, String arg0, String arg1, String arg2, int socket) {
            super(name, arg0, arg1, arg2);
            this.socket = socket;
        }

        @Override
        public void complete(int code, String response) {
            PosixAttachListenerThread.complete(socket, code, response);
        }
    }
}

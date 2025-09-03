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

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.attach.AttachListenerThread;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.BasedOnJDKFile;

public final class PosixAttachListenerThread extends AttachListenerThread {
    private final int listener;

    public PosixAttachListenerThread(int listener) {
        this.listener = listener;
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L256-L313")
    protected AttachOperation dequeue() {
        while (true) {
            int socket = AttachHelper.waitForRequest(listener);
            if (socket == -1) {
                return null;
            }

            PosixAttachSocketChannel channel = new PosixAttachSocketChannel(socket);
            AttachOperation op = readRequest(channel);
            if (op == null) {
                channel.close();
            } else {
                return op;
            }
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L102-L139")
    private static class PosixAttachSocketChannel extends AttachSocketChannel {
        private int socket;

        PosixAttachSocketChannel(int socket) {
            this.socket = socket;
        }

        @Override
        public int read(PointerBase buffer, int size) {
            return PosixUtils.readUninterruptibly(socket, (Pointer) buffer, size, 0);
        }

        @Override
        protected void write(byte[] data) {
            PosixUtils.writeUninterruptibly(socket, data);
        }

        protected boolean isOpen() {
            return socket != -1;
        }

        @Override
        public void close() {
            if (isOpen()) {
                AttachHelper.shutdownSocket(socket);
                Unistd.NoTransitions.close(socket);
                socket = -1;
            }
        }
    }
}

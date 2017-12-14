/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public final class OutputHandler {

    final ListeneableOutputStream out = new ListeneableOutputStream();
    final ListeneableOutputStream err = new ListeneableOutputStream();

    public OutputStream getOut() {
        return out;
    }

    public OutputStream getErr() {
        return err;
    }

    void setOutListener(Listener l) {
        out.l = l;
    }

    void setErrListener(Listener l) {
        err.l = l;
    }

    private static class ListeneableOutputStream extends OutputStream {

        private final CharBuffer cb = CharBuffer.allocate(8192);
        private final RBCH rbch = new RBCH();
        private final Reader r = Channels.newReader(rbch, "UTF-8");
        volatile Listener l;

        @Override
        public void write(int b) throws IOException {
            rbch.put((byte) b);
            wl();
        }

        @Override
        public void write(byte[] b) throws IOException {
            rbch.put(b);
            wl();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            rbch.put(b, off, len);
            wl();
        }

        private void wl() throws IOException {
            if (l == null) {
                return;
            }
            while (!rbch.isEmpty()) {
                int n = r.read(cb);
                if (n == 0) {
                    break;
                }
                cb.flip();
                String str = cb.toString();
                l.outputText(str);
                cb.clear();
            }
        }

        private class RBCH implements ReadableByteChannel {

            private byte[] b;
            private int off;
            private int len;

            @SuppressWarnings("hiding")
            void put(int b) {
                this.b = new byte[]{(byte) b};
                this.off = 0;
                this.len = 1;
            }

            @SuppressWarnings("hiding")
            void put(byte[] b) {
                put(b, 0, b.length);
            }

            @SuppressWarnings("hiding")
            void put(byte[] b, int off, int len) {
                this.b = b;
                this.off = off;
                this.len = len;
            }

            boolean isEmpty() {
                return len == 0;
            }

            @Override
            public int read(ByteBuffer dst) throws IOException {
                if (len == 0) {
                    return 0;
                }
                int n = dst.remaining();
                n = Math.min(n, len);
                dst.put(b, off, n);
                off += n;
                len -= n;
                return n;
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() throws IOException {
            }

        }
    }

    interface Listener {
        void outputText(String str);
    }

    public interface Provider {
        OutputHandler getOutputHandler();
    }
}

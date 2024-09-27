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

package org.graalvm.visualizer.data.serialization.lazy;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Semaphore;

import org.openide.util.Exceptions;

/**
 * @author sdedic
 */
public class FreezeChannel implements ReadableByteChannel {
    public volatile long freezeAt;
    private ReadableByteChannel delegate;
    private long offset;
    public Semaphore condition = new Semaphore(0);
    public Semaphore frozen = new Semaphore(0);
    public Throwable throwException;
    public volatile boolean eof;

    public FreezeChannel(ReadableByteChannel delegate, long start, long freezeAt) throws IOException {
        this.delegate = delegate;
        this.freezeAt = freezeAt;
        this.offset = start;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (eof) {
            throw new EOFException();
        }
        int max = dst.remaining();
        if (max == 0) {
            // sorry
            return 0;
        }
        if (offset <= freezeAt && offset + max > freezeAt) {
            max = (int) (freezeAt - offset);
        }
        if (max == 0) {
            try {
                freezeAt = -1;
                frozen.release();
                condition.acquire();
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
            if (throwException != null) {
                Throwable t = throwException;
                throwException = null;
                if (t instanceof IOException) {
                    throw (IOException) t;
                } else if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else if (t instanceof Error) {
                    throw (Error) t;
                }
            } else if (eof) {
                throw new EOFException();
            }
            int res = read(dst);
            // offset already updated by recursive call
            return res;
        } else {
            ByteBuffer copy = dst.duplicate();
            copy.limit(copy.position() + max);
            int bytes = delegate.read(copy);
            if (bytes == -1) {
                return bytes;
            }
            dst.position(dst.position() + bytes);
            offset += bytes;
            return bytes;
        }
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}

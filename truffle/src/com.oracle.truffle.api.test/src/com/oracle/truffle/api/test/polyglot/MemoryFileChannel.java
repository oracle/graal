/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import org.graalvm.polyglot.io.FileLock;
import org.graalvm.polyglot.io.FileChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.util.Arrays;
import java.util.function.Consumer;

abstract sealed class MemoryFileChannel implements FileChannel permits MemoryFileChannelImpl {

    private final Consumer<MemoryFileChannel> closeAction;
    final boolean read;
    final boolean write;
    final boolean append;
    byte[] originalData;
    byte[] data;
    boolean dirty;
    boolean mapped;
    long limit;
    private boolean closed;
    private long pos;

    MemoryFileChannel(
                    byte[] originalData,
                    byte[] data,
                    Consumer<MemoryFileChannel> closeAction,
                    boolean read,
                    boolean write,
                    boolean append) {
        this.originalData = originalData;
        this.data = data;
        this.closeAction = closeAction;
        this.read = read;
        this.write = write;
        this.append = append;
        this.limit = data.length;
        this.pos = append ? limit : 0;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkClosed();
        checkRead();
        final long available = limit - pos;
        if (available == 0) {
            return -1;
        }
        final int toRead = Math.min((int) available, dst.limit());
        dst.put(data, (int) pos, toRead);
        pos += toRead;
        return toRead;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkClosed();
        checkWrite();
        if (mapped) {
            throw new UnsupportedOperationException("Modification of a mapped channel is unsupported.");
        }
        dirty = true;
        final int len = src.limit() - src.position();
        ensureCapacity((int) (pos + len));
        src.get(data, (int) pos, len);
        pos += len;
        if (pos > limit) {
            limit = pos;
        }
        return len;
    }

    @Override
    public long position() throws IOException {
        checkClosed();
        return pos;
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        checkClosed();
        if (newPosition < 0) {
            throw new IllegalArgumentException(String.valueOf(newPosition));
        }
        pos = newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        checkClosed();
        return limit;
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        checkClosed();
        checkWrite();
        if (mapped) {
            throw new UnsupportedOperationException("Modification of a mapped channel is unsupported.");
        }
        dirty = true;
        if (size < 0) {
            throw new IllegalArgumentException(String.valueOf(size));
        }
        if (append) {
            throw new IOException("Truncate not allowed in append mode.");
        }
        if (size < limit) {
            limit = size;
        }
        if (pos > limit) {
            pos = limit;
        }
        return this;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        closeAction.accept(this);
    }

    private void checkRead() {
        if (!read) {
            throw new NonReadableChannelException();
        }
    }

    private void checkWrite() {
        if (!write) {
            throw new NonWritableChannelException();
        }
    }

    void checkClosed() throws ClosedChannelException {
        if (closed) {
            throw new ClosedChannelException();
        }
    }

    private byte[] ensureCapacity(final int requiredLength) {
        if (requiredLength > data.length) {
            data = Arrays.copyOf(data, Math.max(requiredLength, data.length << 1));
        }
        return data;
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) {
        throw new UnsupportedOperationException();
    }
}

final class MemoryFileChannelImpl extends MemoryFileChannel {

    MemoryFileChannelImpl(
                    byte[] originalData,
                    byte[] data,
                    Consumer<MemoryFileChannel> closeAction,
                    boolean read,
                    boolean write,
                    boolean append) {
        super(originalData, data, closeAction, read, write, append);
    }
}

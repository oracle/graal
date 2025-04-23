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
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@link CachedContent} which works with files. Channels are allocated over
 * memory-mapped buffer from the file.
 */

// PENDING: limit number of memory mappings; make one shared mapping up to the current
// file size, since mappings are freed only when the buffer is GCed, which is unreliable.
public class FileContent implements ReadableByteChannel, CachedContent, AutoCloseable {
    private final Path filePath;
    private FileChannel ioDelegate;
    private boolean eof;
    /**
     * Self-opened channels will be closed by close().
     */
    private boolean selfOpened;

    public FileContent(Path filePath, FileChannel channel) {
        this.filePath = filePath;
        this.ioDelegate = channel;
    }

    @Override
    public String id() {
        return filePath.toString();
    }

    private synchronized void openDelegate() throws IOException {
        if (ioDelegate == null || !ioDelegate.isOpen()) {
            ioDelegate = FileChannel.open(filePath, StandardOpenOption.READ);
            selfOpened = true;
        }
    }

    /**
     * Does nothing
     */
    public boolean resetCache(long offset) {
        return false;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (eof) {
            throw new EOFException();
        }
        openDelegate();
        int count = ioDelegate.read(dst);
        if (count < 0) {
            eof = true;
            return count;
        }
        return count;
    }

    @Override
    public boolean isOpen() {
        return ioDelegate.isOpen();
    }

    @Override
    public synchronized void close() throws IOException {
        if (selfOpened) {
            ioDelegate.close();
        }
        ioDelegate = null;
    }

    private final AtomicInteger subchannelCount = new AtomicInteger();

    private Void subchannelClosed() throws IOException {
        if (subchannelCount.decrementAndGet() == 0) {
            close();
        }
        return null;
    }

    private ReadableByteChannel createLargeChannel(long start, long end) throws IOException {
        List<ByteBuffer> buffers = new ArrayList<>();

        while (end - start >= Integer.MAX_VALUE) {
            MappedByteBuffer mbb = ioDelegate.map(FileChannel.MapMode.READ_ONLY, start, Integer.MAX_VALUE);
            buffers.add(mbb);
            start += Integer.MAX_VALUE;
        }
        if (end > start) {
            buffers.add(ioDelegate.map(FileChannel.MapMode.READ_ONLY, start, end - start));
        }
        return new BufferListChannel(buffers.iterator(), this::subchannelClosed);
    }

    @Override
    public ReadableByteChannel subChannel(long start, long end) throws IOException {
        openDelegate();
        if (end == -1) {
            // XX should also monitor the length of the file
            end = ioDelegate.size();
        }
        if (end - start >= Integer.MAX_VALUE) {
            return createLargeChannel(start, end);
        }
        MappedByteBuffer mbb = ioDelegate.map(FileChannel.MapMode.READ_ONLY, start, end - start);
        subchannelCount.incrementAndGet();
        return new ReadableByteChannel() {
            private boolean closed;
            private boolean eof;

            @Override
            public int read(ByteBuffer dst) throws IOException {
                if (mbb.remaining() == 0) {
                    eof = true;
                    return -1;
                } else if (eof) {
                    throw new EOFException();
                } else if (closed) {
                    throw new ClosedChannelException();
                }
                if (dst.remaining() < mbb.remaining()) {
                    ByteBuffer b = mbb.duplicate();
                    int count = dst.remaining();
                    int pos = mbb.position() + count;
                    b.limit(pos);
                    dst.put(b);
                    mbb.position(pos);
                    return count;
                } else {
                    int count = mbb.remaining();
                    dst.put(mbb);
                    return count;
                }
            }

            @Override
            public boolean isOpen() {
                return !closed;
            }

            @Override
            public void close() throws IOException {
                boolean c = closed;
                closed = true;
                if (!closed) {
                    FileContent.this.subchannelClosed();
                }
            }
        };
    }

    /**
     * Channel which provides contents from a series of buffers.
     */
    public final static class BufferListChannel implements ReadableByteChannel {
        private final Callable<Void> closeHandler;
        private Iterator<ByteBuffer> buffers;
        private ByteBuffer current;
        private boolean eof;

        public BufferListChannel(Iterator<ByteBuffer> buffers, Callable<Void> closeHandler) {
            this.buffers = buffers;
            this.current = buffers.next();
            this.closeHandler = closeHandler;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (eof) {
                throw new EOFException();
            }
            do {
                if (current == null || current.remaining() == 0) {
                    if (!buffers.hasNext()) {
                        eof = true;
                        // clear to allow GC
                        buffers = null;
                        current = null;
                        return -1;
                    }
                    current = buffers.next();
                }
            } while (current.remaining() == 0);
            int cnt = 0;
            if (current.remaining() <= dst.remaining()) {
                cnt = current.remaining();
                dst.put(current);
                current = null;
                return cnt;
            }
            cnt = dst.remaining();
            ByteBuffer from = current.duplicate();
            from.limit(from.position() + dst.remaining());
            dst.put(from);
            current.position(from.limit());
            return cnt;
        }

        @Override
        public boolean isOpen() {
            return !eof;
        }

        @Override
        public void close() throws IOException {
            eof = true;
            if (closeHandler != null) {
                try {
                    closeHandler.call();
                } catch (IOException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IOException(ex);
                }
            }
        }
    }
}

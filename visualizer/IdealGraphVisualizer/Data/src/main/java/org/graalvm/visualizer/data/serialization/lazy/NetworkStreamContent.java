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
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Exceptions;

/**
 * Captures data from the network in a temporary file. This class piggybacks on the main reading
 * {@link BinaryParser} loop - copies data read into the parser's input buffer. The data is received
 * into a temp buffer, then flushed to a file. Chunks from that file (sized as the origial receive
 * buffer) are memory mapped into {@link #cacheBuffers} in a hope that the OS does the memmap
 * effectively and discards pages which are not needed.
 * <p/>
 * {@link Subchannel} can be created for content received from the network and stored in the file.
 * <p/>
 * Note: the chunked mapping is not really needed; instead of that, the file can be re-mapped each
 * time a content is requested.
 */
public class NetworkStreamContent implements ReadableByteChannel, CachedContent, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(NetworkStreamContent.class.getName());

    static int DEFAULT_RECEIVE_BUFFER_SIZE = 10 * 1024 * 1024;    // 10 MBytes

    private static final boolean KEEP_CACHES = Boolean.getBoolean(NetworkStreamContent.class.getName() + ".keepCaches"); // NOI18N

    private static final String CACHE_FILE_EXT = ".bgv"; // NOI18N
    private static final String CACHE_FILE_TEMPLATE = "igvdata_%d"; // NOI18N
    private static final String CACHE_DIRECTORY_NAME = "igv"; // NOI18N

    private final int receiveBufferSize = DEFAULT_RECEIVE_BUFFER_SIZE;
    private final List<ByteBuffer> cacheBuffers = new ArrayList<>();
    private ByteBuffer receiveBuffer;
    private final ReadableByteChannel ioDelegate;
    private final File cacheDir;

    //@GuardedBy(this)
    private FileChannel dumpChannel;
    //@GuardedBy(this)
    File dumpFile;

    private long readBytes;
    private static final AtomicInteger contentIdGenerator = new AtomicInteger();
    private long receiveBufferOffset;

    /**
     * If the file was truncated / remapped, this will be > 0, offset to the start
     * of the file. An attempt to read/obtain content before this offset will throw
     * an exception
     */
    // @GuardedBy(this)
    private long firstBufferOffset;

    private final AtomicInteger bufferId = new AtomicInteger();

    private final String contentId;

    public NetworkStreamContent(ReadableByteChannel ioDelegate, File cacheDir) throws IOException {
        this.cacheDir = cacheDir;
        this.ioDelegate = ioDelegate;
        receiveBuffer = ByteBuffer.allocateDirect(receiveBufferSize);
        synchronized (this) {
            if (cacheDir != null) {
                dumpFile = tempFile();
                contentId = dumpFile.getPath();
                /*
                 * On Linux (MAC ?) when If StandardOpenOption.DELETE_ON_CLOSE is used, the file is removed
                 * right after opening, it consumes disk space, but is not visible. I prefer the old
                 * behaviour of File.deleteOnExit() when the machine attempts to delete the file, and the
                 * user knows what consumes his hard drive.
                 *
                 */
                LOG.log(Level.FINE, "Created temp file {0}, ", dumpFile);
                if (!KEEP_CACHES) {
                    dumpFile.deleteOnExit();
                }
                dumpChannel = FileChannel.open(dumpFile.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.READ,
                        StandardOpenOption.DSYNC,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                dumpFile = null;
                dumpChannel = null;
                contentId = "";
            }
        }
    }

    public File getDumpFile() {
        return dumpFile;
    }

    private File tempFile() throws IOException {
        return File.createTempFile(String.format(CACHE_FILE_TEMPLATE, bufferId.incrementAndGet()), CACHE_FILE_EXT, cacheDir);
    }

    public synchronized boolean resetCache(long offset) {
        if (KEEP_CACHES || firstBufferOffset > offset) {
            return false;
        }
        long pos = firstBufferOffset;
        long off = firstBufferOffset;
        for (Iterator<ByteBuffer> bit = cacheBuffers.iterator(); bit.hasNext(); ) {
            ByteBuffer bb = bit.next();
            int l = bb.capacity();
            pos += l;
            if (pos > offset) {
                break;
            }
            bit.remove();
            // TODO: maybe clean the bb by using ((DirectBuffer) buffer).cleaner().clean()
            off = pos;
        }
        FileChannel wfch = null;
        File f = null;
        // if the incoming channel is closed AND all read bytes were reset,
        // do not even create the new file
        if (!(receiveBuffer == null && readBytes <= offset)) {
            try {
                f = tempFile();
                wfch = FileChannel.open(f.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.READ,
                        StandardOpenOption.DSYNC,
                        StandardOpenOption.TRUNCATE_EXISTING);
                if (!cacheBuffers.isEmpty()) {
                    ByteBuffer out = ByteBuffer.allocate(receiveBufferSize);
                    for (ByteBuffer bb : cacheBuffers) {
                        ByteBuffer copy = bb.asReadOnlyBuffer();
                        out.put(copy);
                        out.flip();
                        while (out.hasRemaining()) {
                            wfch.write(out);
                        }
                        out.clear();
                    }

                }
                wfch.force(true);
            } catch (IOException ex) {
                try {
                    if (f != null) {
                        f.delete();
                    }
                    if (wfch != null) {
                        wfch.close();
                    }
                } catch (IOException clex) {
                    Exceptions.printStackTrace(clex);
                }
                Exceptions.printStackTrace(ex);
                return false;
            }
        }
        if (wfch == null && receiveBuffer != null) {
            return false;
        }
        try {
            dumpFile.delete();
            dumpChannel.close();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        dumpFile = f;
        dumpChannel = wfch;
        firstBufferOffset = off;
        return receiveBuffer == null && dumpFile == null;
    }

    @Override
    public String id() {
        return contentId;
    }

    private synchronized void flushToDisk() throws IOException {
        if (dumpChannel == null) {
            return;
        }
        ByteBuffer bb = receiveBuffer.duplicate();
        bb.flip();
        long startPos = dumpChannel.position();
        int len = bb.remaining();
        dumpChannel.write(bb);
        dumpChannel.force(false);
        receiveBufferOffset += len;
        LOG.log(Level.FINER, "Flushed {0} bytes to {1}, recbuffer starts at {2}", new Object[]{len, dumpFile, receiveBufferOffset});
        ByteBuffer mappedBB = dumpChannel.map(FileChannel.MapMode.READ_ONLY, startPos, len);
        mappedBB.position(len);
        cacheBuffers.add(mappedBB);
        receiveBuffer.clear();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        synchronized (this) {
            if (receiveBuffer == null) {
                notifyAll();
                throw new EOFException();
            }
        }
        int pos = dst.position();
        int count = ioDelegate.read(dst);
        synchronized (this) {
            try {
                if (count < 0) {
                    if (receiveBuffer != null) {
                        flushToDisk();
                        receiveBuffer = null;
                    }
                    return count;
                }
                readBytes += count;

                // buffer in our cache:
                ByteBuffer del = dst.asReadOnlyBuffer();
                del.flip();
                del.position(pos);
                while (del.remaining() > 0) {
                    if (del.remaining() < receiveBuffer.remaining()) {
                        receiveBuffer.put(del);
                    } else {
                        del.limit(pos + receiveBuffer.remaining());
                        receiveBuffer.put(del);
                        flushToDisk();
                        pos = del.position();
                        del = dst.asReadOnlyBuffer();
                        del.flip();
                        del.position(pos);
                    }
                }
            } finally {
                // notify potential waiters:
                notifyAll();
            }
        }
        long bufferedCount = (cacheBuffers.size()) * (long) receiveBufferSize + receiveBuffer.position();
        assert bufferedCount == readBytes;

        return count;
    }

    @Override
    public boolean isOpen() {
        return ioDelegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (receiveBuffer != null) {
                flushToDisk();
                receiveBuffer = null;
                notifyAll();
            }
        }
        ioDelegate.close();
    }

    @Override
    public synchronized ReadableByteChannel subChannel(long start, long end) throws IOException {
        if (start < firstBufferOffset) {
            throw new IOException("Cached content not available");
        }
        LOG.log(Level.FINER, "Allocating subchannel from dumpfile {0} range {1}-{2}", new Object[]{
                dumpFile, start, end});
        Iterator<ByteBuffer> buffers = buffers(start, end);
        if (end > 0) {
            return new FileContent.BufferListChannel(buffers, null);
        } else {
            return new FileContent.BufferListChannel(
                    new FollowupIterator(buffers, readBytes),
                    null
            );
        }
    }

    // @GuardedBy(this)
    private Iterator<ByteBuffer> buffers(long start, long endPos) {
        int fromBuffer = -1;
        long pos = firstBufferOffset;
        long prevPos = 0;
        int startAt = 0;
        int endAt = 0;
        ByteBuffer b;
        ByteBuffer copyBuffer = null;
        ByteBuffer startBuf;
        ByteBuffer endBuf;
        List<ByteBuffer> buffers = new ArrayList<>();
        int toBuffer;
        long end = endPos == -1 ? readBytes : endPos;

        LOG.log(Level.FINER, "Total read bytes {0}, receiveBufferOffset {1}", new Object[]{readBytes, receiveBufferOffset});
        try {

            if (start >= receiveBufferOffset) {
                copyBuffer = ByteBuffer.allocate(receiveBuffer.position());
                ByteBuffer src = receiveBuffer.duplicate().flip();
                copyBuffer.put(src);
                startAt = (int) (start - receiveBufferOffset);
                startBuf = copyBuffer;
                fromBuffer = cacheBuffers.size() - 1;
                LOG.log(Level.FINEST, "start in receiveBuffer, offset {0}", startAt);
            } else {
                do {
                    fromBuffer++;
                    b = cacheBuffers.get(fromBuffer);
                    prevPos = pos;
                    pos += b.position();
                } while (pos <= start);
                startAt = (int) (start - prevPos);
                startBuf = cacheBuffers.get(fromBuffer).asReadOnlyBuffer();
                LOG.log(Level.FINEST, "start in buffer {0}, offset {1}", new Object[]{fromBuffer, startAt});
            }
            toBuffer = fromBuffer;
            pos = prevPos;
            if (end > receiveBufferOffset) {
                if (copyBuffer == null) {
                    copyBuffer = ByteBuffer.allocate(receiveBuffer.position());
                    ByteBuffer src = receiveBuffer.duplicate().flip();
                    copyBuffer.put(src);
                    copyBuffer.flip();
                }
                toBuffer = cacheBuffers.size();
                endAt = (int) (end - receiveBufferOffset);
                endBuf = copyBuffer;
                LOG.log(Level.FINEST, "end in receiveBuffer, offset {0}", endAt);
            } else {
                do {
                    b = cacheBuffers.get(toBuffer);
                    toBuffer++;
                    prevPos = pos;
                    pos += b.position();
                } while (pos < end);
                toBuffer--;
                endAt = (int) (end - prevPos);
                if (fromBuffer == toBuffer) {
                    endBuf = startBuf;
                } else {
                    endBuf = (fromBuffer == toBuffer) ? startBuf : cacheBuffers.get(toBuffer).asReadOnlyBuffer();
                    endBuf.flip();
                }
                LOG.log(Level.FINEST, "end in buffer {0}, offset {1}", new Object[]{toBuffer, endAt});
            }

            startBuf.flip();
            startBuf.position(startAt);
            endBuf.limit(endAt);
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Error reading from dumpfile " + dumpFile, ex);
            throw ex;
        }
        buffers.add(startBuf);
        for (int i = fromBuffer + 1; i < toBuffer; i++) {
            b = cacheBuffers.get(i).asReadOnlyBuffer();
            buffers.add(b.flip());
        }
        if (startBuf != endBuf) {
            buffers.add(endBuf);
        }
        return buffers.iterator();
    }

    private Iterator<ByteBuffer> waitBuffers(long fromPos, Consumer<Long> newPos) {
        while (true) {
            synchronized (this) {
                if (readBytes > fromPos) {
                    LOG.log(Level.FINEST, "Additional buffers available, total read {0}, need {1}", new Object[]{readBytes, fromPos});
                    newPos.accept(readBytes);
                    return buffers(fromPos, -1);
                }
                if (receiveBuffer == null) {
                    return null;
                }
                try {
                    LOG.log(Level.FINEST, "Not enough data ({0}, need {1}), sleeping...", new Object[]{readBytes, fromPos});
                    wait();
                } catch (InterruptedException ex) {
                    return null;
                }
            }
        }
    }

    private synchronized boolean hasMoreData(long pos) {
        return receiveBuffer != null || readBytes > pos;
    }

    class FollowupIterator implements Iterator<ByteBuffer> {
        private Iterator<ByteBuffer> delegate;
        private long pos;
        private boolean eof;

        public FollowupIterator(Iterator<ByteBuffer> delegate, long pos) {
            LOG.log(Level.FINER, "Created followup iterator after pos {0}", pos);
            this.delegate = delegate;
            this.pos = pos;
        }

        void setPos(long pos) {
            this.pos = pos;
        }

        @Override
        public boolean hasNext() {
            if (eof) {
                return false;
            }
            if (delegate != null) {
                if (delegate.hasNext()) {
                    return true;
                }
                delegate = null;
            }
            return hasMoreData(pos);
        }

        @Override
        public ByteBuffer next() {
            if (eof) {
                throw new NoSuchElementException();
            }
            if (delegate == null) {
                delegate = waitBuffers(pos, this::setPos);
                if (delegate == null) {
                    eof = true;
                    return ByteBuffer.allocate(0);
                }
            }
            return delegate.next();
        }
    }
}

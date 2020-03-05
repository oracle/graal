/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.storage;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;

/**
 * Follows src/hotspot/share/jfr/recorder/storage/jfrBuffer.hpp but backed by a
 * direct ByteBuffer 'b', rather than memory allocated via malloc.
 * 
 * Matches JDK 14+ : concurrency related code was altered (for the better I
 * assume) compared to JDK 11
 * 
 * Initially for ByteBuffer 'b': b.position == b.committedPosition ==
 * flushedPosition = 0 b.limit == b.capacity = size
 * 
 * At all times: flushedPosition <= committedPosition <= b.position <= b.limit
 * == b.capacity == size
 * 
 * During event write, b.position updates for each put operation On successful
 * write end, committedPosition is set to b.position Otherwise b.position is
 * reset to committedPosition
 * 
 * The only accessor of b.position should be the EventWriter. Readers should use
 * flushed and committed positions
 * 
 * During read, data between flushedPosition and committedPosition is accessed
 * After read, flushedPosition is updated to committedPosition
 * 
 * committedPosition (_pos): used by readers and changed by writers
 * flushedPosition (_top): used by writers, changed by writers identity:
 * manipulated by storage system when assigning buffers
 *
 * 
 */

public class JfrBuffer {

    private ByteBuffer buffer;
    private final int size;

    private enum Flags {
        RETIRED, TRANSIENT, LEASE, EXCLUDED
    }

    private final EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);

    // JFR.TODO critical section performance can probably be improved here
    // Used when transferring data into the buffer where flushed, committed
    // and buffer data will all change
    private final ReentrantLock globalLock = new ReentrantLock();

    // _pos
    private final AtomicInteger committedPosition = new AtomicInteger(0);

    // _top
    private final AtomicInteger flushedPosition = new AtomicInteger(0);
    // flushedPosition is a critical section modified by thread local on promote as well as recorder service when
    // writing data to disk
    private final ReentrantLock flushedLock = new ReentrantLock();

    private final AtomicLong identity = new AtomicLong(-1);

    public JfrBuffer(int size) {
        this.size = size;
        this.buffer = ByteBuffer.allocateDirect(this.size);
        assert (this.buffer.limit() == this.buffer.capacity() && this.buffer.limit() == this.size);
    }

    public void initialize() {
        assert (this.identity.get() == -1);
        setCommittedPosition(start());
        setFlushedPosition(start());

        // ByteBuffer specific
        this.buffer.clear();

        assert (this.getFreeSize() == this.size);
        assert (this.buffer.limit() == this.buffer.capacity() && this.buffer.limit() == this.size);

        assert (!this.isTransient());
        assert (!this.isLeased());
        assert (!this.isRetired());
    }

    public void reinitialize() {
        this.reinitialize(false);
    }

    public void reinitialize(boolean excluded) {
        this.flushedLock.lock();
        try {
            assert (!this.isLeased());

            if (this.isExcluded() != excluded) {
                if (excluded) {
                    setExcluded();
                } else {
                    clearExcluded();
                }
            }

            this.setCommittedPosition(start());
            this.setFlushedPosition(start());
            this.clearRetired();

            // ByteBuffer specific
            this.buffer.clear();
            assert (this.buffer.limit() == this.buffer.capacity() && this.buffer.limit() == this.size);
        } finally {
            this.flushedLock.unlock();
        }
    }

    public int start() {
        return 0;
    }

    public int end() {
        return this.buffer.limit();
    }

    public int getCommittedPosition() {
        return this.committedPosition.get();
    }

    public void setCommittedPosition(int position) {
        assert (position <= end());
        this.committedPosition.set(position);
    }

    public int getFlushedPosition() {
        return this.flushedPosition.get();
    }

    public void setFlushedPosition(int position) {
        assert (position <= end());
        assert (start() <= position);
        this.flushedPosition.set(position);
    }

    public boolean isEmpty() {
        return this.committedPosition.get() == 0;
    }

    public ReentrantLock getGlobalLock() {
        return this.globalLock;
    }

    public ReentrantLock getFlushLock() {
        return this.flushedLock;
    }

    public void reset() {
        this.buffer.clear();
        this.flushedPosition.set(0);
        this.committedPosition.set(0);
    }

    public boolean acquiredBy(Thread thread) {
        return this.identity.get() == thread.getId();
    }

    public boolean acquiredBySelf() {
        return this.identity.get() == Thread.currentThread().getId();
    }

    public void acquire(Thread thread) {
        if (acquiredBy(thread)) {
            return;
        }
        long newId = thread.getId();
        long currentId;

        do {
            currentId = this.identity.get();
        } while (currentId != -1 || !this.identity.compareAndSet(currentId, newId));
    }

    public boolean tryAcquire(Thread thread) {
        long newId = thread.getId();
        long currentId = this.identity.get();

        return (currentId == -1 && this.identity.compareAndSet(currentId, newId));
    }

    public long identity() {
        return this.identity.get();
    }

    public void release() {
        assert (this.identity.get() != -1);
        this.identity.set(-1);
        if (this.isLeased()) {
            this.clearLeased();
        }
    }

    // Moves data between flushed and committed position to promotion buffer
    // Sets flushed/committed to 0, buffer is empty after
    public void promoteTo(JfrBuffer promotionBuffer, int size) {
        this.flushedLock.lock();
        try {
            assert (this.buffer != null);
            assert (promotionBuffer.buffer != null);
            assert (promotionBuffer.acquiredBySelf());

            int start = this.getFlushedPosition();
            int end = this.getCommittedPosition();

            int actualSize = end - start;
            assert (actualSize <= size);

            if (actualSize > 0) {
                int limit = this.buffer.limit();
                int pos = this.buffer.position();
                try {
                    promotionBuffer.getGlobalLock().lock();
                    this.buffer.position(start);
                    this.buffer.limit(end);
                    promotionBuffer.put(this.buffer);
                    promotionBuffer.setCommittedPosition(promotionBuffer.buffer.position());
                    promotionBuffer.release();

                    this.buffer.limit(limit);
                    this.buffer.position(pos);
                } catch (Exception e) {
                    JfrLogger.logError("JfrBuffer move failed. Data may be corrupted");
                    JfrLogger.logStackTrace(e);
                } finally {
                    promotionBuffer.getGlobalLock().unlock();
                }
            }
            this.setCommittedPosition(start());
            this.setFlushedPosition(start());
        } finally {
            this.flushedLock.unlock();
        }
    }

    // Transfers data from [committed position, committed position + size] to the
    // new buffer. Does not commit data in the new buffer
    public void transferTo(JfrBuffer newBuffer, int size) {
        int oldLimit = this.buffer.limit();
        int oldPosition = this.buffer.position();

        this.buffer.limit(this.getCommittedPosition() + size);
        this.buffer.position(this.getCommittedPosition());
        newBuffer.put(this.buffer);

        this.buffer.limit(oldLimit);
        this.buffer.position(oldPosition);
    }

    public void put(ByteBuffer buffer) {
        assert (this.acquiredBySelf());
        assert (this.getGlobalLock().isHeldByCurrentThread());
        this.buffer.put(buffer);
    }

    public int discard() {
        int pos = this.committedPosition.get();
        int top = this.getFlushedPosition();
        setFlushedPosition(top);
        return pos - top;
    }

    public int getUnflushedSize() {
        return this.committedPosition.get() - this.flushedPosition.get();
    }

    public boolean isTransient() {
        return this.flags.contains(Flags.TRANSIENT);
    }

    public void setTransient() {
        this.flags.add(Flags.TRANSIENT);
        assert (this.isTransient());
    }

    public boolean isLeased() {
        return this.flags.contains(Flags.LEASE);
    }

    public void setLeased() {
        this.flags.add(Flags.LEASE);
        assert (this.isLeased());
    }

    public void clearLeased() {
        this.flags.remove(Flags.LEASE);
        assert (!this.isLeased());
    }

    public boolean isExcluded() {
        return this.flags.contains(Flags.EXCLUDED);
    }

    public void setExcluded() {
        assert (acquiredBySelf());
        this.flags.add(Flags.EXCLUDED);
        assert (this.isExcluded());
    }

    public void clearExcluded() {
        if (this.isExcluded()) {
            assert (this.identity() != -1);
            this.flags.remove(Flags.EXCLUDED);
        }
        assert (!this.isExcluded());
    }

    public void setRetired() {
        this.flags.add(Flags.RETIRED);
    }

    public void clearRetired() {
        this.flags.remove(Flags.RETIRED);
        assert (!this.isRetired());
    }

    public boolean isRetired() {
        return this.flags.contains(Flags.RETIRED);
    }

    public int getFreeSize() {
        return end() - this.committedPosition.get();
    }

    /**
     * SubstrateVM specific below.
     */

    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    public void deallocateBuffer() {
        this.buffer = null;
    }

    public int getSize() {
        return this.buffer.capacity();
    }
}

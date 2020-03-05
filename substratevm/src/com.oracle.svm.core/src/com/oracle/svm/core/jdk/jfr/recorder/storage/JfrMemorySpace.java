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

import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jdk.jfr.recorder.storage.operations.JfrBufferOperations.BufferOperation;

public class JfrMemorySpace<U extends JfrMemorySpaceRetrieval, V extends JfrMemorySpace.Client> {

    public interface Client {
        void registerFull(JfrBuffer buffer, Thread thread);
    }

    private final boolean epochAware;

    private final Queue<JfrBuffer> free = new ConcurrentLinkedQueue<>();
    private final Deque<JfrBuffer> liveOne = new LinkedList<>();
    private final Deque<JfrBuffer> liveTwo = new LinkedList<>();

    private final int minElementSize;
    private final int cacheCount;

    private final V callback;

    public JfrMemorySpace(int minElementSize, int cacheCount, V callback) {
        this(minElementSize, cacheCount, callback, false);
    }

    public JfrMemorySpace(int minElementSize, int cacheCount, V callback, boolean epochAware) {
        this.minElementSize = minElementSize;
        this.cacheCount = cacheCount;
        this.callback = callback;

        this.epochAware = epochAware;
    }

    public boolean initialize(boolean initializeFree) {
        for (int i = 0; i < this.cacheCount; i++) {
            if (initializeFree) {
                this.allocateFree(this.minElementSize);
            } else {
                this.allocateLive(this.minElementSize);
            }
        }

        if (initializeFree) {
            assert (free.size() == this.cacheCount);
        } else {
            assert (live().size() == this.cacheCount);
        }
        return true;
    }

    public int minElementSize() {
        return this.minElementSize;
    }

    public Queue<JfrBuffer> free() {
        return free;
    }

    public Deque<JfrBuffer> live() {
        return live(false);
    }

    public Deque<JfrBuffer> live(boolean previousEpoch) {
        if (this.epochAware) {
            return previousEpoch ? previousEpochList() : currentEpochList();
        }
        return liveOne;
    }

    private Deque<JfrBuffer> previousEpochList() {
        return epochList(JfrTraceIdEpoch.previousEpoch());
    }

    private Deque<JfrBuffer> currentEpochList() {
        return epochList(JfrTraceIdEpoch.currentEpoch());
    }

    private Deque<JfrBuffer> epochList(boolean epoch) {
        if (epoch) {
            return liveOne;
        }
        return liveTwo;
    }

    public JfrBuffer get(int size, Thread thread) {
        return U.get(size, this, thread);
    }

    public void registerFull(JfrBuffer buffer, Thread thread) {
        callback.registerFull(buffer, thread);
    }

    private JfrBuffer allocateFree(int size) {
        JfrBuffer b = new JfrBuffer(size);
        b.initialize();
        this.free().add(b);
        return b;
    }

    private JfrBuffer allocateLive(int size) {
        return allocateLive(size, false);
    }

    private JfrBuffer allocateLive(int size, boolean previousEpoch) {
        JfrBuffer b = new JfrBuffer(size);
        b.initialize();
        this.live(previousEpoch).add(b);
        return b;
    }

    // Acquire buffer, otherwise return null
    private JfrBuffer acquireFree(int size, Thread t) {
        return U.get(size, this, free().iterator(), t);
    }

    private JfrBuffer acquireLive(int size, Thread t) {
        return acquireLive(size, t, false);
    }

    // Acquire buffer, otherwise return null
    private JfrBuffer acquireLive(int size, Thread t, boolean previousEpoch) {
        return U.get(size, this, live(previousEpoch).iterator(), t);
    }

    public void releaseLive(JfrBuffer buffer) {
        assert (buffer != null);
        assert (this.live().contains(buffer));
        this.live().remove(buffer);
        assert (!this.live().contains(buffer));
        if (buffer.isTransient()) {
            this.deallocate(buffer);
            return;
        }
        assert (buffer.isEmpty());
        assert (!buffer.isRetired());
        assert (buffer.identity() == -1);
        if (this.shouldPopulateCache()) {
            assert (!free.contains(buffer));
            this.free().add(buffer);
        } else {
            this.deallocate(buffer);
        }
    }

    private boolean shouldPopulateCache() {
        return this.free.size() < this.cacheCount;
    }

    private void deallocate(JfrBuffer buffer) {
        assert (buffer != null);
        assert (!this.free.contains(buffer));
        assert (!this.live().contains(buffer));
        buffer.deallocateBuffer();
        JfrLogger.logError("deallocate buffer");
    }

    public void iterateLiveList(BufferOperation<JfrBuffer> op) {
        iterateLiveList(op, false);
    }

    public void iterateLiveList(BufferOperation<JfrBuffer> op, boolean previousEpoch) {
        for (JfrBuffer b : live(previousEpoch)) {
            if (!op.process(b)) {
                return;
            }
        }
    }

    // Attempt retry count times to acquire buffer from live, lease it, and return
    // it, null otherwise
    public static JfrBuffer acquireLiveLeaseWithRetry(int size, JfrMemorySpace<?, ?> mspace, int retryCount, Thread t,
            boolean previousEpoch) {

        JfrBuffer b = acquireLiveWithRetry(size, mspace, retryCount, t, previousEpoch);
        if (b != null) {
            b.setLeased();
            return b;
        }
        return null;
    }

    public static JfrBuffer acquireLiveWithRetry(int size, JfrMemorySpace<?, ?> mspace, int retryCount, Thread t) {
        return acquireLiveWithRetry(size, mspace, retryCount, t, false);
    }

    // Attempt retry count times to acquire buffer from live, and return it, null
    // otherwise
    public static JfrBuffer acquireLiveWithRetry(int size, JfrMemorySpace<?, ?> mspace, int retryCount, Thread t,
            boolean previousEpoch) {
        JfrBuffer b;
        for (int i = 0; i < retryCount; i++) {
            b = mspace.acquireLive(size, t, previousEpoch);
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    // Allocate to live, set acquire, lease, transient, and return it
    public static JfrBuffer acquireTransientLeaseToLive(int size, JfrMemorySpace<?, ?> mspace, Thread t,
            boolean previousEpoch) {
        JfrBuffer b = mspace.allocateLive(size, previousEpoch);
        b.acquire(t);
        b.setLeased();
        b.setTransient();

        return b;
    }

    // Acquire or allocate to live, return it
    public static JfrBuffer mspaceGetLive(int size, JfrMemorySpace<?, ?> mspace, Thread t) {
        JfrBuffer b = mspace.acquireFree(size, t);
        if (b != null) {
            mspace.live().add(b);
            return b;
        }

        return mspace.allocateLive(size);
    }

}

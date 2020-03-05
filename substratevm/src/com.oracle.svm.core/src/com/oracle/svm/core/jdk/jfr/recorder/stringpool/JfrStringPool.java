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

package com.oracle.svm.core.jdk.jfr.recorder.stringpool;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrCheckpointClient;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrCheckpointWriter;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.JfrTypeWriter;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkWriter;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrBuffer;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrMemorySpace;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrMemorySpaceRetrieval;
import com.oracle.svm.core.jdk.jfr.recorder.storage.operations.JfrBufferOperations;
import com.oracle.svm.core.jdk.jfr.utilities.JfrTypes;

public final class JfrStringPool implements JfrCheckpointClient {
    private static class JfrString {
        long id;
        String str;

        JfrString(long id, String str) {
            this.id = id;
            this.str = str;
        }

    }

    private static final int bufferCount = 2;
    private static final int bufferSize = 512 * 1024;


    private static JfrStringPool instance;
    private JfrMemorySpace<JfrMemorySpaceRetrieval, JfrStringPool> mspace;

    private final Queue<JfrString> stringsZero = new LinkedList<>();
    private final Queue<JfrString> stringsOne = new LinkedList<>();

    private int sizeZero = 0;
    private int sizeOne = 0;

    private final AtomicInteger currentGeneration = new AtomicInteger(0);
    private final AtomicInteger lastGeneration = new AtomicInteger(0);

    private JfrStringPool() {
    }

    public static JfrStringPool create() {
        assert (instance == null);
        instance = new JfrStringPool();
        return instance;
    }

    public static JfrStringPool instance() {
        return instance;
    }

    public boolean initialize() {
        this.mspace = new JfrMemorySpace<>(bufferSize, bufferCount, this);
        this.mspace.initialize(false);
        assert (this.mspace.free().isEmpty());
        assert (!this.mspace.live().isEmpty());

        return true;
    }

    public boolean isModified() {
        int last = this.lastGeneration.get();
        int current = this.currentGeneration.get();
        if (last != current) {
            this.lastGeneration.compareAndSet(last, this.currentGeneration.get());
            return true;
        }
        return false;
    }

    private Queue<JfrString> queue(boolean epoch) {
        return epoch ? stringsZero : stringsOne;
    }

    private void increment(boolean epoch, int size) {
        if (epoch) {
            sizeZero += size;
        } else {
            sizeOne += size;
        }
    }

    public boolean addStringConstant(boolean epoch, long id, String s) {
        if (JfrTraceIdEpoch.currentEpoch() != epoch) {
            return !epoch;
        }
        queue(epoch).add(new JfrString(id, s));
        increment(epoch, s.length() + 4);
        this.currentGeneration.incrementAndGet();

        return epoch;
    }

    public void write(JfrChunkWriter chunkWriter) {
        boolean epoch = JfrTraceIdEpoch.previousEpoch();

        int size = epoch ? sizeZero : sizeOne;
        if (queue(epoch).size() > 0) {
            // JFR.TODO optimize StringPool to write directly to buffer instead of storing
            // in queue
            JfrBuffer b = lease(size, true, Thread.currentThread());
            b.acquire(Thread.currentThread());
            JfrCheckpointWriter writer = new JfrCheckpointWriter(b, Thread.currentThread(), this);
            writer.open();
            JfrTypeWriter stringWriter = new JfrTypeWriter(JfrTypes.JfrTypeId.TYPE_STRING.id, writer);
            try {
                stringWriter.begin();
                for (JfrString s : queue(epoch)) {
                    stringWriter.incrementCount(writeString(writer, s));
                }
                stringWriter.end();
            } catch (IOException e) {
                e.printStackTrace();
            }
            writer.close();

            JfrBufferOperations.BufferOperation<JfrBuffer> wo = new JfrBufferOperations.CheckpointWrite<>(chunkWriter);
            JfrBufferOperations.CompositeAnd<JfrBuffer> wro = new JfrBufferOperations.CompositeAnd<>(
                    new JfrBufferOperations.MutexedWrite<>(wo),
                    new JfrBufferOperations.ReleaseExcision<>(mspace, mspace.live(true)));

            mspace.iterateLiveList(wro, true);
        }
    }

    private int writeString(JfrCheckpointWriter writer, JfrString s) throws IOException {
        writer.encoded().writeLong(s.id);
        writer.writeString(s.str);
        return 1;
    }

    public void clear() {
        boolean epoch = JfrTraceIdEpoch.previousEpoch();
        queue(epoch).clear();
    }

    @Override
    public void registerFull(JfrBuffer buffer, Thread thread) {
        assert (buffer != null);
        assert (buffer.acquiredBy(thread));
        assert (buffer.isRetired());
    }

    @Override
    public JfrBuffer flush(JfrBuffer oldBuffer, int used, int requested, Thread t) {
        assert (oldBuffer != null && oldBuffer.isLeased());
        if (requested == 0) {
            // indicates a lease is being returned
            release(oldBuffer);
            // JFR.TODO
            // setConstantPending()
        }
        JfrBuffer newBuffer = lease(used + requested, true, t);
        oldBuffer.transferTo(newBuffer, used);

        release(oldBuffer);
        return newBuffer;
    }

    private void release(JfrBuffer buffer) {
        buffer.clearLeased();
        if (buffer.isTransient()) {
            buffer.setRetired();
        } else {
            buffer.release();
        }
    }
    @Override
    public JfrBuffer lease(int size, boolean previousEpoch, Thread t) {
        return lease(size, 100, previousEpoch, t);
    }

    private JfrBuffer lease(int size, int retryCount, boolean previousEpoch, Thread t) {
        int max = mspace.minElementSize();
        JfrBuffer b;
        if (size <= max) {
            b = JfrMemorySpace.acquireLiveLeaseWithRetry(size, mspace, retryCount, t, previousEpoch);
            if (b != null) {
                return b;
            }
        }

        b = JfrMemorySpace.acquireTransientLeaseToLive(size, mspace, t, previousEpoch);

        return b;
    }

}

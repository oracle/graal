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

package com.oracle.svm.core.jdk.jfr.recorder.storage.operations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrCheckpointWriter;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkWriter;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrBuffer;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrMemorySpace;
import com.oracle.svm.core.jdk.jfr.utilities.JfrTypes;

public class JfrBufferOperations {

    public interface BufferOperation<T extends JfrBuffer> {
        default boolean process(T node) {
            return false;
        }

        default int elements() {
            return 0;
        }

        default boolean write(ByteBuffer data, int size) {
            return false;
        }

        default int size() {
            return 0;
        }

        default boolean discard(T node, int top, int unflushedSize) {
            return false;
        }
    }

    public static class Release<T extends JfrBuffer> implements BufferOperation<T> {
        private final JfrMemorySpace<?, ?> space;

        public Release(JfrMemorySpace<?, ?> space) {
            this.space = space;
        }

        @Override
        public boolean process(T node) {
            if (node.isTransient()) {
                // JFR.TODO
                // Support transient buffers by releasing them from this.space
                JfrLogger.logError("Releasing transient node");
            }

            node.reinitialize();
            if (node.identity() != -1) {
                assert (node.isEmpty());
                assert (!node.isRetired());
                node.release();
            }

            return true;
        }

        @Override
        public int elements() {
            return 0;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    public static class ReleaseExcision<T extends JfrBuffer> implements BufferOperation<T> {
        private final JfrMemorySpace<?, ?> space;

        public ReleaseExcision(JfrMemorySpace<?, ?> space, Collection<T> list) {
            this.space = space;
        }

        @Override
        public boolean process(T node) {
            assert (node != null);
            if (node.isTransient()) {
                // JFR.TODO excise transient nodes
            }

            node.reinitialize();
            if (node.identity() != -1) {
                assert (node.isEmpty());
                assert (!node.isRetired());
                node.release();
            }

            return true;
        }

        @Override
        public int elements() {
            return 0;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    public static class UnbufferedWriteToChunk<T extends JfrBuffer> implements BufferOperation<T> {
        private final JfrChunkWriter writer;
        private int elements = 0;
        private int size = 0;

        public UnbufferedWriteToChunk(JfrChunkWriter writer) {
            this.writer = writer;
        }

        public boolean write(ByteBuffer data, int size) {
            try {
                writer.writeUnbuffered(data);
                this.elements++;
                this.size += size;
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return false;
        }

        public int elements() {
            return this.elements;
        }

        public int size() {
            return this.size;
        }
    }

    public static class MutexedWrite<T extends JfrBuffer> implements BufferOperation<T> {
        private final BufferOperation<T> write;

        public MutexedWrite(BufferOperation<T> writeOp) {
            this.write = writeOp;
        }

        @Override
        public boolean process(T node) {
            assert (node != null);
            int flushed = node.getFlushedPosition();
            int committed = node.getCommittedPosition();

            int unflushedSize = committed - flushed;
            assert (unflushedSize >= 0);

            if (unflushedSize == 0) {
                return true;
            }

            ByteBuffer toWrite = node.getBuffer().duplicate();
            toWrite.position(flushed);
            toWrite.limit(committed);

            boolean result = write.write(toWrite, unflushedSize);
            if (result) {
                node.setFlushedPosition(committed);
            }

            return result;
        }

        @Override
        public int elements() {
            return write.elements();
        }

        @Override
        public int size() {
            return write.size();
        }
    }

    public static class CompositeAnd<T extends JfrBuffer> implements BufferOperation<T> {

        private final List<BufferOperation<T>> operations;

        public CompositeAnd(BufferOperation<T>... ops) {
            operations = Arrays.asList(ops);
        }

        @Override
        public boolean process(T node) {
            for (BufferOperation<T> op : operations) {
                if (!op.process(node)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int elements() {
            int elements = 0;
            for (BufferOperation<T> op : operations) {
                elements += op.elements();
            }

            return elements;
        }

        @Override
        public int size() {
            int size = 0;
            for (BufferOperation<T> op : operations) {
                size += op.size();
            }

            return size;
        }
    }

    public static class Excluded<T extends JfrBuffer> implements BufferOperation<T> {
        private final boolean negation;

        public Excluded(boolean negation) {
            this.negation = negation;
        }

        @Override
        public boolean process(T node) {
            return negation != node.isExcluded();
        }

        @Override
        public int elements() {
            return 0;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    public static class SafepointWrite<T extends JfrBuffer> implements BufferOperation<T> {
        private final BufferOperation<T> operation;

        public SafepointWrite(BufferOperation<T> operation) {
            this.operation = operation;
        }

        @Override
        public boolean process(T node) {
            if (node.getFlushLock().tryLock()) {
                try {
                    int flushed = node.getFlushedPosition();
                    int unflushedSize = node.getCommittedPosition() - flushed;
                    if (unflushedSize == 0) {
                        node.setFlushedPosition(flushed);
                        return true;
                    }

                    ByteBuffer toWrite = node.getBuffer().duplicate();
                    toWrite.position(flushed);
                    toWrite.limit(flushed + unflushedSize);
                    boolean result = operation.write(toWrite, unflushedSize);

                    node.setFlushedPosition(flushed + unflushedSize);
                    return result;
                } finally {
                    node.getFlushLock().unlock();
                }
            } else {
                // Skip the buffer as it is in the process of moving to the global ring buffer
                // It will be written out later
                return true;
            }
        }

        @Override
        public int elements() {
            return operation.elements();
        }

        @Override
        public int size() {
            return operation.size();
        }
    }

    public static class ConcurrentWrite<T extends JfrBuffer> implements BufferOperation<T> {
        private final BufferOperation<T> operation;

        public ConcurrentWrite(BufferOperation<T> operation) {
            this.operation = operation;
        }

        @Override
        public boolean process(T node) {
            if (node.isRetired()) {
                return write(node);
            } else {
                node.getFlushLock().lock();
                try {
                    return write(node);
                } finally {
                    node.getFlushLock().unlock();
                }
            }

        }

        private boolean write(T node) {
                int flushed = node.getFlushedPosition();
                int unflushedSize = node.getCommittedPosition() - flushed;
                if (unflushedSize == 0) {
                    node.setFlushedPosition(flushed);
                    return true;
                }

                ByteBuffer toWrite = node.getBuffer().duplicate();
                toWrite.position(flushed);
                toWrite.limit(flushed + unflushedSize);
                boolean result = operation.write(toWrite, unflushedSize);

                node.setFlushedPosition(flushed + unflushedSize);
                return result;
        }

        @Override
        public int elements() {
            return operation.elements();
        }

        @Override
        public int size() {
            return operation.size();
        }
    }

    public static class PredicatedSafepointWrite<T extends JfrBuffer> implements BufferOperation<T> {
        private final BufferOperation<T> operation;
        private final BufferOperation<T> predicate;

        public PredicatedSafepointWrite(BufferOperation<T> operation, BufferOperation<T> predicate) {
            this.operation = new SafepointWrite<>(operation);
            this.predicate = predicate;
        }

        @Override
        public boolean process(T node) {
            return predicate.process(node) && operation.process(node);
        }
    }

    public static class PredicatedConcurrentWrite<T extends JfrBuffer> implements BufferOperation<T> {
        private final BufferOperation<T> operation;
        private final BufferOperation<T> predicate;

        public PredicatedConcurrentWrite(BufferOperation<T> operation, BufferOperation<T> predicate) {
            this.operation = new ConcurrentWrite<>(operation);
            this.predicate = predicate;
        }

        @Override
        public boolean process(T node) {
            return predicate.process(node) && operation.process(node);
        }
    }

    public static class ScavengingRelease<T extends JfrBuffer> implements BufferOperation<T> {
        private final JfrMemorySpace<?, ?> mspace;
        private final Collection<T> list;

        private int count = 0;
        private int amount = 0;

        public ScavengingRelease(JfrMemorySpace<?, ?> mspace, Collection<T> list) {
            this.mspace = mspace;
            this.list = list;
        }

        @Override
        public boolean process(T node) {
            assert (node != null);
            assert (!node.isTransient());

            if (node.isRetired()) {
                return exciseWithRelease(node);
            }

            return true;
        }

        private boolean exciseWithRelease(T node) {
            assert (node != null);
            assert (node.isRetired());
            assert (node.identity() != -1);
            assert (node.isEmpty());
            assert (!node.isLeased());
            assert (!node.isExcluded());

            count++;
            amount += node.getSize();
            node.clearRetired();
            node.release();
            mspace.releaseLive(node);

            return true;
        }

        @Override
        public int elements() {
            return count;
        }

        @Override
        public int size() {
            return amount;
        }
    }

    public static class DefaultDiscarder<T extends JfrBuffer> implements BufferOperation<T> {
        private int elements = 0;
        private int size = 0;

        @Override
        public int elements() {
            return this.elements;
        }

        @Override
        public boolean discard(T node, int top, int unflushedSize) {
            elements++;
            size += unflushedSize;
            return true;
        }

        @Override
        public int size() {
            return this.size;
        }
    }

    public static class ConcurrentDiscard<T extends JfrBuffer> extends Discard<T> {
        public ConcurrentDiscard(BufferOperation<T> op) {
            super(op);
        }

        @Override
        public boolean process(T node) {
            boolean result;
            node.getFlushLock().lock();
            try {
                result = super.process(node);
            } finally {
                node.getFlushLock().unlock();
            }

            return result;
        }
    }

    public static class Discard<T extends JfrBuffer> implements BufferOperation<T> {

        private final BufferOperation<T> op;

        public Discard(BufferOperation<T> op) {
            this.op = op;
        }

        @Override
        public boolean process(T node) {

            int top = node.getFlushedPosition();
            int unflushedSize = node.getCommittedPosition() - top;
            if (unflushedSize == 0) {
                return true;
            }

            boolean result = op.discard(node, top, unflushedSize);
                node.setFlushedPosition(top + unflushedSize);

            return result;
        }

        @Override
        public int elements() {
            return op.elements();
        }

        @Override
        public int size() {
            return op.size();
        }
    }

    public static class CheckpointWrite<T extends JfrBuffer> implements BufferOperation<T> {
        private final JfrChunkWriter writer;
        int processed = 0;

        public CheckpointWrite(JfrChunkWriter writer) {
            this.writer = writer;
        }

        @Override
        public boolean write(ByteBuffer data, int size) {
            processed += writeCheckpoints(data, size);
            return true;
        }

        private int writeCheckpoints(ByteBuffer data, int size) {
            int processed = 0;
            assert (writer.isValid());
            assert (data != null);
            assert (data.limit() == data.position() + size);

            while (data.hasRemaining()) {
                processed += writeCheckpointEvent(data);
            }

            return processed;
        }

        private int writeCheckpointEvent(ByteBuffer data) {
            assert (data != null);
            try {
                long eventBegin = writer.reserve(Integer.BYTES);
                long lastOffset = writer.getLastCheckpointOffset();
                long delta = lastOffset == 0 ? 0 : lastOffset - eventBegin;
                int checkpointSize = (int) data.getLong();
                assert (checkpointSize > JfrCheckpointWriter.jfrCheckpointEntrySize);

                writeCheckpointHeader(data, delta);
                writeCheckpointData(data, checkpointSize);

                long eventSize = writer.getCurrentOffset() - eventBegin;
                writer.padded().writeInt((int) eventSize, eventBegin);
                writer.setLastCheckpointOffset(eventBegin);

                return checkpointSize;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return 0;
        }

        private void writeCheckpointData(ByteBuffer data, int checkpointSize) throws IOException {
            assert ((data.position() + checkpointSize - JfrCheckpointWriter.jfrCheckpointEntrySize) == data.limit());

            int startPos = data.position();
            int written = writer.writeUnbuffered(data);
            int endPos = data.position();
            assert (endPos - startPos == written);
        }

        private void writeCheckpointHeader(ByteBuffer data, long delta) throws IOException {
            writer.encoded().writeLong(JfrTypes.ReservedEvent.EVENT_CHECKPOINT.id);
            writer.encoded().writeLong(data.getLong()); // startTime
            writer.encoded().writeLong(data.getLong()); // duration
            writer.encoded().writeLong(delta);
            writer.encoded().writeInt(data.getInt()); // type
            writer.encoded().writeInt(data.getInt()); // count
        }

        @Override
        public int elements() {
            return processed;
        }

    }
}

/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.logging;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.VMOperation;

/// Transfers prepared log message parts to a dedicated output thread.
final class LogAsyncWriter {
    /// Bounds native memory retained for queued messages.
    private static final int QUEUE_CAPACITY = 256;

    /// Reusable queue entries contain all state needed by the output thread.
    private final Record[] records;

    /// Serializes producers and preserves the order of multi-part messages.
    private static final VMMutex PRODUCER_LOCK = new VMMutex("LogAsyncWriter.producer");

    /// Protects queue state and coordinates producers with the consumer.
    private static final VMMutex CONSUMER_LOCK = new VMMutex("LogAsyncWriter.consumer");

    /// Wait condition associated with the consumer lock.
    private static final VMCondition CONSUMER_CONDITION = new VMCondition(CONSUMER_LOCK, "queue");

    /// Selects whether producers wait for queue space instead of dropping messages.
    private final boolean stall;

    /// Dedicated thread that performs all output I/O.
    private final Thread worker;

    /// Queue index consumed next.
    private int head;

    /// Queue index populated next.
    private int tail;

    /// Number of occupied queue entries.
    private int size;

    /// Number of records currently being written by the consumer.
    private int inFlight;

    /// Requests the consumer to drain the queue and exit.
    private boolean stopping;

    /// Creates the reusable queue and starts its output thread.
    LogAsyncWriter(boolean stall) {
        this.stall = stall;
        records = new Record[QUEUE_CAPACITY];
        LogTagSet placeholder = LogTagSet.VALUES[0];
        for (int index = 0; index < records.length; index++) {
            records[index] = new Record(placeholder);
            records[index].message.reserve(1024);
        }
        worker = new Thread(this::run, "SVM AsyncLogWriter");
        worker.setDaemon(true);
        worker.start();
    }

    /// Enqueues every selected message part, copying its native bytes before returning. Returns
    /// `false` when the caller must use synchronous output instead.
    boolean enqueue(LogOutput output, LogDecorations decorations, LogMessage message, LogLevel outputLevel) {
        if (Thread.currentThread() == worker || VMOperation.isInProgress()) {
            /* A VM operation must not wait for a lock owned by a thread stopped at a safepoint. */
            return false;
        }

        PRODUCER_LOCK.lock();
        try {
            int lineCount = message.lineCount();
            for (int index = 0; index < lineCount; index++) {
                if (!outputLevel.enables(message.lineLevel(index))) {
                    continue;
                }
                Record record;
                CONSUMER_LOCK.lock();
                try {
                    while (size + inFlight == records.length && stall && !stopping) {
                        /* Keep the producer lock while waiting so later producers cannot overtake this message. */
                        CONSUMER_CONDITION.block();
                    }
                    if (stopping) {
                        return false;
                    }
                    if (size + inFlight == records.length) {
                        return true;
                    }

                    record = records[tail];
                    tail = (tail + 1) % records.length;
                    record.output = output;
                    record.level = message.lineLevel(index);
                    record.decorations.copyFrom(decorations);
                    message.writeLineTo(index, record.message);
                    size++;
                    CONSUMER_CONDITION.broadcast();
                } finally {
                    CONSUMER_LOCK.unlock();
                }
            }
            return true;
        } finally {
            PRODUCER_LOCK.unlock();
        }
    }

    /// Stops the writer after draining all queued records.
    void shutdown() {
        CONSUMER_LOCK.lock();
        try {
            stopping = true;
            CONSUMER_CONDITION.broadcast();
        } finally {
            CONSUMER_LOCK.unlock();
        }

        /* Wait until a producer that was already enqueuing observes the shutdown state. */
        PRODUCER_LOCK.lock();
        try {
            // The lock acquisition is the synchronization point; no work is needed here.
        } finally {
            PRODUCER_LOCK.unlock();
        }
        if (Thread.currentThread() != worker) {
            boolean interrupted = false;
            try {
                while (worker.isAlive()) {
                    try {
                        worker.join();
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /// Releases the native buffers held by queue records after the worker has stopped.
    void clear() {
        for (Record record : records) {
            record.message.clear();
        }
    }

    /// Runs the single consumer that performs formatting and native output writes.
    private void run() {
        /* LogOutput formats records into buffers owned by the current platform thread. */
        LogThreadLocal.get();
        for (;;) {
            Record record;
            CONSUMER_LOCK.lock();
            try {
                while (size == 0 && !stopping) {
                    CONSUMER_CONDITION.block();
                }
                if (size == 0) {
                    CONSUMER_CONDITION.broadcast();
                    return;
                }
                record = records[head];
                head = (head + 1) % records.length;
                size--;
                inFlight++;
            } finally {
                CONSUMER_LOCK.unlock();
            }

            try {
                record.output.write(record.decorations, record.message, record.level);
            } finally {
                record.message.reset();
                record.output = null;
                record.level = null;

                CONSUMER_LOCK.lock();
                try {
                    inFlight--;
                    CONSUMER_CONDITION.broadcast();
                } finally {
                    CONSUMER_LOCK.unlock();
                }
            }
        }
    }

    /// Holds one copied message part and its event metadata.
    private static final class Record {
        /// Destination selected by the producer.
        private LogOutput output;

        /// Level of the copied message line.
        private LogLevel level;

        /// Event metadata copied before the record is published.
        private final LogDecorations decorations;

        /// Native bytes copied from the producer's message buffer.
        private final NativeMemoryLog message = new NativeMemoryLog();

        /// Creates an empty reusable record.
        private Record(LogTagSet placeholder) {
            decorations = new LogDecorations(placeholder);
        }
    }
}

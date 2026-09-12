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

import static com.oracle.svm.guest.staging.option.RuntimeOptionKey.RuntimeOptionKeyFlag.Immutable;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

/// Transfers prepared log message parts to a dedicated output thread.
///
/// Producers are serialized by [#PRODUCER_LOCK], while [#CONSUMER_LOCK] protects the byte
/// [queue][QueueState] and coordinates producers with the single consumer. Ordinary producers may wait for queue
/// capacity in stall mode. A thread executing a VM operation must not perform such a wait because
/// the consumer, or a producer holding a required lock, could have been stopped for the VM
/// operation's safepoint.
///
/// VM operation [#enqueue] calls avoid that dependency as follows:
///
/// - [#PRODUCER_LOCK] is acquired with [VMMutex#tryLock()]. Failure selects synchronous output
///   immediately instead of waiting for a producer that might be stopped at the safepoint.
/// - After acquiring [#PRODUCER_LOCK], [#enqueue] checks under [#CONSUMER_LOCK] that the queue has
///   room for every selected line, including bytes retained by an in-flight record. If the complete
///   message does not fit, synchronous output is selected before any line is published.
/// - Once admitted, no other producer can consume that capacity, and the consumer can only release
///   capacity. The VM operation therefore cannot enter the queue-full wait.
/// - With [#PRODUCER_LOCK] held, only the consumer can contend for [#CONSUMER_LOCK]. The consumer
///   holds that lock only in uninterruptible, no-transition queue bookkeeping sections, so it
///   cannot be stopped at a safepoint while owning the lock.
///
/// Together these rules prevent asynchronous logging from creating a cycle in which a VM
/// operation waits for a thread stopped by its own safepoint. They do not make output I/O
/// nonblocking. Synchronous fallback can still delay a VM operation in destination I/O, and I/O can
/// delay the asynchronous consumer or a flush. Such delays depend on the output destination making
/// progress rather than on resuming a thread stopped at the safepoint, so they are separate from
/// the safepoint deadlock prevented here. The consumer remains alive across ordinary logging
/// reconfiguration and is stopped only by explicit shutdown during VM teardown or failed startup.
/// Teardown must detach the consumer before an embedded VM's isolate can be destroyed.
final class LogAsyncWriter {
    /// Smallest supported asynchronous message chunk, matching HotSpot's product minimum.
    private static final long MINIMUM_BUFFER_SIZE = 100L * 1024;

    /// Largest supported asynchronous message chunk, matching HotSpot's product maximum.
    private static final long MAXIMUM_BUFFER_SIZE = 50L * 1024 * 1024;

    /// Formats the tag prefix into producer-owned thread-local storage before queue reservation.
    private static final NativeMemoryLog PREFIX_BUFFER = new NativeMemoryLog(NativeMemoryLog.BufferKind.DECORATOR);

    /// Serializes producers and preserves the order of multi-part messages.
    private static final VMMutex PRODUCER_LOCK = new VMMutex("LogAsyncWriter.producer");

    /// Protects queue state and coordinates producers with the consumer.
    private static final VMMutex CONSUMER_LOCK = new VMMutex("LogAsyncWriter.consumer");

    /// Wait condition associated with the consumer lock.
    private static final VMCondition CONSUMER_CONDITION = new VMCondition(CONSUMER_LOCK, "queue");

    /// Native queue state remains accessible while the consumer is outside Java state.
    private static final CIsolateData<QueueState> QUEUE_STATE = CIsolateDataFactory.createStruct("logAsyncWriterQueue", QueueState.class);

    /// Runtime controls for the asynchronous writer's native storage.
    public static final class Options {
        /// Bounds the single native chunk that holds queued asynchronous messages.
        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/runtime/globals.hpp#L1879-L1882") @Option(help = "Memory budget in bytes for asynchronous logging messages.", type = OptionType.Expert) //
        public static final RuntimeOptionKey<Long> AsyncLogBufferSize = new RuntimeOptionKey<>(2L * 1024 * 1024, Immutable) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
                validateBufferSize(newValue);
                super.onValueUpdate(values, oldValue, newValue);
            }
        };
    }

    /// Destinations are kept in Java state and referenced from raw records by a slot number that
    /// remains stable until every referring record has drained.
    private volatile LogOutput[] outputSlots = new LogOutput[4];

    /// Number of populated entries in [#outputSlots].
    private int outputSlotCount;

    /// Reusable event metadata used only by the consumer thread.
    private final LogDecorations consumerDecorations = new LogDecorations(LogTagSet.VALUES[0]);

    /// Dedicated thread that performs queued record output I/O until shutdown.
    private final Thread worker;

    /// Becomes true when the worker reaches its Java entry point after thread-start listeners run.
    private volatile boolean workerReady;

    /// Selects whether producers wait for queue space instead of dropping messages.
    private boolean stall;

    /// Prevents stale producers from publishing records while outputs are being disabled.
    private boolean active;

    /// Allocates the complete queue budget and creates its long-lived output thread.
    LogAsyncWriter() {
        long requestedSize = Options.AsyncLogBufferSize.getValue();
        validateBufferSize(requestedSize);
        int bufferSize = (int) requestedSize;
        Pointer buffer = NullableNativeMemory.malloc(bufferSize, NmtCategory.Logging);
        VMError.guarantee(buffer.isNonNull(), "Could not allocate the asynchronous log buffer.");

        QueueState state = QUEUE_STATE.get();
        state.setBuffer(buffer);
        state.setCapacity(bufferSize);
        state.setHead(0);
        state.setTail(0);
        state.setUsedBytes(0);
        state.setWrapOffset(-1);
        state.setQueuedRecords(0);
        state.setInFlight(false);
        state.setShutdownRequested(false);

        worker = new Thread(this::run, "SVM AsyncLogWriter");
        worker.setDaemon(true);
    }

    /// Starts the consumer and waits until its thread-start listeners have completed.
    void start() {
        worker.start();
        while (!workerReady && worker.isAlive()) {
            Thread.onSpinWait();
        }
        VMError.guarantee(workerReady, "The asynchronous log consumer failed during thread startup.");
    }

    /// Registers `output` before a route that refers to it becomes visible to producers.
    void registerOutput(LogOutput output) {
        PRODUCER_LOCK.lock();
        try {
            for (int index = 0; index < outputSlotCount; index++) {
                if (outputSlots[index] == output) {
                    return;
                }
            }
            if (outputSlotCount == outputSlots.length) {
                LogOutput[] expanded = new LogOutput[outputSlots.length * 2];
                System.arraycopy(outputSlots, 0, expanded, 0, outputSlots.length);
                outputSlots = expanded;
            }
            outputSlots[outputSlotCount++] = output;
        } finally {
            PRODUCER_LOCK.unlock();
        }
    }

    /// Activates asynchronous publication with the `shouldStall` queue-full policy.
    void activate(boolean shouldStall) {
        PRODUCER_LOCK.lock();
        try {
            QueueState state = QUEUE_STATE.get();
            CONSUMER_LOCK.lock();
            try {
                assert state.getQueuedRecords() == 0 && !state.getInFlight();
                assert !state.getShutdownRequested() && state.getBuffer().isNonNull();
                stall = shouldStall;
                active = true;
            } finally {
                CONSUMER_LOCK.unlock();
            }
        } finally {
            PRODUCER_LOCK.unlock();
        }
    }

    /// Enqueues every selected message part, copying its native bytes before returning. Returns
    /// `false` when the caller must use synchronous output instead.
    boolean enqueue(LogOutput output, LogDecorations decorations, LogMessage message, LogLevel outputLevel) {
        if (Thread.currentThread() == worker) {
            return false;
        }

        boolean vmOperationInProgress = VMOperation.isInProgress();
        if (vmOperationInProgress) {
            /*
             * VMMutex.hasOwner() is not sufficient here. A producer can be stopped while returning
             * from the native lock call, after acquiring the platform mutex but before recording
             * itself as the VMMutex owner. This is a classic time-of-check to time-of-use (TOCTOU)
             * race condition: between checking if a mutex is locked and acting on that answer,
             * another thread can acquire or release it.
             */
            if (!PRODUCER_LOCK.tryLock()) {
                return false;
            }
        } else {
            PRODUCER_LOCK.lock();
        }
        try {
            /* A producer that retained the writer before disableLogging must not use old outputs. */
            if (!active) {
                return false;
            }

            int outputSlot = findOutputSlot(output);
            VMError.guarantee(outputSlot >= 0, "Asynchronous log output was not registered.");
            PREFIX_BUFFER.reset();
            decorations.getTagSet().writePrefix(PREFIX_BUFFER);
            int prefixLength = PREFIX_BUFFER.getPosition();

            QueueState state = QUEUE_STATE.get();
            int lineCount = message.lineCount();
            if (vmOperationInProgress) {
                /* A VM operation must admit the complete message without waiting. */
                CONSUMER_LOCK.lock();
                try {
                    if (!canReserveMessage(state, message, lineCount, outputLevel, prefixLength)) {
                        return false;
                    }
                } finally {
                    CONSUMER_LOCK.unlock();
                }
            } else if (!canRecordsFitIndividually(state.getCapacity(), message, lineCount, outputLevel, prefixLength)) {
                /* Avoid splitting one logical message between synchronous and async output. */
                return false;
            } else if (!stall) {
                CONSUMER_LOCK.lock();
                try {
                    if (!active) {
                        return false;
                    }
                    if (!canReserveMessage(state, message, lineCount, outputLevel, prefixLength)) {
                        /* Drop one complete logical event rather than publishing selected fragments. */
                        output.droppedAsyncMessages.incrementAndGet();
                        return true;
                    }
                } finally {
                    CONSUMER_LOCK.unlock();
                }
            }

            for (int index = 0; index < lineCount; index++) {
                LogLevel level = message.lineLevel(index);
                if (!outputLevel.enables(level)) {
                    continue;
                }
                int lineLength = message.lineLength(index);
                int allocationSize = recordSize(prefixLength, lineLength);
                CONSUMER_LOCK.lock();
                try {
                    Record record = reserveRecord(state, allocationSize);
                    while (record.isNull() && stall && active) {
                        VMError.guarantee(!vmOperationInProgress, "Logging in a VM operation checks first if the message fits in the queue");
                        /* Keep the producer lock while waiting, so later producers cannot overtake this message. */
                        CONSUMER_CONDITION.block();
                        record = reserveRecord(state, allocationSize);
                    }
                    if (!active) {
                        return false;
                    }
                    if (record.isNull()) {
                        /* Drop mode reserved the complete message while holding the producer lock. */
                        throw VMError.shouldNotReachHere("Reserved asynchronous log capacity became unavailable.");
                    }

                    fillRecord(record, allocationSize, outputSlot, level, decorations, prefixLength, message, index, lineLength);
                    state.setQueuedRecords(state.getQueuedRecords() + 1);
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

    /// Waits until every record published before this call has been written while preventing a
    /// producer from overtaking the drain.
    static void flush() {
        PRODUCER_LOCK.lock();
        try {
            QueueState state = QUEUE_STATE.get();
            CONSUMER_LOCK.lock();
            try {
                while (state.getQueuedRecords() != 0 || state.getInFlight()) {
                    CONSUMER_CONDITION.block();
                }
            } finally {
                CONSUMER_LOCK.unlock();
            }
            LogConfiguration.asyncWriterInstance().flushDroppedMessages();
        } finally {
            PRODUCER_LOCK.unlock();
        }
    }

    /// Prevents further publication, drains old records, and releases output references.
    void deactivateAndFlush() {
        PRODUCER_LOCK.lock();
        try {
            QueueState state = QUEUE_STATE.get();
            CONSUMER_LOCK.lock();
            try {
                active = false;
                CONSUMER_CONDITION.broadcast();
                while (state.getQueuedRecords() != 0 || state.getInFlight()) {
                    CONSUMER_CONDITION.block();
                }
            } finally {
                CONSUMER_LOCK.unlock();
            }
            flushDroppedMessages();
            clearOutputSlots();
        } finally {
            PRODUCER_LOCK.unlock();
        }
    }

    /// Prevents further publication, drains queued records, terminates the consumer, and releases
    /// the queue chunk before isolate destruction.
    void shutdown() {
        PRODUCER_LOCK.lock();
        try {
            QueueState state = QUEUE_STATE.get();
            CONSUMER_LOCK.lock();
            try {
                active = false;
                CONSUMER_CONDITION.broadcast();
                while (state.getQueuedRecords() != 0 || state.getInFlight()) {
                    CONSUMER_CONDITION.block();
                }
                state.setShutdownRequested(true);
                CONSUMER_CONDITION.broadcast();
            } finally {
                CONSUMER_LOCK.unlock();
            }
            flushDroppedMessages();
            clearOutputSlots();
        } finally {
            PRODUCER_LOCK.unlock();
        }

        boolean interrupted = false;
        while (worker.isAlive()) {
            try {
                worker.join();
            } catch (InterruptedException exception) {
                /* Teardown must still detach the consumer; restore interruption after it exits. */
                interrupted = true;
            }
        }

        QueueState state = QUEUE_STATE.get();
        Pointer buffer = state.getBuffer();
        if (buffer.isNonNull()) {
            NullableNativeMemory.free(buffer);
            state.setBuffer(Word.nullPointer());
            state.setCapacity(0);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /// Gets the configured byte capacity for white-box queue tests.
    static int bufferCapacity() {
        return QUEUE_STATE.get().getCapacity();
    }

    /// Gets whether the byte budget is restricted to startup configuration.
    static boolean bufferSizeIsImmutable() {
        return Options.AsyncLogBufferSize.isImmutable();
    }

    /// Enters native state and runs the consumer loop until shutdown requests termination.
    @Uninterruptible(reason = "The asynchronous log consumer must not prevent safepoints.", calleeMustBe = false)
    @NeverInline("The native transition must surround exactly one call without an exception edge.")
    private void run() {
        workerReady = true;
        for (;;) {
            CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
            Record record = takeRecordInNative();
            CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);

            if (record.isNull()) {
                return;
            }
            writeRecord(record);
            finishRecord();
        }
    }

    /// Claims one record while the consumer is in native state.
    @Uninterruptible(reason = "The consumer is in native state and must use no-transition synchronization.")
    @NeverInline("Must remain the single invoke between the C function prologue and epilogue.")
    private static Record takeRecordInNative() {
        QueueState state = QUEUE_STATE.get();
        CONSUMER_LOCK.lockNoTransition();
        try {
            while (state.getQueuedRecords() == 0 && !state.getShutdownRequested()) {
                CONSUMER_CONDITION.blockNoTransition();
            }
            if (state.getShutdownRequested()) {
                return Word.nullPointer();
            }
            assert !state.getInFlight();
            state.setQueuedRecords(state.getQueuedRecords() - 1);
            state.setInFlight(true);
            return recordAt(state, state.getHead());
        } finally {
            CONSUMER_LOCK.unlock();
        }
    }

    /// Formats and writes a claimed raw record while the consumer is back in Java state.
    private static void writeRecord(Record record) {
        LogAsyncWriter writer = LogConfiguration.asyncWriterInstance();
        LogOutput output = writer.outputForSlot(record.getOutputSlot());
        try {
            LogDecorations decorations = writer.consumerDecorations;
            decorations.restore(LogTagSet.VALUES[record.getTagSetOrdinal()], record.getSystemMillis(), record.getSystemNanos(), record.getUptimeNanos(), record.getThreadId());
            output.write(decorations, recordData(record), record.getMessageLength(), record.getPrefixLength(), LogLevel.VALUES[record.getLevelOrdinal()]);
            writeDroppedMessages(output);
        } catch (Throwable throwable) {
            /* A failed destination must not terminate the VM-lifetime consumer thread. */
            Log.log().string("Unexpected exception while writing an asynchronous log record: ").exception(throwable);
        }
    }

    /// Releases the claimed record and any end padding after its output completes.
    @Uninterruptible(reason = "Queue capacity must be released without a safepoint while holding the consumer lock.")
    private static void finishRecord() {
        QueueState state = QUEUE_STATE.get();
        CONSUMER_LOCK.lockNoTransition();
        try {
            Record record = recordAt(state, state.getHead());
            int newHead = state.getHead() + record.getAllocationSize();
            state.setUsedBytes(state.getUsedBytes() - record.getAllocationSize());
            if (state.getWrapOffset() >= 0 && newHead == state.getWrapOffset()) {
                state.setUsedBytes(state.getUsedBytes() - (state.getCapacity() - state.getWrapOffset()));
                state.setWrapOffset(-1);
                newHead = 0;
            }
            state.setHead(newHead);
            state.setInFlight(false);
            if (state.getUsedBytes() == 0) {
                /* Normalization makes an empty queue maximally useful for the next record. */
                state.setHead(0);
                state.setTail(0);
                state.setWrapOffset(-1);
            }
            CONSUMER_CONDITION.broadcast();
        } finally {
            CONSUMER_LOCK.unlock();
        }
    }

    /// Reserves aligned contiguous bytes without publishing the record to the consumer.
    private static Record reserveRecord(QueueState state, int allocationSize) {
        if (allocationSize > state.getCapacity() - state.getUsedBytes()) {
            return Word.nullPointer();
        }
        int head = state.getHead();
        int tail = state.getTail();
        int offset;
        if (tail < head) {
            if (allocationSize > head - tail) {
                return Word.nullPointer();
            }
            offset = tail;
        } else if (allocationSize <= state.getCapacity() - tail) {
            offset = tail;
        } else {
            if (state.getWrapOffset() >= 0 || allocationSize > head) {
                return Word.nullPointer();
            }
            state.setWrapOffset(tail);
            state.setUsedBytes(state.getUsedBytes() + state.getCapacity() - tail);
            offset = 0;
        }
        state.setTail(offset + allocationSize);
        state.setUsedBytes(state.getUsedBytes() + allocationSize);
        return recordAt(state, offset);
    }

    /// Checks whether every selected record fits in an otherwise empty queue. Unlike
    /// `canReserveMessage`, this ignores the current occupancy and ring layout; it only rejects a
    /// message when one of its records is larger than the complete queue capacity.
    private static boolean canRecordsFitIndividually(int capacity, LogMessage message, int lineCount, LogLevel outputLevel, int prefixLength) {
        for (int index = 0; index < lineCount; index++) {
            if (outputLevel.enables(message.lineLevel(index)) && recordSize(prefixLength, message.lineLength(index)) > capacity) {
                return false;
            }
        }
        return true;
    }

    /// Simulates reservations for the complete selected message without changing queue state.
    private static boolean canReserveMessage(QueueState state, LogMessage message, int lineCount, LogLevel outputLevel, int prefixLength) {
        int capacity = state.getCapacity();
        int head = state.getHead();
        int tail = state.getTail();
        int usedBytes = state.getUsedBytes();
        int wrapOffset = state.getWrapOffset();
        for (int index = 0; index < lineCount; index++) {
            if (!outputLevel.enables(message.lineLevel(index))) {
                continue;
            }
            int allocationSize = recordSize(prefixLength, message.lineLength(index));
            if (allocationSize > capacity - usedBytes) {
                return false;
            }
            int offset;
            if (tail < head) {
                if (allocationSize > head - tail) {
                    return false;
                }
                offset = tail;
            } else if (allocationSize <= capacity - tail) {
                offset = tail;
            } else {
                if (wrapOffset >= 0 || allocationSize > head) {
                    return false;
                }
                wrapOffset = tail;
                usedBytes += capacity - tail;
                offset = 0;
            }
            tail = offset + allocationSize;
            usedBytes += allocationSize;
        }
        return true;
    }

    /// Copies record metadata and inline payload into already reserved queue storage.
    private static void fillRecord(Record record, int allocationSize, int outputSlot, LogLevel level, LogDecorations decorations, int prefixLength, LogMessage message, int lineIndex,
                    int lineLength) {
        record.setAllocationSize(allocationSize);
        record.setMessageLength(prefixLength + lineLength);
        record.setPrefixLength(prefixLength);
        record.setOutputSlot(outputSlot);
        record.setLevelOrdinal(level.ordinal());
        record.setTagSetOrdinal(decorations.getTagSet().ordinal());
        record.setSystemMillis(decorations.systemMillis());
        record.setSystemNanos(decorations.systemNanos());
        record.setUptimeNanos(decorations.uptimeNanos());
        record.setThreadId(decorations.threadId());

        CCharPointer target = recordData(record);
        if (prefixLength != 0) {
            UnmanagedMemoryUtil.copy((Pointer) PREFIX_BUFFER.getBuffer(), (Pointer) target, Word.unsigned(prefixLength));
        }
        message.copyLineTo(lineIndex, target.addressOf(prefixLength), lineLength);
    }

    /// Gets the aligned allocation size for one header and payload.
    static int recordSize(int prefixLength, int lineLength) {
        long unaligned = recordHeaderSize().rawValue() + prefixLength + lineLength;
        long aligned = (unaligned + SubstrateTarget.getWordSize() - 1) & -SubstrateTarget.getWordSize();
        /* A saturated result still selects synchronous output without overflowing queue math. */
        return aligned > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) aligned;
    }

    /// Gets the address of the raw record at `offset` in the queue chunk.
    @Uninterruptible(reason = "Performs only native address arithmetic.")
    private static Record recordAt(QueueState state, int offset) {
        return (Record) state.getBuffer().add(offset);
    }

    /// Gets the inline payload immediately following `record`'s aligned header.
    private static CCharPointer recordData(Record record) {
        return (CCharPointer) ((Pointer) record).add(recordHeaderSize());
    }

    /// Gets an output slot without allocating in the producer path.
    private int findOutputSlot(LogOutput output) {
        LogOutput[] slots = outputSlots;
        for (int index = 0; index < outputSlotCount; index++) {
            if (slots[index] == output) {
                return index;
            }
        }
        return -1;
    }

    /// Resolves a raw record's output slot after returning to Java state.
    private LogOutput outputForSlot(int slot) {
        VMError.guarantee(slot >= 0 && slot < outputSlotCount, "Invalid asynchronous log output slot.");
        LogOutput result = outputSlots[slot];
        VMError.guarantee(result != null, "Asynchronous log output slot was cleared too early.");
        return result;
    }

    /// Reports counters that had no later successful record on the same output. The producer lock
    /// and an empty queue ensure that no producer or consumer can race with the reset.
    private void flushDroppedMessages() {
        for (int index = 0; index < outputSlotCount; index++) {
            LogOutput output = outputSlots[index];
            try {
                writeDroppedMessages(output);
            } catch (Throwable throwable) {
                /* A failed destination must not prevent other destinations from being flushed. */
                Log.log().string("Unexpected exception while reporting dropped asynchronous log records: ").exception(throwable);
            }
        }
    }

    /// Reports and resets the pending drop counter for `output`.
    private static void writeDroppedMessages(LogOutput output) {
        int droppedMessages = output.droppedAsyncMessages.getAndSet(0);
        if (droppedMessages != 0) {
            /* A configuration caller can have started before asynchronous logging was active. */
            LogThreadLocal.ensureInitialized();
            output.writeDroppedAsyncMessages(droppedMessages);
        }
    }

    /// Releases managed output references after all queue records have drained.
    private void clearOutputSlots() {
        for (int index = 0; index < outputSlotCount; index++) {
            outputSlots[index] = null;
        }
        outputSlotCount = 0;
    }

    /// Rejects byte budgets outside the range supported by HotSpot's product option.
    static void validateBufferSize(Long value) {
        if (value == null || value < MINIMUM_BUFFER_SIZE || value > MAXIMUM_BUFFER_SIZE) {
            throw new IllegalArgumentException("AsyncLogBufferSize must be between 100K and 50M.");
        }
    }

    @Fold
    static UnsignedWord recordHeaderSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(Record.class), Word.unsigned(SubstrateTarget.getWordSize()));
    }

    // @formatter:off
    /// A variable-sized queue entry whose raw header is followed immediately by inline prefix and
    /// message bytes. The complete allocation is contiguous and never wraps around the end of the
    /// [queue][QueueState] chunk.
    @RawStructure
    interface Record extends PointerBase {
        @RawField int  getAllocationSize();
        @RawField void setAllocationSize(int value);

        @RawField int  getMessageLength();
        @RawField void setMessageLength(int value);

        @RawField int  getPrefixLength();
        @RawField void setPrefixLength(int value);

        @RawField int  getOutputSlot();
        @RawField void setOutputSlot(int value);

        @RawField int  getLevelOrdinal();
        @RawField void setLevelOrdinal(int value);

        @RawField int  getTagSetOrdinal();
        @RawField void setTagSetOrdinal(int value);

        @RawField long getSystemMillis();
        @RawField void setSystemMillis(long value);

        @RawField long getSystemNanos();
        @RawField void setSystemNanos(long value);

        @RawField long getUptimeNanos();
        @RawField void setUptimeNanos(long value);

        @RawField long getThreadId();
        @RawField void setThreadId(long value);
    }

    /// Native ownership and byte-ring state accessed by both producers and the native consumer.
    /// The diagrams linearize the chunk; its left and right ends are adjacent in the ring. `head`
    /// identifies the next record for the consumer, while `tail` identifies the next producer
    /// insertion point.
    ///
    /// The logical queue can cross the chunk boundary, but an individual `Record` never does.
    /// Keeping each header and its inline bytes contiguous lets the consumer access the complete
    /// entry as one native memory range without temporary storage. When a record does not fit
    /// between `tail` and the end of the chunk, the producer stores the old `tail` in `wrapOffset`
    /// and resumes allocation at offset zero. The skipped suffix from `wrapOffset` to `capacity` is
    /// the wrap pad. It contains no record, but remains part of `usedBytes` so producers cannot
    /// overcommit the queue. After the consumer finishes the last record before `wrapOffset`, it
    /// reclaims the wrap pad, moves `head` to zero, and resets `wrapOffset` to `-1`.
    ///
    /// ```text
    /// tail > head (queued records are contiguous)
    ///
    /// 0              head                              tail capacity
    /// +----------------+===================================+--------+
    /// |      free      |          queued records           |  free  |
    /// +----------------+===================================+--------+
    ///                 ^ head                              ^ tail
    /// <--------------  capacity ------------------------------------>
    ///
    /// tail < head (queued records wrap around the end)
    ///
    /// 0              tail         head              wrapOffset capacity
    /// +===============+------------+===================+...........+
    /// | queued records|    free    |  queued records   | wrap pad  |
    /// +===============+------------+===================+...........+
    ///                 ^ tail       ^ head
    /// ```
    @RawStructure
    public interface QueueState extends PointerBase {
        @RawField Pointer getBuffer();
        @RawField void    setBuffer(Pointer value);

        @RawField int     getCapacity();
        @RawField void    setCapacity(int value);

        @RawField int     getHead();
        @RawField void    setHead(int value);

        @RawField int     getTail();
        @RawField void    setTail(int value);

        @RawField int     getUsedBytes();
        @RawField void    setUsedBytes(int value);

        @RawField int     getWrapOffset();
        @RawField void    setWrapOffset(int value);

        @RawField int     getQueuedRecords();
        @RawField void    setQueuedRecords(int value);

        @RawField boolean getInFlight();
        @RawField void    setInFlight(boolean value);

        @RawField boolean getShutdownRequested();
        @RawField void    setShutdownRequested(boolean value);
    }
    // @formatter:on
}

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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CIntPointer;

import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.NativeMemoryLog;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.util.VMError;

/// The LogMessage class represents a multi-line message
/// guaranteed to be sent and written to the log outputs
/// in a way that prevents interleaving by other log messages.
///
/// Each [LogTagSet] has its own `LogMessage` instance which is
/// obtained via [LogTagSet#message()]. The first thread to call
/// [#line] will obtain the lock on the message object, and
/// it is released by [#close].
///
/// In a context where heap allocation is unrestricted, a message should
/// be used in a try-with-resources statement:
///
/// ```
/// try (LogMessage msg = LogTagSet.class_load.message()) {
///     msg.info().string("info message");
///     msg.debug().string("debug message");
/// }
/// ```
///
/// Since javac generates a call to [Throwable#addSuppressed] for
/// try-with-resource statements, they cannot be used in an
/// allocation-restricted context. Instead, use a try-finally:
///
/// ```
/// LogMessage msg = LogTagSet.gc.message();
/// try {
///     msg.info().string("info message");
///     msg.debug().string("debug message");
/// } finally {
///     msg.close();
/// }
/// ```
///
/// When [#close] is called, the message is written to the outputs
/// configured for the associated log tag set and the message object
/// lock is released.
///
/// Note that each line can have a different log level.
/// Log outputs on DEBUG level will see both of the messages above,
/// and the DEBUG line will immediately follow the INFO line.
/// They will have identical decorations (apart from level).
/// Log outputs on the INFO level will see the INFO message,
/// but not the DEBUG message.
public final class LogMessage implements AutoCloseable {
    private static final int INITIAL_LINE_CAPACITY = 10;
    private static final int LINE_ENTRY_INTS = 2;

    /// Tag set that receives the completed message.
    private final LogTagSet tagSet;

    /// Buffer for building a single log line.
    private final NativeMemoryLog lineBuffer;

    /// Synchronizes use of a shared LogMessage object.
    private final VMMutex mutex;

    /// Native line records contain the level ordinal followed by the message start offset.
    private CIntPointer lines;

    /// Number of line records currently stored in `lines`.
    private int lineCount;

    /// Number of line records available in `lines`.
    private int lineCapacity;

    /// Most severe level among the message parts currently being accumulated.
    private LogLevel mostSevereLevel = LogLevel.OFF;

    /// Preallocated iterator used to inspect the message parts while the scope is open.
    private final LineIterator lineIterator = new LineIterator();

    /// Creates the preallocated scope for `tagSet` and its native `lineBuffer` buffer.
    @Platforms(Platform.HOSTED_ONLY.class)
    LogMessage(LogTagSet tagSet, NativeMemoryLog lineBuffer) {
        this.tagSet = tagSet;
        this.lineBuffer = lineBuffer;
        this.mutex = new VMMutex("LogMessage:" + tagSet);
    }

    /// Gets the most severe level recorded in the current message.
    LogLevel getMostSevereLevel() {
        return mostSevereLevel;
    }

    /// Commits the accumulated message and makes this scope available for reuse.
    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Unified logging must not allocate at run time.")
    public void close() {
        if (!mutex.isOwner()) {
            return;
        }
        VMError.guarantee(mutex.isOwner(), "Non-empty LogMessage can only be closed by the thread that owns it.");
        try {
            tagSet.write(this);
        } finally {
            lineBuffer.reset();
            lineCount = 0;
            mostSevereLevel = LogLevel.OFF;
            mutex.unlock();
        }
    }

    /// Determines if the current thread has started writing to this message.
    boolean inUseByCurrentThread() {
        return mutex.isOwner();
    }

    /// Gets the buffer for formatting the a message line.
    /// Each call to this method starts a new line.
    ///
    /// @param level the level at which the line will be logged
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Unified logging must not allocate at run time.")
    public NativeMemoryLog line(LogLevel level) {
        // Exclusive access to this message object is acquired
        // on first attempt to add a line to it.
        if (!mutex.isOwner()) {
            mutex.lock();
            VMError.guarantee(lineBuffer.getPosition() == 0, "LogMessage buffer should be empty upon acquiring lock");
        }
        if (level.ordinal() > mostSevereLevel.ordinal()) {
            mostSevereLevel = level;
        }
        ensureLineCapacity();
        int lineOffset = lineCount * LINE_ENTRY_INTS;
        int position = lineBuffer.getPosition();
        lines.write(lineOffset, level.ordinal());
        lines.write(lineOffset + 1, position);
        lineCount++;
        return lineBuffer;
    }

    /// Gets the buffer for formatting a message line at level [LogLevel#INFO].
    /// Each call to this method starts a new line.
    public NativeMemoryLog info() {
        return line(LogLevel.INFO);
    }

    /// Gets the buffer for formatting a message line at [LogLevel#DEBUG].
    /// Each call to this method starts a new line.
    public NativeMemoryLog debug() {
        return line(LogLevel.DEBUG);
    }

    /// Gets the buffer for formatting a message line at [LogLevel#TRACE].
    /// Each call to this method starts a new line.
    public NativeMemoryLog trace() {
        return line(LogLevel.TRACE);
    }

    /// Creates a view over message lines at or above `level` without allocating a new iterator.
    LineIterator iterator(LogLevel level) {
        verifyOpen();
        VMError.guarantee(level != null, "LogMessage requires a non-null iterator level.");
        lineIterator.initialize(level);
        return lineIterator;
    }

    /// Verifies that the message scope is open on the current thread.
    private void verifyOpen() {
        VMError.guarantee(mutex.isOwner(), "LogMessage can only be accessed within its open scope.");
    }

    /// Ensures that one more native line record can be stored.
    private void ensureLineCapacity() {
        if (lineCount < lineCapacity) {
            return;
        }
        long requestedCapacity = lineCapacity == 0 ? INITIAL_LINE_CAPACITY : (long) lineCapacity * 2;
        VMError.guarantee(requestedCapacity <= Integer.MAX_VALUE, "LogMessage line capacity is too large.");
        long requestedBytes = requestedCapacity * LINE_ENTRY_INTS * Integer.BYTES;
        VMError.guarantee(requestedBytes <= Integer.MAX_VALUE, "LogMessage line metadata is too large.");
        lines = lineCapacity == 0 ? //
                        NullableNativeMemory.malloc((int) requestedBytes, NmtCategory.Logging) : //
                        NullableNativeMemory.realloc(lines, (int) requestedBytes, NmtCategory.Logging);
        VMError.guarantee(lines.isNonNull(), "Could not grow LogMessage line metadata.");
        lineCapacity = (int) requestedCapacity;
    }

    /// Iterates over message lines whose levels are at or above the selection level.
    public final class LineIterator {
        /// First line index that has not been consumed by the iterator.
        private int currentLineIndex;

        /// Selection level used to skip more detailed message lines.
        private LogLevel selectionLevel;

        private LineIterator() {
        }

        /// Initializes this preallocated iterator for the current open message.
        private void initialize(LogLevel level) {
            selectionLevel = level;
            currentLineIndex = 0;
            skipMessagesWithFinerLevel();
        }

        /// Skips message lines finer than the configured selection level.
        private void skipMessagesWithFinerLevel() {
            while (currentLineIndex < lineCount && lineLevel(currentLineIndex).ordinal() < selectionLevel.ordinal()) {
                currentLineIndex++;
            }
        }

        /// Gets whether all selected message lines have been consumed.
        boolean isAtEnd() {
            verifyOpen();
            return currentLineIndex == lineCount;
        }

        /// Advances to the next selected message line.
        void next() {
            verifyOpen();
            VMError.guarantee(currentLineIndex < lineCount, "LogMessage line iterator is already at its end.");
            currentLineIndex++;
            skipMessagesWithFinerLevel();
        }

        /// Gets the level of the current selected message line.
        LogLevel level() {
            verifyCurrentLine();
            return lineLevel(currentLineIndex);
        }

        /// Writes the current line to `other`, escaping or preserving embedded newlines according to
        /// `foldMultilines` and using `decoratorsLength` for continuation prefixes.
        void writeTo(Log other, boolean foldMultilines, int decoratorsLength) {
            verifyCurrentLine();
            int start = lines.read(currentLineIndex * LINE_ENTRY_INTS + 1);
            int end = currentLineIndex + 1 < lineCount ? lines.read((currentLineIndex + 1) * LINE_ENTRY_INTS + 1) : lineBuffer.getPosition();
            VMError.guarantee(other != null, "NativeMemoryLog cannot write to a null log.");
            VMError.guarantee(start >= 0 && start <= end && end <= lineBuffer.getPosition(), "Invalid NativeMemoryLog range.");
            for (int pos = start; pos < end; pos++) {
                char value = (char) lineBuffer.getBuffer().read(pos);
                if (foldMultilines && value == '\\') {
                    other.character('\\');
                    other.character('\\');
                } else if (foldMultilines && value == '\n') {
                    other.character('\\');
                    other.character('n');
                } else if (!foldMultilines && value == '\n') {
                    other.newline();
                    if (decoratorsLength != 0) {
                        other.character('[').spaces(decoratorsLength - 3).string("] ");
                    }
                } else {
                    other.character(value);
                }
            }
        }

        /// Copies the current line's raw native bytes into `target` for asynchronous dispatch.
        void writeCurrentLineTo(NativeMemoryLog target) {
            verifyCurrentLine();
            int start = lines.read(currentLineIndex * LINE_ENTRY_INTS + 1);
            int end = currentLineIndex + 1 < lineCount ? lines.read((currentLineIndex + 1) * LINE_ENTRY_INTS + 1) : lineBuffer.getPosition();
            target.reset();
            lineBuffer.writeRangeTo(target, start, end);
        }

        /// Gets the level stored for a native line record.
        private LogLevel lineLevel(int lineIndex) {
            return LogLevel.VALUES[lines.read(lineIndex * LINE_ENTRY_INTS)];
        }

        /// Verifies that the iterator is positioned at a message line.
        private void verifyCurrentLine() {
            verifyOpen();
            VMError.guarantee(currentLineIndex < lineCount, "LogMessage line iterator is not positioned at a line.");
        }
    }
}

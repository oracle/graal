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

import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.util.VMError;

/// Represents an allocation-free multi-line logging scope whose lines are committed as one event.
///
/// Each [LogTagSet] has one shared facade, while mutable message bytes, line metadata, and event
/// decorations are owned by the current carrier thread. A carrier thread may have only one open
/// message scope at a time, including across different tag sets.
///
/// In a context where heap allocation is unrestricted, a message is used in a try-with-resources
/// statement:
///
/// ```
/// try (LogMessage msg = LogTagSet.class_load.message()) {
///     msg.info().string("info message");
///     msg.debug().string("debug message");
/// }
/// ```
///
/// Since javac adds a call to [Throwable#addSuppressed] for try-with-resources statements, an
/// allocation-restricted context uses try-finally instead:
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
/// Each line can have a different level. An output configured at DEBUG receives both lines, with
/// the DEBUG line immediately following the INFO line. An output configured at INFO receives only
/// the INFO line. Every output receives the same event timestamp, uptime, thread, and tag
/// decorations; the level decorator reflects the line's explicit level.
public final class LogMessage implements AutoCloseable {
    private static final int LINE_ENTRY_INTS = 2;

    /// Tag set that receives the completed message.
    private final LogTagSet tagSet;

    /// Stateless facade over the current thread's message buffer.
    private final NativeMemoryLog lineBuffer;

    @Platforms(Platform.HOSTED_ONLY.class)
    LogMessage(LogTagSet tagSet) {
        this.tagSet = tagSet;
        this.lineBuffer = new NativeMemoryLog(NativeMemoryLog.BufferKind.MESSAGE);
    }

    /// Gets the most severe level recorded in the current message.
    LogLevel getMostSevereLevel() {
        verifyOpen();
        return LogThreadLocal.mostDetailedLevel();
    }

    /// Commits the accumulated message and makes this scope available for reuse.
    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Unified logging must not allocate at run time.")
    public void close() {
        if (LogThreadLocal.activeTagSet() != tagSet) {
            return;
        }
        try {
            if (lineCount() != 0) {
                tagSet.write(this);
            }
        } finally {
            LogThreadLocal.deactivate();
        }
    }

    /// Gets the buffer for formatting a message line. Each call starts a new line.
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Unified logging must not allocate at run time.")
    public NativeMemoryLog line(LogLevel level) {
        verifyOpen();
        if (level.ordinal() > LogThreadLocal.mostDetailedLevel().ordinal()) {
            LogThreadLocal.setMostDetailedLevel(level);
        }
        ensureLineCapacity();
        int lineIndex = lineCount();
        int lineOffset = lineIndex * LINE_ENTRY_INTS;
        int position = lineBuffer.getPosition();
        CIntPointer lines = LogThreadLocal.lines();
        lines.write(lineOffset, level.ordinal());
        lines.write(lineOffset + 1, position);
        LogThreadLocal.setLineCount(lineIndex + 1);
        return lineBuffer;
    }

    /// Gets the buffer for formatting a message line at level [LogLevel#INFO].
    public NativeMemoryLog info() {
        return line(LogLevel.INFO);
    }

    /// Gets the buffer for formatting a message line at level [LogLevel#DEBUG].
    public NativeMemoryLog debug() {
        return line(LogLevel.DEBUG);
    }

    /// Gets the buffer for formatting a message line at level [LogLevel#TRACE].
    public NativeMemoryLog trace() {
        return line(LogLevel.TRACE);
    }

    int lineCount() {
        verifyOpen();
        return LogThreadLocal.lineCount();
    }

    LogLevel lineLevel(int index) {
        verifyLine(index);
        return LogLevel.VALUES[LogThreadLocal.lines().read(index * LINE_ENTRY_INTS)];
    }

    /// Writes one selected line to `other`, preserving or folding embedded newlines.
    void writeLineTo(int index, Log other, boolean foldMultilines, int decoratorsLength) {
        verifyLine(index);
        int start = lineStart(index);
        int end = index + 1 < lineCount() ? lineStart(index + 1) : lineBuffer.getPosition();
        for (int position = start; position < end; position++) {
            char value = (char) lineBuffer.getBuffer().read(position);
            if (foldMultilines && value == '\\') {
                other.character('\\').character('\\');
            } else if (foldMultilines && value == '\n') {
                other.character('\\').character('n');
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

    /// Copies one selected line into a record-owned buffer for asynchronous dispatch.
    void writeLineTo(int index, NativeMemoryLog target) {
        verifyLine(index);
        int start = lineStart(index);
        int end = index + 1 < lineCount() ? lineStart(index + 1) : lineBuffer.getPosition();
        target.reset();
        lineBuffer.writeRangeTo(target, start, end);
    }

    private static int lineStart(int index) {
        return LogThreadLocal.lines().read(index * LINE_ENTRY_INTS + 1);
    }

    private void verifyOpen() {
        VMError.guarantee(LogThreadLocal.activeTagSet() == tagSet, "LogMessage can only be accessed within its open scope.");
    }

    private void verifyLine(int index) {
        verifyOpen();
        VMError.guarantee(index >= 0 && index < lineCount(), "LogMessage line index is outside the message.");
    }

    private void ensureLineCapacity() {
        int count = lineCount();
        int capacity = LogThreadLocal.lineCapacity();
        if (count < capacity) {
            return;
        }
        long requestedCapacity = capacity == 0 ? 10 : (long) capacity * 2;
        long requestedBytes = requestedCapacity * LINE_ENTRY_INTS * Integer.BYTES;
        VMError.guarantee(requestedCapacity <= Integer.MAX_VALUE && requestedBytes <= Integer.MAX_VALUE, "LogMessage line metadata is too large.");
        CIntPointer lines = LogThreadLocal.lines();
        lines = capacity == 0 ? com.oracle.svm.core.memory.NullableNativeMemory.malloc((int) requestedBytes, com.oracle.svm.core.nmt.NmtCategory.Logging)
                        : com.oracle.svm.core.memory.NullableNativeMemory.realloc(lines, (int) requestedBytes, com.oracle.svm.core.nmt.NmtCategory.Logging);
        VMError.guarantee(lines.isNonNull(), "Could not grow LogMessage line metadata.");
        LogThreadLocal.setLines(lines);
        LogThreadLocal.setLineCapacity((int) requestedCapacity);
    }
}

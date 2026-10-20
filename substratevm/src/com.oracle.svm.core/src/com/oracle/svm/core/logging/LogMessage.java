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

import com.oracle.svm.core.log.NativeMemoryBuffer;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

/// Builds and synchronously delivers a complete message in one uninterruptible region.
/// A carrier thread owns the mutable bytes and line metadata; tag sets share immutable facades.
/// Allocating preparation and level predicates stay outside the annotated `log*` owner.
/// Try/finally closes the scope without the interruptible suppressed-exception machinery emitted
/// by try-with-resources. Nested message scopes, including output callbacks, are unsupported.
public final class LogMessage implements AutoCloseable {
    /// Number of integers in each native line-table entry. The first integer stores the line's
    /// [LogLevel] ordinal and the second stores its starting byte offset in [#lineBuffer].
    private static final int LINE_ENTRY_INTS = 2;

    /// Tag set that receives the completed message.
    private final LogTagSet tagSet;

    /// The current thread's message buffer.
    private static final NativeMemoryBuffer lineBuffer = new NativeMemoryBuffer(NativeMemoryBuffer.PRIMARY, true);

    @Platforms(Platform.HOSTED_ONLY.class)
    LogMessage(LogTagSet tagSet) {
        this.tagSet = tagSet;
    }

    @Uninterruptible(reason = "Releases carrier-owned message bytes.", callerMustBe = true)
    static void releaseBuffer(org.graalvm.nativeimage.IsolateThread thread) {
        lineBuffer.release(thread);
    }

    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    static void resetBuffer() {
        lineBuffer.reset();
    }

    /// Gets the most severe level recorded in the current message.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    static LogLevel getMostSevereLevel() {
        verifyOpen();
        return LogThreadLocal.mostDetailedLevel();
    }

    /// Commits the accumulated message and makes this scope available for reuse.
    @Override
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    public void close() {
        if (!LogThreadLocal.hasActiveMessage()) {
            return;
        }
        try {
            if (lineCount() != 0) {
                tagSet.write();
            }
        } finally {
            LogThreadLocal.deactivate();
        }
    }

    /// Gets the buffer for formatting a message line. Each call starts a new line.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    @SuppressWarnings("static-method")
    public NativeMemoryBuffer line(LogLevel level) {
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
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer info() {
        return line(LogLevel.INFO);
    }

    /// Gets the buffer for formatting a message line at level [LogLevel#DEBUG].
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer debug() {
        return line(LogLevel.DEBUG);
    }

    /// Gets the buffer for formatting a message line at level [LogLevel#TRACE].
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer trace() {
        return line(LogLevel.TRACE);
    }

    /// Gets the number of lines in the current thread's open message.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    static int lineCount() {
        verifyOpen();
        return LogThreadLocal.lineCount();
    }

    /// Gets the level of line `index` in the current thread's open message.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    static LogLevel lineLevel(int index) {
        verifyLine(index);
        return LogLevel.VALUES[LogThreadLocal.lines().read(index * LINE_ENTRY_INTS)];
    }

    /// Writes one selected line to `other`, preserving or folding embedded newlines.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    static void writeLineTo(int index, NativeMemoryBuffer other, boolean foldMultilines, LogOutput output, int decoratorWidth) {
        verifyLine(index);
        int start = lineStart(index);
        int end = index + 1 < lineCount() ? lineStart(index + 1) : lineBuffer.getPosition();
        for (int position = start; position < end; position++) {
            char value = (char) lineBuffer.getBuffer().read(position);
            if (value == '\r' && position + 1 < end && lineBuffer.getBuffer().read(position + 1) == '\n') {
                /* The following LF represents the complete platform-independent newline. */
                continue;
            } else if (foldMultilines && value == '\\') {
                other.character('\\').character('\\');
            } else if (foldMultilines && value == '\n') {
                other.character('\\').character('n');
            } else if (!foldMultilines && value == '\n') {
                other.newline();
                output.writeContinuationPrefix(decoratorWidth);
            } else {
                other.character(value);
            }
        }
    }

    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    private static int lineStart(int index) {
        return LogThreadLocal.lines().read(index * LINE_ENTRY_INTS + 1);
    }

    /// Requires an open scope on the current thread before accessing its message state.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    private static void verifyOpen() {
        VMError.guarantee(LogThreadLocal.hasActiveMessage(), "LogMessage can only be accessed within an open scope.");
    }

    /// Requires `index` to identify a line in the current thread's open message.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    private static void verifyLine(int index) {
        verifyOpen();
        VMError.guarantee(index >= 0 && index < lineCount(), "LogMessage line index is outside the message.");
    }

    /// Grows the current thread's native line table when another line would not fit.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_ACCESS, callerMustBe = true)
    private static void ensureLineCapacity() {
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

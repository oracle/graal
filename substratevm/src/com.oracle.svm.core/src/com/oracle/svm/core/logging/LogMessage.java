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

import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.util.VMError;

/// Represents a logging scope containing one or more lines that does not allocate on the Java heap.
/// Its lines are committed as one event to either configured `-Xlog` outputs or a legacy fallback
/// route.
///
/// Each [LogTagSet] has one shared facade, while mutable message bytes, line metadata, and event
/// decorations are owned by the current carrier thread. A carrier thread may have only one open
/// message scope at a time, including across different tag sets.
///
/// Every message scope belongs to a `void log*` method with the ownership annotations described
/// by [LogTagSet#message()]. The owner is never inlined and includes synchronous delivery from
/// [#close()]. Ordinary logging sites keep their level predicate and any allocating preparation
/// outside this method, and annotation checking covers its callees. GC owners can use
/// [LogTagSet#LOG_MESSAGE_OWNER_IN_GC] with `calleeMustBe = false`, relying on their existing GC
/// context for allocation and safepoint safety in callees. They still use
/// `@NeverInline(LogTagSet.LOG_MESSAGE_OWNER)`; there are no method-specific checker exemptions.
///
/// ```java
/// @NeverInline(LogTagSet.LOG_MESSAGE_OWNER)
/// @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER)
/// private static void logClassLoad(String className, String loaderDescription) {
///     LogMessage message = LogTagSet.class_load.message();
///     try {
///         message.info().string(className).string(" loader=").string(loaderDescription);
///     } finally {
///         message.close();
///     }
/// }
/// ```
///
/// Try-finally avoids the interruptible [Throwable#addSuppressed] call generated for
/// try-with-resources. Native buffer growth does not allocate on the Java heap. Nested message
/// scopes, including from an output callback, are unsupported.
public final class LogMessage implements AutoCloseable {
    /// Number of integers in each native line-table entry. The first integer stores the line's
    /// [LogLevel] ordinal and the second stores its starting byte offset in [#lineBuffer].
    private static final int LINE_ENTRY_INTS = 2;

    /// Tag set that receives the completed message.
    private final LogTagSet tagSet;

    /// The current thread's message buffer.
    private final NativeMemoryLog lineBuffer;

    @Platforms(Platform.HOSTED_ONLY.class)
    LogMessage(LogTagSet tagSet) {
        this.tagSet = tagSet;
        this.lineBuffer = new NativeMemoryLog(NativeMemoryLog.BufferKind.MESSAGE);
    }

    /// Gets the most severe level recorded in the current message.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static LogLevel getMostSevereLevel() {
        verifyOpen();
        return LogThreadLocal.mostDetailedLevel();
    }

    /// Commits the accumulated message and makes this scope available for reuse.
    @Override
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void close() {
        if (!LogThreadLocal.hasActiveMessage()) {
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
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
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
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public NativeMemoryLog info() {
        return line(LogLevel.INFO);
    }

    /// Gets the buffer for formatting a message line at level [LogLevel#DEBUG].
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public NativeMemoryLog debug() {
        return line(LogLevel.DEBUG);
    }

    /// Gets the buffer for formatting a message line at level [LogLevel#TRACE].
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public NativeMemoryLog trace() {
        return line(LogLevel.TRACE);
    }

    /// Gets the number of lines in the current thread's open message.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static int lineCount() {
        verifyOpen();
        return LogThreadLocal.lineCount();
    }

    /// Gets the level of line `index` in the current thread's open message.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static LogLevel lineLevel(int index) {
        verifyLine(index);
        return LogLevel.VALUES[LogThreadLocal.lines().read(index * LINE_ENTRY_INTS)];
    }

    /// Writes one selected line to `other`, preserving or folding embedded newlines.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void writeLineTo(int index, Log other, boolean foldMultilines, LogOutput output, int decoratorWidth) {
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

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int lineStart(int index) {
        return LogThreadLocal.lines().read(index * LINE_ENTRY_INTS + 1);
    }

    /// Requires an open scope on the current thread before accessing its message state.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void verifyOpen() {
        VMError.guarantee(LogThreadLocal.hasActiveMessage(), "LogMessage can only be accessed within an open scope.");
    }

    /// Requires `index` to identify a line in the current thread's open message.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void verifyLine(int index) {
        verifyOpen();
        VMError.guarantee(index >= 0 && index < lineCount(), "LogMessage line index is outside the message.");
    }

    /// Grows the current thread's native line table when another line would not fit.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
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

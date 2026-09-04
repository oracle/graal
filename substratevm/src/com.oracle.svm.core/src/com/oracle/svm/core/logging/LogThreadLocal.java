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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.guest.staging.core.thread.ThreadListener;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

/// Holds mutable unified-logging state for the current platform thread.
public final class LogThreadLocal implements ThreadListener {
    private static final FastThreadLocalWord<Data> state = FastThreadLocalFactory.createWord("LogThreadLocal.state");

    @Platforms(Platform.HOSTED_ONLY.class)
    public LogThreadLocal() {
    }

    static Data get() {
        Data result = state.get();
        if (result.isNull()) {
            result = NullableNativeMemory.calloc(SizeOf.get(Data.class), NmtCategory.Logging);
            VMError.guarantee(result.isNonNull(), "Could not allocate unified logging thread-local state.");
            state.set(result);
        }
        return result;
    }

    static void activate(LogTagSet tagSet) {
        Data data = get();
        VMError.guarantee(data.getActiveTagSet() == 0, "Nested unified logging is not allowed.");
        data.setActiveTagSet(tagSet.ordinal() + 1);
        data.setLineCount(0);
        data.setMostDetailedLevel(LogLevel.OFF.ordinal());
        NativeMemoryLog.reset(data.getMessageBuffer());
    }

    static void deactivate() {
        Data data = get();
        data.setActiveTagSet(0);
        data.setLineCount(0);
        data.setMostDetailedLevel(LogLevel.OFF.ordinal());
        NativeMemoryLog.reset(data.getMessageBuffer());
    }

    /// Gets the thread-local [NativeMemoryLog] buffer for `kind`, allocating it first
    /// if it has not yet been allocated.
    static NativeMemoryLog.Data getBuffer(NativeMemoryLog.BufferKind kind) {
        Data data = state.get();
        VMError.guarantee(data.isNonNull(), "Unified logging thread-local state is not active.");
        return switch (kind) {
            case MESSAGE -> data.getMessageBuffer();
            case OUTPUT -> data.getOutputBuffer();
            case DECORATOR -> data.getDecoratorBuffer();
            default -> throw VMError.shouldNotReachHere(kind.name());
        };
    }

    static void setBuffer(NativeMemoryLog.BufferKind kind, NativeMemoryLog.Data buffer) {
        Data data = state.get();
        VMError.guarantee(data.isNonNull(), "Unified logging thread-local state is not active.");
        switch (kind) {
            case MESSAGE -> data.setMessageBuffer(buffer);
            case OUTPUT -> data.setOutputBuffer(buffer);
            case DECORATOR -> data.setDecoratorBuffer(buffer);
            case RECORD -> VMError.shouldNotReachHere("Record buffers are not thread local.");
        }
    }

    static CIntPointer lines() {
        return get().getLines();
    }

    static int lineCount() {
        return get().getLineCount();
    }

    static void setLineCount(int value) {
        get().setLineCount(value);
    }

    static int lineCapacity() {
        return get().getLineCapacity();
    }

    static void setLineCapacity(int value) {
        get().setLineCapacity(value);
    }

    static void setLines(CIntPointer value) {
        get().setLines(value);
    }

    static LogTagSet activeTagSet() {
        int ordinal = get().getActiveTagSet();
        return ordinal == 0 ? null : LogTagSet.VALUES[ordinal - 1];
    }

    static LogLevel mostDetailedLevel() {
        return LogLevel.VALUES[get().getMostDetailedLevel()];
    }

    static void setMostDetailedLevel(LogLevel level) {
        get().setMostDetailedLevel(level.ordinal());
    }

    static long systemNanos() {
        return get().getSystemNanos();
    }

    static long uptimeNanos() {
        return get().getUptimeNanos();
    }

    static long threadId() {
        return get().getThreadId();
    }

    @Override
    @Uninterruptible(reason = "Release native logging buffers after the thread exits.")
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        Data data = state.get(isolateThread);
        if (data.isNull()) {
            return;
        }
        freeBuffer(data.getMessageBuffer());
        freeBuffer(data.getOutputBuffer());
        freeBuffer(data.getDecoratorBuffer());
        NullableNativeMemory.free(data.getLines());
        state.set(isolateThread, WordFactory.nullPointer());
        NullableNativeMemory.free(data);
    }

    @Uninterruptible(reason = "Release a native logging buffer.")
    private static void freeBuffer(NativeMemoryLog.Data buffer) {
        NullableNativeMemory.free(buffer);
    }

    // @formatter:off
    /// A struct for the per-thread logging state.
    ///
    /// ```c
    /// struct LogThreadLocal.Data {
    ///     struct NativeMemoryLog.Data *messageBuffer;
    ///     struct NativeMemoryLog.Data *outputBuffer;
    ///     struct NativeMemoryLog.Data *decoratorBuffer;
    ///     int *lines;
    ///     int lineCount;
    ///     int lineCapacity;
    ///     int mostDetailedLevel;
    ///     int activeTagSet;
    ///     long systemMillis;
    ///     long systemNanos;
    ///     long uptimeNanos;
    ///     long threadId;
    /// };
    /// ```
    @RawStructure
    interface Data extends PointerBase {
        @RawField NativeMemoryLog.Data getMessageBuffer();
        @RawField void                 setMessageBuffer(NativeMemoryLog.Data value);

        @RawField NativeMemoryLog.Data getOutputBuffer();
        @RawField void                 setOutputBuffer(NativeMemoryLog.Data value);

        @RawField NativeMemoryLog.Data getDecoratorBuffer();
        @RawField void                 setDecoratorBuffer(NativeMemoryLog.Data value);

        @RawField CIntPointer getLines();
        @RawField void        setLines(CIntPointer value);

        @RawField int  getLineCount();
        @RawField void setLineCount(int value);

        @RawField int  getLineCapacity();
        @RawField void setLineCapacity(int value);

        @RawField int  getMostDetailedLevel();
        @RawField void setMostDetailedLevel(int value);

        @RawField int  getActiveTagSet();
        @RawField void setActiveTagSet(int value);

        @RawField long getSystemMillis();
        @RawField void setSystemMillis(long value);

        @RawField long getSystemNanos();
        @RawField void setSystemNanos(long value);

        @RawField long getUptimeNanos();
        @RawField void setUptimeNanos(long value);

        @RawField long getThreadId();
        @RawField void setThreadId(long value);
    }
    // @formatter:on
}

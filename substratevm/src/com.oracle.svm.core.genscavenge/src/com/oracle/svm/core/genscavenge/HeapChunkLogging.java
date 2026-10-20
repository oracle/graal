/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.guest.staging.log.Log.RIGHT_ALIGN;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.log.NativeMemoryBuffer;
import com.oracle.svm.core.thread.ContinuationInternals;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.guest.staging.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;

class HeapChunkLogging {
    private static final int MAX_CHUNKS_TO_PRINT = 64 * 1024;

    /// Fits four addresses, a full unsigned percentage, a signed pin count, the space name
    /// (at most `S` followed by an integer age), and separators, even for corrupt numeric values.
    private static final int DIAGNOSTIC_ROW_CAPACITY = 256;

    /// Borrows stack storage only during formatting, independently of ordinary logging buffers.
    private static final NativeMemoryBuffer DIAGNOSTIC_ROW = new NativeMemoryBuffer(NativeMemoryBuffer.DIAGNOSTIC, true);

    public static void logChunks(Log log, AlignedHeapChunk.AlignedHeader firstChunk, String shortSpaceName, boolean isToSpace) {
        int i = 0;
        AlignedHeapChunk.AlignedHeader chunk = firstChunk;
        while (chunk.isNonNull() && i < MAX_CHUNKS_TO_PRINT) {
            Pointer bottom = AlignedHeapChunk.getObjectsStart(chunk);
            Pointer top = HeapChunk.getTopPointer(chunk);
            Pointer end = AlignedHeapChunk.getObjectsEnd(chunk);

            logChunk(log, chunk, bottom, top, end, true, shortSpaceName, isToSpace);

            chunk = HeapChunk.getNext(chunk);
            i++;
        }
        if (chunk.isNonNull()) {
            assert i == MAX_CHUNKS_TO_PRINT;
            log.newline().string("... (truncated)");
        }
    }

    public static void logChunks(Log log, UnalignedHeapChunk.UnalignedHeader firstChunk, String shortSpaceName, boolean isToSpace) {
        int i = 0;
        UnalignedHeapChunk.UnalignedHeader chunk = firstChunk;
        while (chunk.isNonNull() && i < MAX_CHUNKS_TO_PRINT) {
            Pointer bottom = UnalignedHeapChunk.getObjectStart(chunk);
            Pointer top = HeapChunk.getTopPointer(chunk);
            Pointer end = UnalignedHeapChunk.getObjectEnd(chunk);

            logChunk(log, chunk, bottom, top, end, false, shortSpaceName, isToSpace);

            chunk = HeapChunk.getNext(chunk);
            i++;
        }
        if (chunk.isNonNull()) {
            assert i == MAX_CHUNKS_TO_PRINT;
            log.newline().string("... (truncated)");
        }
    }

    /// Streams one bounded record to the supplied diagnostic logger. The legacy handler may
    /// block, so the continuation stays pinned until it has consumed the stack bytes.
    public static void logChunk(Log log, HeapChunk.Header<?> chunk, Pointer bottom, Pointer top, Pointer end, boolean isAligned, String shortSpaceName, boolean isToSpace) {
        Target_jdk_internal_vm_Continuation continuation = ContinuationInternals.pinCurrentForNativeStack();
        try {
            logDiagnosticChunk(log, chunk, bottom, top, end, isAligned, shortSpaceName, isToSpace);
        } finally {
            ContinuationInternals.unpinForNativeStack(continuation);
        }
    }

    /// Keeps each row's storage in its own frame, including recursive diagnostic invocations.
    @NeverInline("Owns the stack storage passed to the legacy diagnostic handler.")
    private static void logDiagnosticChunk(Log log, HeapChunk.Header<?> chunk, Pointer bottom, Pointer top, Pointer end, boolean isAligned, String shortSpaceName, boolean isToSpace) {
        NativeMemoryBuffer.Data storage = UnsafeStackValue.get(NativeMemoryBuffer.stackBufferSize(DIAGNOSTIC_ROW_CAPACITY));
        int length = logDiagnosticChunk(storage, chunk, bottom, top, end, isAligned, shortSpaceName, isToSpace);
        log.string(NativeMemoryBuffer.dataStart(storage), length).newline();
    }

    /// Clears the carrier's borrowed pointer before legacy delivery can block or reenter logging.
    @Uninterruptible(reason = "Formats a diagnostic row in caller-owned stack storage.")
    private static int logDiagnosticChunk(NativeMemoryBuffer.Data storage, HeapChunk.Header<?> chunk, Pointer bottom, Pointer top, Pointer end, boolean isAligned, String shortSpaceName,
                    boolean isToSpace) {
        DIAGNOSTIC_ROW.initializeStackBuffer(storage, DIAGNOSTIC_ROW_CAPACITY);
        try {
            logChunkRecord(DIAGNOSTIC_ROW, chunk, bottom, top, end, isAligned, shortSpaceName, isToSpace);
            return DIAGNOSTIC_ROW.getPosition();
        } finally {
            DIAGNOSTIC_ROW.clearStackBuffer();
        }
    }

    /// Formats chunk metadata without entering a legacy handler.
    @Uninterruptible(reason = "Formats GC diagnostics without safepoints.", callerMustBe = true)
    public static void logChunks(NativeMemoryBuffer log, AlignedHeapChunk.AlignedHeader firstChunk, String shortSpaceName, boolean isToSpace) {
        int i = 0;
        AlignedHeapChunk.AlignedHeader chunk = firstChunk;
        while (chunk.isNonNull() && i < MAX_CHUNKS_TO_PRINT) {
            Pointer bottom = AlignedHeapChunk.getObjectsStart(chunk);
            Pointer top = HeapChunk.getTopPointer(chunk);
            Pointer end = AlignedHeapChunk.getObjectsEnd(chunk);

            logChunk(log, chunk, bottom, top, end, true, shortSpaceName, isToSpace);

            chunk = HeapChunk.getNext(chunk);
            i++;
        }
        if (chunk.isNonNull()) {
            assert i == MAX_CHUNKS_TO_PRINT;
            GCLogMessage.ensureLinePrefix(log);
            log.string("... (truncated)").newline();
        }
    }

    /// Formats chunk metadata without entering a legacy handler.
    @Uninterruptible(reason = "Formats GC diagnostics without safepoints.", callerMustBe = true)
    public static void logChunks(NativeMemoryBuffer log, UnalignedHeapChunk.UnalignedHeader firstChunk, String shortSpaceName, boolean isToSpace) {
        int i = 0;
        UnalignedHeapChunk.UnalignedHeader chunk = firstChunk;
        while (chunk.isNonNull() && i < MAX_CHUNKS_TO_PRINT) {
            Pointer bottom = UnalignedHeapChunk.getObjectStart(chunk);
            Pointer top = HeapChunk.getTopPointer(chunk);
            Pointer end = UnalignedHeapChunk.getObjectEnd(chunk);

            logChunk(log, chunk, bottom, top, end, false, shortSpaceName, isToSpace);

            chunk = HeapChunk.getNext(chunk);
            i++;
        }
        if (chunk.isNonNull()) {
            assert i == MAX_CHUNKS_TO_PRINT;
            GCLogMessage.ensureLinePrefix(log);
            log.string("... (truncated)").newline();
        }
    }

    /// Formats chunk metadata without entering a legacy handler.
    @Uninterruptible(reason = "Formats GC diagnostics without safepoints.", callerMustBe = true)
    public static void logChunk(NativeMemoryBuffer log, HeapChunk.Header<?> chunk, Pointer bottom, Pointer top, Pointer end, boolean isAligned, String shortSpaceName, boolean isToSpace) {
        GCLogMessage.ensureLinePrefix(log);
        logChunkRecord(log, chunk, bottom, top, end, isAligned, shortSpaceName, isToSpace);
        log.newline();
    }

    /// Shares the chunk text between GC events and fatal diagnostics without imposing GC prefixes
    /// or legacy delivery on either caller.
    @Uninterruptible(reason = "Formats chunk metadata without safepoints.", callerMustBe = true)
    private static void logChunkRecord(NativeMemoryBuffer log, HeapChunk.Header<?> chunk, Pointer bottom, Pointer top, Pointer end, boolean isAligned, String shortSpaceName, boolean isToSpace) {
        log.string("|").zhex(chunk).string("|").zhex(bottom).string(", ").zhex(top).string(", ").zhex(end);
        log.string("|");
        if (top.isNonNull()) {
            UnsignedWord used = top.subtract(bottom);
            UnsignedWord capacity = end.subtract(bottom);
            UnsignedWord usedPercent = used.multiply(100).unsignedDivide(capacity);
            log.unsigned(usedPercent, 3, RIGHT_ALIGN).string("%");
        } else {
            log.spaces(3).string("?");
        }
        log.string("|").string(shortSpaceName, 3, RIGHT_ALIGN);
        log.string("|").string(isAligned ? "A" : "U");
        log.string("|").string(isToSpace ? "T" : " ");
        log.string("|");
        if (isAligned) {
            AlignedHeapChunk.AlignedHeader aChunk = (AlignedHeapChunk.AlignedHeader) chunk;
            if (aChunk.getObjectPinCount() != 0) {
                log.signed(aChunk.getObjectPinCount());
            }
        }
    }
}

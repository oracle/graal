/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.RingBuffer;

public class RuntimeCodeInfoHistory {
    private static final RingBuffer.Consumer<CodeCacheLogEntry> PRINT_WITH_JAVA_HEAP_DATA = RuntimeCodeInfoHistory::printEntryWithJavaHeapData;
    private static final RingBuffer.Consumer<CodeCacheLogEntry> PRINT_WITHOUT_JAVA_HEAP_DATA = RuntimeCodeInfoHistory::printEntryWithoutJavaHeapData;

    private final RingBuffer<CodeCacheLogEntry> recentOperations;

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeCodeInfoHistory() {
        recentOperations = new RingBuffer<>(20, CodeCacheLogEntry::new);
    }

    @Fold
    public static RuntimeCodeInfoHistory singleton() {
        return ImageSingletons.lookup(RuntimeCodeInfoHistory.class);
    }

    public void logAdd(CodeInfo info) {
        logOperation("Added", info);
    }

    public void logMakeNonEntrant(CodeInfo info) {
        logOperation("Made non-entrant", info);
    }

    public void logInvalidate(CodeInfo info) {
        logOperation("Invalidated", info);
    }

    private void logOperation(String kind, CodeInfo info) {
        assert VMOperation.isInProgressAtSafepoint();

        traceCodeCache(kind, info, true);
        recentOperations.next().setValues(kind, info, CodeInfoAccess.getState(info), CodeInfoAccess.getName(info), CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info));
    }

    public void logFree(CodeInfo info) {
        assert VMOperation.isInProgressAtSafepoint() || VMThreads.isTearingDown();

        traceCodeCache("Freed", info, false);
        recentOperations.next().setValues("Freed", info, CodeInfoAccess.getState(info), null, CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info));
    }

    private static void traceCodeCache(String kind, CodeInfo info, boolean allowJavaHeapAccess) {
        if (RuntimeCodeCache.Options.TraceCodeCache.getValue()) {
            Log.log().string(kind).string(" method: ");
            CodeInfoAccess.printCodeInfo(Log.log(), info, allowJavaHeapAccess);
        }
    }

    public void printRecentOperations(Log log, boolean allowJavaHeapAccess) {
        log.string("The ").signed(recentOperations.size()).string(" most recent RuntimeCodeInfo operations (oldest first): ").indent(true);
        recentOperations.foreach(log, allowJavaHeapAccess ? PRINT_WITH_JAVA_HEAP_DATA : PRINT_WITHOUT_JAVA_HEAP_DATA);
        log.indent(false);
    }

    private static void printEntryWithJavaHeapData(Object context, CodeCacheLogEntry entry) {
        printEntry(context, entry, true);
    }

    private static void printEntryWithoutJavaHeapData(Object context, CodeCacheLogEntry entry) {
        printEntry(context, entry, false);
    }

    private static void printEntry(Object context, CodeCacheLogEntry entry, boolean allowJavaHeapAccess) {
        Log log = (Log) context;
        entry.print(log, allowJavaHeapAccess);
    }

    private static class CodeCacheLogEntry {
        private long timestamp;
        private String kind;
        private String codeName;
        private CodeInfo codeInfo;
        private int codeInfoState;
        private CodePointer codeStart;
        private CodePointer codeEnd;

        @Platforms(Platform.HOSTED_ONLY.class)
        CodeCacheLogEntry() {
        }

        public void setValues(String kind, CodeInfo codeInfo, int codeInfoState, String codeName, CodePointer codeStart, CodePointer codeEnd) {
            assert Heap.getHeap().isInImageHeap(kind);
            this.timestamp = System.currentTimeMillis();
            this.kind = kind;
            this.codeInfo = codeInfo;
            this.codeInfoState = codeInfoState;
            this.codeName = codeName;
            this.codeStart = codeStart;
            this.codeEnd = codeEnd;
        }

        public void print(Log log, boolean allowJavaHeapAccess) {
            if (kind != null) {
                log.unsigned(timestamp).string(" - ").string(kind).spaces(1);
                String name = allowJavaHeapAccess ? codeName : null;
                CodeInfoAccess.printCodeInfo(log, codeInfo, codeInfoState, name, codeStart, codeEnd);
            }
        }
    }
}

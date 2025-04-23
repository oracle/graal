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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfoAccess.HasInstalledCode;
import com.oracle.svm.core.collections.RingBuffer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;

import jdk.graal.compiler.api.replacements.Fold;

public class RuntimeCodeInfoHistory {
    private static final RingBuffer.Consumer<CodeCacheLogEntry> PRINT_WITH_JAVA_HEAP_DATA = RuntimeCodeInfoHistory::printEntryWithJavaHeapData;
    private static final RingBuffer.Consumer<CodeCacheLogEntry> PRINT_WITHOUT_JAVA_HEAP_DATA = RuntimeCodeInfoHistory::printEntryWithoutJavaHeapData;

    private final RingBuffer<CodeCacheLogEntry> recentOperations;

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeCodeInfoHistory() {
        recentOperations = new RingBuffer<>(SubstrateOptions.DiagnosticBufferSize.getValue(), CodeCacheLogEntry::new);
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
        assert VMOperation.isInProgressAtSafepoint() : kind;

        traceCodeCache(kind, info, true);
        logOperation0(kind, info, CodeInfoAccess.getName(info));
    }

    @Uninterruptible(reason = "Prevent the GC from logging any invalidations as this could causes races.")
    private void logOperation0(String kind, CodeInfo info, String name) {
        recentOperations.next().setValues(kind, info, CodeInfoAccess.getState(info), name, CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info),
                        RuntimeCodeInfoAccess.getInstalledCode(info));
    }

    public void logFree(CodeInfo info) {
        assert VMOperation.isInProgressAtSafepoint() || VMThreads.isTearingDown() : "invalid state";

        traceCodeCache("Freed", info, false);
        logOperation0("Freed", info, null);
    }

    private static void traceCodeCache(String kind, CodeInfo info, boolean allowJavaHeapAccess) {
        if (RuntimeCodeCache.Options.TraceCodeCache.getValue()) {
            Log.log().string(kind).string(" method: ");
            CodeInfoAccess.printCodeInfo(Log.log(), info, allowJavaHeapAccess);
            Log.log().newline();
        }
    }

    public void printRecentOperations(Log log, boolean allowJavaHeapAccess) {
        log.string("The ").signed(recentOperations.size()).string(" most recent RuntimeCodeInfo operations:").indent(true);
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
        private long uptimeMillis;
        private String kind;
        private String codeName;
        private CodeInfo codeInfo;
        private int codeInfoState;
        private CodePointer codeStart;
        private CodePointer codeEnd;
        private HasInstalledCode hasInstalledCode;
        private long installedCodeAddress;
        private long installedCodeEntryPoint;
        private UnsignedWord safepointId;

        @Platforms(Platform.HOSTED_ONLY.class)
        CodeCacheLogEntry() {
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void setValues(String kind, CodeInfo codeInfo, int codeInfoState, String codeName, CodePointer codeStart, CodePointer codeEnd, SubstrateInstalledCode installedCode) {
            assert VMOperation.isInProgressAtSafepoint();
            assert Heap.getHeap().isInImageHeap(kind);

            this.safepointId = Safepoint.singleton().getSafepointId();
            this.uptimeMillis = Isolates.getUptimeMillis();
            this.kind = kind;
            this.codeInfo = codeInfo;
            this.codeInfoState = codeInfoState;
            this.codeName = codeName;
            this.codeStart = codeStart;
            this.codeEnd = codeEnd;
            if (installedCode != null) {
                hasInstalledCode = HasInstalledCode.Yes;
                installedCodeAddress = installedCode.getAddress();
                installedCodeEntryPoint = installedCode.getEntryPoint();
            } else {
                hasInstalledCode = HasInstalledCode.No;
                installedCodeAddress = 0;
                installedCodeEntryPoint = 0;
            }
        }

        public void print(Log log, boolean allowJavaHeapAccess) {
            if (kind != null) {
                log.rational(uptimeMillis, TimeUtils.millisPerSecond, 3).string("s - ").string(kind).spaces(1);
                String name = allowJavaHeapAccess ? codeName : null;
                CodeInfoAccess.printCodeInfo(log, codeInfo, codeInfoState, name, codeStart, codeEnd, hasInstalledCode, installedCodeAddress, installedCodeEntryPoint);
                log.string(", safepointId: ").unsigned(safepointId).newline();
            }
        }
    }
}

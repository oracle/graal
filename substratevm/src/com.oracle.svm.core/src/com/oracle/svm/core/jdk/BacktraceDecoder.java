/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoDecoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

/**
 * Decoder for backtraces computed by {@link BacktraceVisitor} and stored in
 * {@link Target_java_lang_Throwable#backtrace}.
 */
public abstract class BacktraceDecoder {
    private final CodeInfoDecoder.FrameInfoCursor frameInfoCursor = new CodeInfoDecoder.FrameInfoCursor();

    /**
     * Visits the backtrace stored in {@code Throwable#backtrace}.
     *
     * @param holder internal backtrace stored in {@link Target_java_lang_Throwable#backtrace}
     * @param maxFramesProcessed the maximum number of frames that should be
     *            {@linkplain #processSourceReference processed}
     * @param maxFramesDecode the maximum number of frames that should be decoded (0 means all)
     * @return the number of decoded frames
     */
    protected final int visitBacktrace(Object holder, int maxFramesProcessed, int maxFramesDecode) {
        int maxFramesDecodeLimit = maxFramesDecode > 0 ? maxFramesDecode : Integer.MAX_VALUE;
        int framesDecoded = 0;

        if (holder instanceof long[] backtrace) {
            int backtraceIndex = 0;
            while (backtraceIndex < backtrace.length && backtrace[backtraceIndex] != 0) {
                long entry = backtrace[backtraceIndex];
                if (BacktraceHolder.isSourceReference(entry)) {
                    visitSourceReference(maxFramesProcessed, framesDecoded, backtrace, null, backtraceIndex);
                    framesDecoded++;
                    backtraceIndex += BacktraceHolder.slotsPerSourceReference(BacktraceHolder.getSourceReferenceKind(entry));
                } else {
                    CodePointer ip = Word.pointer(entry);
                    framesDecoded = visitCodePointer(ip, framesDecoded, maxFramesProcessed, maxFramesDecodeLimit);
                    backtraceIndex += BacktraceHolder.slotsPerCodePointer();
                }
                if (framesDecoded == maxFramesDecodeLimit) {
                    break;
                }
            }
        } else if (holder instanceof BacktraceHolder backtrace) {
            int backtraceIndex = 0;
            while (backtrace.hasNext(backtraceIndex)) {
                if (BacktraceHolder.isSourceReference(backtrace.raw[backtraceIndex])) {
                    visitSourceReference(maxFramesProcessed, framesDecoded, backtrace.raw, backtrace.side, backtraceIndex);
                    framesDecoded++;
                } else {
                    CodePointer ip = Word.pointer(backtrace.raw[backtraceIndex]);
                    framesDecoded = visitCodePointer(ip, framesDecoded, maxFramesProcessed, maxFramesDecodeLimit);
                }
                backtraceIndex += backtrace.slotsForEntry(backtraceIndex);
                if (framesDecoded == maxFramesDecodeLimit) {
                    break;
                }
            }
        } else {
            VMError.guarantee(holder == null, "Unexpected backtrace type");
        }
        return framesDecoded - maxFramesProcessed;
    }

    private void visitSourceReference(int maxFramesProcessed, int framesDecoded, long[] raw, Object[] side, int backtraceIndex) {
        if (framesDecoded >= maxFramesProcessed) {
            return;
        }

        Class<?> sourceClass = BacktraceHolder.readSourceClass(raw, side, backtraceIndex);
        String sourceMethodName = BacktraceHolder.readSourceMethodName(raw, side, backtraceIndex);
        int sourceLineNumber = BacktraceHolder.readSourceLineNumber(raw, backtraceIndex);

        processSourceReference(sourceClass, sourceMethodName, sourceLineNumber);
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    private int visitCodePointer(CodePointer ip, int oldFramesDecoded, int maxFramesProcessed, int maxFramesDecode) {
        int framesDecoded = oldFramesDecoded;
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
        if (untetheredInfo.isNull()) {
            /* Unknown frame. Must not happen for AOT-compiled code. */
            VMError.shouldNotReachHere("Stack walk must walk only frames of known code.");
        }

        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo tetheredCodeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            framesDecoded = visitFrame(ip, tetheredCodeInfo, framesDecoded, maxFramesProcessed, maxFramesDecode);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
        return framesDecoded;
    }

    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", callerMustBe = true, calleeMustBe = false)
    private int visitFrame(CodePointer ip, CodeInfo tetheredCodeInfo, int oldFramesDecoded, int maxFramesProcessed, int maxFramesDecode) {
        int framesDecoded = oldFramesDecoded;

        frameInfoCursor.initialize(tetheredCodeInfo, ip, true);
        while (frameInfoCursor.advance()) {
            FrameInfoQueryResult frameInfo = frameInfoCursor.get();
            if (!StackTraceUtils.shouldShowFrame(frameInfo)) {
                /* Always ignore the frame. It is an internal frame of the VM. */
                continue;
            }
            if (framesDecoded == 0 && Throwable.class.isAssignableFrom(frameInfo.getSourceClass())) {
                /*
                 * We are still in the constructor invocation chain at the beginning of the stack
                 * trace, which is also filtered by the Java HotSpot VM.
                 */
                continue;
            }
            if (framesDecoded < maxFramesProcessed) {
                processSourceReference(frameInfo.getSourceClass(), frameInfo.getSourceMethodName(), frameInfo.getSourceLineNumber());
            }
            framesDecoded++;
            if (framesDecoded == maxFramesDecode) {
                break;
            }
        }
        return framesDecoded;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Some implementations allocate.")
    protected abstract void processSourceReference(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber);
}

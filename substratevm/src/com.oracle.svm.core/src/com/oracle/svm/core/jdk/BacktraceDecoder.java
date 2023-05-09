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
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoDecoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.util.VMError;

public abstract class BacktraceDecoder {

    /**
     * Visits the backtrace stored in {@code Throwable#backtrace}.
     * 
     * @param backtrace internal backtrace stored in {@link Target_java_lang_Throwable#backtrace}
     * @param maxFramesProcessed the maximum number of frames that should be
     *            {@linkplain #processFrameInfo processed}
     * @param maxFramesDecode the maximum number of frames that should be decoded
     * @return the number of decoded frames
     */
    protected final int visitBacktrace(Object backtrace, int maxFramesProcessed, int maxFramesDecode) {
        int framesDecoded = 0;
        if (backtrace != null) {
            final long[] trace = (long[]) backtrace;
            for (long address : trace) {
                if (address == 0) {
                    break;
                }
                CodePointer ip = WordFactory.pointer(address);
                framesDecoded = visitCodePointer(ip, framesDecoded, maxFramesProcessed, maxFramesDecode);
                if (framesDecoded == maxFramesDecode) {
                    break;
                }
            }
        }
        return framesDecoded - maxFramesProcessed;
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

    private final CodeInfoDecoder.FrameInfoCursor frameInfoCursor = new CodeInfoDecoder.FrameInfoCursor();

    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", callerMustBe = true, calleeMustBe = false)
    private int visitFrame(CodePointer ip, CodeInfo tetheredCodeInfo, int oldFramesDecoded, int maxFramesProcessed, int maxFramesDecode) {
        int framesDecoded = oldFramesDecoded;
        frameInfoCursor.initialize(tetheredCodeInfo, ip);
        while (frameInfoCursor.advance()) {
            FrameInfoQueryResult frameInfo = frameInfoCursor.get();
            if (!StackTraceUtils.shouldShowFrame(frameInfo, false, true, false)) {
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
                processFrameInfo(frameInfo);
            }
            framesDecoded++;
            if (framesDecoded == maxFramesDecode) {
                break;
            }
        }
        return framesDecoded;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Some implementations allocate.")
    protected abstract void processFrameInfo(FrameInfoQueryResult frameInfo);
}

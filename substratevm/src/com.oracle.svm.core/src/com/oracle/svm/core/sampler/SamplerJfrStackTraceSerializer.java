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

package com.oracle.svm.core.sampler;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoDecoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.jfr.JfrBuffer;
import com.oracle.svm.core.jfr.JfrFrameType;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrStackTraceRepository;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.ExecutionSampleEvent;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/**
 * A concrete implementation of {@link SamplerStackTraceSerializer} designed for JFR stack trace
 * serialization.
 *
 * All static mutable state in this class is preallocated and reused by multiple threads. This is
 * safe because only one thread at a time may serialize stack traces.
 */
public final class SamplerJfrStackTraceSerializer implements SamplerStackTraceSerializer {
    private static final CodeInfoDecoder.FrameInfoCursor FRAME_INFO_CURSOR = new CodeInfoDecoder.FrameInfoCursor();
    private static final StackTraceVisitorData VISITOR_DATA = new StackTraceVisitorData();

    @Override
    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    public Pointer serializeStackTrace(Pointer rawStackTrace, Pointer bufferEnd, int sampleSize, int sampleHash,
                    boolean isTruncated, long sampleTick, long threadId, long threadState, int skipCount) {
        Pointer current = rawStackTrace;
        CIntPointer statusPtr = StackValue.get(CIntPointer.class);
        JfrStackTraceRepository.JfrStackTraceTableEntry entry = SubstrateJVM.getStackTraceRepo().getOrPutStackTrace(current, Word.unsigned(sampleSize), sampleHash, statusPtr);
        long stackTraceId = entry.isNull() ? 0 : entry.getId();

        int status = statusPtr.read();
        if (status == JfrStackTraceRepository.JfrStackTraceTableEntryStatus.INSERTED || status == JfrStackTraceRepository.JfrStackTraceTableEntryStatus.EXISTING_RAW) {
            /* Walk the IPs and serialize the stacktrace. */
            assert current.add(sampleSize).belowThan(bufferEnd);
            boolean serialized = serializeStackTrace(current, sampleSize, isTruncated, stackTraceId, skipCount);
            if (serialized) {
                SubstrateJVM.getStackTraceRepo().commitSerializedStackTrace(entry);
            }
        } else {
            /* Processing is not needed: skip the rest of the data. */
            assert status == JfrStackTraceRepository.JfrStackTraceTableEntryStatus.EXISTING_SERIALIZED || status == JfrStackTraceRepository.JfrStackTraceTableEntryStatus.INSERT_FAILED;
        }
        current = current.add(sampleSize);

        /*
         * Emit an event depending on the end marker of the raw stack trace. This needs to be done
         * here because the sampler can't emit the event directly.
         */
        long endMarker = current.readLong(0);
        if (endMarker == SamplerSampleWriter.EXECUTION_SAMPLE_END) {
            if (stackTraceId != 0) {
                ExecutionSampleEvent.writeExecutionSample(sampleTick, threadId, stackTraceId, threadState);
            } else {
                JfrThreadLocal.increaseMissedSamples();
            }
        } else {
            assert endMarker == SamplerSampleWriter.JFR_STACK_TRACE_END;
        }

        current = current.add(SamplerSampleWriter.END_MARKER_SIZE);
        return current;
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static boolean serializeStackTrace(Pointer rawStackTrace, int sampleSize, boolean isTruncated, long stackTraceId, int skipCount) {
        assert sampleSize % Long.BYTES == 0;

        JfrBuffer targetBuffer = SubstrateJVM.getStackTraceRepo().getCurrentBuffer();
        if (targetBuffer.isNull()) {
            return false;
        }

        /*
         * One IP may correspond to multiple Java-level stack frames. We need to precompute the
         * number of stack trace elements because the count can't be patched later on
         * (JfrNativeEventWriter.putInt() would not necessarily reserve enough bytes). We also
         * precompute if the stack trace was truncated.
         */
        visitRawStackTrace(rawStackTrace, sampleSize, Word.nullPointer(), skipCount);
        if (VISITOR_DATA.getUsedFrames() == 0) {
            return false;
        }

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, targetBuffer);
        JfrNativeEventWriter.putLong(data, stackTraceId);
        JfrNativeEventWriter.putBoolean(data, isTruncated || VISITOR_DATA.isTruncated());
        JfrNativeEventWriter.putInt(data, VISITOR_DATA.getUsedFrames());
        visitRawStackTrace(rawStackTrace, sampleSize, data, skipCount);
        boolean success = JfrNativeEventWriter.commit(data);

        /* Buffer can get replaced with a larger one. */
        SubstrateJVM.getStackTraceRepo().setCurrentBuffer(data.getJfrBuffer());
        return success;
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static void visitRawStackTrace(Pointer rawStackTrace, int sampleSize, JfrNativeEventWriterData data, int skipCount) {
        VISITOR_DATA.reset(skipCount);

        Pointer ipPtr = rawStackTrace;
        Pointer rawStackTraceEnd = rawStackTrace.add(sampleSize);
        while (ipPtr.belowThan(rawStackTraceEnd)) {
            long ip = ipPtr.readLong(0);
            visitFrame(data, ip);
            ipPtr = ipPtr.add(Long.BYTES);
        }
    }

    @Uninterruptible(reason = "Prevent JFR recording, epoch change, and that the GC frees the CodeInfo.")
    private static void visitFrame(JfrNativeEventWriterData data, long address) {
        CodePointer ip = Word.pointer(address);
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
        if (untetheredInfo.isNull()) {
            /* Unknown frame. Must not happen for AOT-compiled code. */
            VMError.shouldNotReachHere("Stack walk must walk only frames of known code.");
        }

        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo tetheredCodeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            visitFrame(data, tetheredCodeInfo, ip);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static void visitFrame(JfrNativeEventWriterData data, CodeInfo codeInfo, CodePointer ip) {
        FRAME_INFO_CURSOR.initialize(codeInfo, ip, false);
        while (FRAME_INFO_CURSOR.advance()) {
            if (VISITOR_DATA.shouldSkipFrame()) {
                VISITOR_DATA.incrementSkippedFrames();
                continue;
            } else if (VISITOR_DATA.shouldTruncate()) {
                VISITOR_DATA.setTruncated();
                break;
            }

            VISITOR_DATA.incrementUsedFrames();
            if (data.isNonNull()) {
                FrameInfoQueryResult frame = FRAME_INFO_CURSOR.get();
                serializeStackTraceElement(data, frame);
            }
        }
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static void serializeStackTraceElement(JfrNativeEventWriterData data, FrameInfoQueryResult stackTraceElement) {
        long methodId = SubstrateJVM.getMethodRepo().getMethodId(stackTraceElement.getSourceClass(), stackTraceElement.getSourceMethodName(),
                        stackTraceElement.getSourceMethodSignature(), stackTraceElement.getSourceMethodId(), stackTraceElement.getSourceMethodModifiers());
        JfrNativeEventWriter.putLong(data, methodId);
        JfrNativeEventWriter.putInt(data, stackTraceElement.getSourceLineNumber());
        JfrNativeEventWriter.putInt(data, stackTraceElement.getBci());
        JfrNativeEventWriter.putLong(data, JfrFrameType.FRAME_AOT_COMPILED.getId());
    }

    private static final class StackTraceVisitorData {
        private int framesToSkip;
        private int skippedFrames;
        private int usedFrames;
        private boolean truncated;

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void reset(int skipCount) {
            framesToSkip = skipCount;
            skippedFrames = 0;
            usedFrames = 0;
            truncated = false;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true) //
        public boolean shouldSkipFrame() {
            return skippedFrames < framesToSkip;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void incrementSkippedFrames() {
            skippedFrames++;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true) //
        public boolean shouldTruncate() {
            return usedFrames >= SubstrateJVM.getStackTraceRepo().getStackTraceDepth();
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setTruncated() {
            truncated = true;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public boolean isTruncated() {
            return truncated;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void incrementUsedFrames() {
            usedFrames++;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public int getUsedFrames() {
            return usedFrames;
        }
    }
}

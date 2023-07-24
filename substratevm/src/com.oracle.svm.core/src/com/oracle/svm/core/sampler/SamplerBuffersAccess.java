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

package com.oracle.svm.core.sampler;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoDecoder.FrameInfoCursor;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.jfr.JfrBuffer;
import com.oracle.svm.core.jfr.JfrFrameType;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrStackTraceRepository.JfrStackTraceTableEntry;
import com.oracle.svm.core.jfr.JfrStackTraceRepository.JfrStackTraceTableEntryStatus;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.ExecutionSampleEvent;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

public final class SamplerBuffersAccess {
    /** This value is used by multiple threads but only by a single thread at a time. */
    private static final FrameInfoCursor FRAME_INFO_CURSOR = new FrameInfoCursor();

    @Platforms(Platform.HOSTED_ONLY.class)
    private SamplerBuffersAccess() {
    }

    @Uninterruptible(reason = "Prevent JFR recording.")
    public static void processActiveBuffers() {
        assert VMOperation.isInProgressAtSafepoint();

        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            SamplerBuffer buffer = JfrThreadLocal.getSamplerBuffer(thread);
            if (buffer.isNonNull()) {
                serializeStackTraces(buffer);
                assert JfrThreadLocal.getSamplerBuffer(thread) == buffer;
            }
        }
    }

    /**
     * The raw instruction pointer stack traces are decoded to Java-level stack trace information,
     * which is then serialized into a buffer. This method may be called by different threads:
     * <ul>
     * <li>The JFR recorder thread processes full buffers periodically.</li>
     * <li>When the JFR epoch changes, all buffers that belong to the current epoch need to be
     * processed within the VM operation that changes the epoch.</li>
     * </ul>
     */
    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    public static void processFullBuffers(boolean useSafepointChecks) {
        while (true) {
            SamplerBuffer buffer = SubstrateJVM.getSamplerBufferPool().popFullBuffer();
            if (buffer.isNull()) {
                /* No more buffers. */
                break;
            }

            serializeStackTraces(buffer);
            SubstrateJVM.getSamplerBufferPool().releaseBuffer(buffer);

            /* Do a safepoint check if the caller requested one. */
            if (useSafepointChecks) {
                safepointCheck();
            }
        }

        SubstrateJVM.getSamplerBufferPool().adjustBufferCount();
    }

    @Uninterruptible(reason = "The callee explicitly does a safepoint check.", calleeMustBe = false)
    private static void safepointCheck() {
        safepointCheck0();
    }

    private static void safepointCheck0() {
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static void serializeStackTraces(SamplerBuffer rawStackTraceBuffer) {
        assert rawStackTraceBuffer.isNonNull();

        Pointer end = rawStackTraceBuffer.getPos();
        Pointer current = SamplerBufferAccess.getDataStart(rawStackTraceBuffer);
        while (current.belowThan(end)) {
            Pointer entryStart = current;
            assert entryStart.unsignedRemainder(Long.BYTES).equal(0);

            /* Sample hash. */
            int sampleHash = current.readInt(0);
            current = current.add(Integer.BYTES);

            /* Is truncated. */
            boolean isTruncated = current.readInt(0) == 1;
            current = current.add(Integer.BYTES);

            /* Sample size, excluding the header and the end marker. */
            int sampleSize = current.readInt(0);
            current = current.add(Integer.BYTES);

            /* Padding. */
            current = current.add(Integer.BYTES);

            /* Tick. */
            long sampleTick = current.readLong(0);
            current = current.add(Long.BYTES);

            /* Event thread. */
            long threadId = current.readLong(0);
            current = current.add(Long.BYTES);

            /* Thread state. */
            long threadState = current.readLong(0);
            current = current.add(Long.BYTES);

            assert current.subtract(entryStart).equal(SamplerSampleWriter.getHeaderSize());

            CIntPointer statusPtr = StackValue.get(CIntPointer.class);
            JfrStackTraceTableEntry entry = SubstrateJVM.getStackTraceRepo().getOrPutStackTrace(current, WordFactory.unsigned(sampleSize), sampleHash, statusPtr);
            long stackTraceId = entry.isNull() ? 0 : entry.getId();

            int status = statusPtr.read();
            if (status == JfrStackTraceTableEntryStatus.INSERTED || status == JfrStackTraceTableEntryStatus.EXISTING_RAW) {
                /* Walk the IPs and serialize the stacktrace. */
                assert current.add(sampleSize).belowThan(end);
                boolean serialized = serializeStackTrace(current, sampleSize, isTruncated, stackTraceId);
                if (serialized) {
                    SubstrateJVM.getStackTraceRepo().commitSerializedStackTrace(entry);
                }
            } else {
                /* Processing is not needed: skip the rest of the data. */
                assert status == JfrStackTraceTableEntryStatus.EXISTING_SERIALIZED || status == JfrStackTraceTableEntryStatus.INSERT_FAILED;
            }
            current = current.add(sampleSize);

            /*
             * Emit an event depending on the end marker of the raw stack trace. This needs to be
             * done here because the sampler can't emit the event directly.
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
        }

        SamplerBufferAccess.reinitialize(rawStackTraceBuffer);
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static boolean serializeStackTrace(Pointer rawStackTrace, int sampleSize, boolean isTruncated, long stackTraceId) {
        assert sampleSize % Long.BYTES == 0;

        JfrBuffer targetBuffer = SubstrateJVM.getStackTraceRepo().getCurrentBuffer();
        if (targetBuffer.isNull()) {
            return false;
        }

        /*
         * One IP may correspond to multiple Java-level stack frames. We need to precompute the
         * number of stack trace elements because the count can't be patched later on
         * (JfrNativeEventWriter.putInt() would not necessarily reserve enough bytes).
         */
        int numStackTraceElements = visitRawStackTrace(rawStackTrace, sampleSize, WordFactory.nullPointer());
        if (numStackTraceElements == 0) {
            return false;
        }

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, targetBuffer);
        JfrNativeEventWriter.putLong(data, stackTraceId);
        JfrNativeEventWriter.putBoolean(data, isTruncated);
        JfrNativeEventWriter.putInt(data, numStackTraceElements);
        visitRawStackTrace(rawStackTrace, sampleSize, data);
        boolean success = JfrNativeEventWriter.commit(data);

        /* Buffer can get replaced with a larger one. */
        SubstrateJVM.getStackTraceRepo().setCurrentBuffer(data.getJfrBuffer());
        return success;
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static int visitRawStackTrace(Pointer rawStackTrace, int sampleSize, JfrNativeEventWriterData data) {
        int numStackTraceElements = 0;
        Pointer rawStackTraceEnd = rawStackTrace.add(sampleSize);
        Pointer ipPtr = rawStackTrace;
        while (ipPtr.belowThan(rawStackTraceEnd)) {
            long ip = ipPtr.readLong(0);
            numStackTraceElements += visitFrame(data, ip);
            ipPtr = ipPtr.add(Long.BYTES);
        }
        return numStackTraceElements;
    }

    @Uninterruptible(reason = "Prevent JFR recording, epoch change, and that the GC frees the CodeInfo.")
    private static int visitFrame(JfrNativeEventWriterData data, long address) {
        CodePointer ip = WordFactory.pointer(address);
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
        if (untetheredInfo.isNull()) {
            /* Unknown frame. Must not happen for AOT-compiled code. */
            VMError.shouldNotReachHere("Stack walk must walk only frames of known code.");
        }

        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo tetheredCodeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            return visitFrame(data, tetheredCodeInfo, ip);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static int visitFrame(JfrNativeEventWriterData data, CodeInfo codeInfo, CodePointer ip) {
        int numStackTraceElements = 0;
        FRAME_INFO_CURSOR.initialize(codeInfo, ip, false);
        while (FRAME_INFO_CURSOR.advance()) {
            if (data.isNonNull()) {
                FrameInfoQueryResult frame = FRAME_INFO_CURSOR.get();
                serializeStackTraceElement(data, frame);
            }
            numStackTraceElements++;
        }
        return numStackTraceElements;
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static void serializeStackTraceElement(JfrNativeEventWriterData data, FrameInfoQueryResult stackTraceElement) {
        long methodId = SubstrateJVM.getMethodRepo().getMethodId(stackTraceElement.getSourceClass(), stackTraceElement.getSourceMethodName(), stackTraceElement.getMethodId());
        JfrNativeEventWriter.putLong(data, methodId);
        JfrNativeEventWriter.putInt(data, stackTraceElement.getSourceLineNumber());
        JfrNativeEventWriter.putInt(data, stackTraceElement.getBci());
        JfrNativeEventWriter.putLong(data, JfrFrameType.FRAME_AOT_COMPILED.getId());
    }
}

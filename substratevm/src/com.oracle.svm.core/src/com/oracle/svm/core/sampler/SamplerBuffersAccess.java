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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.BufferNodeAccess;
import com.oracle.svm.core.jfr.BufferNode;
import com.oracle.svm.core.jfr.JfrChunkWriter;

public final class SamplerBuffersAccess {

    @Platforms(Platform.HOSTED_ONLY.class)
    private SamplerBuffersAccess() {
    }

    /**
     * This is the ony place where the {@link SamplerBufferList} is iterated and {@link BufferNode}s
     * are freed. The {@link JfrChunkWriter#lock()} must be acquired when calling this method.
     */
    @Uninterruptible(reason = "Locking no transition.")
    public static void processActiveBuffers() {
        SamplerBufferList samplerBufferList = SamplerBufferPool.getSamplerBufferList();
        BufferNode node = samplerBufferList.getHead();
        BufferNode prev = WordFactory.nullPointer();

        while (node.isNonNull()) {
            BufferNode next = node.getNext();

            SamplerBuffer buffer = BufferNodeAccess.getSamplerBuffer(node);
            // Try to remove old nodes. If a node's buffer is null, it means it's no longer needed.
            if (buffer.isNull()) {
                samplerBufferList.removeNode(node, prev);
                BufferNodeAccess.free(node);
                node = next;
                continue;
            }
            assert SamplerBufferAccess.verify(buffer);
            // serialize active buffers
            SubstrateJVM.getStackTraceRepo().serializeStackTraces(buffer);

            prev = node;
            node = next;
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
    public static void processFullBuffers() {
        while (true) {
            SamplerBuffer buffer = SubstrateJVM.getSamplerBufferPool().popFullBuffer();
            if (buffer.isNull()) {
                /* No more buffers. */
                break;
            }

            SubstrateJVM.getStackTraceRepo().serializeStackTraces(buffer);
            SubstrateJVM.getSamplerBufferPool().releaseBuffer(buffer);
        }

        SubstrateJVM.getSamplerBufferPool().adjustBufferCount();
    }
<<<<<<< HEAD
=======

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
        FRAME_INFO_CURSOR.initialize(codeInfo, ip);
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
>>>>>>> 36915eee070d9433c26d7ec247b8cc08b98b89ae
}

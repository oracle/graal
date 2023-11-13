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
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

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
            serializeStackTraces(buffer);

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

            serializeStackTraces(buffer);
            SubstrateJVM.getSamplerBufferPool().releaseBuffer(buffer);
        }

        SubstrateJVM.getSamplerBufferPool().adjustBufferCount();
    }

    @Uninterruptible(reason = "Prevent JFR recording and epoch change.")
    private static void serializeStackTraces(SamplerBuffer rawStackTraceBuffer) {
        assert rawStackTraceBuffer.isNonNull();

        SubstrateJVM.getStackTraceRepo().lock();
        try {
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

                current = serializeStackTrace(current, end, sampleSize, sampleHash, isTruncated, sampleTick, threadId, threadState);
            }
            rawStackTraceBuffer.setSerializedPos(end);
        } finally {
            SubstrateJVM.getStackTraceRepo().unlock();
        }
//        SamplerBufferAccess.reinitialize(rawStackTraceBuffer);
    }

    @Uninterruptible(reason = "Wraps the call to the possibly interruptible serializer.", calleeMustBe = false)
    private static Pointer serializeStackTrace(Pointer rawStackTrace, Pointer bufferEnd, int sampleSize, int sampleHash,
                    boolean isTruncated, long sampleTick, long threadId, long threadState) {
        return SamplerStackTraceSerializer.singleton().serializeStackTrace(rawStackTrace, bufferEnd, sampleSize,
                        sampleHash, isTruncated, sampleTick, threadId, threadState);
    }
}

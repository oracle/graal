/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk11.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Manages the global JFR memory. A lot of the methods must be uninterruptible to ensure that we can
 * iterate and process the global JFR memory at a safepoint without having to worry about partial
 * modifications that were interrupted by the safepoint.
 */
public class JfrGlobalMemory {
    private static final int PROMOTION_RETRY_COUNT = 100;

    private long bufferCount;
    private long bufferSize;
    private JfrBuffers buffers;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrGlobalMemory() {
    }

    public void initialize(long globalBufferSize, long globalBufferCount) {
        this.bufferCount = globalBufferCount;
        this.bufferSize = globalBufferSize;

        // Allocate all buffers eagerly.
        buffers = UnmanagedMemory.calloc(SizeOf.unsigned(JfrBuffers.class).multiply(WordFactory.unsigned(bufferCount)));
        for (int i = 0; i < bufferCount; i++) {
            JfrBuffer buffer = JfrBufferAccess.allocate(WordFactory.unsigned(bufferSize));
            buffers.addressOf(i).write(buffer);
        }
    }

    public void teardown() {
        if (buffers.isNonNull()) {
            for (int i = 0; i < bufferCount; i++) {
                JfrBuffer buffer = buffers.addressOf(i).read();
                JfrBufferAccess.free(buffer);
            }
            UnmanagedMemory.free(buffers);
            buffers = WordFactory.nullPointer();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public JfrBuffers getBuffers() {
        assert buffers.isNonNull();
        return buffers;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getBufferCount() {
        return bufferCount;
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public boolean write(JfrBuffer threadLocalBuffer, UnsignedWord unflushedSize) {
        JfrBuffer promotionBuffer = acquireBufferWithRetry(unflushedSize, PROMOTION_RETRY_COUNT);
        if (promotionBuffer.isNull()) {
            return false;
        }
        boolean shouldSignal;
        JfrRecorderThread recorderThread = SubstrateJVM.getRecorderThread();
        try {
            // Copy all committed but not yet flushed memory to the promotion buffer.
            assert JfrBufferAccess.getAvailableSize(promotionBuffer).aboveOrEqual(unflushedSize);
            UnmanagedMemoryUtil.copy(threadLocalBuffer.getTop(), promotionBuffer.getPos(), unflushedSize);
            JfrBufferAccess.increasePos(promotionBuffer, unflushedSize);
            shouldSignal = recorderThread.shouldSignal(promotionBuffer);
        } finally {
            releasePromotionBuffer(promotionBuffer);
        }
        JfrBufferAccess.increaseTop(threadLocalBuffer, unflushedSize);
        // Notify the thread that writes the global memory to disk.
        if (shouldSignal) {
            recorderThread.signal();
        }
        return true;
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private JfrBuffer acquireBufferWithRetry(UnsignedWord size, int retryCount) {
        assert size.belowOrEqual(WordFactory.unsigned(bufferSize));
        for (int retry = 0; retry < retryCount; retry++) {
            for (int i = 0; i < bufferCount; i++) {
                JfrBuffer buffer = buffers.addressOf(i).read();
                if (JfrBufferAccess.getAvailableSize(buffer).aboveOrEqual(size) && JfrBufferAccess.acquire(buffer)) {
                    // Recheck the available size after acquiring the buffer.
                    if (JfrBufferAccess.getAvailableSize(buffer).aboveOrEqual(size)) {
                        return buffer;
                    }
                    JfrBufferAccess.release(buffer);
                }
            }
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private static void releasePromotionBuffer(JfrBuffer buffer) {
        assert JfrBufferAccess.isAcquired(buffer);
        JfrBufferAccess.release(buffer);
    }
}

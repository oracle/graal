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
package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.thread.VMOperation;

/**
 * Manages the global JFR buffers (see {@link JfrBufferType#GLOBAL_MEMORY}). The memory has a very
 * long lifetime, as it is allocated during JFR startup and released during JFR teardown.
 *
 * A lot of the methods must be uninterruptible to ensure that we can iterate and process the global
 * JFR memory at a safepoint without having to worry about partial modifications that were
 * interrupted by the safepoint.
 */
public class JfrGlobalMemory {
    private static final int PROMOTION_RETRY_COUNT = 100;

    private final JfrBufferList buffers;
    private UnsignedWord bufferSize;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrGlobalMemory() {
        this.buffers = new JfrBufferList();
    }

    public void initialize(UnsignedWord globalBufferSize, long globalBufferCount) {
        this.bufferSize = globalBufferSize;

        /* Allocate all buffers eagerly. */
        for (int i = 0; i < globalBufferCount; i++) {
            JfrBuffer buffer = JfrBufferAccess.allocate(bufferSize, JfrBufferType.GLOBAL_MEMORY);
            if (buffer.isNull()) {
                throw new OutOfMemoryError("Could not allocate JFR buffer.");
            }

            JfrBufferNode node = buffers.addNode(buffer);
            if (node.isNull()) {
                throw new OutOfMemoryError("Could not allocate JFR buffer node.");
            }
        }
    }

    public void clear() {
        assert VMOperation.isInProgressAtSafepoint();

        JfrBufferNode node = buffers.getHead();
        while (node.isNonNull()) {
            JfrBuffer buffer = JfrBufferNodeAccess.getBuffer(node);
            JfrBufferAccess.reinitialize(buffer);
            node = node.getNext();
        }
    }

    public void teardown() {
        freeBuffers();

        /* Free the nodes. */
        buffers.teardown();
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private void freeBuffers() {
        /* Free the buffers. */
        JfrBufferNode node = buffers.getHead();
        while (node.isNonNull()) {
            JfrBufferNodeAccess.lockNoTransition(node);
            try {
                JfrBuffer buffer = JfrBufferNodeAccess.getBuffer(node);
                JfrBufferAccess.free(buffer);
                node.setBuffer(WordFactory.nullPointer());
            } finally {
                JfrBufferNodeAccess.unlock(node);
            }
            node = node.getNext();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public JfrBufferList getBuffers() {
        return buffers;
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public boolean write(JfrBuffer buffer, boolean flushpoint) {
        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(buffer);
        return write(buffer, unflushedSize, flushpoint);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public boolean write(JfrBuffer buffer, UnsignedWord unflushedSize, boolean flushpoint) {
        if (unflushedSize.equal(0)) {
            return true;
        }

        JfrBufferNode promotionNode = tryAcquirePromotionBuffer(unflushedSize);
        if (promotionNode.isNull()) {
            return false;
        }

        boolean shouldSignal;
        try {
            /* Copy all committed but not yet flushed memory to the promotion buffer. */
            JfrBuffer promotionBuffer = JfrBufferNodeAccess.getBuffer(promotionNode);
            assert JfrBufferAccess.getAvailableSize(promotionBuffer).aboveOrEqual(unflushedSize);
            UnmanagedMemoryUtil.copy(JfrBufferAccess.getFlushedPos(buffer), promotionBuffer.getCommittedPos(), unflushedSize);
            JfrBufferAccess.increaseCommittedPos(promotionBuffer, unflushedSize);
            shouldSignal = SubstrateJVM.getRecorderThread().shouldSignal(promotionBuffer);
        } finally {
            JfrBufferNodeAccess.unlock(promotionNode);
        }

        JfrBufferAccess.increaseFlushedPos(buffer, unflushedSize);

        /*
         * Notify the thread that writes the global memory to disk. If we're flushing, the global
         * buffers are about to get persisted anyway.
         */
        if (shouldSignal && !flushpoint) {
            SubstrateJVM.getRecorderThread().signal();
        }
        return true;
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private JfrBufferNode tryAcquirePromotionBuffer(UnsignedWord size) {
        assert size.belowOrEqual(bufferSize);
        for (int retry = 0; retry < PROMOTION_RETRY_COUNT; retry++) {
            JfrBufferNode node = buffers.getHead();
            while (node.isNonNull()) {
                if (JfrBufferNodeAccess.tryLock(node)) {
                    JfrBuffer buffer = JfrBufferNodeAccess.getBuffer(node);
                    if (JfrBufferAccess.getAvailableSize(buffer).aboveOrEqual(size)) {
                        /* Recheck the available size after acquiring the buffer. */
                        if (JfrBufferAccess.getAvailableSize(buffer).aboveOrEqual(size)) {
                            return node;
                        }
                    }
                    JfrBufferNodeAccess.unlock(node);
                }
                node = node.getNext();
            }
        }
        return WordFactory.nullPointer();
    }
}

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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.management.SubstrateThreadMXBean;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.sampler.JfrExecutionSampler;
import com.oracle.svm.core.locks.VMMutex;

/**
 * Keeps track of {@link #availableBuffers available} and {@link #fullBuffers full} buffers. If
 * sampling is enabled, this pool maintains the desirable number of buffers in the system.
 */
public class SamplerBufferPool {
    private final VMMutex mutex;
    private final SamplerBufferStack availableBuffers;
    private final SamplerBufferStack fullBuffers;

    private int bufferCount;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SamplerBufferPool() {
        mutex = new VMMutex("SamplerBufferPool");
        availableBuffers = new SamplerBufferStack();
        fullBuffers = new SamplerBufferStack();
    }

    public void teardown() {
        clear(availableBuffers);
        /* There should not be any unprocessed buffers. */
        assert bufferCount == 0;
    }

    private void clear(SamplerBufferStack stack) {
        while (true) {
            SamplerBuffer buffer = stack.popBuffer();
            if (buffer.isNull()) {
                break;
            }
            free(buffer);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isLockedByCurrentThread() {
        return availableBuffers.isLockedByCurrentThread() || fullBuffers.isLockedByCurrentThread();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public SamplerBuffer acquireBuffer(boolean allowAllocation) {
        SamplerBuffer buffer = availableBuffers.popBuffer();
        if (buffer.isNull() && allowAllocation) {
            buffer = SubstrateJVM.getSamplerBufferPool().tryAllocateBuffer();
        }
        return buffer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void releaseBuffer(SamplerBuffer buffer) {
        SamplerBufferAccess.reinitialize(buffer);
        availableBuffers.pushBuffer(buffer);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void pushFullBuffer(SamplerBuffer buffer) {
        fullBuffers.pushBuffer(buffer);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public SamplerBuffer popFullBuffer() {
        return fullBuffers.popBuffer();
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public void adjustBufferCount() {
        mutex.lockNoTransition();
        try {
            int diff = diff();
            if (diff > 0) {
                for (int i = 0; i < diff; i++) {
                    if (!allocateAndPush()) {
                        break;
                    }
                }
            } else {
                for (int i = diff; i < 0; i++) {
                    if (!popAndFree()) {
                        break;
                    }
                }
            }
        } finally {
            mutex.unlock();
        }
    }

    public int getBufferCount() {
        /*
         * Buffer count can change at any time when a thread starts/exits, so querying the count is
         * racy.
         */
        return bufferCount;
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private SamplerBuffer tryAllocateBuffer() {
        mutex.lockNoTransition();
        try {
            return tryAllocateBuffer0();
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean allocateAndPush() {
        assert bufferCount >= 0;
        SamplerBuffer buffer = tryAllocateBuffer0();
        if (buffer.isNonNull()) {
            availableBuffers.pushBuffer(buffer);
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private SamplerBuffer tryAllocateBuffer0() {
        UnsignedWord headerSize = SamplerBufferAccess.getHeaderSize();
        UnsignedWord dataSize = WordFactory.unsigned(SubstrateJVM.getThreadLocal().getThreadLocalBufferSize());

        SamplerBuffer result = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(headerSize.add(dataSize));
        if (result.isNonNull()) {
            bufferCount++;
            result.setSize(dataSize);
            result.setNext(WordFactory.nullPointer());
            SamplerBufferAccess.reinitialize(result);
        }
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean popAndFree() {
        assert bufferCount > 0;
        SamplerBuffer buffer = availableBuffers.popBuffer();
        if (buffer.isNonNull()) {
            free(buffer);
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void free(SamplerBuffer buffer) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(buffer);
        bufferCount--;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private int diff() {
        if (JfrExecutionSampler.singleton().isSampling()) {
            /* Cache buffers for the sampler. */
            double buffersToCache = ImageSingletons.lookup(SubstrateThreadMXBean.class).getThreadCount() * 1.5 + 0.5;
            return ((int) buffersToCache) - bufferCount;
        }
        /* Don't cache any buffers. */
        return -bufferCount;
    }
}

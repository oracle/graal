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

import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.util.VMError;

/**
 * The pool that maintains the desirable number of buffers in the system by allocating/releasing
 * extra buffers.
 */
class SamplerBufferPool {

    private static final VMMutex mutex = new VMMutex("SamplerBufferPool");
    private static long bufferCount;

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", mayBeInlined = true)
    public static void releaseBufferAndAdjustCount(SamplerBuffer threadLocalBuffer) {
        adjustBufferCount0(threadLocalBuffer);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", mayBeInlined = true)
    public static void adjustBufferCount() {
        adjustBufferCount0(WordFactory.nullPointer());
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", mayBeInlined = true)
    private static void adjustBufferCount0(SamplerBuffer threadLocalBuffer) {
        mutex.lockNoTransition();
        try {
            releaseThreadLocalBuffer(threadLocalBuffer);
            long diff = diff();
            if (diff > 0) {
                for (int i = 0; i < diff; i++) {
                    if (!allocateAndPush()) {
                        break;
                    }
                }
            } else {
                for (long i = diff; i < 0; i++) {
                    if (!popAndFree()) {
                        break;
                    }
                }
            }
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", mayBeInlined = true)
    private static void releaseThreadLocalBuffer(SamplerBuffer buffer) {
        /*
         * buffer is null if the thread is not running yet, or we did not perform the stack walk for
         * this thread during the run.
         */
        if (buffer.isNonNull()) {
            if (SamplerBufferAccess.isEmpty(buffer)) {
                /* We can free it right away. */
                SamplerBufferAccess.free(buffer);
            } else {
                /* Put it in the stack with other unprocessed buffers. */
                buffer.setFreeable(true);
                SubstrateSigprofHandler.singleton().fullBuffers().pushBuffer(buffer);
            }
            VMError.guarantee(bufferCount > 0);
            bufferCount--;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean allocateAndPush() {
        VMError.guarantee(bufferCount >= 0);
        JfrThreadLocal jfrThreadLocal = (JfrThreadLocal) SubstrateJVM.getThreadLocal();
        SamplerBuffer buffer = SamplerBufferAccess.allocate(WordFactory.unsigned(jfrThreadLocal.getThreadLocalBufferSize()));
        if (buffer.isNonNull()) {
            SubstrateSigprofHandler.singleton().availableBuffers().pushBuffer(buffer);
            bufferCount++;
            return true;
        } else {
            return false;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean popAndFree() {
        VMError.guarantee(bufferCount > 0);
        SamplerBuffer buffer = SubstrateSigprofHandler.singleton().availableBuffers().popBuffer();
        if (buffer.isNonNull()) {
            SamplerBufferAccess.free(buffer);
            bufferCount--;
            return true;
        } else {
            return false;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long diff() {
        double diffD = SubstrateSigprofHandler.singleton().substrateThreadMXBean().getThreadCount() * 1.5 - bufferCount;
        return (long) (diffD + 0.5);
    }
}

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

public final class SamplerBuffersAccess {

    @Platforms(Platform.HOSTED_ONLY.class)
    private SamplerBuffersAccess() {
    }

    @Uninterruptible(reason = "Locking no transition.")
    public static void processActiveBuffers(boolean flushpoint) {
        SamplerBufferList samplerBufferList = SamplerBufferPool.getSamplerBufferList();
        BufferNode node = samplerBufferList.getHead();
        BufferNode prev = WordFactory.nullPointer();

        while (node.isNonNull()) {
            BufferNode next = node.getNext();
            boolean lockAcquired = BufferNodeAccess.tryLock(node);
            if (!lockAcquired) {
                assert flushpoint;
                // Must block because if nodes are skipped, then flushed events may be missing
                // stacktrace data
                BufferNodeAccess.lockNoTransition(node);
            }
            SamplerBuffer buffer = BufferNodeAccess.getSamplerBuffer(node);
            // Try to remove old nodes. If a node's buffer is null, it means it's no longer needed.
            if (buffer.isNull()) {
                samplerBufferList.removeNode(node, prev);
                BufferNodeAccess.free(node);
                node = next;
                continue;
            }
            assert SamplerBufferAccess.verify(buffer);
            try {
                // serialize active buffers
                SubstrateJVM.getStackTraceRepo().serializeStackTraces(buffer);
            } finally {
                BufferNodeAccess.unlock(node);
            }
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
}

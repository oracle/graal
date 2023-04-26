/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.thread.VMOperation;

import com.oracle.svm.core.Uninterruptible;

/**
 * Multiple instances of this data structure are used to keep track of the global and the various
 * thread-local buffers.
 */
public class JfrBufferList extends BufferList {

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrBufferList() {
    }

    /**
     * This is called when a recording is stopped. We cannot safely destroy Java buffers here
     * because their corresponding interruptible EventWriters may still be using them.
     */
    public void teardown() {
        assert VMOperation.isInProgressAtSafepoint();

        BufferNode node = head;
        while (node.isNonNull()) {
            /* If the buffer is still alive, then mark it as removed from the list. */
            JfrBuffer buffer = BufferNodeAccess.getJfrBuffer(node);
            if (buffer.isNonNull()) {
                assert JfrBufferAccess.isRetired(buffer);
                buffer.setNode(WordFactory.nullPointer());
            }

            BufferNode next = node.getNext();
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);
            node = next;
        }
        head = WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public BufferNode addNode(JfrBuffer buffer) {
        assert buffer.getBufferType() != null && buffer.getBufferType() != JfrBufferType.C_HEAP;
        return super.addNode(buffer);
    }
}

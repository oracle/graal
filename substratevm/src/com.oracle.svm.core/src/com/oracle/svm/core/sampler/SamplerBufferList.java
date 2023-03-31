/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.jfr.BufferNodeAccess;
import com.oracle.svm.core.jfr.BufferNode;
import com.oracle.svm.core.jfr.BufferList;

/**
 * Singly linked list that stores {@link SamplerBuffer}s. When entering a safepoint, it is
 * guaranteed that none of the blocked Java threads holds the list's lock.
 *
 * Nodes should only be removed from this list by
 * {@link SamplerBuffersAccess#processSamplerBuffers()} Nodes are marked for removal if their buffer
 * field is null. This means that the buffer has been put on the full buffer queue because it is
 * full or the owning thread has exited.
 *
 * The following invariants are crucial if the list is used for thread-local buffers:
 * <ul>
 * <li>Each thread shall only have one active node on the list at a time. An inactive node is one
 * that does not have a buffer.</li>
 * <li>Only threads executing at a safepoint or that hold the {@link JfrChunkWriter#lock()} may
 * iterate or remove nodes from the list.</li>
 * </ul>
 */
public class SamplerBufferList extends BufferList {

    @Platforms(Platform.HOSTED_ONLY.class)
    public SamplerBufferList() {
    }

    public void teardown() {
        assert VMOperation.isInProgressAtSafepoint();

        BufferNode node = head;
        while (node.isNonNull()) {
            // If the buffer is still alive, then mark it as removed from the list.
            SamplerBuffer buffer = BufferNodeAccess.getSamplerBuffer(node);
            // Buffer should have been removed and put on full list in stopRecording.
            assert buffer.isNull();
            BufferNode next = node.getNext();
            BufferNodeAccess.free(node);
            node = next;
        }
        head = WordFactory.nullPointer();
    }
}

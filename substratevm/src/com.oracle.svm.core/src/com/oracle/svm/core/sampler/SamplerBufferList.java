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
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.sampler.AbstractJfrExecutionSampler;

/**
 * Nodes should only be removed from this list by
 * {@link SamplerBuffersAccess#processActiveBuffers()}. Nodes are marked for removal if their buffer
 * field is null. This means that the buffer has been put on the full buffer queue because it is
 * full or the owning thread has exited.
 *
 * This class also contains some checks to ensure the BufferList lock won't be held while sampling
 * is enabled. Otherwise, deadlock can occur with a single thread due to recursive locking. This is
 * because the sampler code may add a new node to the {@link SamplerBufferList} when the thread's
 * current {@link SamplerBuffer} reaches capacity.
 */
public class SamplerBufferList extends BufferList {

    @Platforms(Platform.HOSTED_ONLY.class)
    public SamplerBufferList() {
    }

    /**
     * This is called after {@link SamplerBufferPool#teardown()}, so all buffers should already be
     * freed. All nodes in the list can be freed.
     */
    public void teardown() {
        assert VMOperation.isInProgressAtSafepoint();

        BufferNode node = head;
        while (node.isNonNull()) {
            assert BufferNodeAccess.getSamplerBuffer(node).isNull();
            BufferNode next = node.getNext();
            BufferNodeAccess.free(node);
            node = next;
        }
        head = WordFactory.nullPointer();
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public BufferNode getHead() {
        assert !AbstractJfrExecutionSampler.isExecutionSamplingAllowedInCurrentThread();
        return super.getHead();
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public BufferNode addNode(com.oracle.svm.core.jfr.Buffer buffer) {
        assert !AbstractJfrExecutionSampler.isExecutionSamplingAllowedInCurrentThread();
        return super.addNode(buffer);
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public void removeNode(BufferNode node, BufferNode prev) {
        assert !AbstractJfrExecutionSampler.isExecutionSamplingAllowedInCurrentThread();
        super.removeNode(node, prev);
    }
}

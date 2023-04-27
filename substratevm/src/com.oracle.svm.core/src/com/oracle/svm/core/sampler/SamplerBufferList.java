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

/**
 * Nodes should only be removed from this list by
 * {@link SamplerBuffersAccess#processActiveBuffers()}. Nodes are marked for removal if their buffer
 * field is null. This means that the buffer has been put on the full buffer queue because it is
 * full or the owning thread has exited.
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

    /**
     * This method is necessary because allocation cannot be done on code paths used by the
     * SIGPROF-based execution sampler. The caller of this node must ensure that
     * {@link SamplerBuffer} and {@link BufferNode} already have references to each other in both
     * directions. This method simply adds the node to the active nodes list.
     */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public BufferNode addNode(BufferNode node) {
        assert node.isNonNull();
        assert node.getBuffer().isNonNull();
        lockNoTransition();
        try {
            node.setNext(head);
            head = node;
            return node;
        } finally {
            unlock();
        }
    }
}

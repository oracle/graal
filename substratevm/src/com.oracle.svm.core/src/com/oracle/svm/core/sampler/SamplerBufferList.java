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

package com.oracle.svm.core.sampler;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.internal.misc.Unsafe;

/** Nodes should only be removed from this list by {@link com.oracle.svm.core.sampler.SamplerBuffersAccess#processSamplerBuffers()}
 * Nodes are marked for removal if their buffer field is null. This means that the buffer has been put on the full
 * buffer queue because it is full or the owning thread has exited.*/
public class SamplerBufferList {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(SamplerBufferList.class, "lock");

    @SuppressWarnings("unused") private volatile int lock;
    private SamplerBufferNode head;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SamplerBufferList() {
    }
    public void teardown() {
        com.oracle.svm.core.util.VMError.guarantee( VMOperation.isInProgressAtSafepoint());

        SamplerBufferNode node = head;
        while (node.isNonNull()) {
            /* If the buffer is still alive, then mark it as removed from the list. */
            SamplerBuffer buffer = SamplerBufferNodeAccess.getBuffer(node);
            if (buffer.isNonNull()) {
                buffer.setNode(WordFactory.nullPointer());
            }

            SamplerBufferNode next = node.getNext();
            SamplerBufferNodeAccess.free(node);
            node = next;
        }
        head = WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public SamplerBufferNode getHead() {
        lockNoTransition();
        try {
            return head;
        } finally {
            unlock();
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public SamplerBufferNode addNode(SamplerBuffer buffer) {
        VMError.guarantee( buffer.isNonNull());

        SamplerBufferNode node = SamplerBufferNodeAccess.allocate(buffer);
        if (node.isNull()) {
            return WordFactory.nullPointer();
        }

        lockNoTransition();
        try {
            VMError.guarantee(buffer.getNode().isNull());
            buffer.setNode(node);

            node.setNext(head);
            head = node;
            return node;
        } finally {
            unlock();
        }
    }

    /**
     * Removes a node from the list. The buffer that is referenced by the node must have already
     * been freed by the caller.
     */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public void removeNode(SamplerBufferNode node, SamplerBufferNode prev) {
        com.oracle.svm.core.util.VMError.guarantee( head.isNonNull());

        lockNoTransition();
        try {
            VMError.guarantee(SamplerBufferNodeAccess.getBuffer(node).isNull());

            SamplerBufferNode next = node.getNext();
            if (node == head) {
                VMError.guarantee( prev.isNull());
                head = next;
            } else if (prev.isNonNull()) {
                VMError.guarantee( prev.getNext() == node);
                prev.setNext(next);
            } else {
                /* We are removing an old head (other threads added nodes in the meanwhile). */
                SamplerBufferNode p = findPrev(node);
                VMError.guarantee( p.isNonNull() && p.getNext() == node);
                p.setNext(next);
            }
        } finally {
            unlock();
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private SamplerBufferNode findPrev(SamplerBufferNode node) {
        SamplerBufferNode cur = head;
        SamplerBufferNode prev = WordFactory.nullPointer();
        while (cur.isNonNull()) {
            if (cur == node) {
                return prev;
            }
            prev = cur;
            cur = cur.getNext();
        }
        throw VMError.shouldNotReachHere("SamplerBufferNode not found in SamplerBufferList.");
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    private void lockNoTransition() {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    private void unlock() {
        JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
    }
}

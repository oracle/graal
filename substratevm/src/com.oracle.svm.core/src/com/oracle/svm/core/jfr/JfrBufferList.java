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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;

import jdk.internal.misc.Unsafe;

/**
 * Singly linked list that stores {@link JfrBuffer}s. Multiple instances of this data structure are
 * used to keep track of the global and the various thread-local buffers. When entering a safepoint,
 * it is guaranteed that none of the blocked Java threads holds the list's lock.
 *
 * The following invariants are crucial if the list is used for thread-local buffers:
 * <ul>
 * <li>Each thread shall only add one node to the list.</li>
 * <li>Only threads that hold the {@link JfrChunkWriter#lock()} may iterate or remove nodes from the
 * list.</li>
 * </ul>
 */
public class JfrBufferList {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(JfrBufferList.class, "lock");

    @SuppressWarnings("unused") private volatile int lock;
    private JfrBufferNode head;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrBufferList() {
    }

    /**
     * This method is called after all the threads already stopped recording. So, the
     * {@link JfrBuffer}s were already flushed and freed, but there may still be nodes in the list.
     * This node data needs to be freed.
     */
    public void teardown() {
        assert VMOperation.isInProgressAtSafepoint();

        JfrBufferNode node = head;
        while (node.isNonNull()) {
            assert node.getBuffer().isNull();

            JfrBufferNode next = node.getNext();
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);
            node = next;
        }
    }

    @Uninterruptible(reason = "Locking with no transition.")
    public JfrBufferNode getHead() {
        lock();
        try {
            return head;
        } finally {
            unlock();
        }
    }

    /**
     * Must be uninterruptible because if this list is acquired and we safepoint for an epoch change
     * in this method, the thread doing the epoch change will be blocked accessing the list.
     */
    @Uninterruptible(reason = "Locking with no transition. List must not be acquired entering epoch change.")
    public JfrBufferNode addNode(JfrBuffer buffer) {
        assert buffer.isNonNull();
        assert buffer.getBufferType() != null && buffer.getBufferType() != JfrBufferType.C_HEAP;

        JfrBufferNode node = JfrBufferNodeAccess.allocate(buffer);
        if (node.isNull()) {
            return WordFactory.nullPointer();
        }

        assert buffer.getNode().isNull();
        buffer.setNode(node);

        lock();
        try {
            node.setNext(head);
            head = node;
            return node;
        } finally {
            unlock();
        }
    }

    /**
     * Removes a node from the list. The buffer contained in the nodes must have already been freed
     * by the caller.
     */
    @Uninterruptible(reason = "Should not be interrupted while flushing.")
    public void removeNode(JfrBufferNode node, JfrBufferNode prev) {
        assert head.isNonNull();
        assert node.getBuffer().isNull();

        lock();
        try {
            JfrBufferNode next = node.getNext();
            if (node == head) {
                assert prev.isNull();
                head = next;
            } else {
                assert prev.isNonNull();
                assert prev.getNext() == node;
                prev.setNext(next);
            }
        } finally {
            unlock();
        }
    }

    @Uninterruptible(reason = "Whole critical section must be uninterruptible because we are locking without transition.", callerMustBe = true)
    private void lock() {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
    }

    @Uninterruptible(reason = "Whole critical section must be uninterruptible because we are locking without transition.", callerMustBe = true)
    private void unlock() {
        JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
    }
}

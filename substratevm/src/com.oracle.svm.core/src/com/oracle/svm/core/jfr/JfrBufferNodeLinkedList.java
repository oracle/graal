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

import com.oracle.svm.core.Uninterruptible;
import jdk.internal.misc.Unsafe;
import org.graalvm.word.WordFactory;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;

import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;

/**
 * {@link JfrBufferNodeLinkedList} is a singly linked list used to store thread local JFR buffers.
 * Threads shall only add one node to the list. Only the thread performing a flush or epoch change
 * shall iterate this list and is allowed to remove nodes. There is a list-level lock that is
 * acquired when adding nodes, and when beginning iteration at the head. Threads may access their
 * own nodes at any time up until they set the alive flag to false
 * {@link JfrBufferNodeAccess#setRetired(JfrBufferNode)}. When entering a safepoint, the list lock
 * must not be held by one of the blocked Java threads.
 */
public class JfrBufferNodeLinkedList {

    private static final long LOCK_OFFSET = Unsafe.getUnsafe().objectFieldOffset(JfrBufferNodeLinkedList.class, "lock");

    @SuppressWarnings("unused")
    private volatile int lock;
    private volatile JfrBufferNode head;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static JfrBufferNode createNode(JfrBuffer buffer, IsolateThread thread) {
        JfrBufferNode node = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(JfrBufferNode.class));
        if (node.isNonNull()) {
            JfrBufferNodeAccess.setAlive(node);
            node.setValue(buffer);
            node.setThread(thread);
            node.setNext(WordFactory.nullPointer());
        }
        return node;
    }

    public JfrBufferNodeLinkedList() {
    }

    public void teardown() {
        assert VMOperation.isInProgressAtSafepoint();
        JfrBufferNode node = head;
        while (node.isNonNull()) {
            JfrBufferNode next = node.getNext();
            JfrBufferAccess.free(node.getValue());
            /*
             * Once JfrBufferNode. JfrBufferNodeAccess.setRetired(node) is called, another thread
             * may free the node at any time. In this case it shouldn't matter because the recording
             * has ended and this is called at a safepoint.
             */
            JfrBufferNodeAccess.setRetired(node);
            removeNode(node, WordFactory.nullPointer());
            node = next;
        }
    }

    @Uninterruptible(reason = "Locking with no transition.")
    public JfrBufferNode getHead() {
        acquireList();
        try {
            return head;
        } finally {
            releaseList();
        }
    }

    /**
     * Removes a node from the linked list. The buffer contained in the nodes must have already been
     * freed by the caller.
     */
    @Uninterruptible(reason = "Should not be interrupted while flushing.")
    public void removeNode(JfrBufferNode node, JfrBufferNode prev) {
        assert head.isNonNull();
        assert !node.getAlive();

        JfrBufferNode next = node.getNext(); // next can never be null

        if (node == head) {
            assert prev.isNull();
            head = next; // head could now be null if there was only one node in the list
        } else {
            assert prev.isNonNull();
            assert prev.getNext() == node;
            prev.setNext(next);
        }

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);
    }

    /**
     * Must be uninterruptible because if this list is acquired and we safepoint for an epoch change
     * in this method, the thread doing the epoch change will be blocked accessing the list.
     */
    @Uninterruptible(reason = "Locking with no transition. List must not be acquired entering epoch change.")
    public JfrBufferNode addNode(JfrBuffer buffer, IsolateThread thread) {
        assert buffer.isNonNull();
        JfrBufferNode newNode = createNode(buffer, thread);
        if (newNode.isNull()) {
            return WordFactory.nullPointer();
        }
        acquireList();
        try {
            // Old head could be null
            JfrBufferNode oldHead = head;
            newNode.setNext(oldHead);
            head = newNode;
            return newNode;
        } finally {
            releaseList();
        }
    }

    @Uninterruptible(reason = "Locking with no transition and list must not be acquired entering epoch change.", callerMustBe = true)
    private void acquireList() {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
    }

    @Uninterruptible(reason = "Locking with no transition and list must not be acquired entering epoch change.", callerMustBe = true)
    private void releaseList() {
        JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
    }
}

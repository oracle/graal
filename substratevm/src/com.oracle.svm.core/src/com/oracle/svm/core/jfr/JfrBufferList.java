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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

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

    public void teardown() {
        assert VMOperation.isInProgressAtSafepoint();

        JfrBufferNode node = head;
        while (node.isNonNull()) {
            /* If the buffer is still alive, then mark it as removed from the list. */
            JfrBuffer buffer = JfrBufferNodeAccess.getBuffer(node);
            if (buffer.isNonNull()) {
                assert JfrBufferAccess.isRetired(buffer);
                buffer.setNode(WordFactory.nullPointer());
            }

            JfrBufferNode next = node.getNext();
            JfrBufferNodeAccess.free(node);
            node = next;
        }
        head = WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public JfrBufferNode getHead() {
        lockNoTransition();
        try {
            return head;
        } finally {
            unlock();
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public JfrBufferNode addNode(JfrBuffer buffer) {
        assert buffer.isNonNull();
        assert buffer.getBufferType() != null && buffer.getBufferType() != JfrBufferType.C_HEAP;

        JfrBufferNode node = JfrBufferNodeAccess.allocate(buffer);
        if (node.isNull()) {
            return WordFactory.nullPointer();
        }

        lockNoTransition();
        try {
            assert buffer.getNode().isNull();
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
    public void removeNode(JfrBufferNode node, JfrBufferNode prev) {
        assert head.isNonNull();

        lockNoTransition();
        try {
            assert JfrBufferNodeAccess.getBuffer(node).isNull();

            JfrBufferNode next = node.getNext();
            if (node == head) {
                assert prev.isNull();
                head = next;
            } else if (prev.isNonNull()) {
                assert prev.getNext() == node;
                prev.setNext(next);
            } else {
                /* We are removing an old head (other threads added nodes in the meanwhile). */
                JfrBufferNode p = findPrev(node);
                assert p.isNonNull() && p.getNext() == node;
                p.setNext(next);
            }
        } finally {
            unlock();
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private JfrBufferNode findPrev(JfrBufferNode node) {
        JfrBufferNode cur = head;
        JfrBufferNode prev = WordFactory.nullPointer();
        while (cur.isNonNull()) {
            if (cur == node) {
                return prev;
            }
            prev = cur;
            cur = cur.getNext();
        }
        throw VMError.shouldNotReachHere("JfrBufferNode not found in JfrBufferList.");
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

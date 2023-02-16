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
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.IsolateThread;
import com.oracle.svm.core.util.VMError;
import org.graalvm.word.PointerBase;
import com.oracle.svm.core.thread.JavaSpinLockUtils;

/**
 * JfrBufferNodeLinkedList is a singly linked list used to store thread local JFR buffers. Threads
 * shall only add one node to the list. Only the thread performing a flush or epoch change shall
 * iterate this list and is allowed to remove nodes. There is a list-level lock that is acquired
 * when adding nodes, and when beginning iteration at the head. Threads may access their own nodes
 * at any time up until they call JfrBufferNode.setAlive(false). The list lock must not be held at a
 * safepoint.
 */
public class JfrBufferNodeLinkedList {
    @RawStructure
    public interface JfrBufferNode extends PointerBase {
        @RawField
        JfrBufferNode getNext();

        @RawField
        void setNext(JfrBufferNode value);

        @RawField
        JfrBuffer getValue();

        @RawField
        void setValue(JfrBuffer value);

        @RawField
        IsolateThread getThread();

        @RawField
        void setThread(IsolateThread thread);

        @RawField
        boolean getAlive();

        @RawField
        void setAlive(boolean alive);
    }

    private static final long LOCK_OFFSET;

    static {
        try {
            LOCK_OFFSET = Unsafe.getUnsafe().objectFieldOffset(JfrBufferNodeLinkedList.class.getDeclaredField("lock"));
        } catch (Throwable ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
    private volatile int lock;
    private volatile JfrBufferNode head;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean isHead(JfrBufferNode node) {
        return node == head || head.isNull();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setHead(JfrBufferNode node) {
        head = node;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static JfrBufferNode createNode(JfrBuffer buffer, IsolateThread thread) {
        if (buffer.isNull()) {
            return WordFactory.nullPointer();
        }
        JfrBufferNode node = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(JfrBufferNode.class));
        if (node.isNonNull()) {
            node.setAlive(true);
            node.setValue(buffer);
            node.setThread(thread);
            node.setNext(WordFactory.nullPointer());
        }
        return node;
    }

    public JfrBufferNodeLinkedList() {
    }

    @Uninterruptible(reason = "Locking with no transition.")
    public void teardown() {
        JfrBufferNode node = getAndLockHead();
        while (node.isNonNull()) {
            JfrBufferNode next = node.getNext();
            removeNode(node, WordFactory.nullPointer());
            node = next;
        }
        releaseList();
    }

    @Uninterruptible(reason = "Locking with no transition.", callerMustBe = true)
    public JfrBufferNode getAndLockHead() {
        acquireList();
        return head;
    }

    @Uninterruptible(reason = "Should not be interrupted while flushing.")
    public boolean removeNode(JfrBufferNode node, JfrBufferNode prev) {
        JfrBufferNode next = node.getNext(); // next can never be null

        if (isHead(node)) {
            assert prev.isNull();
            setHead(next); // head could now be null if there was only one node in the list
        } else {
            assert prev.isNonNull();
            prev.setNext(next);
        }

        assert node.getValue().isNonNull();
        JfrBufferAccess.free(node.getValue());
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);
        return true;
    }

    /**
     * Must be uninterruptible because if this list is acquired and we safepoint for an epoch change
     * in this method, the thread doing the epoch change will be blocked accessing the list.
     */
    @Uninterruptible(reason = "Locking with no transition. List must not be acquired entering epoch change.")
    public JfrBufferNode addNode(JfrBuffer buffer, IsolateThread thread) {
        JfrBufferNode newNode = createNode(buffer, thread);
        acquireList();
        try {
            // Old head could be null
            JfrBufferNode oldHead = head;
            newNode.setNext(oldHead);
            setHead(newNode);
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
    public void releaseList() {
        JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean isLocked() {
        return lock == 1;
    }
}

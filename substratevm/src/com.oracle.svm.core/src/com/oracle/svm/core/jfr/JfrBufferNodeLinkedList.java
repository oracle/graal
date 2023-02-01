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
import org.graalvm.word.WordFactory;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.RawFieldOffset;
import org.graalvm.nativeimage.c.struct.SizeOf;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import org.graalvm.nativeimage.IsolateThread;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.thread.VMOperation;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.compiler.nodes.NamedLocationIdentity;

public class JfrBufferNodeLinkedList {
    @RawStructure
    public interface JfrBufferNode extends UninterruptibleEntry {
        @RawField
        JfrBuffer getValue();

        @RawField
        void setValue(JfrBuffer value);

        @RawField
        IsolateThread getThread();

        @RawField
        void setThread(IsolateThread thread);

        @RawField
        IsolateThread getLockOwner();

        @RawField
        void setLockOwner(IsolateThread owner);

        @RawField
        boolean getAlive();

        @RawField
        void setAlive(boolean alive);

        @RawField
        int getAcquired();

        @RawField
        void setAcquired(int value);

        @RawFieldOffset
        static int offsetOfAcquired() {
            throw VMError.unimplemented(); // replaced
        }

        @RawField
        <T extends UninterruptibleEntry> T getPrev();

        @RawField
        void setPrev(UninterruptibleEntry value);
    }

    private static final int ACQUIRED = 1;
    private static final int NOT_ACQUIRED = 0;
    private volatile JfrBufferNode head;
    private JfrBufferNode tail; // this never gets deleted until torn down
    private static final int ACQUIRE_RETRY_COUNT = 10000;

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public JfrBufferNode getAndLockTail() {
        VMError.guarantee(tail.isNonNull(), "Tail Node should never be null");
        if (tryAcquire(tail)) {
            return tail;
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static boolean tryAcquire(JfrBufferNode node) {
        for (int retry = 0; retry < ACQUIRE_RETRY_COUNT; retry++) {
            if (node.isNull() || acquire(node)) {
                return true;
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean isTail(JfrBufferNode node) {
        return node == tail;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean isHead(JfrBufferNode node) {
        return node == head;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void setHead(JfrBufferNode node) {
        VMError.guarantee(isAcquired(head) || com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint(), "Cannot set JfrBufferNodeLinkedList head before acquiring.");
        head = node;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static org.graalvm.word.UnsignedWord getHeaderSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(JfrBufferNode.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static JfrBufferNode createNode(JfrBuffer buffer, IsolateThread thread) {
        JfrBufferNode node = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(getHeaderSize());
        node.setAlive(true);
        node.setValue(buffer);
        node.setThread(thread);
        node.setPrev(WordFactory.nullPointer());
        node.setNext(WordFactory.nullPointer());
        node.setAcquired(0);
        return node;
    }

    public JfrBufferNodeLinkedList() {
        tail = createNode(WordFactory.nullPointer(), WordFactory.nullPointer());
        head = tail;
    }

    public void teardown() {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(tail);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean lockSection(JfrBufferNode target) {
        VMError.guarantee(target.isNonNull(), "Attempted to lock buffer node that is null.");
        // acquire target and adjacent nodes
        if (acquire(target)) {
            if (target.getPrev().isNull() || acquire(target.getPrev())) {
                if (target.getNext().isNull() || acquire(target.getNext())) {
                    return true;
                }
                // couldn't acquire all three locks. So release all of them.
                if (target.getPrev().isNonNull()) {
                    release(target.getPrev());
                }
            }
            release(target);
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean lockAdjacent(JfrBufferNode target) {
        VMError.guarantee(target.isNonNull(), "Attempted to lock buffer node that is null.");
        VMError.guarantee(isAcquired(target), "Target node should be acquired when locking adjacent nodes.");
        // adjacent nodes
        if (target.getPrev().isNull() || acquire(target.getPrev())) {
            if (target.getNext().isNull() || acquire(target.getNext())) {
                return true;
            }
            // couldn't acquire all three locks. So release all of them.
            if (target.getPrev().isNonNull()) {
                release(target.getPrev());
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean removeNode(JfrBufferNode node, boolean flushing) {
        JfrBufferNode next = node.getNext(); // next can never be null
        JfrBufferNode prev = node.getPrev();
        // tail must always exist until torn down
        VMError.guarantee(next.isNonNull(), "Attmpted to remove tail node from JfrBufferNodeLinkedList");

        // make one attempt to get all the locks. If flushing, must acquire adjacent nodes
        if (flushing && !VMOperation.isInProgressAtSafepoint() && !lockAdjacent(node)) {
            return false;
        }
        VMError.guarantee((isAcquired(node) && isAcquired(next)) || VMOperation.isInProgressAtSafepoint(), "Cannot remove JfrBufferNodeLinkedList node outside safepoint without acquiring section.");
        if (isHead(node)) {
            VMError.guarantee(prev.isNull(), "Head should be first node in JfrBufferNodeLinkedList.");
            setHead(next); // head could now be tail if there was only one node in the list
            head.setPrev(WordFactory.nullPointer());
        } else {
            VMError.guarantee(isAcquired(prev) || VMOperation.isInProgressAtSafepoint(), "Cannot remove JfrBufferNodeLinkedList node outside safepoint without acquiring prev.");
            prev.setNext(next);
            next.setPrev(prev);
        }

        VMError.guarantee(node.getValue().isNonNull(), "JFR buffer should always exist until removal of respective JfrBufferNodeLinkedList node.");
        JfrBufferAccess.free(node.getValue());
        release(node);
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);

        release(next);
        if (prev.isNonNull()) {
            release(prev);
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void addNode(JfrBufferNode node) {
        JfrBufferNode oldHead;
        // spin until we acquire
        while (true) {
            // update value from main memory
            oldHead = head;
            if (!acquire(oldHead)) {
                continue;
            }
            if (!isHead(oldHead)) {
                // head may have been written sometime between setting and acquiring oldHead
                release(oldHead);
                continue;
            }
            break;
        }
        VMError.guarantee(oldHead.getPrev().isNull(), "Adding node: Head should be first node in JfrBufferNodeLinkedList.");
        node.setPrev(WordFactory.nullPointer());
        node.setNext(oldHead);
        oldHead.setPrev(node);

        setHead(node);
        release(oldHead);
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static boolean acquire(JfrBufferNode node) {
        if (VMOperation.isInProgressAtSafepoint()) {
            VMError.guarantee(!isAcquired(node), "JfrBufferNodes should not be in acquired state when entering safepoints.");
            return true;
        }
        boolean success = ((org.graalvm.word.Pointer) node).logicCompareAndSwapInt(JfrBufferNode.offsetOfAcquired(), NOT_ACQUIRED, ACQUIRED,
                        NamedLocationIdentity.OFF_HEAP_LOCATION);
        if (success) {
            node.setLockOwner(CurrentIsolate.getCurrentThread());
        }
        return success;
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static void release(JfrBufferNode node) {
        if (VMOperation.isInProgressAtSafepoint()) {
            return;
        }
        VMError.guarantee(isAcquired(node), "JfrBufferNodes should only be released when in acquired state.");
        node.setAcquired(NOT_ACQUIRED);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isAcquired(JfrBufferNode node) {
        return node.getAcquired() == ACQUIRED && node.getLockOwner() == CurrentIsolate.getCurrentThread();
    }
}

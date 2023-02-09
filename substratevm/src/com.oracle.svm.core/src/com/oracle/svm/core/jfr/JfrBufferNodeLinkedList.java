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
import com.oracle.svm.core.thread.SpinLockUtils;

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

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean isHead(JfrBufferNode node) {
        return node == head;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void setHead(JfrBufferNode node) {
        head = node;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
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

    public void teardown() {
        // TODO: maybe iterate list freeing nodes, just in case.
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public JfrBufferNode getAndLockHead() {
        acquireList();
        return head;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean removeNode(JfrBufferNode node, JfrBufferNode prev) {
        JfrBufferNode next = node.getNext(); // next can never be null

        if (isHead(node)) {
            VMError.guarantee(prev.isNull(), "If head, prev should be null ");
            setHead(next); // head could now be tail if there was only one node in the list
        } else {
            VMError.guarantee(prev.isNonNull(), "If not head, prev should be non-null ");
            prev.setNext(next);
        }

        VMError.guarantee(node.getValue().isNonNull(), "JFR buffer should always exist until removal of respective JfrBufferNodeLinkedList node.");
        JfrBufferAccess.free(node.getValue());
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
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

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void acquireList() {
        SpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void releaseList() {
        SpinLockUtils.unlock(this, LOCK_OFFSET);
    }
}

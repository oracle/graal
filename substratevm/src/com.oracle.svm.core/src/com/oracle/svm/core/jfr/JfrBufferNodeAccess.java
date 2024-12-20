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

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.thread.NativeSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;

/**
 * Used to access the raw memory of a {@link JfrBufferNode}.
 */
public final class JfrBufferNodeAccess {
    private JfrBufferNodeAccess() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JfrBufferNode allocate(JfrBuffer buffer) {
        JfrBufferNode node = NullableNativeMemory.malloc(SizeOf.unsigned(JfrBufferNode.class), NmtCategory.JFR);
        if (node.isNonNull()) {
            node.setBuffer(buffer);
            node.setNext(Word.nullPointer());
            node.setLockOwner(Word.nullPointer());
            NativeSpinLockUtils.initialize(ptrToLock(node));
        }
        return node;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void free(JfrBufferNode node) {
        NullableNativeMemory.free(node);
    }

    /** Should be used instead of {@link JfrBufferNode#getBuffer}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JfrBuffer getBuffer(JfrBufferNode node) {
        assert isLockedByCurrentThread(node) || VMOperation.isInProgressAtSafepoint();
        return node.getBuffer();
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    public static boolean tryLock(JfrBufferNode node) {
        assert node.isNonNull();
        if (NativeSpinLockUtils.tryLock(ptrToLock(node))) {
            setLockOwner(node);
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    public static void lockNoTransition(JfrBufferNode node) {
        assert node.isNonNull();
        NativeSpinLockUtils.lockNoTransition(ptrToLock(node));
        setLockOwner(node);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    public static void unlock(JfrBufferNode node) {
        assert node.isNonNull();
        assert isLockedByCurrentThread(node);
        node.setLockOwner(Word.nullPointer());
        NativeSpinLockUtils.unlock(ptrToLock(node));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isLockedByCurrentThread(JfrBufferNode node) {
        assert CurrentIsolate.getCurrentThread().isNonNull();
        return node.isNonNull() && node.getLockOwner() == CurrentIsolate.getCurrentThread();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void setLockOwner(JfrBufferNode node) {
        assert node.getLockOwner().isNull();
        node.setLockOwner(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CIntPointer ptrToLock(JfrBufferNode node) {
        return (CIntPointer) ((Pointer) node).add(JfrBufferNode.offsetOfLock());
    }
}

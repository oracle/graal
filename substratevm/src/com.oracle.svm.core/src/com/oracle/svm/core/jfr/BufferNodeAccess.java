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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.thread.NativeSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.sampler.SamplerBuffer;

public final class BufferNodeAccess {
    private BufferNodeAccess() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static BufferNode allocate(Buffer buffer) {
        BufferNode node = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(BufferNode.class));
        if (node.isNonNull()) {
            node.setBuffer(buffer);
            node.setNext(WordFactory.nullPointer());
            node.setLockOwner(WordFactory.nullPointer());
            NativeSpinLockUtils.initialize(ptrToLock(node));
        }
        return node;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void free(BufferNode node) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(node);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    public static boolean tryLock(BufferNode node) {
        assert node.isNonNull();
        if (NativeSpinLockUtils.tryLock(ptrToLock(node))) {
            setLockOwner(node);
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    public static void lockNoTransition(BufferNode node) {
        assert node.isNonNull();
        NativeSpinLockUtils.lockNoTransition(ptrToLock(node));
        setLockOwner(node);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    public static void unlock(BufferNode node) {
        assert node.isNonNull();
        assert isLockedByCurrentThread(node);
        node.setLockOwner(WordFactory.nullPointer());
        NativeSpinLockUtils.unlock(ptrToLock(node));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isLockedByCurrentThread(BufferNode node) {
        assert CurrentIsolate.getCurrentThread().isNonNull();
        return node.isNonNull() && node.getLockOwner() == CurrentIsolate.getCurrentThread();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void setLockOwner(BufferNode node) {
        assert node.getLockOwner().isNull();
        node.setLockOwner(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CIntPointer ptrToLock(BufferNode node) {
        return (CIntPointer) ((Pointer) node).add(BufferNode.offsetOfLock());
    }

    /** Should be used instead of {@link com.oracle.svm.core.jfr.BufferNode#getBuffer}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JfrBuffer getJfrBuffer(BufferNode node) {
        assert isLockedByCurrentThread(node) || VMOperation.isInProgressAtSafepoint();
        return (JfrBuffer) node.getBuffer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static SamplerBuffer getSamplerBuffer(BufferNode node) {
        assert isLockedByCurrentThread(node) || VMOperation.isInProgressAtSafepoint();
        return (SamplerBuffer) node.getBuffer();
    }
}

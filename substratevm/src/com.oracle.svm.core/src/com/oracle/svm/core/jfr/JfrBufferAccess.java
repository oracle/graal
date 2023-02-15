/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.thread.NativeSpinLockUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import org.graalvm.nativeimage.CurrentIsolate;

/**
 * Used to access the raw memory of a {@link JfrBuffer}.
 */
public final class JfrBufferAccess {
    private JfrBufferAccess() {
    }

    @Fold
    public static UnsignedWord getHeaderSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(JfrBuffer.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JfrBuffer allocate(JfrBufferType bufferType) {
        JfrThreadLocal jfrThreadLocal = (JfrThreadLocal) SubstrateJVM.getThreadLocal();
        return allocate(WordFactory.unsigned(jfrThreadLocal.getThreadLocalBufferSize()), bufferType);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JfrBuffer allocate(UnsignedWord dataSize, JfrBufferType bufferType) {
        UnsignedWord headerSize = JfrBufferAccess.getHeaderSize();
        JfrBuffer result = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(headerSize.add(dataSize));
        if (result.isNonNull()) {
            result.setSize(dataSize);
            result.setBufferType(bufferType);
            NativeSpinLockUtils.initialize(ptrToLock(result));
            tryLock(result, Integer.MAX_VALUE);
            reinitialize(result);
            unlock(result);
        }
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void free(JfrBuffer buffer) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(buffer);
    }

    @Uninterruptible(reason = "Prevent safepoints as those could change the top pointer.")
    public static void reinitialize(JfrBuffer buffer) {
        assert buffer.isNonNull();
        assert (isLocked(buffer) && buffer.getLockOwner() == CurrentIsolate.getCurrentThread()) ||
                        (buffer.getBufferType() != JfrBufferType.THREAD_LOCAL_JAVA && buffer.getBufferType() != JfrBufferType.THREAD_LOCAL_NATIVE);
        org.graalvm.nativeimage.CurrentIsolate.getCurrentThread();
        Pointer pos = getDataStart(buffer);
        buffer.setCommittedPos(pos);
        buffer.setFlushedPos(pos);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isLocked(JfrBuffer buffer) {
        assert buffer.isNonNull();
        return NativeSpinLockUtils.isLocked(ptrToLock(buffer));
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static boolean tryLock(JfrBuffer buffer) {
        assert buffer.isNonNull();
        boolean result = NativeSpinLockUtils.tryLock(ptrToLock(buffer));
        if (result) {
            buffer.setLockOwner(org.graalvm.nativeimage.CurrentIsolate.getCurrentThread());
        }
        return result;
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static boolean tryLock(JfrBuffer buffer, int retries) {
        assert buffer.isNonNull();
        boolean result = NativeSpinLockUtils.tryLock(ptrToLock(buffer), retries);
        if (result) {
            buffer.setLockOwner(org.graalvm.nativeimage.CurrentIsolate.getCurrentThread());
        }
        return result;
    }

    @Uninterruptible(reason = "We must guarantee that all buffers are in unacquired state when entering a safepoint.", callerMustBe = true)
    public static void unlock(JfrBuffer buffer) {
        assert buffer.isNonNull();
        assert (isLocked(buffer) && buffer.getLockOwner() == CurrentIsolate.getCurrentThread()) ||
                        (buffer.getBufferType() != JfrBufferType.THREAD_LOCAL_JAVA && buffer.getBufferType() != JfrBufferType.THREAD_LOCAL_NATIVE);

        NativeSpinLockUtils.unlock(ptrToLock(buffer));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getAddressOfCommittedPos(JfrBuffer buffer) {
        assert buffer.isNonNull();
        return ((Pointer) buffer).add(JfrBuffer.offsetOfCommittedPos());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getDataStart(JfrBuffer buffer) {
        assert buffer.isNonNull();
        return ((Pointer) buffer).add(getHeaderSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getDataEnd(JfrBuffer buffer) {
        assert buffer.isNonNull();
        return getDataStart(buffer).add(buffer.getSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getAvailableSize(JfrBuffer buffer) {
        assert buffer.isNonNull();
        return getDataEnd(buffer).subtract(buffer.getCommittedPos());
    }

    @Uninterruptible(reason = "Prevent safepoints as those could change the top pointer.", callerMustBe = true)
    public static UnsignedWord getUnflushedSize(JfrBuffer buffer) {
        assert buffer.isNonNull();
        return buffer.getCommittedPos().subtract(buffer.getFlushedPos());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void increaseCommittedPos(JfrBuffer buffer, UnsignedWord delta) {
        assert buffer.isNonNull();
        buffer.setCommittedPos(buffer.getCommittedPos().add(delta));
    }

    @Uninterruptible(reason = "Prevent safepoints as those could change the top pointer.")
    public static void increaseFlushedPos(JfrBuffer buffer, UnsignedWord delta) {
        assert buffer.isNonNull();
        assert (isLocked(buffer) && buffer.getLockOwner() == CurrentIsolate.getCurrentThread()) ||
                        (buffer.getBufferType() != JfrBufferType.THREAD_LOCAL_JAVA && buffer.getBufferType() != JfrBufferType.THREAD_LOCAL_NATIVE);

        buffer.setFlushedPos(buffer.getFlushedPos().add(delta));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isEmpty(JfrBuffer buffer) {
        assert buffer.isNonNull();
        return getDataStart(buffer).equal(buffer.getCommittedPos());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean verify(JfrBuffer buffer) {
        if (buffer.isNull()) {
            return false;
        }

        Pointer start = getDataStart(buffer);
        Pointer end = getDataEnd(buffer);
        return buffer.getCommittedPos().aboveOrEqual(start) && buffer.getCommittedPos().belowOrEqual(end) &&
                        buffer.getFlushedPos().aboveOrEqual(start) && buffer.getFlushedPos().belowOrEqual(end) &&
                        buffer.getFlushedPos().belowOrEqual(buffer.getCommittedPos());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CIntPointer ptrToLock(JfrBuffer buffer) {
        return (CIntPointer) ((Pointer) buffer).add(JfrBuffer.offsetOfLocked());
    }
}

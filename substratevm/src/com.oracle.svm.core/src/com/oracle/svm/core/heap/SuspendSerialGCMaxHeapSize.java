/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;

/**
 * Allows the max heap size restriction to be temporarily suspended, in order to avoid running out
 * of memory during critical VM operations. Note calling {@link #suspendInCurrentThread()} will have
 * effect only for the current thread, so other threads may still attempt to allocate and throw an
 * {@link OutOfMemoryError}.
 *
 * This option will only take effect if the SerialGC is used, and the option
 * SerialGCOptions.IgnoreMaxHeapSizeWhileInVMInternalCode is enabled.
 */
public class SuspendSerialGCMaxHeapSize {
    private static final FastThreadLocalInt nestingDepth = FastThreadLocalFactory.createInt("SuspendSerialGCMaxHeapSize.nestingDepth");

    /**
     * Temporarily suspend the heap limit for the current thread. Must be paired with a call to
     * {@link #resumeInCurrentThread}, best placed in a {@code finally} block. This method may be
     * called multiple times in a nested fashion.
     */
    @Uninterruptible(reason = "Called from code that must not allocate before suspending the heap limit.", callerMustBe = true)
    public static void suspendInCurrentThread() {
        int oldValue = nestingDepth.get();
        int newValue = oldValue + 1;
        assert oldValue >= 0;
        nestingDepth.set(newValue);
    }

    /**
     * Undoes suspending the heap limit for the current thread. This may only be called after a call
     * to {@link #suspendInCurrentThread}.
     */
    @Uninterruptible(reason = "Called from code that must not allocate after resuming the heap limit.", callerMustBe = true)
    public static void resumeInCurrentThread() {
        int oldValue = nestingDepth.get();
        int newValue = oldValue - 1;
        assert newValue >= 0;
        nestingDepth.set(newValue);
    }

    /**
     * Returns true if the heap limit is currently suspended.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isSuspended() {
        return nestingDepth.get() > 0;
    }
}

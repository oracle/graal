/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.threadlocal.VMThreadLocalOffsetProvider;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.Disallowed;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Image singleton that owns the thread-local pending exception state for threaded bytecode
 * handler stubs. During image building it records the largest stub ABI arity that can publish
 * pending state. At run time it installs one holder per Java thread and exposes the VM thread-local
 * offset folded into generated unwind-path snippets.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
public final class PendingExceptionStateSupport implements ThreadListener {
    public static final Object OBJECT_SLOT_SENTINEL = new ObjectSlotSentinel();
    public static final long PRIMITIVE_SLOT_SENTINEL = 0xBAAD_CAFE_BAAD_CAFEL;

    private static final FastThreadLocalObject<PendingExceptionStateHolder> pendingExceptionState = FastThreadLocalFactory.createObject(PendingExceptionStateHolder.class,
                    "PendingExceptionStateSupport.pendingExceptionState");

    @UnknownPrimitiveField(availability = ReadyForCompilation.class) private int maxSlots;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) private boolean useSlotSentinel;
    private final VMThreadLocalInfo threadLocalInfo;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PendingExceptionStateSupport() {
        maxSlots = 0;
        useSlotSentinel = false;
        threadLocalInfo = new VMThreadLocalInfo(pendingExceptionState);
    }

    /**
     * Returns the image singleton folded into generated code.
     */
    @Fold
    public static PendingExceptionStateSupport singleton() {
        return ImageSingletons.lookup(PendingExceptionStateSupport.class);
    }

    /**
     * Records the largest bytecode-handler stub ABI arity that may need pending exception state
     * slots.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMaxSlots(int newMaxSlots) {
        maxSlots = Math.max(maxSlots, newMaxSlots);
    }

    /**
     * Enables diagnostic poisoning of pending-state slots. When disabled, consumed object slots are
     * cleared to {@code null} for GC and consumed primitive slots are left unchanged. When enabled,
     * initially empty and consumed slots contain recognizable sentinel values to expose stale or
     * duplicate reads.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void setUseSlotSentinel(boolean useSlotSentinel) {
        this.useSlotSentinel = useSlotSentinel;
    }

    /**
     * Resolves the VM thread-local offset after thread locals have been laid out and before
     * bytecode-handler stubs are compiled.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void initializeThreadLocalOffset() {
        threadLocalInfo.offset = VMThreadLocalOffsetProvider.getOffset(pendingExceptionState);
        VMError.guarantee(threadLocalInfo.offset >= 0, "Pending exception state thread-local offset is not initialized");
    }

    /**
     * Returns metadata used by generated code to load the per-thread holder.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public VMThreadLocalInfo getThreadLocalInfo() {
        return threadLocalInfo;
    }

    @Override
    public void beforeThreadRun() {
        pendingExceptionState.set(new PendingExceptionStateHolder(maxSlots, useSlotSentinel));
    }

    @Override
    @Uninterruptible(reason = "Called from the thread listener shutdown path.")
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        pendingExceptionState.set(null);
    }

    static void initializeSlotSentinels(PendingExceptionStateHolder holder) {
        Arrays.fill(holder.objectSlots, OBJECT_SLOT_SENTINEL);
        Arrays.fill(holder.primitiveSlots, PRIMITIVE_SLOT_SENTINEL);
    }

    private static final class ObjectSlotSentinel {
    }
}

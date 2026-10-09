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
package com.oracle.svm.core.metaspace;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.guest.staging.SubstrateGCOptions;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Common support for allocating runtime metadata objects in memory that is not part of the
 * collected Java heap.
 */
public abstract class AbstractMetaspace implements Metaspace {
    private static final String OUT_OF_METASPACE_MSG = "Could not allocate a metaspace chunk because the metaspace is exhausted.\nMaximum metaspace size can be adjusted at build-time with " +
                    "'" + SubstrateOptionsParser.commandArgument(SubstrateGCOptions.ConcealedOptions.MaxMetaspaceSize, "<size in MB>m") + "'.";
    private static final OutOfMemoryError OUT_OF_METASPACE = new OutOfMemoryError(OUT_OF_METASPACE_MSG);

    @Platforms(Platform.HOSTED_ONLY.class)
    protected AbstractMetaspace() {
        if (SubstrateGCOptions.PrintMetaspace.getValue()) {
            RuntimeSupport.getRuntimeSupport().addTearDownHook(new MetaspaceTearDownHook());
        }
    }

    @Fold
    public static AbstractMetaspace singleton() {
        return (AbstractMetaspace) Metaspace.singleton();
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final boolean isInAddressSpace(Object obj) {
        return isInAddressSpace(Word.objectToUntrackedPointer(obj));
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final boolean isInAllocatedMemory(Object obj) {
        return isInAllocatedMemory(Word.objectToUntrackedPointer(obj));
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final boolean isInAddressSpace(Pointer ptr) {
        return CommittedMemoryProvider.get().isInMetaspace(ptr);
    }

    /**
     * {@link DynamicHub}s can be allocated like normal hybrid (and therefore array-like) objects.
     * The number of vtable entries is used as the array length. Note that inlined fields like
     * {@code closedTypeWorldTypeCheckSlots} are not relevant here, as they are not available in the
     * open type world configuration.
     */
    @Override
    public final DynamicHub allocateDynamicHub(int numVTableEntries) {
        assert !SubstrateOptions.useClosedTypeWorldHubLayout();
        assert LayoutEncoding.getArrayBaseOffsetAsInt(DynamicHub.fromClass(DynamicHub.class).getLayoutEncoding()) == KnownOffsets.singleton().getVTableBaseOffset();

        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            DynamicHub result = allocateDynamicHub0(numVTableEntries);
            checkOutOfMemory(result);
            assert Heap.getHeap().getObjectHeader().verifyDynamicHubOffset(result);
            return result;
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    public final byte[] allocateByteArray(int length) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            byte[] result = allocateByteArray0(length);
            checkOutOfMemory(result);
            return result;
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    public final int[] allocateIntArray(int length) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            int[] result = allocateIntArray0(length);
            checkOutOfMemory(result);
            return result;
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    public final <T> T allocateObject(Class<T> clazz) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            DynamicHub hub = DynamicHub.fromClass(clazz);
            assert LayoutEncoding.isPureInstance(hub.getLayoutEncoding());

            Object result = allocateObject0(hub);
            checkOutOfMemory(result);
            return clazz.cast(result);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    /** Returns {@code null} if the allocation failed. */
    protected abstract DynamicHub allocateDynamicHub0(int numVTableEntries);

    /** Returns {@code null} if the allocation failed. */
    protected abstract byte[] allocateByteArray0(int length);

    /** Returns {@code null} if the allocation failed. */
    protected abstract int[] allocateIntArray0(int length);

    /** Returns {@code null} if the allocation failed. */
    protected abstract Object allocateObject0(DynamicHub hub);

    /** Prints the information requested by {@code PrintMetaspace}. */
    protected abstract void printMetaspaceInfo(Log log);

    /** Prints allocation statistics as part of fatal-error diagnostics. */
    protected abstract void printAllocationStatistics(Log log);

    private static void checkOutOfMemory(Object result) {
        if (result != null) {
            return;
        }

        if (SubstrateGCOptions.MetaspaceExhaustionIsFatal.getValue()) {
            throw VMError.shouldNotReachHere(OUT_OF_METASPACE_MSG);
        }
        throw OUT_OF_METASPACE;
    }

    private static final class MetaspaceTearDownHook implements RuntimeSupport.Hook {
        @Override
        public void execute(boolean isFirstIsolate) {
            AbstractMetaspace.singleton().printMetaspaceInfo(Log.log());
        }
    }

    public static final class DumpMetaspaceInfo extends SubstrateDiagnostics.DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, SubstrateDiagnostics.ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            AbstractMetaspace.singleton().printAllocationStatistics(log);
        }
    }
}

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
package com.oracle.svm.core.gc.shenandoah;

import static com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.enqueue;
import static com.oracle.svm.core.thread.VMOperation.SystemEffect.SAFEPOINT;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.gc.shared.InitializeReservedRegistersForPossiblyUnattachedThread;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperation;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationData;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationWrapperData;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahLibrary;
import com.oracle.svm.core.heap.VMOperationInfo;
import com.oracle.svm.core.heap.VMOperationInfos;

/** Shenandoah-related VM operations. */
public class ShenandoahVMOperations {
    private static final ShenandoahVMOperation OP_COLLECT_FOR_ALLOCATION = new ShenandoahVMOperation(VMOperationInfos.get(ShenandoahVMOperation.class, "Collect for allocation", SAFEPOINT), true);
    private static final ShenandoahVMOperation OP_COLLECT_FULL = new ShenandoahVMOperation(VMOperationInfos.get(ShenandoahVMOperation.class, "Collect full", SAFEPOINT), true);

    public final CEntryPointLiteral<CFunctionPointer> funcCollectForAllocation;
    public final CEntryPointLiteral<CFunctionPointer> funcCollectFull;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ShenandoahVMOperations() {
        funcCollectForAllocation = CEntryPointLiteral.create(ShenandoahVMOperations.class, "collectForAllocation",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationData.class, NativeGCVMOperationWrapperData.class);
        funcCollectFull = CEntryPointLiteral.create(ShenandoahVMOperations.class, "collectFull",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationData.class, NativeGCVMOperationWrapperData.class);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseShenandoahGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void collectForAllocation(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationData data,
                    NativeGCVMOperationWrapperData wrapperData) {
        enqueue(OP_COLLECT_FOR_ALLOCATION, data, wrapperData);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseShenandoahGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void collectFull(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationData data,
                    NativeGCVMOperationWrapperData wrapperData) {
        enqueue(OP_COLLECT_FULL, data, wrapperData);
    }

    private static class ShenandoahVMOperation extends NativeGCVMOperation {
        protected ShenandoahVMOperation(VMOperationInfo info, boolean isGC) {
            super(info, isGC);
        }

        @Override
        public boolean executePrologue(NativeGCVMOperationData data) {
            return ShenandoahLibrary.executeVMOperationPrologue(data);
        }

        @Override
        protected void operate0(NativeGCVMOperationData data) {
            ShenandoahLibrary.executeVMOperationMain(data);
        }

        @Override
        public void executeEpilogue(NativeGCVMOperationData data) {
            ShenandoahLibrary.executeVMOperationEpilogue(data);
        }
    }
}

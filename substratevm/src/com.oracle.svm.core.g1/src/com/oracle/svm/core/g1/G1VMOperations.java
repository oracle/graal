/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1;

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

import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.gc.shared.InitializeReservedRegistersForPossiblyUnattachedThread;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperation;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationData;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationWrapperData;
import com.oracle.svm.core.heap.VMOperationInfo;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.g1.nativelib.G1Library;

/** G1-related VM operations. */
public class G1VMOperations {
    private static final G1VMOperation OP_COLLECT_FOR_ALLOCATION = new G1VMOperation(VMOperationInfos.get(G1VMOperation.class, "Collect for allocation", SAFEPOINT), true);
    private static final G1VMOperation OP_EXECUTE_PAUSE_REMARK = new G1VMOperation(VMOperationInfos.get(G1VMOperation.class, "Pause remark", SAFEPOINT), true);
    private static final G1VMOperation OP_EXECUTE_PAUSE_CLEANUP = new G1VMOperation(VMOperationInfos.get(G1VMOperation.class, "Pause cleanup", SAFEPOINT), true);
    private static final G1VMOperation OP_COLLECT_FULL = new G1VMOperation(VMOperationInfos.get(G1VMOperation.class, "Collect full", SAFEPOINT), true);
    private static final G1VMOperation OP_TRY_INITIATE_CONC_MARK = new G1VMOperation(VMOperationInfos.get(G1VMOperation.class, "Try init concurrent mark", SAFEPOINT), true);
    private static final G1VMOperation OP_VERIFY_HEAP = new G1VMOperation(VMOperationInfos.get(G1VMOperation.class, "Verify heap", SAFEPOINT), false);

    public final CEntryPointLiteral<CFunctionPointer> funcCollectForAllocation;
    public final CEntryPointLiteral<CFunctionPointer> funcExecutePauseRemark;
    public final CEntryPointLiteral<CFunctionPointer> funcExecutePauseCleanup;
    public final CEntryPointLiteral<CFunctionPointer> funcCollectFull;
    public final CEntryPointLiteral<CFunctionPointer> funcVerifyHeap;
    public final CEntryPointLiteral<CFunctionPointer> funcTryInitiateConcMarkOp;

    @Platforms(Platform.HOSTED_ONLY.class)
    public G1VMOperations() {
        funcCollectForAllocation = CEntryPointLiteral.create(G1VMOperations.class, "collectForAllocation",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationData.class, NativeGCVMOperationWrapperData.class);
        funcExecutePauseRemark = CEntryPointLiteral.create(G1VMOperations.class, "executePauseRemark",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationData.class, NativeGCVMOperationWrapperData.class);
        funcExecutePauseCleanup = CEntryPointLiteral.create(G1VMOperations.class, "executePauseCleanup",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationData.class, NativeGCVMOperationWrapperData.class);
        funcCollectFull = CEntryPointLiteral.create(G1VMOperations.class, "collectFull",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationData.class, NativeGCVMOperationWrapperData.class);
        funcVerifyHeap = CEntryPointLiteral.create(G1VMOperations.class, "verifyHeap",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationData.class, NativeGCVMOperationWrapperData.class);
        funcTryInitiateConcMarkOp = CEntryPointLiteral.create(G1VMOperations.class, "tryInitiateConcMark",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationData.class, NativeGCVMOperationWrapperData.class);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseG1GC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void collectForAllocation(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationData data,
                    NativeGCVMOperationWrapperData wrapperData) {
        enqueue(OP_COLLECT_FOR_ALLOCATION, data, wrapperData);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseG1GC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void executePauseRemark(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationData data,
                    NativeGCVMOperationWrapperData wrapperData) {
        enqueue(OP_EXECUTE_PAUSE_REMARK, data, wrapperData);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseG1GC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void executePauseCleanup(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationData data,
                    NativeGCVMOperationWrapperData wrapperData) {
        enqueue(OP_EXECUTE_PAUSE_CLEANUP, data, wrapperData);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseG1GC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void collectFull(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationData data,
                    NativeGCVMOperationWrapperData wrapperData) {
        enqueue(OP_COLLECT_FULL, data, wrapperData);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseG1GC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void verifyHeap(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationData data,
                    NativeGCVMOperationWrapperData wrapperData) {
        enqueue(OP_VERIFY_HEAP, data, wrapperData);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseG1GC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void tryInitiateConcMark(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationData data,
                    NativeGCVMOperationWrapperData wrapperData) {
        enqueue(OP_TRY_INITIATE_CONC_MARK, data, wrapperData);
    }

    private static class G1VMOperation extends NativeGCVMOperation {
        protected G1VMOperation(VMOperationInfo info, boolean isGC) {
            super(info, isGC);
        }

        @Override
        public boolean executePrologue(NativeGCVMOperationData data) {
            return G1Library.executeVMOperationPrologue(data);
        }

        @Override
        protected void operate0(NativeGCVMOperationData data) {
            G1Library.executeVMOperationMain(data);
        }

        @Override
        public void executeEpilogue(NativeGCVMOperationData data) {
            G1Library.executeVMOperationEpilogue(data);
        }
    }
}

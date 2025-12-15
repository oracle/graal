/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shared.graal;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.word.Word;

/**
 * This class contains the {@link SubstrateForeignCallTarget}s for the allocation slow path. These
 * methods are {@link Uninterruptible} to ensure that newly allocated objects are either placed in
 * the young generation or that the covered cards are marked as dirty (this dirtying is done by the
 * GC at a later point in time, see ReduceInitialCardMarks and DeferInitialCardMark in HotSpot).
 * This allows the compiler to safely eliminate GC write barriers for initializing writes to the
 * last allocated object, which reduces code size.
 */
public abstract class NativeGCAllocationSupport implements GCAllocationSupport {
    private static final SubstrateForeignCallDescriptor SLOW_NEW_INSTANCE = SnippetRuntime.findForeignCall(NativeGCAllocationSupport.class, "slowPathNewInstance", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_ARRAY = SnippetRuntime.findForeignCall(NativeGCAllocationSupport.class, "slowPathNewArray", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_STORED_CONTINUATION = SnippetRuntime.findForeignCall(NativeGCAllocationSupport.class, "slowPathNewStoredContinuation", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_POD_INSTANCE = SnippetRuntime.findForeignCall(NativeGCAllocationSupport.class, "slowPathNewPodInstance", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor[] UNCONDITIONAL_FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{SLOW_NEW_INSTANCE, SLOW_NEW_ARRAY};

    /* The following thread locals may only be accessed in uninterruptible code. */
    public static final FastThreadLocalObject<Object> podReferenceMapTL = FastThreadLocalFactory.createObject(Object.class, "podReferenceMap");

    @Fold
    public static NativeGCAllocationSupport singleton() {
        return ImageSingletons.lookup(NativeGCAllocationSupport.class);
    }

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(UNCONDITIONAL_FOREIGN_CALLS);
        if (ContinuationSupport.isSupported()) {
            foreignCalls.register(SLOW_NEW_STORED_CONTINUATION);
        }
        if (Pod.RuntimeSupport.isPresent()) {
            foreignCalls.register(SLOW_NEW_POD_INSTANCE);
        }
    }

    @Override
    public ForeignCallDescriptor getNewInstanceStub() {
        return SLOW_NEW_INSTANCE;
    }

    @Override
    public ForeignCallDescriptor getNewArrayStub() {
        return SLOW_NEW_ARRAY;
    }

    @Override
    public ForeignCallDescriptor getNewStoredContinuationStub() {
        return SLOW_NEW_STORED_CONTINUATION;
    }

    @Override
    public ForeignCallDescriptor getNewPodInstanceStub() {
        return SLOW_NEW_POD_INSTANCE;
    }

    @Override
    public boolean useTLAB() {
        return SubstrateGCOptions.UseTLAB.getValue();
    }

    @Override
    public boolean shouldAllocateInTLAB(UnsignedWord size, boolean isArray) {
        /* Always try to allocate in the TLAB (humongous objects will not fit into the TLAB). */
        return true;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.")
    private static Object slowPathNewInstance(Word objectHeader) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            DynamicHub hub = Heap.getHeap().getObjectHeader().dynamicHubFromObjectHeader(objectHeader);
            guaranteeAllocationAllowed("allocateInstance", hub);

            Object result = NativeGCAllocationSupport.singleton().allocateInstance0(hub);
            return checkForOOME(result);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.")
    private static Object slowPathNewArray(Word objectHeader, int length) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            DynamicHub hub = Heap.getHeap().getObjectHeader().dynamicHubFromObjectHeader(objectHeader);
            guaranteeAllocationAllowed("allocateArray", hub);
            checkArrayLength(length);

            Object result = NativeGCAllocationSupport.singleton().allocateArray0(length, hub);
            return checkForOOME(result);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.")
    private static Object slowPathNewStoredContinuation(Word objectHeader, int length) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            DynamicHub hub = Heap.getHeap().getObjectHeader().dynamicHubFromObjectHeader(objectHeader);
            guaranteeAllocationAllowed("allocateStoredContinuation", hub);
            checkArrayLength(length);

            Object result = NativeGCAllocationSupport.singleton().allocateStoredContinuation0(length, hub);
            return checkForOOME(result);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.")
    private static Object slowPathNewPodInstance(Word objectHeader, int length, byte[] referenceMap) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            DynamicHub hub = Heap.getHeap().getObjectHeader().dynamicHubFromObjectHeader(objectHeader);
            guaranteeAllocationAllowed("allocatePod", hub);
            checkArrayLength(length);

            /* The reference map object can live in the Java heap. */
            podReferenceMapTL.set(referenceMap);
            Object result = NativeGCAllocationSupport.singleton().allocatePod0(length, hub);
            podReferenceMapTL.set(null);
            return checkForOOME(result);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected abstract Object allocateInstance0(DynamicHub hub);

    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected abstract Object allocateArray0(int length, DynamicHub hub);

    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected abstract Object allocateStoredContinuation0(int length, DynamicHub hub);

    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected abstract Object allocatePod0(int length, DynamicHub hub);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void checkArrayLength(int length) {
        if (probability(VERY_SLOW_PATH_PROBABILITY, length < 0)) {
            throwNegativeArraySizeExceptionInterruptibly();
        }
    }

    @Uninterruptible(reason = "No need to be uninterruptible because no object was allocated.", calleeMustBe = false)
    private static void throwNegativeArraySizeExceptionInterruptibly() {
        throwNegativeArraySizeException();
    }

    private static void throwNegativeArraySizeException() {
        throw new NegativeArraySizeException();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void guaranteeAllocationAllowed(String callSite, DynamicHub hub) {
        if (probability(VERY_SLOW_PATH_PROBABILITY, Heap.getHeap().isAllocationDisallowed())) {
            throwAllocationNotAllowedInterruptibly(callSite, hub);
        }
    }

    @Uninterruptible(reason = "No need to be uninterruptible because it kills the process.", calleeMustBe = false)
    private static void throwAllocationNotAllowedInterruptibly(String callSite, DynamicHub hub) {
        throw NoAllocationVerifier.exit(callSite, DynamicHub.toClass(hub).getName());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Object checkForOOME(Object result) {
        if (probability(VERY_SLOW_PATH_PROBABILITY, result == null)) {
            throwHeapSizeExceededInterruptibly();
        }
        return result;
    }

    @Uninterruptible(reason = "No need to be uninterruptible because no object was allocated.", calleeMustBe = false)
    private static void throwHeapSizeExceededInterruptibly() {
        throw OutOfMemoryUtil.heapSizeExceeded();
    }
}

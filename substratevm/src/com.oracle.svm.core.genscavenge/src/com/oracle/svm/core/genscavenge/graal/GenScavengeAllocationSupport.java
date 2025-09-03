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
package com.oracle.svm.core.genscavenge.graal;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ContinuationSupport;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.word.Word;

/**
 * This class contains the {@link SubstrateForeignCallTarget}s for the allocation slow path. These
 * methods are {@link Uninterruptible} to ensure that newly allocated objects are either placed in
 * the young generation or that all their covered cards are marked as dirty. This allows the
 * compiler to safely eliminate GC write barriers for initializing writes, which reduces code size.
 */
public class GenScavengeAllocationSupport implements GCAllocationSupport {
    private static final SubstrateForeignCallDescriptor SLOW_NEW_INSTANCE = SnippetRuntime.findForeignCall(GenScavengeAllocationSupport.class, "slowNewInstance", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_ARRAY = SnippetRuntime.findForeignCall(GenScavengeAllocationSupport.class, "slowNewArray", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_STORED_CONTINUATION = SnippetRuntime.findForeignCall(GenScavengeAllocationSupport.class, "slowNewStoredContinuation", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_POD_INSTANCE = SnippetRuntime.findForeignCall(GenScavengeAllocationSupport.class, "slowNewPodInstance", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor[] UNCONDITIONAL_FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{SLOW_NEW_INSTANCE, SLOW_NEW_ARRAY};

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
        return SubstrateGCOptions.TlabOptions.UseTLAB.getValue();
    }

    @Override
    public boolean shouldAllocateInTLAB(UnsignedWord size, boolean isArray) {
        return !isArray || arrayAllocatedInAlignedChunk(size);
    }

    @Override
    public Word getTLABInfo() {
        return ThreadLocalAllocation.getTlabAddress();
    }

    @Override
    public int tlabTopOffset() {
        return ThreadLocalAllocation.Descriptor.offsetOfAllocationTop();
    }

    @Override
    public int tlabEndOffset() {
        return ThreadLocalAllocation.Descriptor.offsetOfAllocationEnd();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean arrayAllocatedInAlignedChunk(UnsignedWord objectSize) {
        return objectSize.belowThan(HeapParameters.getLargeArrayThreshold());
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.")
    private static Object slowNewInstance(Word objectHeader) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            Object result = slowNewInstanceInterruptibly(objectHeader);
            HeapImpl.getHeap().dirtyAllReferencesOf(result);
            return result;
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.")
    private static Object slowNewArray(Word objectHeader, int length) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            Object result = slowNewArrayLikeObjectInterruptibly(objectHeader, length, null);
            HeapImpl.getHeap().dirtyAllReferencesOf(result);
            return result;
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.")
    private static Object slowNewPodInstance(Word objectHeader, int arrayLength, byte[] referenceMap) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            Object result = slowNewArrayLikeObjectInterruptibly(objectHeader, arrayLength, referenceMap);
            HeapImpl.getHeap().dirtyAllReferencesOf(result);
            return result;
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Just to be consistent with the other allocation slowpath code.")
    private static Object slowNewStoredContinuation(Word objectHeader, int length) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            /* Stored continuations only use explicit write barriers, so no dirtying needed. */
            return slowNewArrayLikeObjectInterruptibly(objectHeader, length, null);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Uninterruptible(reason = "Switch from uninterruptible to interruptible code.", calleeMustBe = false)
    private static Object slowNewInstanceInterruptibly(Word objectHeader) {
        return ThreadLocalAllocation.slowPathNewInstance(objectHeader);
    }

    @Uninterruptible(reason = "Switch from uninterruptible to interruptible code.", calleeMustBe = false)
    private static Object slowNewArrayLikeObjectInterruptibly(Word objectHeader, int length, byte[] podReferenceMap) {
        return ThreadLocalAllocation.slowPathNewArrayLikeObject(objectHeader, length, podReferenceMap);
    }
}

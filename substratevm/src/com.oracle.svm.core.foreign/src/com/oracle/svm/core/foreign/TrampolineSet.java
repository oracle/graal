/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import java.lang.invoke.MethodHandle;
import java.util.BitSet;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.AbstractRuntimeCodeInstaller.RuntimeCodeInstallerPlatformHelper;
import com.oracle.svm.core.heap.AbstractPinnedObjectSupport;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * A set of trampolines that can be assigned to specific upcall stubs with specific method handles.
 */
final class TrampolineSet {
    private static UnsignedWord allocationSize() {
        return VirtualMemoryProvider.get().getGranularity();
    }

    private static UnsignedWord alignment() {
        return Word.unsigned(SubstrateOptions.runtimeCodeAlignment());
    }

    private static int maxTrampolineCount() {
        long result = allocationSize().rawValue() / AbiUtils.singleton().trampolineSize();
        return NumUtil.safeToInt(result);
    }

    public static Pointer getAllocationBase(Pointer ptr) {
        var offset = ptr.unsignedRemainder(allocationSize());
        assert offset.belowOrEqual(allocationSize());
        assert offset.belowOrEqual(Integer.MAX_VALUE);
        assert offset.unsignedRemainder(AbiUtils.singleton().trampolineSize()).equal(0);
        return ptr.subtract(offset);
    }

    private static final int FREED = -1;

    private record TrampolineObjects(Object methodHandle, Object firstObjectArgument) {
    }

    /*
     * Invariant: {@code freed <= assigned <= trampolineCount}
     */
    private int assigned = 0; // Contains FREED after being freed
    private int freed = 0;
    private final int trampolineCount = maxTrampolineCount();
    /**
     * Each element corresponds to a trampoline by index. An element either refers to
     * {@link MethodHandle} or to the {@link MethodHandle} and the first object argument (aggregated
     * in {@link TrampolineObjects}). If pinning is not necessary for any of the referred objects,
     * those are stored directly. Otherwise, the {@link PinnedObject} is stored.
     */
    private final Object[] objs = new Object[trampolineCount];
    /**
     * Array of run-time object arguments passed by each trampoline. This contains the addresses of
     * (pinned) {@link MethodHandle} instances for regular upcalls, or for direct upcalls with an
     * object argument, the addresses of the (pinned) objects passed for the argument. Those objects
     * or pins are stored in {@link #objs}.
     */
    private final PointerBase[] runtimeArguments = new PointerBase[trampolineCount];
    private final CFunctionPointer[] stubs = new CFunctionPointer[trampolineCount];
    private final BitSet patchedStubs;
    private final Pointer trampolines;

    private final PinnedObject runtimeArgumentsPin;
    private final PinnedObject stubsPin;

    private static BitSet initializedPatchedStubs(int nbits) {
        BitSet patchedStubs = null;
        assert (patchedStubs = new BitSet(nbits)).isEmpty();
        return patchedStubs;
    }

    private boolean getAndSetPatchedStub(int idx) {
        assert patchedStubs != null;
        boolean res = patchedStubs.get(idx);
        patchedStubs.set(idx);
        return res;
    }

    private PointerBase maybePinMethodHandle(int trampolineIdx, MethodHandle methodHandle) {
        assert 0 <= trampolineIdx && trampolineIdx < trampolineCount;
        assert objs[trampolineIdx] == null;
        PointerBase result;
        if (AbstractPinnedObjectSupport.needsPinning(methodHandle)) {
            PinnedObject pinned = PinnedObject.create(methodHandle);
            objs[trampolineIdx] = pinned;
            result = pinned.addressOfObject();
        } else {
            objs[trampolineIdx] = methodHandle;
            result = Word.objectToUntrackedPointer(methodHandle);
        }
        return result;
    }

    private PointerBase maybePinFirstObjectArgument(int trampolineIdx, Object firstObjectArgument) {
        assert patchedStubs.get(trampolineIdx);
        assert 0 <= trampolineIdx && trampolineIdx < trampolineCount;
        /*
         * If this method is called, method 'maybePinMethodHandle' was always called first. We
         * therefore either expect a MethodHandle (if it doesn't need pinning) or a PinnedObject.
         */
        assert objs[trampolineIdx] instanceof MethodHandle || objs[trampolineIdx] instanceof PinnedObject pinnedObject && pinnedObject.getObject() instanceof MethodHandle;

        Object methodHandleOrPin = objs[trampolineIdx];
        Object firstObjectArgumentOrPin;
        PointerBase addressOfFirstObjectArgument;
        if (AbstractPinnedObjectSupport.needsPinning(firstObjectArgument)) {
            firstObjectArgumentOrPin = PinnedObject.create(firstObjectArgument);
            addressOfFirstObjectArgument = ((PinnedObject) firstObjectArgumentOrPin).addressOfObject();
        } else {
            firstObjectArgumentOrPin = firstObjectArgument;
            addressOfFirstObjectArgument = Word.objectToUntrackedPointer(firstObjectArgument);
        }
        objs[trampolineIdx] = new TrampolineObjects(methodHandleOrPin, firstObjectArgumentOrPin);

        return addressOfFirstObjectArgument;
    }

    TrampolineSet(AbiUtils.TrampolineTemplate template) {
        assert allocationSize().rawValue() % AbiUtils.singleton().trampolineSize() == 0;

        assert trampolineCount <= maxTrampolineCount();
        runtimeArgumentsPin = PinnedObject.create(runtimeArguments);
        stubsPin = PinnedObject.create(stubs);

        trampolines = prepareTrampolines(runtimeArgumentsPin, stubsPin, template);
        patchedStubs = initializedPatchedStubs(stubs.length);
    }

    Pointer base() {
        return trampolines;
    }

    boolean hasFreeTrampolines() {
        assert (0 <= assigned && assigned <= trampolineCount) || assigned == FREED;
        return assigned != FREED && assigned != trampolineCount;
    }

    private int getTrampolineIndex(Pointer trampolinePointer) {
        return UnsignedUtils.safeToInt(trampolinePointer.subtract(trampolines).unsignedDivide(AbiUtils.singleton().trampolineSize()));
    }

    Pointer assignTrampoline(MethodHandle methodHandle, CFunctionPointer upcallStubPointer) {
        int idx = assigned++;

        runtimeArguments[idx] = maybePinMethodHandle(idx, methodHandle);
        stubs[idx] = upcallStubPointer;
        assert !patchedStubs.get(idx);

        return trampolines.add(idx * AbiUtils.singleton().trampolineSize());
    }

    void prepareTrampolineForDirectUpcall(Pointer trampolinePointer, CFunctionPointer directUpcallStubPointer, Object argument) {
        VMError.guarantee(trampolinePointer.aboveOrEqual(trampolines), "invalid trampoline pointer");
        int idx = getTrampolineIndex(trampolinePointer);
        VMError.guarantee(idx >= 0 && idx < stubs.length, "invalid trampoline index");
        assert !getAndSetPatchedStub(idx) : "attempt to patch trampoline twice";
        if (argument != null) {
            runtimeArguments[idx] = maybePinFirstObjectArgument(idx, argument);
        }
        stubs[idx] = directUpcallStubPointer;
    }

    private Pointer prepareTrampolines(PinnedObject argsArray, PinnedObject stubsArray, AbiUtils.TrampolineTemplate template) {
        UnsignedWord pageSize = allocationSize();
        /* We request a specific alignment to guarantee correctness of getAllocationBase */
        Pointer page = CommittedMemoryProvider.get().allocateExecutableMemory(pageSize, Word.unsigned(SubstrateOptions.runtimeCodeAlignment()));
        if (page.isNull()) {
            throw new OutOfMemoryError("Could not allocate memory for trampolines.");
        }
        VMError.guarantee(page.unsignedRemainder(pageSize).equal(0), "Trampoline allocation must be aligned to allocationSize().");

        Pointer it = page;
        Pointer end = page.add(pageSize);
        for (int i = 0; i < trampolineCount; ++i) {
            VMError.guarantee(getAllocationBase(it).equal(page));
            it = template.write(it, CurrentIsolate.getIsolate(), argsArray.addressOfArrayElement(i), stubsArray.addressOfArrayElement(i));
            VMError.guarantee(it.belowOrEqual(end), "Not enough memory was allocated to hold trampolines");
        }

        VMError.guarantee(VirtualMemoryProvider.get().protect(page, pageSize, VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.EXECUTE) == 0,
                        "Error when making the trampoline allocation executable");

        /*
         * On some architectures, it is necessary to flush the instruction cache if new code was
         * installed and/or to issue an instruction synchronization barrier on other cores currently
         * running a thread that may execute the newly installed code.
         */
        if (RuntimeCodeInstallerPlatformHelper.singleton().needsInstructionCacheSynchronization()) {
            new InstructionCacheOperation(page.rawValue(), pageSize.rawValue()).enqueue();
        }

        return page;
    }

    boolean freeTrampoline(Pointer trampolineAddress) {
        int idx = getTrampolineIndex(trampolineAddress);
        Object methodHandlePinOrTrampolinePins = objs[idx];
        objs[idx] = null;

        if (methodHandlePinOrTrampolinePins instanceof TrampolineObjects trampolineObjects) {
            assert patchedStubs.get(idx);
            assert trampolineObjects.methodHandle != null;
            assert trampolineObjects.firstObjectArgument != null;
            unpinIfNecessary(trampolineObjects.methodHandle);
            unpinIfNecessary(trampolineObjects.firstObjectArgument);
        } else {
            assert methodHandlePinOrTrampolinePins instanceof MethodHandle ||
                            methodHandlePinOrTrampolinePins instanceof PinnedObject pinnedObject && pinnedObject.getObject() instanceof MethodHandle;
            unpinIfNecessary(methodHandlePinOrTrampolinePins);
        }

        runtimeArguments[idx] = Word.nullPointer();
        freed++;
        return tryFree();
    }

    private static void unpinIfNecessary(Object pin) {
        if (pin instanceof PinnedObject pinnedObject) {
            pinnedObject.close();
        }
    }

    private boolean tryFree() {
        assert freed <= trampolineCount;
        if (freed < trampolineCount) {
            return false;
        }
        assert allElementsAreNull(objs);
        runtimeArgumentsPin.close();
        stubsPin.close();
        CommittedMemoryProvider.get().freeExecutableMemory(trampolines, allocationSize(), alignment());
        assigned = FREED;
        if (patchedStubs != null) {
            patchedStubs.clear();
        }
        return true;
    }

    private static boolean allElementsAreNull(Object[] pins) {
        for (Object pin : pins) {
            if (pin != null) {
                return false;
            }
        }
        return true;
    }

    private static class InstructionCacheOperation extends JavaVMOperation {
        private final long codeStart;
        private final long codeSize;

        InstructionCacheOperation(long codeStart, long codeSize) {
            super(VMOperationInfos.get(InstructionCacheOperation.class, "Prepare FFM API trampoline set", SystemEffect.SAFEPOINT));
            this.codeStart = codeStart;
            this.codeSize = codeSize;
        }

        @Override
        protected void operate() {
            RuntimeCodeInstallerPlatformHelper.singleton().performCodeSynchronization(codeStart, codeSize);
        }
    }
}

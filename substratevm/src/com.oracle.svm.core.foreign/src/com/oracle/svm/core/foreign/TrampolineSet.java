/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.AbstractRuntimeCodeInstaller.RuntimeCodeInstallerPlatformHelper;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.word.Word;

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

    private final List<PinnedObject> pins = new ArrayList<>();
    /*
     * Invariant: {@code freed <= assigned <= trampolineCount}
     */
    private int assigned = 0; // Contains FREED after being freed
    private int freed = 0;
    private final int trampolineCount = maxTrampolineCount();
    private final PointerBase[] methodHandles = new PointerBase[trampolineCount];
    private final CFunctionPointer[] stubs = new CFunctionPointer[trampolineCount];
    private final BitSet patchedStubs;
    private final Pointer trampolines;

    private static BitSet initializedPatchedStubs(int nbits) {
        BitSet patchedStubs = null;
        assert (patchedStubs = new BitSet(nbits)).isEmpty();
        return patchedStubs;
    }

    private boolean getAndSetPatchedStub(int id) {
        assert patchedStubs != null;
        boolean res = patchedStubs.get(id);
        patchedStubs.set(id);
        return res;
    }

    private PinnedObject pin(Object object) {
        PinnedObject pinned = PinnedObject.create(object);
        pins.add(pinned);
        return pinned;
    }

    TrampolineSet(AbiUtils.TrampolineTemplate template) {
        assert allocationSize().rawValue() % AbiUtils.singleton().trampolineSize() == 0;

        assert trampolineCount <= maxTrampolineCount();
        trampolines = prepareTrampolines(pin(methodHandles), pin(stubs), template);
        this.patchedStubs = initializedPatchedStubs(stubs.length);
    }

    Pointer base() {
        return trampolines;
    }

    boolean hasFreeTrampolines() {
        assert (0 <= assigned && assigned <= trampolineCount) || assigned == FREED;
        return assigned != FREED && assigned != trampolineCount;
    }

    Pointer assignTrampoline(MethodHandle methodHandle, CFunctionPointer upcallStubPointer) {
        PinnedObject pinned = pin(methodHandle);
        int id = assigned++;

        methodHandles[id] = pinned.addressOfObject();
        stubs[id] = upcallStubPointer;
        assert !patchedStubs.get(id);

        return trampolines.add(id * AbiUtils.singleton().trampolineSize());
    }

    void patchTrampolineForDirectUpcall(Pointer trampolinePointer, CFunctionPointer directUpcallStubPointer) {
        VMError.guarantee(trampolinePointer.aboveOrEqual(trampolines), "invalid trampoline pointer");
        int id = UnsignedUtils.safeToInt(trampolinePointer.subtract(trampolines).unsignedDivide(AbiUtils.singleton().trampolineSize()));
        VMError.guarantee(id >= 0 && id < stubs.length, "invalid trampoline id");
        assert !getAndSetPatchedStub(id) : "attempt to patch trampoline twice";
        stubs[id] = directUpcallStubPointer;
    }

    private Pointer prepareTrampolines(PinnedObject mhsArray, PinnedObject stubsArray, AbiUtils.TrampolineTemplate template) {
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
            it = template.write(it, CurrentIsolate.getIsolate(), mhsArray.addressOfArrayElement(i), stubsArray.addressOfArrayElement(i));
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

    boolean tryFree() {
        freed++;
        assert freed <= trampolineCount;
        if (freed < trampolineCount) {
            return false;
        }
        for (PinnedObject pinned : pins) {
            pinned.close();
        }
        CommittedMemoryProvider.get().freeExecutableMemory(trampolines, allocationSize(), alignment());
        assigned = FREED;
        if (patchedStubs != null) {
            patchedStubs.clear();
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

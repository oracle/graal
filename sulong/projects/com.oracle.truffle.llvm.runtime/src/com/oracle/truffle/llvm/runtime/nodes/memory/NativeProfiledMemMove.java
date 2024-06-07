/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.CountingConditionProfile;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.linux.LLVMLinuxAarch64VaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.VAListPointerWrapperFactoryDelegate;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorageFactory.VAListPointerWrapperFactoryDelegateNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNode.UnitSizeNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeGen.UnitSizeNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class NativeProfiledMemMove extends LLVMNode implements LLVMMemMoveNode {
    static void copyForward(LLVMPointer target, LLVMPointer source, long length,
                    ManagedMemMoveHelperNode helper,
                    UnitSizeNode unitSizeNode) {
        int unitSize = unitSizeNode.execute(helper, length);
        for (long i = 0; i < length; i += unitSize) {
            helper.execute(target.increment(i), source.increment(i), unitSize);
        }
    }

    private static void copyBackward(LLVMPointer target, LLVMPointer source, long length,
                    ManagedMemMoveHelperNode helper,
                    UnitSizeNode unitSizeNode) {
        int unitSize = unitSizeNode.execute(helper, length);
        for (long i = length - unitSize; i >= 0; i -= unitSize) {
            helper.execute(target.increment(i), source.increment(i), unitSize);
        }
    }

    static boolean doCustomVaListCopy(LLVMManagedPointer target, LLVMManagedPointer source) {
        if (source.getOffset() != 0 || target.getOffset() != 0) {
            return false;
        }
        return target.getObject() instanceof LLVMLinuxAarch64VaListStorage && source.getObject() instanceof LLVMLinuxAarch64VaListStorage;
    }

    static boolean doCustomNativeToVaListCopy(LLVMManagedPointer target) {
        return target.getOffset() == 0 && target.getObject() instanceof LLVMLinuxAarch64VaListStorage;
    }

    @Specialization(limit = "2", guards = {"target.getObject() != source.getObject()", "doCustomVaListCopy(target, source)"})
    protected void doManagedCustomCopy(VirtualFrame frame, LLVMManagedPointer target, @SuppressWarnings("unused") LLVMManagedPointer source, long length,
                    @Cached @SuppressWarnings("unused") VAListPointerWrapperFactoryDelegate wrapperFactory,
                    @Bind("wrapperFactory.execute(source.getObject())") Object sourceWrapper,
                    @CachedLibrary("sourceWrapper") LLVMVaListLibrary vaListLibrary) {
        LLVMLinuxAarch64VaListStorage.checkMemmoveLength(target, length);
        vaListLibrary.copy(sourceWrapper, target.getObject(), frame);
    }

    @Specialization(guards = {"target.getObject() != source.getObject()", "doCustomVaListCopy(target, source)"}, replaces = "doManagedCustomCopy")
    protected void doManagedCustomCopyUncached(VirtualFrame frame, LLVMManagedPointer target, LLVMManagedPointer source, long length) {
        LLVMLinuxAarch64VaListStorage.checkMemmoveLength(target, length);
        Object sourceWrapper = VAListPointerWrapperFactoryDelegateNodeGen.getUncached().execute(source.getObject());
        LLVMVaListLibrary.getFactory().getUncached(sourceWrapper).copy(sourceWrapper, target.getObject(), frame.materialize());
    }

    @Specialization(limit = "8", guards = {"helper.guard(target, source)", "target.getObject() != source.getObject()", "!doCustomVaListCopy(target, source)"})
    protected void doManagedNonAliasing(LLVMManagedPointer target, LLVMManagedPointer source, long length,
                    @Cached("create(target, source)") ManagedMemMoveHelperNode helper,
                    @Cached UnitSizeNode unitSizeNode) {
        copyForward(target, source, length, helper, unitSizeNode);
    }

    @Specialization(limit = "8", guards = {"helper.guard(target, source)", "target.getObject() == source.getObject()", "!doCustomVaListCopy(target, source)"})
    protected void doManagedAliasing(LLVMManagedPointer target, LLVMManagedPointer source, long length,
                    @Cached("create(target, source)") ManagedMemMoveHelperNode helper,
                    @Cached UnitSizeNode unitSizeNode,
                    @Cached CountingConditionProfile canCopyForward) {
        if (canCopyForward.profile(Long.compareUnsigned(target.getOffset() - source.getOffset(), length) >= 0)) {
            copyForward(target, source, length, helper, unitSizeNode);
        } else {
            copyBackward(target, source, length, helper, unitSizeNode);
        }
    }

    @Specialization(limit = "4", guards = {"helper.guard(target, source)", "!doCustomNativeToVaListCopy(target)"})
    protected void doManagedNative(LLVMManagedPointer target, LLVMNativePointer source, long length,
                    @Cached("create(target, source)") ManagedMemMoveHelperNode helper,
                    @Cached UnitSizeNode unitSizeNode) {
        copyForward(target, source, length, helper, unitSizeNode);
    }

    @Specialization(limit = "2", guards = {"doCustomNativeToVaListCopy(target)"})
    protected void doManagedNativeCustomCopy(VirtualFrame frame, LLVMManagedPointer target, @SuppressWarnings("unused") LLVMNativePointer source, long length,
                    @Cached @SuppressWarnings("unused") VAListPointerWrapperFactoryDelegate wrapperFactory,
                    @Bind("wrapperFactory.execute(source)") Object sourceWrapper,
                    @CachedLibrary("sourceWrapper") LLVMVaListLibrary vaListLibrary) {
        LLVMLinuxAarch64VaListStorage.checkMemmoveLength(target, length);
        vaListLibrary.copy(sourceWrapper, target.getObject(), frame);
    }

    @Specialization(guards = {"doCustomNativeToVaListCopy(target)"}, replaces = "doManagedNativeCustomCopy")
    protected void doManagedNativeCustomCopyUncached(VirtualFrame frame, LLVMManagedPointer target, LLVMNativePointer source, long length) {
        LLVMLinuxAarch64VaListStorage.checkMemmoveLength(target, length);
        Object sourceWrapper = VAListPointerWrapperFactoryDelegateNodeGen.getUncached().execute(source);
        LLVMVaListLibrary.getFactory().getUncached(sourceWrapper).copy(sourceWrapper, target.getObject(), frame.materialize());
    }

    @Specialization
    protected void doNativeManaged(LLVMNativePointer target, LLVMManagedPointer source, long length,
                    @Cached NativeProfiledMemMoveToNative nativeMemmove) {
        nativeMemmove.executeNative(target, source, length);
    }

    static boolean isManaged(LLVMPointer ptr) {
        return LLVMManagedPointer.isInstance(ptr);
    }

    @Specialization(guards = "isManaged(target) || isManaged(source)", replaces = {"doManagedNonAliasing", "doManagedCustomCopyUncached", "doManagedAliasing", "doManagedNative",
                    "doManagedNativeCustomCopyUncached", "doNativeManaged"})
    protected void doManagedSlowPath(VirtualFrame frame, LLVMPointer target, LLVMPointer source, long length) {
        doManagedSlowPathBoundary(frame.materialize(), target, source, length);
    }

    @TruffleBoundary
    protected void doManagedSlowPathBoundary(MaterializedFrame frame, LLVMPointer target, LLVMPointer source, long length) {
        ManagedMemMoveHelperNode helper = ManagedMemMoveHelperNode.createSlowPath(target, source);
        UnitSizeNode unitSizeNode = UnitSizeNodeGen.create();
        LLVMVaListLibrary vaListLibrary = LLVMVaListLibrary.getFactory().getUncached();
        VAListPointerWrapperFactoryDelegate wrapperFactory = VAListPointerWrapperFactoryDelegateNodeGen.getUncached();
        if (LLVMManagedPointer.isInstance(target) && LLVMManagedPointer.isInstance(source)) {
            // potentially aliasing
            doManagedAliasing(LLVMManagedPointer.cast(target), LLVMManagedPointer.cast(source), length, helper, unitSizeNode, CountingConditionProfile.getUncached());
        } else if (LLVMManagedPointer.isInstance(target) && LLVMManagedPointer.isInstance(source) &&
                        doCustomVaListCopy(LLVMManagedPointer.cast(target), LLVMManagedPointer.cast(source))) {
            LLVMLinuxAarch64VaListStorage.checkMemmoveLength(target, length);
            vaListLibrary.copy(wrapperFactory.execute(LLVMManagedPointer.cast(source).getObject()), LLVMManagedPointer.cast(target).getObject(), frame);
        } else if (LLVMManagedPointer.isInstance(target) && LLVMNativePointer.isInstance(source) &&
                        doCustomNativeToVaListCopy(LLVMManagedPointer.cast(target))) {
            LLVMLinuxAarch64VaListStorage.checkMemmoveLength(target, length);
            vaListLibrary.copy(wrapperFactory.execute(source), LLVMManagedPointer.cast(target).getObject(), frame);
        } else {
            copyForward(target, source, length, helper, unitSizeNode);
        }
    }

    @Specialization(replaces = {"doNativeManaged", "doManagedSlowPath"})
    protected void doNative(LLVMPointer target, LLVMPointer source, long length,
                    @Cached LLVMToNativeNode convertTarget,
                    @Cached NativeProfiledMemMoveToNative nativeMemmove) {
        nativeMemmove.executeNative(convertTarget.executeWithTarget(target), source, length);
    }
}

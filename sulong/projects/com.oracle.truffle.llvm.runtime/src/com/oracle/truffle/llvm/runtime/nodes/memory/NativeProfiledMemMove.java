/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMCopyTargetLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNode.UnitSizeNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeGen.UnitSizeNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class NativeProfiledMemMove extends LLVMNode implements LLVMMemMoveNode {
    private static void copyForward(LLVMPointer target, LLVMPointer source, long length,
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

    static boolean doCustomCopy(LLVMManagedPointer target, LLVMManagedPointer source, long length, LLVMCopyTargetLibrary copyTargetLib) {
        return source.getOffset() == 0 && target.getOffset() == 0 && copyTargetLib.canCopyFrom(target.getObject(), source.getObject(), length);
    }

    static boolean doCustomCopy(LLVMManagedPointer target, LLVMNativePointer source, long length, LLVMCopyTargetLibrary copyTargetLib) {
        return target.getOffset() == 0 && copyTargetLib.canCopyFrom(target.getObject(), source, length);
    }

    @Specialization(limit = "8", guards = {"target.getObject() != source.getObject()", "doCustomCopy(target, source, length, copyTargetLib)"})
    protected void doManagedCustomCopy(LLVMManagedPointer target, LLVMManagedPointer source, long length,
                    @CachedLibrary("target.getObject()") LLVMCopyTargetLibrary copyTargetLib) {
        copyTargetLib.copyFrom(target.getObject(), source.getObject(), length);
    }

    @Specialization(limit = "8", guards = {"helper.guard(target, source)", "target.getObject() != source.getObject()", "!doCustomCopy(target, source, length, copyTargetLib)"})
    protected void doManagedNonAliasing(LLVMManagedPointer target, LLVMManagedPointer source, long length,
                    @Cached("create(target, source)") ManagedMemMoveHelperNode helper,
                    @Cached UnitSizeNode unitSizeNode,
                    @SuppressWarnings("unused") @CachedLibrary("target.getObject()") LLVMCopyTargetLibrary copyTargetLib) {
        copyForward(target, source, length, helper, unitSizeNode);
    }

    @Specialization(limit = "8", guards = {"helper.guard(target, source)", "target.getObject() == source.getObject()", "!doCustomCopy(target, source, length, copyTargetLib)"})
    protected void doManagedAliasing(LLVMManagedPointer target, LLVMManagedPointer source, long length,
                    @Cached("create(target, source)") ManagedMemMoveHelperNode helper,
                    @Cached UnitSizeNode unitSizeNode,
                    @Cached("createCountingProfile()") ConditionProfile canCopyForward,
                    @SuppressWarnings("unused") @CachedLibrary("target.getObject()") LLVMCopyTargetLibrary copyTargetLib) {
        if (canCopyForward.profile(Long.compareUnsigned(target.getOffset() - source.getOffset(), length) >= 0)) {
            copyForward(target, source, length, helper, unitSizeNode);
        } else {
            copyBackward(target, source, length, helper, unitSizeNode);
        }
    }

    @Specialization(limit = "4", guards = {"helper.guard(target, source)", "!doCustomCopy(target, source, length, copyTargetLib)"})
    protected void doManagedNative(LLVMManagedPointer target, LLVMNativePointer source, long length,
                    @Cached("create(target, source)") ManagedMemMoveHelperNode helper,
                    @Cached UnitSizeNode unitSizeNode,
                    @SuppressWarnings("unused") @CachedLibrary("target.getObject()") LLVMCopyTargetLibrary copyTargetLib) {
        copyForward(target, source, length, helper, unitSizeNode);
    }

    @Specialization(limit = "4", guards = {"doCustomCopy(target, source, length, copyTargetLib)"})
    protected void doManagedNativeCustomCopy(LLVMManagedPointer target, LLVMNativePointer source, long length,
                    @CachedLibrary("target.getObject()") LLVMCopyTargetLibrary copyTargetLib) {
        copyTargetLib.copyFrom(target.getObject(), source, length);
    }

    @Specialization(limit = "4", guards = "helper.guard(target, source)")
    protected void doNativeManaged(LLVMNativePointer target, LLVMManagedPointer source, long length,
                    @Cached("create(target, source)") ManagedMemMoveHelperNode helper,
                    @Cached UnitSizeNode unitSizeNode) {
        copyForward(target, source, length, helper, unitSizeNode);
    }

    static boolean isManaged(LLVMPointer ptr) {
        return LLVMManagedPointer.isInstance(ptr);
    }

    @Specialization(guards = "isManaged(target) || isManaged(source)", replaces = {"doManagedNonAliasing", "doManagedCustomCopy", "doManagedAliasing", "doManagedNative", "doManagedNativeCustomCopy",
                    "doNativeManaged"})
    @TruffleBoundary
    protected void doManagedSlowPath(LLVMPointer target, LLVMPointer source, long length) {
        ManagedMemMoveHelperNode helper = ManagedMemMoveHelperNode.createSlowPath(target, source);
        UnitSizeNode unitSizeNode = UnitSizeNodeGen.create();
        LLVMCopyTargetLibrary copyTargetLib = LLVMCopyTargetLibrary.getFactory().getUncached();
        if (LLVMManagedPointer.isInstance(target) && LLVMManagedPointer.isInstance(source)) {
            // potentially aliasing
            doManagedAliasing(LLVMManagedPointer.cast(target), LLVMManagedPointer.cast(source), length, helper, unitSizeNode, ConditionProfile.getUncached(), copyTargetLib);
        } else if (LLVMManagedPointer.isInstance(target) && LLVMManagedPointer.isInstance(source) &&
                        doCustomCopy(LLVMManagedPointer.cast(target), LLVMManagedPointer.cast(source), length, copyTargetLib)) {
            copyTargetLib.copyFrom(LLVMManagedPointer.cast(target).getObject(), LLVMManagedPointer.cast(source).getObject(), length);
        } else if (LLVMManagedPointer.isInstance(target) && LLVMNativePointer.isInstance(source) &&
                        doCustomCopy(LLVMManagedPointer.cast(target), LLVMNativePointer.cast(source), length, copyTargetLib)) {
            copyTargetLib.copyFrom(LLVMManagedPointer.cast(target).getObject(), source, length);
        } else {
            copyForward(target, source, length, helper, unitSizeNode);
        }
    }

    protected static final long MAX_JAVA_LEN = 256;

    @Specialization(guards = "length <= MAX_JAVA_LEN")
    protected void doInJava(Object target, Object source, long length,
                    @Cached LLVMToNativeNode convertTarget,
                    @Cached LLVMToNativeNode convertSource) {
        LLVMMemory memory = getLanguage().getLLVMMemory();
        LLVMNativePointer t = convertTarget.executeWithTarget(target);
        LLVMNativePointer s = convertSource.executeWithTarget(source);
        long targetPointer = t.asNative();
        long sourcePointer = s.asNative();

        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, length > 0 && sourcePointer != targetPointer)) {
            // the unsigned comparison replaces
            // sourcePointer + length <= targetPointer || targetPointer < sourcePointer
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, Long.compareUnsigned(targetPointer - sourcePointer, length) >= 0)) {
                copyForward(memory, targetPointer, sourcePointer, length);
            } else {
                copyBackward(memory, targetPointer, sourcePointer, length);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Specialization(replaces = "doInJava")
    protected void doNative(Object target, Object source, long length,
                    @Cached LLVMToNativeNode convertTarget,
                    @Cached LLVMToNativeNode convertSource) {
        getLanguage().getLLVMMemory().copyMemory(this, convertSource.executeWithTarget(source).asNative(), convertTarget.executeWithTarget(target).asNative(), length);
    }

    private void copyForward(LLVMMemory memory, long target, long source, long length) {
        long targetPointer = target;
        long sourcePointer = source;
        long i64ValuesToWrite = length >> 3;
        for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i64ValuesToWrite); i++) {
            long v64 = memory.getI64(this, sourcePointer);
            memory.putI64(this, targetPointer, v64);
            targetPointer += 8;
            sourcePointer += 8;
        }

        long i8ValuesToWrite = length & 0x07;
        for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i8ValuesToWrite); i++) {
            byte value = memory.getI8(this, sourcePointer);
            memory.putI8(this, targetPointer, value);
            targetPointer++;
            sourcePointer++;
        }
    }

    private void copyBackward(LLVMMemory memory, long target, long source, long length) {
        long targetPointer = target + length;
        long sourcePointer = source + length;
        long i64ValuesToWrite = length >> 3;
        for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i64ValuesToWrite); i++) {
            targetPointer -= 8;
            sourcePointer -= 8;
            long v64 = memory.getI64(this, sourcePointer);
            memory.putI64(this, targetPointer, v64);
        }

        long i8ValuesToWrite = length & 0x07;
        for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i8ValuesToWrite); i++) {
            targetPointer--;
            sourcePointer--;
            byte value = memory.getI8(this, sourcePointer);
            memory.putI8(this, targetPointer, value);
        }
    }
}

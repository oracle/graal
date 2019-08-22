/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class NativeProfiledMemMove extends LLVMNode implements LLVMMemMoveNode {
    protected static final long MAX_JAVA_LEN = 256;

    @CompilationFinal private boolean inJava = true;

    @Specialization(limit = "3", guards = {"nativeWrite.isWritable(target.getObject())", "nativeRead.isReadable(source.getObject())", "target.getObject() != source.getObject()"})
    protected void doManagedNonAliasing(LLVMManagedPointer target, LLVMManagedPointer source, long length,
                    @CachedLibrary("target.getObject()") LLVMManagedWriteLibrary nativeWrite,
                    @CachedLibrary("source.getObject()") LLVMManagedReadLibrary nativeRead) {
        for (long i = 0; i < length; i++) {
            byte value = nativeRead.readI8(source.getObject(), source.getOffset() + i);
            nativeWrite.writeI8(target.getObject(), target.getOffset() + i, value);
        }
    }

    @Specialization(limit = "3", guards = {"nativeWrite.isWritable(target.getObject())", "nativeRead.isReadable(source.getObject())", "target.getObject() == source.getObject()"})
    protected void doManagedAliasing(LLVMManagedPointer target, LLVMManagedPointer source, long length,
                    @CachedLibrary("target.getObject()") LLVMManagedWriteLibrary nativeWrite,
                    @CachedLibrary("source.getObject()") LLVMManagedReadLibrary nativeRead,
                    @Cached("createCountingProfile()") ConditionProfile canCopyForward) {
        if (canCopyForward.profile(Long.compareUnsigned(target.getOffset() - source.getOffset(), length) >= 0)) {
            // forward
            for (long i = 0; i < length; i++) {
                byte value = nativeRead.readI8(source.getObject(), source.getOffset() + i);
                nativeWrite.writeI8(target.getObject(), target.getOffset() + i, value);
            }
        } else {
            // backward
            for (long i = length - 1; i >= 0; i--) {
                byte value = nativeRead.readI8(source.getObject(), source.getOffset() + i);
                nativeWrite.writeI8(target.getObject(), target.getOffset() + i, value);
            }
        }
    }

    @Specialization(limit = "3", guards = "nativeWrite.isWritable(target.getObject())")
    protected void doManaged(LLVMManagedPointer target, LLVMNativePointer source, long length,
                    @Cached("getLLVMMemory()") LLVMMemory memory,
                    @CachedLibrary("target.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        long sourceAddress = source.asNative();
        for (long i = 0; i < length; i++) {
            byte value = memory.getI8(sourceAddress++);
            nativeWrite.writeI8(target.getObject(), target.getOffset() + i, value);
        }
    }

    @Specialization(limit = "3", guards = "nativeRead.isReadable(source.getObject())")
    protected void doManaged(LLVMNativePointer target, LLVMManagedPointer source, long length,
                    @Cached("getLLVMMemory()") LLVMMemory memory,
                    @CachedLibrary("source.getObject()") LLVMManagedReadLibrary nativeRead) {
        long targetAddress = target.asNative();
        for (long i = 0; i < length; i++) {
            byte value = nativeRead.readI8(source.getObject(), source.getOffset() + i);
            memory.putI8(targetAddress++, value);
        }
    }

    @Specialization
    protected void doNative(Object target, Object source, long length,
                    @Cached("getLLVMMemory()") LLVMMemory memory,
                    @Cached LLVMToNativeNode convertTarget,
                    @Cached LLVMToNativeNode convertSource) {
        memmove(memory, convertTarget.executeWithTarget(target), convertSource.executeWithTarget(source), length);
    }

    private void memmove(LLVMMemory memory, LLVMNativePointer target, LLVMNativePointer source, long length) {
        long targetPointer = target.asNative();
        long sourcePointer = source.asNative();
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, length > 0 && sourcePointer != targetPointer)) {
            if (inJava) {
                if (length <= MAX_JAVA_LEN) {
                    // the unsigned comparison replaces
                    // sourcePointer + length <= targetPointer || targetPointer < sourcePointer
                    if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, Long.compareUnsigned(targetPointer - sourcePointer, length) >= 0)) {
                        copyForward(memory, targetPointer, sourcePointer, length);
                    } else {
                        copyBackward(memory, targetPointer, sourcePointer, length);
                    }
                    return;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    inJava = false;
                }
            }
            nativeMemCopy(memory, target, source, length);
        }
    }

    private static void copyForward(LLVMMemory memory, long target, long source, long length) {
        long targetPointer = target;
        long sourcePointer = source;
        long i64ValuesToWrite = length >> 3;
        for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i64ValuesToWrite); i++) {
            long v64 = memory.getI64(sourcePointer);
            memory.putI64(targetPointer, v64);
            targetPointer += 8;
            sourcePointer += 8;
        }

        long i8ValuesToWrite = length & 0x07;
        for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i8ValuesToWrite); i++) {
            byte value = memory.getI8(sourcePointer);
            memory.putI8(targetPointer, value);
            targetPointer++;
            sourcePointer++;
        }
    }

    private static void copyBackward(LLVMMemory memory, long target, long source, long length) {
        long targetPointer = target + length;
        long sourcePointer = source + length;
        long i64ValuesToWrite = length >> 3;
        for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i64ValuesToWrite); i++) {
            targetPointer -= 8;
            sourcePointer -= 8;
            long v64 = memory.getI64(sourcePointer);
            memory.putI64(targetPointer, v64);
        }

        long i8ValuesToWrite = length & 0x07;
        for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i8ValuesToWrite); i++) {
            targetPointer--;
            sourcePointer--;
            byte value = memory.getI8(sourcePointer);
            memory.putI8(targetPointer, value);
        }
    }

    @SuppressWarnings("deprecation")
    private static void nativeMemCopy(LLVMMemory memory, LLVMNativePointer target, LLVMNativePointer source, long length) {
        memory.copyMemory(source.asNative(), target.asNative(), length);
    }
}

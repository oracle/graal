/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class, value = "destination")
@NodeChild(type = LLVMExpressionNode.class, value = "source")
@NodeChild(type = LLVMExpressionNode.class, value = "length")
@NodeChild(type = LLVMExpressionNode.class, value = "isVolatile")
public abstract class LLVMMemCopy extends LLVMBuiltin {

    @Child private LLVMMemMoveNode memMove;

    public LLVMMemCopy(LLVMMemMoveNode memMove) {
        this.memMove = memMove;
    }

    // TODO: remove duplication for length argument with a cast node

    @Specialization
    protected Object doVoid(LLVMVirtualAllocationAddress target, LLVMVirtualAllocationAddress source, int length, boolean isVolatile, @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess arrayAccess) {
        return doVoid(target, source, (long) length, isVolatile, arrayAccess);
    }

    @Specialization
    protected Object doVoid(LLVMVirtualAllocationAddress target, LLVMNativePointer source, int length, boolean isVolatile, @Cached("getLLVMMemory()") LLVMMemory memory,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess arrayAccess) {
        return doVoid(target, source, (long) length, isVolatile, memory, arrayAccess);
    }

    @Specialization
    protected Object doVoid(LLVMNativePointer target, LLVMVirtualAllocationAddress source, int length, boolean isVolatile, @Cached("getLLVMMemory()") LLVMMemory memory,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess arrayAccess) {
        return doVoid(target, source, (long) length, isVolatile, memory, arrayAccess);
    }

    @Specialization
    protected Object doVoid(LLVMPointer target, LLVMPointer source, int length, boolean isVolatile) {
        return doVoid(target, source, (long) length, isVolatile);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object doVoid(LLVMVirtualAllocationAddress target, LLVMVirtualAllocationAddress source, long length, boolean isVolatile,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess arrayAccess) {
        copy(arrayAccess, target, source, length);
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object doVoid(LLVMVirtualAllocationAddress target, LLVMNativePointer source, long length, boolean isVolatile, @Cached("getLLVMMemory()") LLVMMemory memory,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess arrayAccess) {
        copy(arrayAccess, memory, target, source.asNative(), length);
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object doVoid(LLVMNativePointer target, LLVMVirtualAllocationAddress source, long length, boolean isVolatile, @Cached("getLLVMMemory()") LLVMMemory memory,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess arrayAccess) {
        copy(arrayAccess, memory, target.asNative(), source, length);
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object doVoid(LLVMPointer target, LLVMPointer source, long length, boolean isVolatile) {
        memMove.executeWithTarget(target, source, length);
        return null;
    }

    private static void copy(UnsafeArrayAccess arrayAccess, LLVMMemory memory, LLVMVirtualAllocationAddress target, long source, long length) {
        long sourcePointer = source;
        LLVMVirtualAllocationAddress targetAddress = target;
        for (long i = 0; i < length; i++) {
            byte value = memory.getI8(sourcePointer);
            targetAddress.writeI8(arrayAccess, value);
            targetAddress = targetAddress.increment(1);
            sourcePointer++;
        }
    }

    private static void copy(UnsafeArrayAccess arrayAccess, LLVMMemory memory, long target, LLVMVirtualAllocationAddress source, long length) {
        LLVMVirtualAllocationAddress sourcePointer = source;
        long targetAddress = target;
        for (long i = 0; i < length; i++) {
            byte value = sourcePointer.getI8(arrayAccess);
            sourcePointer = sourcePointer.increment(1);
            memory.putI8(targetAddress, value);
            targetAddress++;
        }
    }

    private static void copy(UnsafeArrayAccess memory, LLVMVirtualAllocationAddress target, LLVMVirtualAllocationAddress source, long length) {
        LLVMVirtualAllocationAddress sourcePointer = source;
        LLVMVirtualAllocationAddress targetAddress = target;
        for (long i = 0; i < length; i++) {
            byte value = sourcePointer.getI8(memory);
            sourcePointer = sourcePointer.increment(1);
            targetAddress.writeI8(memory, value);
            targetAddress = targetAddress.increment(1);
        }
    }
}

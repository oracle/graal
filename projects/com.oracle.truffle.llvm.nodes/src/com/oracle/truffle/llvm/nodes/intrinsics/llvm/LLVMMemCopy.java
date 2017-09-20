/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMProfiledMemMove;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMMemCopy {

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class, value = "destination"), @NodeChild(type = LLVMExpressionNode.class, value = "source"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "length"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "align"), @NodeChild(type = LLVMExpressionNode.class, value = "isVolatile")})
    public abstract static class LLVMMemI32Copy extends LLVMBuiltin {

        private final LLVMProfiledMemMove profiledMemMove = new LLVMProfiledMemMove();

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMVirtualAllocationAddress target, LLVMVirtualAllocationAddress source, int length, int align, boolean isVolatile) {
            LLVMMemCopy.copy(target, source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMVirtualAllocationAddress target, LLVMAddress source, int length, int align, boolean isVolatile) {
            LLVMMemCopy.copy(target, source.getVal(), length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMAddress target, LLVMVirtualAllocationAddress source, int length, int align, boolean isVolatile) {
            LLVMMemCopy.copy(target.getVal(), source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMAddress target, LLVMAddress source, int length, int align, boolean isVolatile) {
            profiledMemMove.memmove(target, source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMGlobalVariable target, LLVMVirtualAllocationAddress source, int length, int align, boolean isVolatile,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMMemCopy.copy(globalAccess.getNativeLocation(target).getVal(), source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMVirtualAllocationAddress target, LLVMGlobalVariable source, int length, int align, boolean isVolatile,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMMemCopy.copy(target, globalAccess.getNativeLocation(source).getVal(), length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMGlobalVariable target, LLVMAddress source, int length, int align, boolean isVolatile, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            profiledMemMove.memmove(globalAccess.getNativeLocation(target), source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMAddress target, LLVMGlobalVariable source, int length, int align, boolean isVolatile, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            profiledMemMove.memmove(target, globalAccess.getNativeLocation(source), length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMGlobalVariable target, LLVMGlobalVariable source, int length, int align, boolean isVolatile,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess1, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess2) {
            profiledMemMove.memmove(globalAccess1.getNativeLocation(target), globalAccess2.getNativeLocation(source), length);
            return null;
        }

    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class, value = "destination"), @NodeChild(type = LLVMExpressionNode.class, value = "source"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "length"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "align"), @NodeChild(type = LLVMExpressionNode.class, value = "isVolatile")})
    public abstract static class LLVMMemI64Copy extends LLVMBuiltin {

        private final LLVMProfiledMemMove profiledMemMove = new LLVMProfiledMemMove();

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMVirtualAllocationAddress target, LLVMVirtualAllocationAddress source, long length, int align, boolean isVolatile) {
            LLVMMemCopy.copy(target, source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMVirtualAllocationAddress target, LLVMAddress source, long length, int align, boolean isVolatile) {
            LLVMMemCopy.copy(target, source.getVal(), length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMAddress target, LLVMVirtualAllocationAddress source, long length, int align, boolean isVolatile) {
            LLVMMemCopy.copy(target.getVal(), source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMAddress target, LLVMAddress source, long length, int align, boolean isVolatile) {
            profiledMemMove.memmove(target, source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMGlobalVariable target, LLVMVirtualAllocationAddress source, long length, int align, boolean isVolatile,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMMemCopy.copy(globalAccess.getNativeLocation(target).getVal(), source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMVirtualAllocationAddress target, LLVMGlobalVariable source, long length, int align, boolean isVolatile,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMMemCopy.copy(target, globalAccess.getNativeLocation(source).getVal(), length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMGlobalVariable target, LLVMAddress source, long length, int align, boolean isVolatile, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            profiledMemMove.memmove(globalAccess.getNativeLocation(target), source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMAddress target, LLVMGlobalVariable source, long length, int align, boolean isVolatile, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            profiledMemMove.memmove(target, globalAccess.getNativeLocation(source), length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMGlobalVariable target, LLVMGlobalVariable source, long length, int align, boolean isVolatile,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess1, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess2) {
            profiledMemMove.memmove(globalAccess1.getNativeLocation(target), globalAccess2.getNativeLocation(source), length);
            return null;
        }

    }

    private static void copy(LLVMVirtualAllocationAddress target, long source, long length) {
        long sourcePointer = source;
        LLVMVirtualAllocationAddress targetAddress = target;
        for (long i = 0; i < length; i++) {
            byte value = LLVMMemory.getI8(sourcePointer);
            targetAddress.writeI8(value);
            targetAddress = targetAddress.increment(1);
            sourcePointer++;
        }
    }

    private static void copy(long target, LLVMVirtualAllocationAddress source, long length) {
        LLVMVirtualAllocationAddress sourcePointer = source;
        long targetAddress = target;
        for (long i = 0; i < length; i++) {
            byte value = sourcePointer.getI8();
            sourcePointer = sourcePointer.increment(1);
            LLVMMemory.putI8(targetAddress, value);
            targetAddress++;
        }
    }

    private static void copy(LLVMVirtualAllocationAddress target, LLVMVirtualAllocationAddress source, long length) {
        LLVMVirtualAllocationAddress sourcePointer = source;
        LLVMVirtualAllocationAddress targetAddress = target;
        for (long i = 0; i < length; i++) {
            byte value = sourcePointer.getI8();
            sourcePointer = sourcePointer.increment(1);
            targetAddress.writeI8(value);
            targetAddress = targetAddress.increment(1);
        }
    }

}

/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemoryOpNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMReallocNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMMemoryIntrinsic extends LLVMExpressionNode {

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMalloc extends LLVMMemoryIntrinsic {

        @Specialization
        protected LLVMNativePointer doVoid(int size,
                        @Cached BranchProfile outOfMemory) {
            try {
                return getLanguage().getLLVMMemory().allocateMemory(this, size);
            } catch (OutOfMemoryError e) {
                outOfMemory.enter();
                return LLVMNativePointer.createNull();
            }
        }

        @Specialization
        protected LLVMNativePointer doVoid(long size,
                        @Cached BranchProfile outOfMemory) {
            try {
                return getLanguage().getLLVMMemory().allocateMemory(this, size);
            } catch (OutOfMemoryError e) {
                outOfMemory.enter();
                return LLVMNativePointer.createNull();
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMCalloc extends LLVMMemoryIntrinsic {
        @Child private LLVMMemSetNode memSet;

        public LLVMCalloc(LLVMMemSetNode memSet) {
            this.memSet = memSet;
        }

        @Specialization
        protected LLVMNativePointer doVoid(int n, int size,
                        @Cached BranchProfile outOfMemory) {
            try {
                long length = Math.multiplyExact(n, size);
                LLVMNativePointer address = getLanguage().getLLVMMemory().allocateMemory(this, length);
                memSet.executeWithTarget(address, (byte) 0, length);
                return address;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemory.enter();
                return LLVMNativePointer.createNull();
            }
        }

        @Specialization
        protected LLVMNativePointer doVoid(long n, long size,
                        @Cached BranchProfile outOfMemory) {
            try {
                long length = Math.multiplyExact(n, size);
                LLVMNativePointer address = getLanguage().getLLVMMemory().allocateMemory(this, length);
                memSet.executeWithTarget(address, (byte) 0, length);
                return address;
            } catch (OutOfMemoryError | ArithmeticException e) {
                outOfMemory.enter();
                return LLVMNativePointer.createNull();
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMRealloc extends LLVMMemoryIntrinsic {

        public abstract LLVMNativePointer executeWithTarget(LLVMNativePointer addr, Object size);

        @Specialization
        protected LLVMNativePointer doVoid(LLVMNativePointer addr, int size,
                        @Cached BranchProfile outOfMemory) {
            return doVoid(addr, (long) size, outOfMemory);
        }

        @Specialization
        @SuppressWarnings("deprecation")
        protected LLVMNativePointer doVoid(LLVMNativePointer addr, long size,
                        @Cached BranchProfile outOfMemory) {
            try {
                return getLanguage().getLLVMMemory().reallocateMemory(this, addr, size);
            } catch (OutOfMemoryError e) {
                outOfMemory.enter();
                return LLVMNativePointer.createNull();
            }
        }

        public static LLVMRealloc create() {
            return LLVMReallocNodeGen.create(null, null);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFree extends LLVMMemoryIntrinsic implements LLVMMemoryOpNode {

        @Specialization(guards = "address.isNull()")
        protected Object doNull(@SuppressWarnings("unused") LLVMNativePointer address) {
            // nothing to do
            return null;
        }

        @Specialization
        protected Object doVoid(LLVMNativePointer address) {
            getLanguage().getLLVMMemory().free(this, address);
            return null;
        }
    }
}

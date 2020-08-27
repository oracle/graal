/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory.rmw;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(type = LLVMExpressionNode.class, value = "pointerNode")
@NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
public abstract class LLVMI32RMWNode extends LLVMExpressionNode {

    protected static LLVMI32StoreNode createWrite() {
        return LLVMI32StoreNodeGen.create(null, null);
    }

    public abstract static class LLVMI32RMWXchgNode extends LLVMI32RMWNode {

        @Specialization
        protected int doOp(LLVMNativePointer address, int value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndSetI32(this, address, value);
        }

        @Specialization
        protected int doOp(LLVMManagedPointer address, int value,
                        @Cached LLVMI32LoadNode read,
                        @Cached("createWrite()") LLVMI32StoreNode write) {
            synchronized (address.getObject()) {
                int result = (int) read.executeWithTarget(address);
                write.executeWithTarget(address, value);
                return result;
            }
        }
    }

    public abstract static class LLVMI32RMWAddNode extends LLVMI32RMWNode {

        @Specialization
        protected int doOp(LLVMNativePointer address, int value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndAddI32(this, address, value);
        }

        @Specialization
        protected int doOp(LLVMManagedPointer address, int value,
                        @Cached LLVMI32LoadNode read,
                        @Cached("createWrite()") LLVMI32StoreNode write) {
            synchronized (address.getObject()) {
                int result = (int) read.executeWithTarget(address);
                write.executeWithTarget(address, result + value);
                return result;
            }
        }
    }

    public abstract static class LLVMI32RMWSubNode extends LLVMI32RMWNode {

        @Specialization
        protected int doOp(LLVMNativePointer address, int value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndSubI32(this, address, value);
        }

        @Specialization
        protected int doOp(LLVMManagedPointer address, int value,
                        @Cached LLVMI32LoadNode read,
                        @Cached("createWrite()") LLVMI32StoreNode write) {
            synchronized (address.getObject()) {
                int result = (int) read.executeWithTarget(address);
                write.executeWithTarget(address, result - value);
                return result;
            }
        }
    }

    public abstract static class LLVMI32RMWAndNode extends LLVMI32RMWNode {

        @Specialization
        protected int doOp(LLVMNativePointer address, int value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI32(this, address, value, (a, b) -> a & b);
        }

        @Specialization
        protected int doOp(LLVMManagedPointer address, int value,
                        @Cached LLVMI32LoadNode read,
                        @Cached("createWrite()") LLVMI32StoreNode write) {
            synchronized (address.getObject()) {
                int result = (int) read.executeWithTarget(address);
                write.executeWithTarget(address, result & value);
                return result;
            }
        }
    }

    public abstract static class LLVMI32RMWNandNode extends LLVMI32RMWNode {

        @Specialization
        protected int doOp(LLVMNativePointer address, int value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI32(this, address, value, (a, b) -> ~(a & b));
        }

        @Specialization
        protected int doOp(LLVMManagedPointer address, int value,
                        @Cached LLVMI32LoadNode read,
                        @Cached("createWrite()") LLVMI32StoreNode write) {
            synchronized (address.getObject()) {
                int result = (int) read.executeWithTarget(address);
                write.executeWithTarget(address, ~(result & value));
                return result;
            }
        }
    }

    public abstract static class LLVMI32RMWOrNode extends LLVMI32RMWNode {

        @Specialization
        protected int doOp(LLVMNativePointer address, int value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI32(this, address, value, (a, b) -> a | b);
        }

        @Specialization
        protected int doOp(LLVMManagedPointer address, int value,
                        @Cached LLVMI32LoadNode read,
                        @Cached("createWrite()") LLVMI32StoreNode write) {
            synchronized (address.getObject()) {
                int result = (int) read.executeWithTarget(address);
                write.executeWithTarget(address, result | value);
                return result;
            }
        }
    }

    public abstract static class LLVMI32RMWXorNode extends LLVMI32RMWNode {

        @Specialization
        protected int doOp(LLVMNativePointer address, int value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI32(this, address, value, (a, b) -> a ^ b);
        }

        @Specialization
        protected int doOp(LLVMManagedPointer address, int value,
                        @Cached LLVMI32LoadNode read,
                        @Cached("createWrite()") LLVMI32StoreNode write) {
            synchronized (address.getObject()) {
                int result = (int) read.executeWithTarget(address);
                write.executeWithTarget(address, result ^ value);
                return result;
            }
        }
    }
}

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
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(type = LLVMExpressionNode.class, value = "pointerNode")
@NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
public abstract class LLVMI1RMWNode extends LLVMExpressionNode {

    protected static LLVMI1StoreNode createWrite() {
        return LLVMI1StoreNodeGen.create(null, null);
    }

    public abstract static class LLVMI1RMWXchgNode extends LLVMI1RMWNode {

        @Specialization
        protected boolean doOp(LLVMNativePointer address, boolean value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI1(this, address, value, (a, b) -> b);
        }

        @Specialization
        protected boolean doOp(LLVMManagedPointer address, boolean value,
                        @Cached LLVMI1LoadNode read,
                        @Cached("createWrite()") LLVMI1StoreNode write) {
            synchronized (address.getObject()) {
                boolean result = (boolean) read.executeWithTarget(address);
                write.executeWithTarget(address, value);
                return result;
            }
        }
    }

    public abstract static class LLVMI1RMWAddNode extends LLVMI1RMWNode {

        @Specialization
        protected boolean doOp(LLVMNativePointer address, boolean value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI1(this, address, value, (a, b) -> a ^ b);
        }

        @Specialization
        protected boolean doOp(LLVMManagedPointer address, boolean value,
                        @Cached LLVMI1LoadNode read,
                        @Cached("createWrite()") LLVMI1StoreNode write) {
            synchronized (address.getObject()) {
                boolean result = (boolean) read.executeWithTarget(address);
                write.executeWithTarget(address, result ^ value);
                return result;
            }
        }
    }

    public abstract static class LLVMI1RMWSubNode extends LLVMI1RMWNode {

        @Specialization
        protected boolean doOp(LLVMNativePointer address, boolean value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI1(this, address, value, (a, b) -> a ^ b);
        }

        @Specialization
        protected boolean doOp(LLVMManagedPointer address, boolean value,
                        @Cached LLVMI1LoadNode read,
                        @Cached("createWrite()") LLVMI1StoreNode write) {
            synchronized (address.getObject()) {
                boolean result = (boolean) read.executeWithTarget(address);
                write.executeWithTarget(address, result ^ value);
                return result;
            }
        }
    }

    public abstract static class LLVMI1RMWAndNode extends LLVMI1RMWNode {

        @Specialization
        protected boolean doOp(LLVMNativePointer address, boolean value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI1(this, address, value, (a, b) -> a & b);
        }

        @Specialization
        protected boolean doOp(LLVMManagedPointer address, boolean value,
                        @Cached LLVMI1LoadNode read,
                        @Cached("createWrite()") LLVMI1StoreNode write) {
            synchronized (address.getObject()) {
                boolean result = (boolean) read.executeWithTarget(address);
                write.executeWithTarget(address, result & value);
                return result;
            }
        }
    }

    public abstract static class LLVMI1RMWNandNode extends LLVMI1RMWNode {

        @Specialization
        protected boolean doOp(LLVMNativePointer address, boolean value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI1(this, address, value, (a, b) -> !(a & b));
        }

        @Specialization
        protected boolean doOp(LLVMManagedPointer address, boolean value,
                        @Cached LLVMI1LoadNode read,
                        @Cached("createWrite()") LLVMI1StoreNode write) {
            synchronized (address.getObject()) {
                boolean result = (boolean) read.executeWithTarget(address);
                write.executeWithTarget(address, !(result & value));
                return result;
            }
        }
    }

    public abstract static class LLVMI1RMWOrNode extends LLVMI1RMWNode {

        @Specialization
        protected boolean doOp(LLVMNativePointer address, boolean value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI1(this, address, value, (a, b) -> a | b);
        }

        @Specialization
        protected boolean doOp(LLVMManagedPointer address, boolean value,
                        @Cached LLVMI1LoadNode read,
                        @Cached("createWrite()") LLVMI1StoreNode write) {
            synchronized (address.getObject()) {
                boolean result = (boolean) read.executeWithTarget(address);
                write.executeWithTarget(address, result | value);
                return result;
            }
        }
    }

    public abstract static class LLVMI1RMWXorNode extends LLVMI1RMWNode {

        @Specialization
        protected boolean doOp(LLVMNativePointer address, boolean value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI1(this, address, value, (a, b) -> a ^ b);
        }

        @Specialization
        protected boolean doOp(LLVMManagedPointer address, boolean value,
                        @Cached LLVMI1LoadNode read,
                        @Cached("createWrite()") LLVMI1StoreNode write) {
            synchronized (address.getObject()) {
                boolean result = (boolean) read.executeWithTarget(address);
                write.executeWithTarget(address, result ^ value);
                return result;
            }
        }
    }
}

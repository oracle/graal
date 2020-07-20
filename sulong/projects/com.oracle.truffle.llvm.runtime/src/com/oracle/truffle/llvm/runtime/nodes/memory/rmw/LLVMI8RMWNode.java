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
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(type = LLVMExpressionNode.class, value = "pointerNode")
@NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
public abstract class LLVMI8RMWNode extends LLVMExpressionNode {

    protected static LLVMI8StoreNode createWrite() {
        return LLVMI8StoreNodeGen.create(null, null);
    }

    public abstract static class LLVMI8RMWXchgNode extends LLVMI8RMWNode {

        @Specialization
        protected byte doOp(LLVMNativePointer address, byte value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI8(this, address, value, (a, b) -> b);
        }

        @Specialization
        protected byte doOp(LLVMManagedPointer address, byte value,
                        @Cached LLVMI8LoadNode read,
                        @Cached("createWrite()") LLVMI8StoreNode write) {
            synchronized (address.getObject()) {
                byte result = (byte) read.executeWithTarget(address);
                write.executeWithTarget(address, value);
                return result;
            }
        }
    }

    public abstract static class LLVMI8RMWAddNode extends LLVMI8RMWNode {

        @Specialization
        protected byte doOp(LLVMNativePointer address, byte value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI8(this, address, value, (a, b) -> ((byte) (a + b)));
        }

        @Specialization
        protected byte doOp(LLVMManagedPointer address, byte value,
                        @Cached LLVMI8LoadNode read,
                        @Cached("createWrite()") LLVMI8StoreNode write) {
            synchronized (address.getObject()) {
                byte result = (byte) read.executeWithTarget(address);
                write.executeWithTarget(address, ((byte) (result + value)));
                return result;
            }
        }
    }

    public abstract static class LLVMI8RMWSubNode extends LLVMI8RMWNode {

        @Specialization
        protected byte doOp(LLVMNativePointer address, byte value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI8(this, address, value, (a, b) -> ((byte) (a - b)));
        }

        @Specialization
        protected byte doOp(LLVMManagedPointer address, byte value,
                        @Cached LLVMI8LoadNode read,
                        @Cached("createWrite()") LLVMI8StoreNode write) {
            synchronized (address.getObject()) {
                byte result = (byte) read.executeWithTarget(address);
                write.executeWithTarget(address, ((byte) (result - value)));
                return result;
            }
        }
    }

    public abstract static class LLVMI8RMWAndNode extends LLVMI8RMWNode {

        @Specialization
        protected byte doOp(LLVMNativePointer address, byte value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI8(this, address, value, (a, b) -> ((byte) (a & b)));
        }

        @Specialization
        protected byte doOp(LLVMManagedPointer address, byte value,
                        @Cached LLVMI8LoadNode read,
                        @Cached("createWrite()") LLVMI8StoreNode write) {
            synchronized (address.getObject()) {
                byte result = (byte) read.executeWithTarget(address);
                write.executeWithTarget(address, ((byte) (result & value)));
                return result;
            }
        }
    }

    public abstract static class LLVMI8RMWNandNode extends LLVMI8RMWNode {

        @Specialization
        protected byte doOp(LLVMNativePointer address, byte value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI8(this, address, value, (a, b) -> ((byte) ~(a & b)));
        }

        @Specialization
        protected byte doOp(LLVMManagedPointer address, byte value,
                        @Cached LLVMI8LoadNode read,
                        @Cached("createWrite()") LLVMI8StoreNode write) {
            synchronized (address.getObject()) {
                byte result = (byte) read.executeWithTarget(address);
                write.executeWithTarget(address, ((byte) ~(result & value)));
                return result;
            }
        }
    }

    public abstract static class LLVMI8RMWOrNode extends LLVMI8RMWNode {

        @Specialization
        protected byte doOp(LLVMNativePointer address, byte value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI8(this, address, value, (a, b) -> ((byte) (a | b)));
        }

        @Specialization
        protected byte doOp(LLVMManagedPointer address, byte value,
                        @Cached LLVMI8LoadNode read,
                        @Cached("createWrite()") LLVMI8StoreNode write) {
            synchronized (address.getObject()) {
                byte result = (byte) read.executeWithTarget(address);
                write.executeWithTarget(address, ((byte) (result | value)));
                return result;
            }
        }
    }

    public abstract static class LLVMI8RMWXorNode extends LLVMI8RMWNode {

        @Specialization
        protected byte doOp(LLVMNativePointer address, byte value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI8(this, address, value, (a, b) -> ((byte) (a ^ b)));
        }

        @Specialization
        protected byte doOp(LLVMManagedPointer address, byte value,
                        @Cached LLVMI8LoadNode read,
                        @Cached("createWrite()") LLVMI8StoreNode write) {
            synchronized (address.getObject()) {
                byte result = (byte) read.executeWithTarget(address);
                write.executeWithTarget(address, ((byte) (result ^ value)));
                return result;
            }
        }
    }
}

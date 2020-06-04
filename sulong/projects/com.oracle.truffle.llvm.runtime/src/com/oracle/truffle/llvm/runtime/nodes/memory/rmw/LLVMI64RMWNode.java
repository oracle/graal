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
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(type = LLVMExpressionNode.class, value = "pointerNode")
@NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
public abstract class LLVMI64RMWNode extends LLVMExpressionNode {

    protected static LLVMI64StoreNode createWrite() {
        return LLVMI64StoreNodeGen.create(null, null);
    }

    public abstract static class LLVMI64RMWXchgNode extends LLVMI64RMWNode {

        @Specialization
        protected long doOp(LLVMNativePointer address, long value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndSetI64(this, address, value);
        }

        @Specialization
        protected Object doOp(LLVMManagedPointer address, long value,
                        @Cached LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                Object result = read.executeWithTarget(address);
                write.executeWithTarget(address, value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWAddNode extends LLVMI64RMWNode {

        @Specialization
        protected long doOp(LLVMNativePointer address, long value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndAddI64(this, address, value);
        }

        @Specialization
        protected long doOp(LLVMManagedPointer address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                        @Cached LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = toNative.executeWithTarget(read.executeWithTarget(address)).asNative();
                write.executeWithTarget(address, result + value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWSubNode extends LLVMI64RMWNode {

        @Specialization
        protected long doOp(LLVMNativePointer address, long value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndSubI64(this, address, value);
        }

        @Specialization
        protected long doOp(LLVMManagedPointer address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                        @Cached LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = toNative.executeWithTarget(read.executeWithTarget(address)).asNative();
                write.executeWithTarget(address, result - value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWAndNode extends LLVMI64RMWNode {

        @Specialization
        protected long doOp(LLVMNativePointer address, long value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI64(this, address, value, (a, b) -> a & b);
        }

        @Specialization
        protected long doOp(LLVMManagedPointer address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                        @Cached LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = toNative.executeWithTarget(read.executeWithTarget(address)).asNative();
                write.executeWithTarget(address, result & value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWNandNode extends LLVMI64RMWNode {

        @Specialization
        protected long doOp(LLVMNativePointer address, long value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI64(this, address, value, (a, b) -> ~(a & b));
        }

        @Specialization
        protected long doOp(LLVMManagedPointer address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                        @Cached LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = toNative.executeWithTarget(read.executeWithTarget(address)).asNative();
                write.executeWithTarget(address, ~(result & value));
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWOrNode extends LLVMI64RMWNode {

        @Specialization
        protected long doOp(LLVMNativePointer address, long value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI64(this, address, value, (a, b) -> a | b);
        }

        @Specialization
        protected long doOp(LLVMManagedPointer address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                        @Cached LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = toNative.executeWithTarget(read.executeWithTarget(address)).asNative();
                write.executeWithTarget(address, result | value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWXorNode extends LLVMI64RMWNode {

        @Specialization
        protected long doOp(LLVMNativePointer address, long value,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getAndOpI64(this, address, value, (a, b) -> a ^ b);
        }

        @Specialization
        protected long doOp(LLVMManagedPointer address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                        @Cached LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = toNative.executeWithTarget(read.executeWithTarget(address)).asNative();
                write.executeWithTarget(address, result ^ value);
                return result;
            }
        }
    }
}

/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory.rmw;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;

@NodeChildren(value = {@NodeChild(type = LLVMExpressionNode.class, value = "pointerNode"), @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")})
public abstract class LLVMI64RMWNode extends LLVMExpressionNode {

    protected static LLVMI64LoadNode createRead() {
        return LLVMI64LoadNodeGen.create(null);
    }

    protected static LLVMI64StoreNode createWrite() {
        return LLVMI64StoreNodeGen.create(null, null);
    }

    public abstract static class LLVMI64RMWXchgNode extends LLVMI64RMWNode {
        @Specialization
        protected long doOp(LLVMGlobal address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            LLVMAddress adr = globalAccess.executeWithTarget(address);
            return memory.getAndSetI64(adr, value);
        }

        @Specialization
        protected long doOp(LLVMAddress address, long value,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAndSetI64(address, value);
        }

        @Specialization
        protected long doOp(LLVMTruffleObject address, long value,
                        @Cached("createRead()") LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = (long) read.executeWithTarget(address);
                write.executeWithTarget(address, value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWAddNode extends LLVMI64RMWNode {
        @Specialization
        protected long doOp(LLVMGlobal address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            LLVMAddress adr = globalAccess.executeWithTarget(address);
            return memory.getAndAddI64(adr, value);
        }

        @Specialization
        protected long doOp(LLVMAddress address, long value,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAndAddI64(address, value);
        }

        @Specialization
        protected long doOp(LLVMTruffleObject address, long value,
                        @Cached("createRead()") LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = (long) read.executeWithTarget(address);
                write.executeWithTarget(address, result + value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWSubNode extends LLVMI64RMWNode {
        @Specialization
        protected long doOp(LLVMGlobal address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            LLVMAddress adr = globalAccess.executeWithTarget(address);
            return memory.getAndSubI64(adr, value);
        }

        @Specialization
        protected long doOp(LLVMAddress address, long value,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAndSubI64(address, value);
        }

        @Specialization
        protected long doOp(LLVMTruffleObject address, long value,
                        @Cached("createRead()") LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = (long) read.executeWithTarget(address);
                write.executeWithTarget(address, result - value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWAndNode extends LLVMI64RMWNode {
        @Specialization
        protected long doOp(LLVMGlobal address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            LLVMAddress adr = globalAccess.executeWithTarget(address);
            return memory.getAndOpI64(adr, value, (a, b) -> a & b);
        }

        @Specialization
        protected long doOp(LLVMAddress address, long value,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAndOpI64(address, value, (a, b) -> a & b);
        }

        @Specialization
        protected long doOp(LLVMTruffleObject address, long value,
                        @Cached("createRead()") LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = (long) read.executeWithTarget(address);
                write.executeWithTarget(address, result & value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWNandNode extends LLVMI64RMWNode {
        @Specialization
        protected long doOp(LLVMGlobal address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            LLVMAddress adr = globalAccess.executeWithTarget(address);
            return memory.getAndOpI64(adr, value, (a, b) -> ~(a & b));
        }

        @Specialization
        protected long doOp(LLVMAddress address, long value,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAndOpI64(address, value, (a, b) -> ~(a & b));
        }

        @Specialization
        protected long doOp(LLVMTruffleObject address, long value,
                        @Cached("createRead()") LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = (long) read.executeWithTarget(address);
                write.executeWithTarget(address, ~(result & value));
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWOrNode extends LLVMI64RMWNode {
        @Specialization
        protected long doOp(LLVMGlobal address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            LLVMAddress adr = globalAccess.executeWithTarget(address);
            return memory.getAndOpI64(adr, value, (a, b) -> a | b);
        }

        @Specialization
        protected long doOp(LLVMAddress address, long value,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAndOpI64(address, value, (a, b) -> a | b);
        }

        @Specialization
        protected long doOp(LLVMTruffleObject address, long value,
                        @Cached("createRead()") LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = (long) read.executeWithTarget(address);
                write.executeWithTarget(address, result | value);
                return result;
            }
        }
    }

    public abstract static class LLVMI64RMWXorNode extends LLVMI64RMWNode {
        @Specialization
        protected long doOp(LLVMGlobal address, long value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            LLVMAddress adr = globalAccess.executeWithTarget(address);
            return memory.getAndOpI64(adr, value, (a, b) -> a ^ b);
        }

        @Specialization
        protected long doOp(LLVMAddress address, long value,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAndOpI64(address, value, (a, b) -> a ^ b);
        }

        @Specialization
        protected long doOp(LLVMTruffleObject address, long value,
                        @Cached("createRead()") LLVMI64LoadNode read,
                        @Cached("createWrite()") LLVMI64StoreNode write) {
            synchronized (address.getObject()) {
                long result = (long) read.executeWithTarget(address);
                write.executeWithTarget(address, result ^ value);
                return result;
            }
        }
    }
}

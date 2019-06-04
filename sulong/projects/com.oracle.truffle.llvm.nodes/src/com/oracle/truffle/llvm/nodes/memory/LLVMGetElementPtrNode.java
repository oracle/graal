/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.memory.LLVMGetElementPtrNodeGen.LLVMIncrementPointerNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
@NodeField(type = long.class, name = "typeWidth")
public abstract class LLVMGetElementPtrNode extends LLVMExpressionNode {

    @Child private LLVMIncrementPointerNode incrementNode = LLVMIncrementPointerNodeGen.create();

    public abstract long getTypeWidth();

    @Specialization
    protected Object longIncrement(Object addr, long val) {
        long incr = getTypeWidth() * val;
        return incrementNode.executeWithTarget(addr, incr);
    }

    @Specialization
    protected Object longIncrement(Object addr, LLVMNativePointer val) {
        long incr = getTypeWidth() * val.asNative();
        return incrementNode.executeWithTarget(addr, incr);
    }

    @Specialization
    protected Object intIncrement(Object addr, int val) {
        long incr = getTypeWidth() * val;
        return incrementNode.executeWithTarget(addr, incr);
    }

    public abstract static class LLVMIncrementPointerNode extends LLVMNode {

        public abstract Object executeWithTarget(Object addr, int val);

        public abstract Object executeWithTarget(Object addr, long val);

        public abstract Object executeWithTarget(Object addr, Object val);

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer addr, int incr) {
            return addr.increment(incr);
        }

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer addr, long incr) {
            return addr.increment(incr);
        }

        @Specialization
        protected LLVMVirtualAllocationAddress doTruffleObject(LLVMVirtualAllocationAddress addr, int incr) {
            return addr.increment(incr);
        }

        @Specialization
        protected LLVMVirtualAllocationAddress doTruffleObject(LLVMVirtualAllocationAddress addr, long incr) {
            return addr.increment(incr);
        }
    }
}

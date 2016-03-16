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
package com.oracle.truffle.llvm.nodes.impl.memory;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFrameUtil;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.types.LLVMAddress;

@NodeFields({@NodeField(type = int.class, name = "size"), @NodeField(type = int.class, name = "alignment"), @NodeField(type = LLVMContext.class, name = "context"),
                @NodeField(type = FrameSlot.class, name = "stackPointerSlot")})
public abstract class LLVMAllocInstruction extends LLVMAddressNode {

    abstract int getSize();

    abstract int getAlignment();

    abstract LLVMContext getContext();

    abstract FrameSlot getStackPointerSlot();

    public abstract static class LLVMAllocaInstruction extends LLVMAllocInstruction {
        @Specialization
        public LLVMAddress execute(VirtualFrame frame) {
            return LLVMFrameUtil.allocateMemory(getContext().getStack(), frame, getStackPointerSlot(), getSize(), getAlignment());
        }

    }

    @NodeChild(type = LLVMI32Node.class)
    public abstract static class LLVMI32AllocaInstruction extends LLVMAllocInstruction {
        @Specialization
        public LLVMAddress execute(VirtualFrame frame, int nr) {
            return LLVMFrameUtil.allocateMemory(getContext().getStack(), frame, getStackPointerSlot(), getSize() * nr, getAlignment());
        }
    }

    @NodeChild(type = LLVMI64Node.class)
    public abstract static class LLVMI64AllocaInstruction extends LLVMAllocInstruction {
        @Specialization
        public LLVMAddress execute(VirtualFrame frame, long nr) {
            return LLVMFrameUtil.allocateMemory(getContext().getStack(), frame, getStackPointerSlot(), (int) (getSize() * nr), getAlignment());
        }
    }

}

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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

@NodeChild(value = "value", type = LLVMExpressionNode.class)
public abstract class LLVMAMD64PushNode extends LLVMStatementNode {
    protected FrameSlot getStackPointerSlot() {
        CompilerAsserts.neverPartOfCompilation();
        return getRootNode().getFrameDescriptor().findFrameSlot(LLVMStack.FRAME_ID);
    }

    public abstract static class LLVMAMD64PushwNode extends LLVMAMD64PushNode {
        @Specialization
        protected void doVoid(VirtualFrame frame, short value,
                        @Cached("getStackPointerSlot()") FrameSlot slot,
                        @CachedLanguage LLVMLanguage language) {
            LLVMMemory memory = language.getLLVMMemory();
            StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, slot);
            long sp = basePointer.get(this, memory);
            sp -= LLVMExpressionNode.I16_SIZE_IN_BYTES;
            basePointer.set(sp);
            memory.putI16(this, sp, value);
        }
    }

    public abstract static class LLVMAMD64PushlNode extends LLVMAMD64PushNode {
        @Specialization
        protected void doVoid(VirtualFrame frame, int value,
                        @Cached("getStackPointerSlot()") FrameSlot slot,
                        @CachedLanguage LLVMLanguage language) {
            LLVMMemory memory = language.getLLVMMemory();
            StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, slot);
            long sp = basePointer.get(this, memory);
            sp -= LLVMExpressionNode.I32_SIZE_IN_BYTES;
            basePointer.set(sp);
            memory.putI32(this, sp, value);
        }
    }

    public abstract static class LLVMAMD64PushqNode extends LLVMAMD64PushNode {
        @Specialization
        protected void doVoid(VirtualFrame frame, long value,
                        @Cached("getStackPointerSlot()") FrameSlot slot,
                        @CachedLanguage LLVMLanguage language) {
            LLVMMemory memory = language.getLLVMMemory();
            StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, slot);
            long sp = basePointer.get(this, memory);
            sp -= LLVMExpressionNode.I64_SIZE_IN_BYTES;
            basePointer.set(sp);
            memory.putI64(this, sp, value);
        }
    }
}

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
package com.oracle.truffle.llvm.nodes.asm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.NeedsStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NeedsStack
public abstract class LLVMAMD64PopNode extends LLVMExpressionNode {
    @CompilationFinal private FrameSlot stackPointer;

    protected FrameSlot getStackPointerSlot() {
        if (stackPointer == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackPointer = getRootNode().getFrameDescriptor().findFrameSlot(LLVMStack.FRAME_ID);
        }
        return stackPointer;
    }

    public abstract static class LLVMAMD64PopwNode extends LLVMAMD64PopNode {
        @Override
        @Specialization
        public short executeI16(VirtualFrame frame) {
            FrameSlot slot = getStackPointerSlot();
            long sp = FrameUtil.getLongSafe(frame, slot);
            short value = LLVMMemory.getI16(sp);
            sp += 2;
            frame.setLong(slot, sp);
            return value;
        }
    }

    public abstract static class LLVMAMD64PoplNode extends LLVMAMD64PopNode {
        @Override
        @Specialization
        public int executeI32(VirtualFrame frame) {
            FrameSlot slot = getStackPointerSlot();
            long sp = FrameUtil.getLongSafe(frame, slot);
            int value = LLVMMemory.getI32(sp);
            sp += 4;
            frame.setLong(slot, sp);
            return value;
        }
    }

    public abstract static class LLVMAMD64PopqNode extends LLVMAMD64PopNode {
        @Override
        @Specialization
        public long executeI64(VirtualFrame frame) {
            FrameSlot slot = getStackPointerSlot();
            long sp = FrameUtil.getLongSafe(frame, slot);
            long value = LLVMMemory.getI64(sp);
            sp += 8;
            frame.setLong(slot, sp);
            return value;
        }
    }
}

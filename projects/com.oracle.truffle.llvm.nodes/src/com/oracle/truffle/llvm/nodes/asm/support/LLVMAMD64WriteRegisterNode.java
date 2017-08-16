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
package com.oracle.truffle.llvm.nodes.asm.support;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeField(name = "slot", type = FrameSlot.class)
public abstract class LLVMAMD64WriteRegisterNode extends Node {
    public static final long MASK_16 = 0xFFFFFFFFFFFF0000L;
    public static final long MASK_32 = 0xFFFFFFFF00000000L;

    private final FrameSlot slot;

    public LLVMAMD64WriteRegisterNode(FrameSlot slot) {
        this.slot = slot;
    }

    protected FrameSlot getSlot() {
        return slot;
    }

    public static class LLVMAMD64WriteI8RegisterNode extends LLVMAMD64WriteRegisterNode {
        @Child LLVMExpressionNode register;
        private final int shift;
        private final long mask;

        public LLVMAMD64WriteI8RegisterNode(FrameSlot slot, int shift, LLVMExpressionNode register) {
            super(slot);
            this.shift = shift;
            this.register = register;
            this.mask = ~((long) LLVMExpressionNode.I8_MASK << shift);
        }

        public void execute(VirtualFrame frame, byte value) {
            long reg = register.executeI64(frame);
            long val = (reg & mask) | (Byte.toUnsignedLong(value) << shift);
            getSlot().setKind(FrameSlotKind.Long);
            frame.setLong(getSlot(), val);
        }
    }

    public static class LLVMAMD64WriteI16RegisterNode extends LLVMAMD64WriteRegisterNode {
        @Child LLVMExpressionNode register;

        public LLVMAMD64WriteI16RegisterNode(FrameSlot slot, LLVMExpressionNode register) {
            super(slot);
            this.register = register;
        }

        public void execute(VirtualFrame frame, short value) {
            long reg = register.executeI64(frame);
            long val = (reg & MASK_16) | Short.toUnsignedLong(value);
            getSlot().setKind(FrameSlotKind.Long);
            frame.setLong(getSlot(), val);
        }
    }

    public static class LLVMAMD64WriteI32RegisterNode extends LLVMAMD64WriteRegisterNode {
        public LLVMAMD64WriteI32RegisterNode(FrameSlot slot) {
            super(slot);
        }

        public void execute(VirtualFrame frame, int value) {
            long val = Integer.toUnsignedLong(value);
            getSlot().setKind(FrameSlotKind.Long);
            frame.setLong(getSlot(), val);
        }
    }

    public static class LLVMAMD64WriteI64RegisterNode extends LLVMAMD64WriteRegisterNode {
        public LLVMAMD64WriteI64RegisterNode(FrameSlot slot) {
            super(slot);
        }

        public void execute(VirtualFrame frame, long value) {
            getSlot().setKind(FrameSlotKind.Long);
            frame.setLong(getSlot(), value);
        }
    }
}

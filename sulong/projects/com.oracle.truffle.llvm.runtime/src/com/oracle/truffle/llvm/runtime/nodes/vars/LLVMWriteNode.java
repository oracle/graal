/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.vars;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMVector;

@NodeChild(value = "valueNode", type = LLVMExpressionNode.class)
public abstract class LLVMWriteNode extends LLVMStatementNode {

    protected final FrameSlot slot;

    protected LLVMWriteNode(FrameSlot slot) {
        assert slot != null;
        this.slot = slot;
    }

    public abstract void executeWithTarget(VirtualFrame frame, Object value);

    @Override
    public String toString() {
        return getShortString("slot");
    }

    public abstract static class LLVMWriteI1Node extends LLVMWriteNode {
        protected LLVMWriteI1Node(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeI1(VirtualFrame frame, boolean value) {
            frame.setBoolean(slot, value);
        }
    }

    public abstract static class LLVMWriteI8Node extends LLVMWriteNode {
        protected LLVMWriteI8Node(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeI8(VirtualFrame frame, byte value) {
            frame.setByte(slot, value);
        }
    }

    public abstract static class LLVMWriteI16Node extends LLVMWriteNode {
        protected LLVMWriteI16Node(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeI16(VirtualFrame frame, short value) {
            frame.setInt(slot, value);
        }
    }

    public abstract static class LLVMWriteI32Node extends LLVMWriteNode {
        protected LLVMWriteI32Node(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeI32(VirtualFrame frame, int value) {
            frame.setInt(slot, value);
        }
    }

    public abstract static class LLVMWriteI64Node extends LLVMWriteNode {
        protected LLVMWriteI64Node(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeI64(VirtualFrame frame, long value) {
            if (frame.getFrameDescriptor().getFrameSlotKind(slot) == FrameSlotKind.Long) {
                frame.setLong(slot, value);
            } else {
                frame.setObject(slot, value);
            }
        }

        @Specialization(replaces = "writeI64")
        protected void writePointer(VirtualFrame frame, Object value) {
            if (frame.getFrameDescriptor().getFrameSlotKind(slot) == FrameSlotKind.Long) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                frame.getFrameDescriptor().setFrameSlotKind(slot, FrameSlotKind.Object);
            }
            frame.setObject(slot, value);
        }
    }

    public abstract static class LLVMWriteIVarBitNode extends LLVMWriteNode {
        protected LLVMWriteIVarBitNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeIVarBit(VirtualFrame frame, LLVMIVarBit value) {
            frame.setObject(slot, value);
        }
    }

    public abstract static class LLVMWriteFloatNode extends LLVMWriteNode {
        protected LLVMWriteFloatNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeFloat(VirtualFrame frame, float value) {
            frame.setFloat(slot, value);
        }
    }

    public abstract static class LLVMWriteDoubleNode extends LLVMWriteNode {
        protected LLVMWriteDoubleNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeDouble(VirtualFrame frame, double value) {
            frame.setDouble(slot, value);
        }
    }

    public abstract static class LLVMWrite80BitFloatingNode extends LLVMWriteNode {
        protected LLVMWrite80BitFloatingNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void write80BitFloat(VirtualFrame frame, LLVM80BitFloat value) {
            frame.setObject(slot, value);
        }
    }

    public abstract static class LLVMWritePointerNode extends LLVMWriteNode {
        protected LLVMWritePointerNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeLong(VirtualFrame frame, long value) {
            frame.setObject(slot, LLVMNativePointer.create(value));
        }

        @Fallback
        protected void writeObject(VirtualFrame frame, Object value) {
            frame.setObject(slot, value);
        }
    }

    public abstract static class LLVMWriteVectorNode extends LLVMWriteNode {
        protected LLVMWriteVectorNode(FrameSlot slot) {
            super(slot);
        }

        @Specialization
        protected void writeVector(VirtualFrame frame, LLVMVector value) {
            frame.setObject(slot, value);
        }
    }
}

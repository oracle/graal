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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion.UniqueSlot;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeFields({@NodeField(type = int.class, name = "size"), @NodeField(type = int.class, name = "alignment"), @NodeField(type = Type.class, name = "symbolType")})
public abstract class LLVMGetStackSpaceInstruction extends LLVMExpressionNode {

    abstract int getSize();

    abstract int getAlignment();

    abstract Type getSymbolType();

    @CompilationFinal private FrameSlot stackPointer;

    protected FrameSlot getStackPointerSlot() {
        if (stackPointer == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackPointer = getRootNode().getFrameDescriptor().findFrameSlot(LLVMStack.FRAME_ID);
        }
        return stackPointer;
    }

    public abstract static class LLVMGetStackForConstInstruction extends LLVMGetStackSpaceInstruction {

        @CompilationFinal(dimensions = 1) private Type[] types = null;
        @CompilationFinal(dimensions = 1) private int[] offsets = null;

        public void setTypes(Type[] types) {
            this.types = types;
        }

        public void setOffsets(int[] offsets) {
            this.offsets = offsets;
        }

        public Type[] getTypes() {
            return types;
        }

        public int[] getOffsets() {
            return offsets;
        }

        public int getLength() {
            return offsets.length;
        }

    }

    public abstract static class LLVMAllocaConstInstruction extends LLVMGetStackForConstInstruction {

        @Specialization
        protected LLVMNativePointer doOp(VirtualFrame frame,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return LLVMNativePointer.create(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), getSize(), getAlignment()));
        }
    }

    @NodeField(type = UniqueSlot.class, name = "uniqueSlot")
    public abstract static class LLVMGetUniqueStackSpaceInstruction extends LLVMGetStackForConstInstruction {

        abstract UniqueSlot getUniqueSlot();

        @Specialization
        protected LLVMNativePointer doOp(VirtualFrame frame) {
            return LLVMNativePointer.create(getUniqueSlot().toPointer(frame, getStackPointerSlot()));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAllocaInstruction extends LLVMGetStackSpaceInstruction {

        @Specialization
        protected LLVMNativePointer doOp(VirtualFrame frame, int nr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return LLVMNativePointer.create(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), getSize() * (long) nr, getAlignment()));
        }

        @Specialization
        protected LLVMNativePointer doOp(VirtualFrame frame, long nr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return LLVMNativePointer.create(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), getSize() * nr, getAlignment()));
        }
    }
}

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
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMAllocationFailureException;
import com.oracle.truffle.llvm.runtime.except.LLVMStackOverflowError;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion.UniqueSlot;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class LLVMGetStackSpaceInstruction extends LLVMExpressionNode {

    protected final long size;
    protected final int alignment;
    protected final Type symbolType;

    @CompilationFinal private FrameSlot stackPointer;

    public LLVMGetStackSpaceInstruction(long size, int alignment, Type symbolType) {
        this.size = size;
        this.alignment = alignment;
        this.symbolType = symbolType;
    }

    @Override
    public String toString() {
        return getShortString("size", "alignment", "symbolType");
    }

    protected FrameSlot getStackPointerSlot() {
        if (stackPointer == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackPointer = getRootNode().getFrameDescriptor().findFrameSlot(LLVMStack.FRAME_ID);
        }
        return stackPointer;
    }

    public abstract static class LLVMGetStackForConstInstruction extends LLVMGetStackSpaceInstruction {

        public LLVMGetStackForConstInstruction(long size, int alignment, Type symbolType) {
            super(size, alignment, symbolType);
        }

        @CompilationFinal(dimensions = 1) private Type[] types = null;
        @CompilationFinal(dimensions = 1) private long[] offsets = null;

        public void setTypes(Type[] types) {
            this.types = types;
        }

        public void setOffsets(long[] offsets) {
            this.offsets = offsets;
        }

        public Type[] getTypes() {
            return types;
        }

        public long[] getOffsets() {
            return offsets;
        }

        public int getLength() {
            return offsets.length;
        }

    }

    public abstract static class LLVMAllocaConstInstruction extends LLVMGetStackForConstInstruction {

        public LLVMAllocaConstInstruction(long size, int alignment, Type symbolType) {
            super(size, alignment, symbolType);
        }

        @Specialization
        protected LLVMNativePointer doOp(VirtualFrame frame,
                        @CachedLanguage LLVMLanguage language) {
            try {
                return LLVMNativePointer.create(LLVMStack.allocateStackMemory(this, frame, language.getLLVMMemory(), getStackPointerSlot(), size, alignment));
            } catch (LLVMStackOverflowError soe) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMAllocationFailureException(this, soe);
            }
        }
    }

    public abstract static class LLVMGetUniqueStackSpaceInstruction extends LLVMGetStackForConstInstruction {

        private final UniqueSlot uniqueSlot;

        public LLVMGetUniqueStackSpaceInstruction(long size, int alignment, Type symbolType, UniqueSlot uniqueSlot) {
            super(size, alignment, symbolType);
            this.uniqueSlot = uniqueSlot;
        }

        @Override
        public String toString() {
            return getShortString("size", "alignment", "symbolType", "uniqueSlot");
        }

        @Specialization
        protected LLVMNativePointer doOp(VirtualFrame frame) {
            return LLVMNativePointer.create(uniqueSlot.toPointer(frame, getStackPointerSlot()));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAllocaInstruction extends LLVMGetStackSpaceInstruction {

        public LLVMAllocaInstruction(long size, int alignment, Type symbolType) {
            super(size, alignment, symbolType);
        }

        @Specialization
        protected LLVMNativePointer doOp(VirtualFrame frame, int nr,
                        @CachedLanguage LLVMLanguage language) {
            try {
                return LLVMNativePointer.create(LLVMStack.allocateStackMemory(this, frame, language.getLLVMMemory(), getStackPointerSlot(), size * nr, alignment));
            } catch (LLVMStackOverflowError soe) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMAllocationFailureException(this, soe);
            }
        }

        @Specialization
        protected LLVMNativePointer doOp(VirtualFrame frame, long nr,
                        @CachedLanguage LLVMLanguage language) {
            try {
                return LLVMNativePointer.create(LLVMStack.allocateStackMemory(this, frame, language.getLLVMMemory(), getStackPointerSlot(), size * nr, alignment));
            } catch (LLVMStackOverflowError soe) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMAllocationFailureException(this, soe);
            }
        }
    }
}

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
package com.oracle.truffle.llvm.parser;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.parser.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public final class StackAllocation {

    private final FrameDescriptor rootFrame;

    private final Map<String, FrameDescriptor> frameDescriptors;

    private StackAllocation(Map<String, FrameDescriptor> frameDescriptors) {
        this.frameDescriptors = frameDescriptors;
        rootFrame = new FrameDescriptor();
    }

    public FrameDescriptor getFrame(String functionName) {
        return frameDescriptors.get(functionName);
    }

    public FrameDescriptor getRootFrame() {
        return rootFrame;
    }

    static StackAllocation generate(ModelModule model) {
        final StackAllocationModelVisitor modelVisitor = new StackAllocationModelVisitor();
        model.accept(modelVisitor);
        return new StackAllocation(modelVisitor.getFrames());
    }

    private static final class StackAllocationModelVisitor implements ModelVisitor {

        final Map<String, FrameDescriptor> frames = new HashMap<>();

        public Map<String, FrameDescriptor> getFrames() {
            return frames;
        }

        @Override
        public void visit(FunctionDefinition functionDefinition) {
            final FrameDescriptor frame = new FrameDescriptor();
            if (!(functionDefinition.getType().getReturnType() instanceof VoidType)) {
                frame.addFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
            }
            frame.addFrameSlot(LLVMFrameIDs.FUNCTION_EXCEPTION_VALUE_FRAME_SLOT_ID, FrameSlotKind.Object);

            for (FunctionParameter parameter : functionDefinition.getParameters()) {
                frame.addFrameSlot(parameter.getName(), Type.getFrameSlotKind(parameter.getType()));
            }

            final StackAllocationFunctionVisitor functionVisitor = new StackAllocationFunctionVisitor(frame);
            functionDefinition.accept(functionVisitor);

            frames.put(functionDefinition.getName(), frame);
        }
    }

    private static final class StackAllocationFunctionVisitor extends ValueInstructionVisitor implements FunctionVisitor {

        private final FrameDescriptor frame;

        private StackAllocationFunctionVisitor(FrameDescriptor frame) {
            this.frame = frame;
        }

        @Override
        public void visitValueInstruction(ValueInstruction valueInstruction) {
            final String slotName = valueInstruction.getName();
            final FrameSlotKind slotKind = Type.getFrameSlotKind(valueInstruction.getType());
            frame.addFrameSlot(slotName, slotKind);
        }

        @Override
        public void visit(InstructionBlock block) {
            block.accept(this);
        }
    }
}

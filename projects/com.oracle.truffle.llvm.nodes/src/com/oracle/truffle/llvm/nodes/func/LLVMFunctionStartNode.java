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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller;

public class LLVMFunctionStartNode extends RootNode {

    @Child private LLVMExpressionNode node;
    @Children private final LLVMExpressionNode[] copyArgumentsToFrame;
    @Children private final LLVMStackFrameNuller[] nullers;
    private final String name;
    private final int explicitArgumentsCount;
    private final DebugInformation debugInformation;

    /*
     * Encapsulation of these 3 objects keeps memory footprint low in case no debug info is
     * available.
     */
    private static final class DebugInformation {
        private final SourceSection sourceSection;
        private final String originalName;
        private final Source bcSource;

        DebugInformation(SourceSection sourceSection, String originalName, Source bcSource) {
            this.sourceSection = sourceSection;
            this.originalName = originalName;
            this.bcSource = bcSource;
        }
    }

    public LLVMFunctionStartNode(SourceSection sourceSection, LLVMLanguage language, LLVMExpressionNode node, LLVMExpressionNode[] copyArgumentsToFrame,
                    FrameDescriptor frameDescriptor,
                    String name, LLVMStackFrameNuller[] initNullers, int explicitArgumentsCount, String originalName, Source bcSource) {
        super(language, frameDescriptor);
        this.debugInformation = new DebugInformation(sourceSection, originalName, bcSource);
        this.explicitArgumentsCount = explicitArgumentsCount;
        this.node = node;
        this.copyArgumentsToFrame = copyArgumentsToFrame;
        this.nullers = initNullers;
        this.name = name;
    }

    @Override
    public SourceSection getSourceSection() {
        return debugInformation.sourceSection;
    }

    @CompilationFinal private LLVMStack stack;

    private LLVMStack getStack() {
        if (stack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stack = getLanguage(LLVMLanguage.class).getContextReference().get().getStack();
        }
        return stack;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        long basePointer = getStack().getStackPointer().getVal();
        try {

            nullStack(frame);
            copyArgumentsToFrame(frame);
            Object result = node.executeGeneric(frame);

            return result;
        } finally {
            assert assertDestroyStack(basePointer);
            getStack().setStackPointer(LLVMAddress.fromLong(basePointer));
        }
    }

    /*
     * Allows us to find broken stackpointers immediately because old stackregions are destroyed.
     */
    @SuppressWarnings("deprecation")
    private boolean assertDestroyStack(long basePointer) {
        LLVMAddress currSp = getStack().getStackPointer();
        long size = basePointer - currSp.getVal();
        LLVMMemory.memset(currSp, size, (byte) 0xFF);
        return true;
    }

    @ExplodeLoop
    private void nullStack(VirtualFrame frame) {
        for (LLVMStackFrameNuller nuller : nullers) {
            if (nuller != null) {
                nuller.nullifySlot(frame);
            }
        }
    }

    @ExplodeLoop
    private void copyArgumentsToFrame(VirtualFrame frame) {
        for (LLVMExpressionNode n : copyArgumentsToFrame) {
            n.executeGeneric(frame);
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public String getName() {
        return name;
    }

    public int getExplicitArgumentsCount() {
        return explicitArgumentsCount;
    }

    public String getOriginalName() {
        return debugInformation.originalName;
    }

    public Source getBcSource() {
        return debugInformation.bcSource;
    }
}

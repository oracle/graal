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
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
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
    @Children private final LLVMExpressionNode[] beforeFunction;
    @Children private final LLVMExpressionNode[] afterFunction;
    @Children private final LLVMStackFrameNuller[] nullers;
    private final String name;
    private final int explicitArgumentsCount;
    private final SourceSection sourceSection;
    private final String originalName;
    private final Source bcSource;
    private final FrameSlot baseStackPointer;

    public LLVMFunctionStartNode(SourceSection sourceSection, LLVMLanguage language, LLVMExpressionNode node, LLVMExpressionNode[] beforeFunction, LLVMExpressionNode[] afterFunction,
                    FrameDescriptor frameDescriptor,
                    String name, LLVMStackFrameNuller[] initNullers, FrameSlot baseStackPointer, int explicitArgumentsCount, String originalName, Source bcSource) {
        super(language, frameDescriptor);
        this.sourceSection = sourceSection;
        this.node = node;
        this.beforeFunction = beforeFunction;
        this.afterFunction = afterFunction;
        this.nullers = initNullers;
        this.name = name;
        this.explicitArgumentsCount = explicitArgumentsCount;
        this.baseStackPointer = baseStackPointer;
        this.originalName = originalName;
        this.bcSource = bcSource;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
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
        try {
            frame.setObject(baseStackPointer, getStack().getStackPointer());

            nullStack(frame);
            doBefore(frame);
            Object result = node.executeGeneric(frame);
            doAfter(frame);

            assert destroyStack(frame);
            getStack().setStackPointer((LLVMAddress) frame.getObject(baseStackPointer));
            return result;
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    /*
     * Allows us to find broken stackpointers immediately because old stackregions are destroyed.
     */
    @SuppressWarnings("deprecation")
    private boolean destroyStack(VirtualFrame frame) throws FrameSlotTypeException {
        LLVMAddress oldSp = (LLVMAddress) frame.getObject(baseStackPointer);
        LLVMAddress currSp = getStack().getStackPointer();
        long size = oldSp.getVal() - currSp.getVal();
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
    private void doAfter(VirtualFrame frame) {
        for (LLVMExpressionNode after : afterFunction) {
            after.executeGeneric(frame);
        }
    }

    @ExplodeLoop
    private void doBefore(VirtualFrame frame) {
        for (LLVMExpressionNode before : beforeFunction) {
            before.executeGeneric(frame);
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
        return originalName;
    }

    public Source getBcSource() {
        return bcSource;
    }
}

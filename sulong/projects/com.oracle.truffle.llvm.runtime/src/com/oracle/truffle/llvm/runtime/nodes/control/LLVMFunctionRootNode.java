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
package com.oracle.truffle.llvm.runtime.nodes.control;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.memory.LLVMUniquesRegionAllocNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMFrameNullerUtil;

public abstract class LLVMFunctionRootNode extends LLVMExpressionNode {

    private static final FrameSlot[] NO_SLOTS = new FrameSlot[0];

    @Children private final LLVMStatementNode[] copyArgumentsToFrame;
    @Child private LLVMUniquesRegionAllocNode uniquesRegionAllocNode;
    @Child private LLVMExpressionNode rootBody;

    @CompilationFinal(dimensions = 1) private final FrameSlot[] frameSlotsToInitialize;

    public LLVMFunctionRootNode(LLVMUniquesRegionAllocNode uniquesRegionAllocNode, LLVMStatementNode[] copyArgumentsToFrame, LLVMDispatchBasicBlockNode rootBody, FrameDescriptor frameDescriptor) {
        this.uniquesRegionAllocNode = uniquesRegionAllocNode;
        this.copyArgumentsToFrame = copyArgumentsToFrame;
        this.rootBody = rootBody;
        this.frameSlotsToInitialize = frameDescriptor.getSlots().toArray(NO_SLOTS);
    }

    @ExplodeLoop
    private void copyArgumentsToFrame(VirtualFrame frame) {
        for (LLVMStatementNode n : copyArgumentsToFrame) {
            n.execute(frame);
        }
    }

    @Specialization
    public Object doRun(VirtualFrame frame) {
        nullStack(frame);
        copyArgumentsToFrame(frame);
        uniquesRegionAllocNode.execute(frame);

        return rootBody.executeGeneric(frame);
    }

    @ExplodeLoop
    private void nullStack(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter()) {
            // don't clear slots if we're running in the interpreter
            return;
        }
        for (FrameSlot frameSlot : frameSlotsToInitialize) {
            // avoids phis for the contents of the frame slots tag array
            LLVMFrameNullerUtil.nullFrameSlot(frame, frameSlot);
        }
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.StatementTag.class) {
            return false;
        } else if (tag == StandardTags.RootTag.class) {
            return true;
        } else {
            return super.hasTag(tag);
        }
    }
}

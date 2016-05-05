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
package com.oracle.truffle.llvm.nodes.impl.others;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNode;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;

public abstract class LLVMBlockNode extends LLVMExpressionNode {

    public static class LLVMBlockControlFlowNode extends LLVMBlockNode {

        @Children private final LLVMBasicBlockNode[] bodyNodes;
        @CompilationFinal private final LLVMStackFrameNuller[][] indexToSlotNuller;
        private final FrameSlot returnSlot;
        private final boolean injectBranchProbabilities = LLVMBaseOptionFacade.injectBranchProbabilities();

        public LLVMBlockControlFlowNode(LLVMBasicBlockNode[] bodyNodes, LLVMStackFrameNuller[][] indexToSlotNuller, FrameSlot returnSlot) {
            this.bodyNodes = bodyNodes;
            this.indexToSlotNuller = indexToSlotNuller;
            this.returnSlot = returnSlot;
        }

        @Override
        @ExplodeLoop(merge = true)
        public Object executeGeneric(VirtualFrame frame) {
            CompilerAsserts.compilationConstant(bodyNodes.length);
            int bci = 0;
            int loopCount = 0;
            outer: while (bci != LLVMRetNode.RETURN_FROM_FUNCTION) {
                if (CompilerDirectives.inInterpreter()) {
                    loopCount++;
                }
                CompilerAsserts.partialEvaluationConstant(bci);
                LLVMBasicBlockNode bb = bodyNodes[bci];
                int successorSelection = bb.executeGetSuccessorIndex(frame);
                LLVMStackFrameNuller[] stackNuller = indexToSlotNuller[bci];
                if (stackNuller != null) {
                    for (int j = 0; j < stackNuller.length; j++) {
                        stackNuller[j].nullifySlot(frame);
                    }
                }
                int[] successors = bb.getSuccessors();
                for (int i = 0; i < successors.length; i++) {
                    if (injectBranchProbabilities) {
                        if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(i), i == successorSelection)) {
                            bb.increaseBranchProbabilityDeoptIfZero(i);
                            bci = successors[i];
                            continue outer;
                        }
                    } else {
                        if (i == successorSelection) {
                            bci = successors[i];
                            continue outer;
                        }
                    }
                }
                /*
                 * Avoid a little loop after partial evaluation where the bci remains constant.
                 * Later compiler optimizations would remove the loop, but that way we simplify the
                 * compiler's life.
                 */
                CompilerDirectives.transferToInterpreter();
                throw new Error("No matching successor found");
            }
            LoopNode.reportLoopCount(this, loopCount);
            return frame.getValue(returnSlot);
        }
    }

    public static class LLVMBlockNoControlFlowNode extends LLVMBlockNode {

        @Children private final LLVMNode[] bodyNodes;
        private final FrameSlot returnSlot;

        public LLVMBlockNoControlFlowNode(LLVMNode[] bodyNodes, FrameSlot returnSlot) {
            this.bodyNodes = bodyNodes;
            this.returnSlot = returnSlot;
        }

        @Override
        @ExplodeLoop
        public Object executeGeneric(VirtualFrame frame) {
            CompilerAsserts.compilationConstant(bodyNodes.length);
            for (int i = 0; i < bodyNodes.length; i++) {
                bodyNodes[i].executeVoid(frame);
            }
            return frame.getValue(returnSlot);
        }
    }

}

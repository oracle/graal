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
package com.oracle.truffle.llvm.nodes.impl.control;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStatementNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;

@NodeChild(type = LLVMI1Node.class)
public abstract class LLVMConditionalBranchNode extends LLVMStatementNode {

    @Children final LLVMNode[] truePhiWriteNodes;
    @Children final LLVMNode[] falsePhiWriteNodes;

    public static final int TRUE_SUCCESSOR = 0;
    public static final int FALSE_SUCCESSOR = 1;

    public LLVMConditionalBranchNode(int trueSuccessor, int falseSuccessor, LLVMNode[] truePhiWriteNodes, LLVMNode[] falsePhiWriteNodes) {
        super(trueSuccessor, falseSuccessor);
        this.truePhiWriteNodes = truePhiWriteNodes;
        this.falsePhiWriteNodes = falsePhiWriteNodes;
    }

    // TODO find a better name
    public abstract static class LLVMBrConditionalNode extends LLVMConditionalBranchNode {

        public LLVMBrConditionalNode(int trueSuccessor, int falseSuccessor, LLVMNode[] truePhiWriteNodes, LLVMNode[] falsePhiWriteNodes) {
            super(trueSuccessor, falseSuccessor, truePhiWriteNodes, falsePhiWriteNodes);
        }

        @ExplodeLoop
        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, boolean condition) {
            if (condition) {
                for (int i = 0; i < truePhiWriteNodes.length; i++) {
                    truePhiWriteNodes[i].executeVoid(frame);
                }
                return TRUE_SUCCESSOR;
            } else {
                for (int i = 0; i < falsePhiWriteNodes.length; i++) {
                    falsePhiWriteNodes[i].executeVoid(frame);
                }
                return FALSE_SUCCESSOR;
            }
        }
    }

    public abstract static class LLVMBrConditionalInjectionNode extends LLVMConditionalBranchNode {

        public LLVMBrConditionalInjectionNode(int trueSuccessor, int falseSuccessor, LLVMNode[] truePhiWriteNodes, LLVMNode[] falsePhiWriteNodes) {
            super(trueSuccessor, falseSuccessor, truePhiWriteNodes, falsePhiWriteNodes);
        }

        private final ConditionProfile profile = ConditionProfile.createCountingProfile();

        @ExplodeLoop
        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, boolean condition) {
            if (profile.profile(condition)) {
                for (int i = 0; i < truePhiWriteNodes.length; i++) {
                    truePhiWriteNodes[i].executeVoid(frame);
                }
                return TRUE_SUCCESSOR;
            } else {
                for (int i = 0; i < falsePhiWriteNodes.length; i++) {
                    falsePhiWriteNodes[i].executeVoid(frame);
                }
                return FALSE_SUCCESSOR;
            }
        }

    }

}

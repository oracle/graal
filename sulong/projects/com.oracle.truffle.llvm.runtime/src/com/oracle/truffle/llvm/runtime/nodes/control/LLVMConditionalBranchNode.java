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
package com.oracle.truffle.llvm.runtime.nodes.control;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMConditionalBranchNodeFactory.LLVMConditionalBranchNodeImplNodeGen;

@GenerateWrapper
@NodeField(name = "trueSuccessor", type = int.class)
@NodeField(name = "falseSuccessor", type = int.class)
@NodeChild(value = "condition", type = LLVMExpressionNode.class)
public abstract class LLVMConditionalBranchNode extends LLVMControlFlowNode {

    public static LLVMConditionalBranchNode create(int trueSuccessor, int falseSuccessor, LLVMStatementNode truePhi, LLVMStatementNode falsePhi, LLVMExpressionNode condition) {
        return LLVMConditionalBranchNodeImplNodeGen.create(truePhi, falsePhi, condition, trueSuccessor, falseSuccessor);
    }

    public static final int TRUE_SUCCESSOR = 0;
    public static final int FALSE_SUCCESSOR = 1;

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMConditionalBranchNodeWrapper(this, probe);
    }

    public abstract boolean executeCondition(VirtualFrame frame);

    public abstract int getTrueSuccessor();

    public abstract int getFalseSuccessor();

    /**
     * Override to allow access from generated wrapper.
     */
    @Override
    protected abstract boolean isStatement();

    /**
     * Override to allow access from generated wrapper.
     */
    @Override
    protected abstract void setStatement(boolean statementTag);

    abstract static class LLVMConditionalBranchNodeImpl extends LLVMConditionalBranchNode {

        @Child private LLVMStatementNode truePhi;
        @Child private LLVMStatementNode falsePhi;

        LLVMConditionalBranchNodeImpl(LLVMStatementNode truePhi, LLVMStatementNode falsePhi) {
            this.truePhi = truePhi;
            this.falsePhi = falsePhi;
        }

        @Override
        public String toString() {
            return getShortString("trueSuccessor", "falseSuccessor");
        }

        @Override
        public int getSuccessorCount() {
            return 2;
        }

        @Override
        public LLVMStatementNode getPhiNode(int successorIndex) {
            CompilerAsserts.partialEvaluationConstant(successorIndex);
            if (successorIndex == TRUE_SUCCESSOR) {
                return truePhi;
            } else {
                assert successorIndex == FALSE_SUCCESSOR;
                return falsePhi;
            }
        }

        @Specialization
        public boolean doCondition(boolean condition) {
            return condition;
        }

        @Override
        public int[] getSuccessors() {
            return new int[]{getTrueSuccessor(), getFalseSuccessor()};
        }
    }
}

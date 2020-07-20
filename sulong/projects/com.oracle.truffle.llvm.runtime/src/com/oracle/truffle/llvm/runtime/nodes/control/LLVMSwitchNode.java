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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMSwitchNodeFactory.LLVMSwitchNodeImplNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNode.LLVMEqNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMEqNodeGen;

@GenerateWrapper
public abstract class LLVMSwitchNode extends LLVMControlFlowNode {

    public static LLVMSwitchNode create(int[] successors, LLVMStatementNode[] phiNodes, LLVMExpressionNode cond, LLVMExpressionNode[] cases) {
        return LLVMSwitchNodeImplNodeGen.create(successors, phiNodes, cases, cond);
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMSwitchNodeWrapper(this, probe);
    }

    public abstract Object executeCondition(VirtualFrame frame);

    public abstract boolean checkCase(VirtualFrame frame, int i, Object value);

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

    @NodeChild(value = "cond", type = LLVMExpressionNode.class)
    public abstract static class LLVMSwitchNodeImpl extends LLVMSwitchNode {
        @Children private final LLVMStatementNode[] phiNodes;
        @Children protected final LLVMExpressionNode[] cases;
        @Children protected final LLVMEqNode[] caseEquals;
        @CompilationFinal(dimensions = 1) private final int[] successors;

        private final ValueProfile conditionValueClass = ValueProfile.createClassProfile();

        public LLVMSwitchNodeImpl(int[] successors, LLVMStatementNode[] phiNodes, LLVMExpressionNode[] cases) {
            assert successors.length == cases.length + 1 : "the last entry of the successors array must be the default case";
            this.successors = successors;
            this.phiNodes = phiNodes;
            this.cases = cases;
            this.caseEquals = new LLVMEqNode[cases.length];
            for (int i = 0; i < caseEquals.length; i++) {
                caseEquals[i] = LLVMEqNodeGen.create(null, null);
            }
        }

        @Specialization
        public Object doCondition(Object cond) {
            return conditionValueClass.profile(cond);
        }

        @Override
        public int[] getSuccessors() {
            return successors;
        }

        @Override
        public int getSuccessorCount() {
            return successors.length;
        }

        @Override
        public LLVMStatementNode getPhiNode(int successorIndex) {
            return phiNodes[successorIndex];
        }

        @Override
        public boolean checkCase(VirtualFrame frame, int i, Object value) {
            return caseEquals[i].executeWithTarget(cases[i].executeGeneric(frame), value);
        }
    }
}

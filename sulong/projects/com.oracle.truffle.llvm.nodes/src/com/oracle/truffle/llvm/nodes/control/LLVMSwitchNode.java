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
package com.oracle.truffle.llvm.nodes.control;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.nodes.op.LLVMCompareNode.LLVMEqNode;
import com.oracle.truffle.llvm.nodes.op.LLVMCompareNodeFactory.LLVMEqNodeGen;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

@GenerateWrapper
public abstract class LLVMSwitchNode extends LLVMControlFlowNode implements InstrumentableNode {

    public LLVMSwitchNode(LLVMSourceLocation sourceSection) {
        super(sourceSection);
    }

    protected LLVMSwitchNode(LLVMSwitchNode delegate) {
        super(delegate.getSourceLocation());
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMSwitchNodeWrapper(this, this, probe);
    }

    @GenerateWrapper.OutgoingConverter
    Object convertOutgoing(@SuppressWarnings("unused") Object object) {
        return null;
    }

    @Override
    public boolean isInstrumentable() {
        return getSourceLocation() != null;
    }

    public abstract Object executeCondition(VirtualFrame frame);

    public abstract int[] getSuccessors();

    public abstract boolean executeIsCase(VirtualFrame frame, int i, Object value);

    public static class LLVMSwitchNodeImpl extends LLVMSwitchNode {
        @Children private final LLVMStatementNode[] phiNodes;
        @Child protected LLVMExpressionNode cond;
        @Children protected final LLVMExpressionNode[] cases;
        @Children protected final LLVMEqNode[] caseEquals;
        @CompilationFinal(dimensions = 1) private final int[] successors;

        private final ValueProfile conditionValueClass = ValueProfile.createClassProfile();

        public LLVMSwitchNodeImpl(int[] successors, LLVMStatementNode[] phiNodes, LLVMExpressionNode cond, LLVMExpressionNode[] cases, LLVMSourceLocation sourceSection) {
            super(sourceSection);
            assert successors.length == cases.length + 1 : "the last entry of the successors array must be the default case";
            this.successors = successors;
            this.phiNodes = phiNodes;
            this.cond = cond;
            this.cases = cases;
            this.caseEquals = new LLVMEqNode[cases.length];
            for (int i = 0; i < caseEquals.length; i++) {
                caseEquals[i] = LLVMEqNodeGen.create(null, null);
            }
        }

        @Override
        public Object executeCondition(VirtualFrame frame) {
            return conditionValueClass.profile(cond.executeGeneric(frame));
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
        public boolean executeIsCase(VirtualFrame frame, int i, Object value) {
            return caseEquals[i].executeCompare(cases[i].executeGeneric(frame), value);
        }
    }
}

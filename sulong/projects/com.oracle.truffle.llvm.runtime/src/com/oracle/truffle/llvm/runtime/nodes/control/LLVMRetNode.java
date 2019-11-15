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

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMArgNode;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@GenerateWrapper
public abstract class LLVMRetNode extends LLVMControlFlowNode {

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMRetNodeWrapper(this, probe);
    }

    @GenerateWrapper.OutgoingConverter
    Object convertOutgoing(@SuppressWarnings("unused") Object object) {
        return null;
    }

    @Override
    public int getSuccessorCount() {
        return 1;
    }

    @Override
    public final int[] getSuccessors() {
        return new int[]{LLVMBasicBlockNode.RETURN_FROM_FUNCTION};
    }

    public int getSuccessor() {
        return LLVMBasicBlockNode.RETURN_FROM_FUNCTION;
    }

    @Override
    public LLVMStatementNode getPhiNode(int successorIndex) {
        assert successorIndex == 0;
        return null;
    }

    public abstract Object execute(VirtualFrame frame);

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

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMI1RetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(boolean retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMI8RetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(byte retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMI16RetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(short retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMI32RetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(int retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMI64RetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(long retResult) {
            return retResult;
        }

        @Specialization
        protected Object doOp(LLVMPointer retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMIVarBitRetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(LLVMIVarBit retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMFloatRetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(float retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMDoubleRetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(double retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVM80BitFloatRetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(LLVM80BitFloat retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMAddressRetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(Object retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    public abstract static class LLVMVectorRetNode extends LLVMRetNode {

        @Specialization
        protected Object doOp(LLVMDoubleVector retResult) {
            return retResult;
        }

        @Specialization
        protected Object doOp(LLVMFloatVector retResult) {
            return retResult;
        }

        @Specialization
        protected Object doOp(LLVMI16Vector retResult) {
            return retResult;
        }

        @Specialization
        protected Object doOp(LLVMI1Vector retResult) {
            return retResult;
        }

        @Specialization
        protected Object doOp(LLVMI32Vector retResult) {
            return retResult;
        }

        @Specialization
        protected Object doOp(LLVMI64Vector retResult) {
            return retResult;
        }

        @Specialization
        protected Object doOp(LLVMI8Vector retResult) {
            return retResult;
        }
    }

    @NodeChild(value = "retResult", type = LLVMExpressionNode.class)
    @NodeField(name = "structSize", type = long.class)
    public abstract static class LLVMStructRetNode extends LLVMRetNode {

        @Child private LLVMArgNode argIdx1 = LLVMArgNodeGen.create(1);
        @Child private LLVMMemMoveNode memMove;

        public abstract long getStructSize();

        public LLVMStructRetNode(LLVMMemMoveNode memMove) {
            this.memMove = memMove;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMPointer retResult) {
            return returnStruct(frame, retResult);
        }

        private Object returnStruct(VirtualFrame frame, Object retResult) {
            Object retStructAddress = argIdx1.executeGeneric(frame);
            memMove.executeWithTarget(retStructAddress, retResult, getStructSize());
            return retStructAddress;
        }
    }

    public abstract static class LLVMVoidReturnNode extends LLVMRetNode {

        @Specialization
        protected Object doOp() {
            return LLVMNativePointer.createNull();
        }
    }
}

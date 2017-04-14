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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public abstract class LLVMInvokeNode extends LLVMControlFlowNode {
    public static final int ARG_START_INDEX = 1;
    @Children protected final LLVMExpressionNode[] normalPhiWriteNodes;
    @Children protected final LLVMExpressionNode[] unwindPhiWriteNodes;
    private final int normalSuccessor;
    private final int unwindSuccessor;

    protected final FrameSlot returnValueSlot;
    protected final FrameSlot exceptionValueSlot;

    public static final int NORMAL_SUCCESSOR = 0;
    public static final int UNWIND_SUCCESSOR = 1;

    protected final FunctionType type;

    public LLVMInvokeNode(FunctionType type, FrameSlot returnValueSlot,
                    FrameSlot exceptionValueSlot,
                    int normalSuccessor, int unwindSuccessor,
                    LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes) {
        assert (type.getReturnType() instanceof VoidType) || returnValueSlot != null;
        this.normalSuccessor = normalSuccessor;
        this.unwindSuccessor = unwindSuccessor;
        this.type = type;
        this.normalPhiWriteNodes = normalPhiWriteNodes;
        this.unwindPhiWriteNodes = unwindPhiWriteNodes;
        this.returnValueSlot = returnValueSlot;
        this.exceptionValueSlot = exceptionValueSlot;
    }

    @Override
    public int getSuccessorCount() {
        return 2;
    }

    public int getNormalSuccessor() {
        return normalSuccessor;
    }

    public int getUnwindSuccessor() {
        return unwindSuccessor;
    }

    public void handleException(VirtualFrame frame, LLVMException e) {
        frame.setObject(exceptionValueSlot, e);
    }

    @Override
    public boolean needsBranchProfiling() {
        // we can't use branch profiling because the control flow happens via exception handling
        return false;
    }

    public abstract void execute(VirtualFrame frame);

    @ExplodeLoop
    public void writePhis(VirtualFrame frame, int successorIndex) {
        if (successorIndex == NORMAL_SUCCESSOR) {
            for (int i = 0; i < normalPhiWriteNodes.length; i++) {
                normalPhiWriteNodes[i].executeGeneric(frame);
            }
        } else {
            assert successorIndex == UNWIND_SUCCESSOR;
            for (int i = 0; i < unwindPhiWriteNodes.length; i++) {
                unwindPhiWriteNodes[i].executeGeneric(frame);
            }
        }
    }

    protected void writeResult(VirtualFrame frame, Object value) {
        Type returnType = type.getReturnType();
        CompilerAsserts.partialEvaluationConstant(returnType);
        if (returnType instanceof VoidType) {
            return;
        }
        if (returnType instanceof PrimitiveType) {
            switch (((PrimitiveType) returnType).getPrimitiveKind()) {
                case I1:
                    frame.setBoolean(returnValueSlot, (boolean) value);
                    break;
                case I8:
                    frame.setByte(returnValueSlot, (byte) value);
                    break;
                case I16:
                    frame.setInt(returnValueSlot, (int) value);
                    break;
                case I32:
                    frame.setInt(returnValueSlot, (int) value);
                    break;
                case I64:
                    frame.setLong(returnValueSlot, (long) value);
                    break;
                case FLOAT:
                    frame.setFloat(returnValueSlot, (float) value);
                    break;
                case DOUBLE:
                    frame.setDouble(returnValueSlot, (double) value);
                    break;
                default:
                    frame.setObject(returnValueSlot, value);
            }

        } else {
            frame.setObject(returnValueSlot, value);
        }
    }

    public static final class LLVMSubstitutionInvokeNode extends LLVMInvokeNode {

        @Child private LLVMExpressionNode substitution;

        public LLVMSubstitutionInvokeNode(FunctionType type, LLVMExpressionNode substitution, FrameSlot returnValueSlot,
                        FrameSlot exceptionValueSlot,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes) {
            super(type, returnValueSlot, exceptionValueSlot, normalSuccessor, unwindSuccessor, normalPhiWriteNodes, unwindPhiWriteNodes);
            this.substitution = substitution;
        }

        @Override
        public void execute(VirtualFrame frame) {
            Object returnValue = substitution.executeGeneric(frame);
            writeResult(frame, returnValue);
        }
    }

    public static final class LLVMFunctionInvokeNode extends LLVMInvokeNode {

        @Child private LLVMExpressionNode functionNode;
        @Children private final LLVMExpressionNode[] argumentNodes;
        @Child private LLVMLookupDispatchNode dispatchNode;

        public LLVMFunctionInvokeNode(FunctionType type, LLVMExpressionNode functionNode, LLVMExpressionNode[] argumentNodes, FrameSlot returnValueSlot,
                        FrameSlot exceptionValueSlot,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes) {
            super(type, returnValueSlot, exceptionValueSlot, normalSuccessor, unwindSuccessor, normalPhiWriteNodes, unwindPhiWriteNodes);
            this.functionNode = functionNode;
            this.argumentNodes = argumentNodes;
            this.dispatchNode = LLVMLookupDispatchNodeGen.create(type);
        }

        @Override
        public void execute(VirtualFrame frame) {
            Object function = functionNode.executeGeneric(frame);
            Object[] argValues = prepareArguments(frame);
            Object returnValue = dispatchNode.executeDispatch(frame, function, argValues);
            writeResult(frame, returnValue);
        }

        @ExplodeLoop
        private Object[] prepareArguments(VirtualFrame frame) {
            Object[] argValues = new Object[argumentNodes.length];
            for (int i = 0; i < argumentNodes.length; i++) {
                argValues[i] = argumentNodes[i].executeGeneric(frame);
            }
            return argValues;
        }

    }
}

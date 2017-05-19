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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public abstract class LLVMInvokeNode extends LLVMControlFlowNode {
    @Children protected final LLVMExpressionNode[] normalPhiWriteNodes;
    @Children protected final LLVMExpressionNode[] unwindPhiWriteNodes;
    private final int normalSuccessor;
    private final int unwindSuccessor;

    public static final int NORMAL_SUCCESSOR = 0;
    public static final int UNWIND_SUCCESSOR = 1;

    protected final FunctionType type;

    private final FrameSlot resultLocation;

    public LLVMInvokeNode(FunctionType type, FrameSlot resultLocation,
                    int normalSuccessor, int unwindSuccessor,
                    LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes, SourceSection sourceSection) {
        super(sourceSection);
        this.normalSuccessor = normalSuccessor;
        this.unwindSuccessor = unwindSuccessor;
        this.type = type;
        this.normalPhiWriteNodes = normalPhiWriteNodes;
        this.unwindPhiWriteNodes = unwindPhiWriteNodes;
        this.resultLocation = resultLocation;
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

    protected void writeResult(VirtualFrame frame, Object value) {
        Type returnType = type.getReturnType();
        CompilerAsserts.partialEvaluationConstant(returnType);
        if (returnType instanceof VoidType) {
            return;
        }
        if (returnType instanceof PrimitiveType) {
            switch (((PrimitiveType) returnType).getPrimitiveKind()) {
                case I1:
                    frame.setBoolean(resultLocation, (boolean) value);
                    break;
                case I8:
                    frame.setByte(resultLocation, (byte) value);
                    break;
                case I16:
                    frame.setInt(resultLocation, (int) value);
                    break;
                case I32:
                    frame.setInt(resultLocation, (int) value);
                    break;
                case I64:
                    frame.setLong(resultLocation, (long) value);
                    break;
                case FLOAT:
                    frame.setFloat(resultLocation, (float) value);
                    break;
                case DOUBLE:
                    frame.setDouble(resultLocation, (double) value);
                    break;
                default:
                    frame.setObject(resultLocation, value);
            }

        } else {
            frame.setObject(resultLocation, value);
        }
    }

    @Override
    public boolean needsBranchProfiling() {
        // we can't use branch profiling because the control flow happens via exception handling
        return false;
    }

    public abstract void execute(VirtualFrame frame);

    @CompilationFinal private LLVMThreadingStack threadingStack;

    protected LLVMThreadingStack getThreadingStack() {
        if (threadingStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threadingStack = getContext().getThreadingStack();
        }
        return threadingStack;
    }

    @CompilationFinal private FrameSlot stackPointer;

    protected FrameSlot getStackPointerSlot() {
        if (stackPointer == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackPointer = getRootNode().getFrameDescriptor().findFrameSlot(LLVMStack.FRAME_ID);
        }
        return stackPointer;
    }

    private final ConditionProfile profile = ConditionProfile.createCountingProfile();

    @ExplodeLoop
    public void writePhis(VirtualFrame frame, int successorIndex) {
        if (profile.profile(successorIndex == NORMAL_SUCCESSOR)) {
            runNormalPhis(frame);
        } else {
            assert successorIndex == UNWIND_SUCCESSOR;
            runUnwindPhis(frame);
        }
    }

    @ExplodeLoop
    private void runUnwindPhis(VirtualFrame frame) {
        for (int i = 0; i < unwindPhiWriteNodes.length; i++) {
            unwindPhiWriteNodes[i].executeGeneric(frame);
        }
    }

    @ExplodeLoop
    private void runNormalPhis(VirtualFrame frame) {
        for (int i = 0; i < normalPhiWriteNodes.length; i++) {
            normalPhiWriteNodes[i].executeGeneric(frame);
        }
    }

    public static final class LLVMSubstitutionInvokeNode extends LLVMInvokeNode {

        @Child private LLVMExpressionNode substitution;

        public LLVMSubstitutionInvokeNode(FunctionType type, FrameSlot resultLocation, LLVMExpressionNode substitution,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes, SourceSection sourceSection) {
            super(type, resultLocation, normalSuccessor, unwindSuccessor, normalPhiWriteNodes, unwindPhiWriteNodes, sourceSection);
            this.substitution = substitution;
        }

        @Override
        public void execute(VirtualFrame frame) {
            writeResult(frame, substitution.executeGeneric(frame));
        }
    }

    public static final class LLVMFunctionInvokeNode extends LLVMInvokeNode {

        @Child private LLVMExpressionNode functionNode;
        @Children private final LLVMExpressionNode[] argumentNodes;
        @Child private LLVMLookupDispatchNode dispatchNode;

        public LLVMFunctionInvokeNode(FunctionType type, FrameSlot resultLocation, LLVMExpressionNode functionNode, LLVMExpressionNode[] argumentNodes,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes, SourceSection sourceSection) {
            super(type, resultLocation, normalSuccessor, unwindSuccessor, normalPhiWriteNodes, unwindPhiWriteNodes, sourceSection);
            this.functionNode = functionNode;
            this.argumentNodes = argumentNodes;
            this.dispatchNode = LLVMLookupDispatchNodeGen.create(type);
        }

        @Override
        public void execute(VirtualFrame frame) {
            Object function = functionNode.executeGeneric(frame);
            Object[] argValues = prepareArguments(frame);
            writeResult(frame, dispatchNode.executeDispatch(frame, function, argValues));
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

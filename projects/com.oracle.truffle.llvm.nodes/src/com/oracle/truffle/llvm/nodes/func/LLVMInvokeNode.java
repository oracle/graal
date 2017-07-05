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
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNode;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMAddressProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMDoubleProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMFloatProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI16ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI1ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI32ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI64ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI8ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.wrappers.LLVMInvokeNodeWrapper;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.NeedsStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

@Instrumentable(factory = LLVMInvokeNodeWrapper.class)
public abstract class LLVMInvokeNode extends LLVMControlFlowNode {

    @NeedsStack
    private static class LLVMInvokeNodeImpl extends LLVMInvokeNode {

        @Child protected LLVMExpressionNode normalPhiNode;
        @Child protected LLVMExpressionNode unwindPhiNode;
        @Child protected LLVMValueProfilingNode returnValueProfile;

        protected final FunctionType type;

        @CompilationFinal private LLVMThreadingStack threadingStack;
        @CompilationFinal private FrameSlot stackPointer;

        private final int normalSuccessor;
        private final int unwindSuccessor;
        private final FrameSlot resultLocation;
        private final ConditionProfile profile = ConditionProfile.createCountingProfile();

        LLVMInvokeNodeImpl(FunctionType type, FrameSlot resultLocation,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMExpressionNode normalPhiNode, LLVMExpressionNode unwindPhiNode, SourceSection sourceSection) {
            super(sourceSection);
            this.normalSuccessor = normalSuccessor;
            this.unwindSuccessor = unwindSuccessor;
            this.type = type;
            this.normalPhiNode = normalPhiNode;
            this.unwindPhiNode = unwindPhiNode;
            this.resultLocation = resultLocation;

            initializeReturnValueProfileNode();
        }

        @Override
        public int getSuccessorCount() {
            return 2;
        }

        @Override
        public int getNormalSuccessor() {
            return normalSuccessor;
        }

        @Override
        public int getUnwindSuccessor() {
            return unwindSuccessor;
        }

        @Override
        public void execute(VirtualFrame frame) {
            // checkstyle complains if the class is abstract, so we need to provide a default
            // implementation here
            throw new UnsupportedOperationException("Unimplemented LLVMInvokeNode");
        }

        private void initializeReturnValueProfileNode() {
            CompilerAsserts.neverPartOfCompilation();
            if (type.getReturnType() instanceof PrimitiveType) {
                switch (((PrimitiveType) type.getReturnType()).getPrimitiveKind()) {
                    case I8:
                        this.returnValueProfile = LLVMI8ProfiledValueNodeGen.create(null);
                        break;
                    case I32:
                        this.returnValueProfile = LLVMI32ProfiledValueNodeGen.create(null);
                        break;
                    case I64:
                        this.returnValueProfile = LLVMI64ProfiledValueNodeGen.create(null);
                        break;
                    case FLOAT:
                        this.returnValueProfile = LLVMFloatProfiledValueNodeGen.create(null);
                        break;
                    case DOUBLE:
                        this.returnValueProfile = LLVMDoubleProfiledValueNodeGen.create(null);
                        break;
                    case I1:
                        this.returnValueProfile = LLVMI1ProfiledValueNodeGen.create(null);
                        break;
                    case I16:
                        this.returnValueProfile = LLVMI16ProfiledValueNodeGen.create(null);
                        break;
                    default:
                        this.returnValueProfile = null;
                }
            } else if (type.getReturnType() instanceof PointerType) {
                this.returnValueProfile = LLVMAddressProfiledValueNodeGen.create(null);
            } else {
                this.returnValueProfile = null;
            }
        }

        @Override
        public LLVMExpressionNode getPhiNode(int successorIndex) {
            if (successorIndex == NORMAL_SUCCESSOR) {
                return normalPhiNode;
            } else {
                assert successorIndex == UNWIND_SUCCESSOR;
                return unwindPhiNode;
            }
        }

        @Override
        public void writeResult(VirtualFrame frame, Object value) {
            Type returnType = type.getReturnType();
            CompilerAsserts.partialEvaluationConstant(returnType);
            if (returnType instanceof VoidType) {
                return;
            }
            if (returnType instanceof PrimitiveType) {
                switch (((PrimitiveType) returnType).getPrimitiveKind()) {
                    case I1:
                        frame.setBoolean(resultLocation, (boolean) returnValueProfile.executeWithTarget(value));
                        break;
                    case I8:
                        frame.setByte(resultLocation, (byte) returnValueProfile.executeWithTarget(value));
                        break;
                    case I16:
                        frame.setInt(resultLocation, (short) returnValueProfile.executeWithTarget(value));
                        break;
                    case I32:
                        frame.setInt(resultLocation, (int) returnValueProfile.executeWithTarget(value));
                        break;
                    case I64:
                        frame.setLong(resultLocation, (long) returnValueProfile.executeWithTarget(value));
                        break;
                    case FLOAT:
                        frame.setFloat(resultLocation, (float) returnValueProfile.executeWithTarget(value));
                        break;
                    case DOUBLE:
                        frame.setDouble(resultLocation, (double) returnValueProfile.executeWithTarget(value));
                        break;
                    default:
                        frame.setObject(resultLocation, value);
                }
            } else if (type.getReturnType() instanceof PointerType) {
                Object profiledValue = returnValueProfile.executeWithTarget(value);
                frame.setObject(resultLocation, profiledValue);
            } else {
                frame.setObject(resultLocation, value);
            }
        }

        @Override
        public LLVMThreadingStack getThreadingStack() {
            if (threadingStack == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                threadingStack = getContext().getThreadingStack();
            }
            return threadingStack;
        }

        @Override
        public FrameSlot getStackPointerSlot() {
            if (stackPointer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                stackPointer = getRootNode().getFrameDescriptor().findFrameSlot(LLVMStack.FRAME_ID);
            }
            return stackPointer;
        }

        @Override
        @ExplodeLoop
        public void writePhis(VirtualFrame frame, int successorIndex) {
            if (profile.profile(successorIndex == NORMAL_SUCCESSOR)) {
                normalPhiNode.executeGeneric(frame);
            } else {
                assert successorIndex == UNWIND_SUCCESSOR;
                unwindPhiNode.executeGeneric(frame);
            }
        }
    }

    public static final int NORMAL_SUCCESSOR = 0;
    public static final int UNWIND_SUCCESSOR = 1;

    public LLVMInvokeNode(SourceSection sourceSection) {
        super(sourceSection);
    }

    public abstract int getNormalSuccessor();

    public abstract int getUnwindSuccessor();

    public abstract void execute(VirtualFrame frame);

    public abstract void writeResult(VirtualFrame frame, Object value);

    public abstract LLVMThreadingStack getThreadingStack();

    public abstract FrameSlot getStackPointerSlot();

    public abstract void writePhis(VirtualFrame frame, int successorIndex);

    @Override
    public boolean needsBranchProfiling() {
        // we can't use branch profiling because the control flow happens via exception handling
        return false;
    }

    @Override
    protected boolean isTaggedWith(Class<?> tag) {
        return tag == StandardTags.StatementTag.class || tag == StandardTags.CallTag.class || super.isTaggedWith(tag);
    }

    public static final class LLVMSubstitutionInvokeNode extends LLVMInvokeNodeImpl {

        @Child private LLVMExpressionNode substitution;

        public LLVMSubstitutionInvokeNode(FunctionType type, FrameSlot resultLocation, LLVMExpressionNode substitution,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMExpressionNode normalPhiNode, LLVMExpressionNode unwindPhiNode, SourceSection sourceSection) {
            super(type, resultLocation, normalSuccessor, unwindSuccessor, normalPhiNode, unwindPhiNode, sourceSection);
            this.substitution = substitution;
        }

        @Override
        public void execute(VirtualFrame frame) {
            writeResult(frame, substitution.executeGeneric(frame));
        }
    }

    public static final class LLVMFunctionInvokeNode extends LLVMInvokeNodeImpl {

        @Child private LLVMExpressionNode functionNode;
        @Children private final LLVMExpressionNode[] argumentNodes;
        @Child private LLVMLookupDispatchNode dispatchNode;

        public LLVMFunctionInvokeNode(FunctionType type, FrameSlot resultLocation, LLVMExpressionNode functionNode, LLVMExpressionNode[] argumentNodes,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMExpressionNode normalPhiNode, LLVMExpressionNode unwindPhiNode, SourceSection sourceSection) {
            super(type, resultLocation, normalSuccessor, unwindSuccessor, normalPhiNode, unwindPhiNode, sourceSection);
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

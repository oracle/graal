/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNode;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

@GenerateWrapper
public abstract class LLVMInvokeNode extends LLVMControlFlowNode implements InstrumentableNode {

    private static class LLVMInvokeNodeImpl extends LLVMInvokeNode {

        @Child protected LLVMStatementNode normalPhiNode;
        @Child protected LLVMStatementNode unwindPhiNode;
        @Child protected LLVMValueProfilingNode returnValueProfile;

        protected final FunctionType type;

        @CompilationFinal private FrameSlot stackPointer;

        private final int normalSuccessor;
        private final int unwindSuccessor;
        private final FrameSlot resultLocation;
        private final ConditionProfile profile = ConditionProfile.createCountingProfile();

        LLVMInvokeNodeImpl(FunctionType type, FrameSlot resultLocation,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMStatementNode normalPhiNode, LLVMStatementNode unwindPhiNode, LLVMSourceLocation sourceSection) {
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
            this.returnValueProfile = (LLVMValueProfilingNode) LLVMValueProfilingNode.create(null, type.getReturnType());
        }

        @Override
        public LLVMStatementNode getPhiNode(int successorIndex) {
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
        @ExplodeLoop
        public void writePhis(VirtualFrame frame, int successorIndex) {
            if (profile.profile(successorIndex == NORMAL_SUCCESSOR)) {
                normalPhiNode.execute(frame);
            } else {
                assert successorIndex == UNWIND_SUCCESSOR;
                unwindPhiNode.execute(frame);
            }
        }
    }

    public static final int NORMAL_SUCCESSOR = 0;
    public static final int UNWIND_SUCCESSOR = 1;

    public LLVMInvokeNode(LLVMSourceLocation sourceSection) {
        super(sourceSection);
    }

    protected LLVMInvokeNode(LLVMInvokeNode delegate) {
        super(delegate.getSourceLocation());
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMInvokeNodeWrapper(this, this, probe);
    }

    @Override
    public boolean isInstrumentable() {
        return getSourceLocation() != null;
    }

    public abstract int getNormalSuccessor();

    public abstract int getUnwindSuccessor();

    public abstract void execute(VirtualFrame frame);

    public abstract void writeResult(VirtualFrame frame, Object value);

    public abstract void writePhis(VirtualFrame frame, int successorIndex);

    @Override
    public boolean needsBranchProfiling() {
        // we can't use branch profiling because the control flow happens via exception handling
        return false;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.StatementTag.class || tag == StandardTags.CallTag.class || super.hasTag(tag);
    }

    public static final class LLVMSubstitutionInvokeNode extends LLVMInvokeNodeImpl {

        @Child private LLVMExpressionNode substitution;

        public LLVMSubstitutionInvokeNode(FunctionType type, FrameSlot resultLocation, LLVMExpressionNode substitution,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMStatementNode normalPhiNode, LLVMStatementNode unwindPhiNode, LLVMSourceLocation sourceSection) {
            super(type, resultLocation, normalSuccessor, unwindSuccessor, normalPhiNode, unwindPhiNode, sourceSection);
            this.substitution = substitution;
        }

        @Override
        public void execute(VirtualFrame frame) {
            writeResult(frame, substitution.executeGeneric(frame));
        }
    }

    public static final class LLVMFunctionInvokeNode extends LLVMInvokeNodeImpl {

        @Children private final LLVMExpressionNode[] argumentNodes;
        @Child private LLVMLookupDispatchTargetNode dispatchTargetNode;
        @Child private LLVMDispatchNode dispatchNode;

        public LLVMFunctionInvokeNode(FunctionType type, FrameSlot resultLocation, LLVMExpressionNode functionNode, LLVMExpressionNode[] argumentNodes,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMStatementNode normalPhiNode, LLVMStatementNode unwindPhiNode, LLVMSourceLocation sourceSection) {
            super(type, resultLocation, normalSuccessor, unwindSuccessor, normalPhiNode, unwindPhiNode, sourceSection);
            this.argumentNodes = argumentNodes;
            this.dispatchTargetNode = LLVMLookupDispatchTargetNodeGen.create(functionNode);
            this.dispatchNode = LLVMDispatchNodeGen.create(type);
        }

        @Override
        public void execute(VirtualFrame frame) {
            Object function = dispatchTargetNode.executeGeneric(frame);
            Object[] argValues = prepareArguments(frame);
            writeResult(frame, dispatchNode.executeDispatch(function, argValues));
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

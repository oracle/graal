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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNodeFactory.ArgumentNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public final class LLVMCallNode extends LLVMExpressionNode {
    /*
     * This is a separate node to avoid race conditions.
     */
    private static final class IntrinsicDispatch extends Node {

        @CompilationFinal private LLVMFunctionDescriptor descriptor;
        @Child private LLVMExpressionNode intrinsic;

        IntrinsicDispatch(LLVMFunctionDescriptor descriptor, LLVMExpressionNode[] argumentNodes, FunctionType functionType) {
            this.descriptor = descriptor;
            // Note that functionType is not the same as descriptor.getType() in case of varargs!
            // functionType contains the types of the actual arguments of this particular call site.
            this.intrinsic = descriptor.getIntrinsic().generateNode(functionType, argumentNodes);
        }

        public boolean matches(Object function) {
            return function == descriptor;
        }

        public Object execute(VirtualFrame frame) {
            return intrinsic.executeGeneric(frame);
        }
    }

    public static final int USER_ARGUMENT_OFFSET = 1;

    private final FunctionType functionType;

    @Children private final LLVMExpressionNode[] argumentNodes;
    @Children private ArgumentNode[] prepareArgumentNodes;
    @Child private LLVMLookupDispatchTargetNode dispatchTargetNode;
    @Child private LLVMDispatchNode dispatchNode;
    @Child private IntrinsicDispatch intrinsicDispatch;

    @CompilationFinal private boolean mayBeBuiltin = true;

    private final LLVMSourceLocation source;

    public LLVMCallNode(FunctionType functionType, LLVMExpressionNode functionNode, LLVMExpressionNode[] argumentNodes, LLVMSourceLocation source) {
        this.functionType = functionType;
        this.argumentNodes = argumentNodes;
        this.dispatchTargetNode = LLVMLookupDispatchTargetNodeGen.create(functionNode);
        this.dispatchNode = LLVMDispatchNodeGen.create(functionType);
        this.source = source;
    }

    @ExplodeLoop
    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object function = dispatchTargetNode.executeGeneric(frame);
        if (mayBeBuiltin) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            mayBeBuiltin = false;
            if (function instanceof LLVMFunctionDescriptor) {
                LLVMFunctionDescriptor descriptor = (LLVMFunctionDescriptor) function;
                if (descriptor.isIntrinsicFunction()) {
                    try {
                        intrinsicDispatch = insert(new IntrinsicDispatch(descriptor, argumentNodes, functionType));
                    } catch (LLVMPolyglotException e) {
                        // re-throw with this node to generate correct stack trace
                        throw new LLVMPolyglotException(this, e.getMessage(), e);
                    }
                }
            }
        }
        IntrinsicDispatch intrinsic = intrinsicDispatch;
        if (intrinsic != null) {
            if (intrinsic.matches(function)) {
                return intrinsic.execute(frame);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            intrinsicDispatch = null;
            // re-insert nodes (parent was changed to IntrinsicDispatch node)
            for (int i = 0; i < argumentNodes.length; i++) {
                argumentNodes[i] = insert(argumentNodes[i]);
            }
        }
        Object[] argValues = new Object[argumentNodes.length];
        if (prepareArgumentNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            prepareArgumentNodes = new ArgumentNode[argumentNodes.length];
        }
        for (int i = 0; i < argumentNodes.length; i++) {
            if (prepareArgumentNodes[i] == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                prepareArgumentNodes[i] = insert(ArgumentNodeGen.create());
            }
            argValues[i] = prepareArgumentNodes[i].executeWithTarget(argumentNodes[i].executeGeneric(frame));
        }
        return dispatchNode.executeDispatch(function, argValues);
    }

    protected abstract static class ArgumentNode extends LLVMNode {

        protected abstract Object executeWithTarget(Object value);

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer address) {
            return address.copy();
        }

        @Fallback
        protected Object doOther(Object value) {
            return value;
        }
    }

    @Override
    public LLVMSourceLocation getSourceLocation() {
        return source;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.StatementTag.class || tag == StandardTags.CallTag.class || super.hasTag(tag);
    }
}

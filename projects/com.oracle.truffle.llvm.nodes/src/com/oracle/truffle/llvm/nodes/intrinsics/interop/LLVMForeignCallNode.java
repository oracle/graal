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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.LLVMPerformance;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;

@NodeChildren({@NodeChild("receiver"), @NodeChild("arguments")})
public abstract class LLVMForeignCallNode extends LLVMExpressionNode {

    private final LLVMStack stack;
    @Child private ToLLVMNode slowConvertNode;

    protected LLVMForeignCallNode(LLVMContext context) {
        this.stack = context.getStack();
        this.slowConvertNode = ToLLVMNode.createNode(null);
    }

    public abstract Object executeCall(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments);

    @SuppressWarnings("unused")
    @Specialization(guards = "function.getFunctionIndex() == functionIndex")
    public Object callDirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function.getFunctionIndex()") int functionIndex,
                    @Cached("create(getCallTarget(function))") DirectCallNode callNode,
                    @Cached("createToLLVMNodes(function)") ToLLVMNode[] toLLVMNodes, @Cached("arguments.length") int cachedLength) {
        assert function.getReturnType() != LLVMRuntimeType.STRUCT;
        return callNode.call(packArguments(arguments, toLLVMNodes, cachedLength));
    }

    @Specialization
    public Object callIndirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode, @Cached("arguments.length") int cachedLength) {
        assert function.getReturnType() != LLVMRuntimeType.STRUCT;
        LLVMPerformance.warn(this);
        return callNode.call(getCallTarget(function), packArguments(function, arguments, cachedLength));
    }

    @ExplodeLoop
    private Object[] packArguments(LLVMFunctionDescriptor function, Object[] arguments, int cachedLength) {
        if (arguments.length != cachedLength) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }
        final Object[] packedArguments = new Object[1 + cachedLength];
        packedArguments[0] = stack.getUpperBounds();
        for (int i = 0; i < cachedLength; i++) {
            packedArguments[i + 1] = slowConvertNode.slowConvert(arguments[i], ToLLVMNode.convert(function.getParameterTypes()[i]));
        }
        return packedArguments;
    }

    protected CallTarget getCallTarget(LLVMFunctionDescriptor function) {
        return function.getCallTarget();
    }

    @ExplodeLoop
    private Object[] packArguments(Object[] arguments, ToLLVMNode[] toLLVMNodes, int cachedLength) {
        if (arguments.length != cachedLength) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }
        final Object[] packedArguments = new Object[1 + cachedLength];
        packedArguments[0] = stack.getUpperBounds();
        for (int i = 0; i < toLLVMNodes.length; i++) {
            packedArguments[i + 1] = toLLVMNodes[i].executeWithTarget(arguments[i]);
        }
        for (int i = toLLVMNodes.length; i < cachedLength; i++) {
            packedArguments[i + 1] = arguments[i];
        }
        return packedArguments;
    }

    protected ToLLVMNode[] createToLLVMNodes(LLVMFunctionDescriptor function) {
        CompilerAsserts.neverPartOfCompilation();
        assert !function.isVarArgs() : "not supported yet";
        LLVMRuntimeType[] parameterTypes = function.getParameterTypes();
        ToLLVMNode[] toLLVMNodes = new ToLLVMNode[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            toLLVMNodes[i] = ToLLVMNode.createNode(ToLLVMNode.convert(parameterTypes[i]));
        }
        return toLLVMNodes;
    }

}

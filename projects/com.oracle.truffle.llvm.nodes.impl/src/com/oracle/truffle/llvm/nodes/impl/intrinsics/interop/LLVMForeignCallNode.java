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
package com.oracle.truffle.llvm.nodes.impl.intrinsics.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

@NodeChildren({@NodeChild("receiver"), @NodeChild("arguments")})
public abstract class LLVMForeignCallNode extends LLVMExpressionNode {

    private final LLVMContext context;
    private final LLVMStack stack;

    protected LLVMForeignCallNode(LLVMContext context) {
        this.context = context;
        this.stack = context.getStack();
    }

    public abstract Object executeCall(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments);

    @SuppressWarnings("unused")
    @Specialization(guards = "function.getFunctionIndex() == functionIndex")
    public Object callDirect(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function.getFunctionIndex()") int functionIndex,
                    @Cached("create(getCallTarget(function))") DirectCallNode callNode) {
        assert function.getReturnType() != LLVMRuntimeType.STRUCT;
        return callNode.call(frame, packArguments(arguments));
    }

    @Specialization
    public Object callIndirect(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode) {
        assert function.getReturnType() != LLVMRuntimeType.STRUCT;
        return callNode.call(frame, getCallTarget(function), packArguments(arguments));
    }

    protected CallTarget getCallTarget(LLVMFunctionDescriptor function) {
        return context.getFunction(function);
    }

    private Object[] packArguments(Object[] arguments) {
        final Object[] packedArguments = new Object[1 + arguments.length];
        packedArguments[0] = stack.getUpperBounds();
        System.arraycopy(arguments, 0, packedArguments, 1, arguments.length);
        return packedArguments;
    }

}

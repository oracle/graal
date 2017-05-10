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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMPerformance;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

abstract class LLVMForeignCallNode extends LLVMNode {

    private final LLVMStack stack;
    @Child protected LLVMDataEscapeNode prepareValueForEscape;

    protected LLVMForeignCallNode(LLVMStack stack, Type returnType) {
        this.stack = stack;
        this.prepareValueForEscape = LLVMDataEscapeNodeGen.create(returnType);
    }

    public static class PackForeignArgumentsNode extends Node {
        @Children private final ToLLVMNode[] toLLVM;

        PackForeignArgumentsNode(Type[] parameterTypes, int argumentsLength) {
            this.toLLVM = new ToLLVMNode[argumentsLength];
            for (int i = 0; i < parameterTypes.length; i++) {
                toLLVM[i] = ToLLVMNode.createNode(ToLLVMNode.convert(parameterTypes[i]));
            }
            for (int i = parameterTypes.length; i < argumentsLength; i++) {
                toLLVM[i] = ToLLVMNode.createNode(Object.class);
            }
        }

        @ExplodeLoop
        Object[] pack(Object[] arguments, LLVMStack stack) {
            assert arguments.length == toLLVM.length;
            final Object[] packedArguments = new Object[1 + toLLVM.length];
            packedArguments[0] = stack.getUpperBounds();
            for (int i = 0; i < toLLVM.length; i++) {
                packedArguments[i + 1] = toLLVM[i].executeWithTarget(arguments[i]);
            }
            return packedArguments;
        }
    }

    public static PackForeignArgumentsNode createFastPackArguments(LLVMFunctionDescriptor descriptor, int length) {
        checkArgLength(descriptor.getType().getArgumentTypes().length, length);
        return new PackForeignArgumentsNode(descriptor.getType().getArgumentTypes(), length);
    }

    protected static class SlowPackForeignArgumentsNode extends Node {
        @Child ToLLVMNode slowConvert = ToLLVMNode.createNode(null);

        Object[] pack(LLVMFunctionDescriptor function, Object[] arguments, LLVMStack stack) {
            int actualArgumentsLength = Math.max(arguments.length, function.getType().getArgumentTypes().length);
            final Object[] packedArguments = new Object[1 + actualArgumentsLength];
            packedArguments[0] = stack.getUpperBounds();
            for (int i = 0; i < function.getType().getArgumentTypes().length; i++) {
                packedArguments[i + 1] = slowConvert.slowConvert(arguments[i], ToLLVMNode.convert(function.getType().getArgumentTypes()[i]));
            }
            for (int i = function.getType().getArgumentTypes().length; i < arguments.length; i++) {
                packedArguments[i + 1] = slowConvert.slowConvert(arguments[i]);
            }
            return packedArguments;
        }
    }

    public static SlowPackForeignArgumentsNode createSlowPackArguments() {
        return new SlowPackForeignArgumentsNode();
    }

    public abstract Object executeCall(VirtualFrame frame, LLVMFunction function, Object[] arguments);

    @SuppressWarnings("unused")
    @Specialization(guards = {"function.getFunctionIndex() == functionIndex", "cachedLength == arguments.length"})
    public Object callDirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function.getFunctionIndex()") long functionIndex,
                    @Cached("create(getCallTarget(function))") DirectCallNode callNode,
                    @Cached("createFastPackArguments(function, arguments.length)") PackForeignArgumentsNode packNode,
                    @Cached("arguments.length") int cachedLength,
                    @Cached("function.getContext()") LLVMContext context) {
        assert !(function.getType().getReturnType() instanceof StructureType);
        Object result = callNode.call(packNode.pack(arguments, stack));
        return prepareValueForEscape.executeWithTarget(result, context);
    }

    @Specialization
    public Object callIndirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode, @Cached("createSlowPackArguments()") SlowPackForeignArgumentsNode slowPack) {
        assert !(function.getType().getReturnType() instanceof StructureType);
        LLVMPerformance.warn(this);
        Object result = callNode.call(getCallTarget(function), slowPack.pack(function, arguments, stack));
        return prepareValueForEscape.executeWithTarget(result, function.getContext());
    }

    protected CallTarget getCallTarget(LLVMFunctionDescriptor function) {
        return function.getLLVMIRFunction();
    }

    private static void checkArgLength(int minLength, int actualLength) {
        if (actualLength < minLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwArgLengthException(minLength, actualLength);
        }
    }

    private static void throwArgLengthException(int minLength, int actualLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("At least ").append(minLength).append(" arguments expected, but only ").append(actualLength).append(" arguments received.");
        throw new IllegalStateException(sb.toString());
    }
}

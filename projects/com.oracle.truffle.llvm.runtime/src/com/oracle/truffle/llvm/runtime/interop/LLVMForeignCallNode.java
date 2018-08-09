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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMGetStackNode;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.SlowPathForeignToLLVM;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * Used when an LLVM bitcode method is called from another language.
 */
public abstract class LLVMForeignCallNode extends LLVMNode {

    @Child protected LLVMDataEscapeNode prepareValueForEscape = LLVMDataEscapeNode.create();

    static class PackForeignArgumentsNode extends LLVMNode {
        @Children private final ForeignToLLVM[] toLLVM;

        PackForeignArgumentsNode(NodeFactory nodeFactory, Type[] parameterTypes, LLVMInteropType interopType, int argumentsLength, boolean isVarargs) {
            this.toLLVM = new ForeignToLLVM[argumentsLength];
            if (interopType instanceof LLVMInteropType.Function) {
                LLVMInteropType.Function interopFunctionType = (LLVMInteropType.Function) interopType;
                // If the function has varargs, the parameter types contain an extra VOID entry.
                assert interopFunctionType.getParameterLength() == parameterTypes.length + (isVarargs ? 1 : 0);
                for (int i = 0; i < parameterTypes.length; i++) {
                    LLVMInteropType interopParameterType = interopFunctionType.getParameter(i);
                    if (interopParameterType instanceof LLVMInteropType.Value) {
                        toLLVM[i] = nodeFactory.createForeignToLLVM((LLVMInteropType.Value) interopParameterType);
                    } else {
                        // interop only supported for value types
                        toLLVM[i] = nodeFactory.createForeignToLLVM(ForeignToLLVM.convert(parameterTypes[i]));
                    }
                }
            } else {
                // no interop parameter types available
                for (int i = 0; i < parameterTypes.length; i++) {
                    toLLVM[i] = nodeFactory.createForeignToLLVM(ForeignToLLVM.convert(parameterTypes[i]));
                }
            }
            for (int i = parameterTypes.length; i < argumentsLength; i++) {
                toLLVM[i] = nodeFactory.createForeignToLLVM(ForeignToLLVMType.ANY);
            }
        }

        @ExplodeLoop
        Object[] pack(Object[] arguments, StackPointer stackPointer) {
            assert arguments.length == toLLVM.length;
            final Object[] packedArguments = new Object[1 + toLLVM.length];
            packedArguments[0] = stackPointer;
            for (int i = 0; i < toLLVM.length; i++) {
                packedArguments[i + 1] = toLLVM[i].executeWithTarget(arguments[i]);
            }
            return packedArguments;
        }
    }

    public static LLVMForeignCallNode create() {
        return LLVMForeignCallNodeGen.create();
    }

    protected PackForeignArgumentsNode createFastPackArguments(LLVMFunctionDescriptor descriptor, int length) {
        checkArgLength(descriptor.getType().getArgumentTypes().length, length);
        return new PackForeignArgumentsNode(getNodeFactory(), descriptor.getType().getArgumentTypes(), descriptor.getInteropType(), length, descriptor.getType().isVarargs());
    }

    protected static class SlowPackForeignArgumentsNode extends LLVMNode {
        @Child private SlowPathForeignToLLVM slowConvert = ForeignToLLVM.createSlowPathNode();

        Object[] pack(LLVMFunctionDescriptor function, Object[] arguments, StackPointer stackPointer) {
            int actualArgumentsLength = Math.max(arguments.length, function.getType().getArgumentTypes().length);
            final Object[] packedArguments = new Object[1 + actualArgumentsLength];
            packedArguments[0] = stackPointer;
            for (int i = 0; i < function.getType().getArgumentTypes().length; i++) {
                packedArguments[i + 1] = slowConvert.convert(function.getType().getArgumentTypes()[i], arguments[i]);
            }
            for (int i = function.getType().getArgumentTypes().length; i < arguments.length; i++) {
                packedArguments[i + 1] = slowConvert.convert(ForeignToLLVMType.ANY, arguments[i]);
            }
            return packedArguments;
        }
    }

    public static SlowPackForeignArgumentsNode createSlowPackArguments() {
        return new SlowPackForeignArgumentsNode();
    }

    public abstract Object executeCall(LLVMFunctionDescriptor function, Object[] arguments);

    @CompilationFinal private LLVMThreadingStack threadingStack = null;

    private LLVMThreadingStack getThreadingStack(LLVMContext context) {
        if (threadingStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threadingStack = context.getThreadingStack();
        }
        return threadingStack;
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "3", guards = {"function == cachedFunction", "cachedLength == arguments.length"})
    protected Object callDirectCached(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function") LLVMFunctionDescriptor cachedFunction,
                    @Cached("create(getCallTarget(cachedFunction))") DirectCallNode callNode,
                    @Cached("createFastPackArguments(cachedFunction, arguments.length)") PackForeignArgumentsNode packNode,
                    @Cached("arguments.length") int cachedLength,
                    @Cached("cachedFunction.getContext()") LLVMContext context,
                    @Cached("create()") LLVMGetStackNode getStack) {
        assert !(cachedFunction.getType().getReturnType() instanceof StructureType);
        return directCall(arguments, callNode, packNode, getStack, context);
    }

    private Object directCall(Object[] arguments, DirectCallNode callNode, PackForeignArgumentsNode packNode, LLVMGetStackNode getStack, LLVMContext context) {
        Object result;
        LLVMStack stack = getStack.executeWithTarget(getThreadingStack(context), Thread.currentThread());
        try (StackPointer stackPointer = stack.newFrame()) {
            result = callNode.call(packNode.pack(arguments, stackPointer));
        }
        return prepareValueForEscape.executeWithTarget(result);
    }

    @Specialization(replaces = "callDirectCached")
    protected Object callIndirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode,
                    @Cached("createSlowPackArguments()") SlowPackForeignArgumentsNode slowPack,
                    @Cached("create()") LLVMGetStackNode getStack) {
        assert !(function.getType().getReturnType() instanceof StructureType);
        LLVMStack stack = getStack.executeWithTarget(function.getContext().getThreadingStack(), Thread.currentThread());
        Object result;
        try (StackPointer stackPointer = stack.newFrame()) {
            result = callNode.call(getCallTarget(function), slowPack.pack(function, arguments, stackPointer));
        }
        return prepareValueForEscape.executeWithTarget(result);
    }

    protected CallTarget getCallTarget(LLVMFunctionDescriptor function) {
        if (function.isLLVMIRFunction()) {
            return function.getLLVMIRFunction();
        } else if (function.isIntrinsicFunction()) {
            return function.getNativeIntrinsic().cachedCallTarget(function.getType());
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("native function not supported at this point");
        }
    }

    private static void checkArgLength(int minLength, int actualLength) {
        if (actualLength < minLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwArgLengthException(minLength, actualLength);
        }
    }

    private static void throwArgLengthException(int minLength, int actualLength) {
        throw ArityException.raise(minLength, actualLength);
    }
}

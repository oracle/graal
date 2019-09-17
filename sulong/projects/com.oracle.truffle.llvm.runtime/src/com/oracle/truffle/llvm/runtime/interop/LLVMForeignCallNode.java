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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMGetStackNode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNodeFactory.PackForeignArgumentsNodeGen;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * Used when an LLVM bitcode method is called from another language.
 */
public class LLVMForeignCallNode extends RootNode {

    abstract static class PackForeignArgumentsNode extends LLVMNode {

        abstract Object[] execute(Object[] arguments, StackPointer stackPointer) throws ArityException;

        @Children final ForeignToLLVM[] toLLVM;

        PackForeignArgumentsNode(Type[] parameterTypes, LLVMInteropType interopType) {
            NodeFactory nodeFactory = getNodeFactory();
            this.toLLVM = new ForeignToLLVM[parameterTypes.length];
            if (interopType instanceof LLVMInteropType.Function) {
                LLVMInteropType.Function interopFunctionType = (LLVMInteropType.Function) interopType;
                assert interopFunctionType.getParameterLength() == parameterTypes.length;
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
        }

        @Specialization(guards = "arguments.length == toLLVM.length")
        @ExplodeLoop
        Object[] packNonVarargs(Object[] arguments, StackPointer stackPointer) {
            assert arguments.length >= toLLVM.length;
            final Object[] packedArguments = new Object[1 + arguments.length];
            packedArguments[0] = stackPointer;
            for (int i = 0; i < toLLVM.length; i++) {
                packedArguments[i + 1] = toLLVM[i].executeWithTarget(arguments[i]);
            }
            return packedArguments;
        }

        ForeignToLLVM[] createVarargsToLLVM(int argCount) {
            int count = argCount - toLLVM.length;
            if (count > 0) {
                NodeFactory nodeFactory = LLVMNode.getNodeFactory();
                ForeignToLLVM[] ret = new ForeignToLLVM[count];
                for (int i = 0; i < count; i++) {
                    ret[i] = nodeFactory.createForeignToLLVM(ForeignToLLVMType.ANY);
                }
                return ret;
            } else {
                return new ForeignToLLVM[0];
            }
        }

        boolean checkLength(int argCount, ForeignToLLVM[] varargsToLLVM) {
            return argCount == toLLVM.length + varargsToLLVM.length;
        }

        @Specialization(guards = "checkLength(arguments.length, varargsToLLVM)", replaces = "packNonVarargs")
        @ExplodeLoop
        Object[] packCachedArgCount(Object[] arguments, StackPointer stackPointer,
                        @Cached("createVarargsToLLVM(arguments.length)") ForeignToLLVM[] varargsToLLVM) {
            assert arguments.length >= toLLVM.length;
            final Object[] packedArguments = packNonVarargs(arguments, stackPointer);
            for (int i = toLLVM.length, j = 0; j < varargsToLLVM.length; i++, j++) {
                packedArguments[i + 1] = varargsToLLVM[j].executeWithTarget(arguments[i]);
            }
            return packedArguments;
        }

        ForeignToLLVM createVarargsToLLVM() {
            return LLVMNode.getNodeFactory().createForeignToLLVM(ForeignToLLVMType.ANY);
        }

        @Specialization(guards = "arguments.length >= toLLVM.length", replaces = "packCachedArgCount")
        Object[] packGeneric(Object[] arguments, StackPointer stackPointer,
                        @Cached("createVarargsToLLVM()") ForeignToLLVM varargsToLLVM) {
            Object[] args = packNonVarargs(arguments, stackPointer);
            for (int i = toLLVM.length; i < arguments.length; i++) {
                args[i + 1] = varargsToLLVM.executeWithTarget(arguments[i]);
            }
            return args;
        }

        @Specialization(guards = "arguments.length < toLLVM.length")
        Object[] error(Object[] arguments, @SuppressWarnings("unused") StackPointer stackPointer) throws ArityException {
            CompilerDirectives.transferToInterpreter();
            throw ArityException.create(toLLVM.length, arguments.length);
        }
    }

    private final ContextReference<LLVMContext> ctxRef;
    private final LLVMInteropType.Structured returnBaseType;

    @Child LLVMGetStackNode getStack;
    @Child DirectCallNode callNode;
    @Child LLVMDataEscapeNode prepareValueForEscape;
    @Child PackForeignArgumentsNode packArguments;

    public LLVMForeignCallNode(LLVMLanguage language, LLVMFunctionDescriptor function, LLVMInteropType interopType) {
        super(language);
        this.ctxRef = language.getContextReference();
        this.returnBaseType = getReturnBaseType(interopType);
        this.getStack = LLVMGetStackNode.create();
        this.callNode = DirectCallNode.create(getCallTarget(function));
        this.prepareValueForEscape = LLVMDataEscapeNode.create(function.getType().getReturnType());
        this.packArguments = PackForeignArgumentsNodeGen.create(function.getType().getArgumentTypes(), interopType);
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object result;
        LLVMThreadingStack threadingStack = ctxRef.get().getThreadingStack();
        LLVMStack stack = getStack.executeWithTarget(threadingStack, Thread.currentThread());
        try (StackPointer stackPointer = stack.newFrame()) {
            result = callNode.call(packArguments.execute(frame.getArguments(), stackPointer));
        } catch (ArityException ex) {
            throw silenceException(RuntimeException.class, ex);
        }
        return prepareValueForEscape.executeWithType(result, returnBaseType);
    }

    private static LLVMInteropType.Structured getReturnBaseType(LLVMInteropType functionType) {
        if (functionType instanceof LLVMInteropType.Function) {
            LLVMInteropType returnType = ((LLVMInteropType.Function) functionType).getReturnType();
            if (returnType instanceof LLVMInteropType.Value) {
                return ((LLVMInteropType.Value) returnType).getBaseType();
            }
        }
        return null;
    }

    static CallTarget getCallTarget(LLVMFunctionDescriptor function) {
        if (function.isLLVMIRFunction()) {
            return function.getLLVMIRFunction();
        } else if (function.isIntrinsicFunction()) {
            return function.getIntrinsic().cachedCallTarget(function.getType());
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("native function not supported at this point");
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <E extends Exception> RuntimeException silenceException(Class<E> type, Exception ex) throws E {
        throw (E) ex;
    }
}

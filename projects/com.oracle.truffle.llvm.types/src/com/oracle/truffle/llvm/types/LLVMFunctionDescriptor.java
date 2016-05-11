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
package com.oracle.truffle.llvm.types;

import java.lang.reflect.Method;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.Factory10;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

public final class LLVMFunctionDescriptor implements TruffleObject, Comparable<LLVMFunctionDescriptor> {

    public enum LLVMRuntimeType {
        I1,
        I8,
        I16,
        I32,
        I64,
        I_VAR_BITWIDTH,
        HALF,
        FLOAT,
        DOUBLE,
        X86_FP80,
        ADDRESS,
        STRUCT,
        ARRAY,
        FUNCTION_ADDRESS,
        I1_VECTOR,
        I8_VECTOR,
        I16_VECTOR,
        I32_VECTOR,
        I64_VECTOR,
        FLOAT_VECTOR,
        DOUBLE_VECTOR,
        VOID,
        ILLEGAL;
    }

    private final String functionName;
    private final LLVMRuntimeType returnType;
    private final LLVMRuntimeType[] parameterTypes;
    private final boolean hasVarArgs;
    private final int functionId;

    private LLVMFunctionDescriptor(String name, LLVMRuntimeType llvmReturnType, LLVMRuntimeType[] llvmParamTypes, boolean varArgs, int functionId) {
        this.functionName = name;
        this.returnType = llvmReturnType;
        this.parameterTypes = llvmParamTypes;
        this.hasVarArgs = varArgs;
        this.functionId = functionId;
    }

    public static LLVMFunctionDescriptor create(String name, LLVMRuntimeType llvmReturnType, LLVMRuntimeType[] llvmParamTypes, boolean varArgs, int functionId) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMFunctionDescriptor func = new LLVMFunctionDescriptor(name, llvmReturnType, llvmParamTypes, varArgs, functionId);
        return func;
    }

    public static LLVMFunctionDescriptor create(int index) {
        return new LLVMFunctionDescriptor(null, LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false, index);
    }

    public String getName() {
        return functionName;
    }

    public LLVMRuntimeType getReturnType() {
        return returnType;
    }

    public LLVMRuntimeType[] getParameterTypes() {
        return parameterTypes;
    }

    public boolean isVarArgs() {
        return hasVarArgs;
    }

    /**
     * Gets an unique index for a function descriptor.
     *
     * @return the function's index
     */
    public int getFunctionIndex() {
        return functionId;
    }

    @Override
    public String toString() {
        return getName() + " " + getFunctionIndex();
    }

    @Override
    public int compareTo(LLVMFunctionDescriptor o) {
        return Integer.compare(functionId, o.getFunctionIndex());
    }

    @Override
    public int hashCode() {
        return functionId;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVMFunctionDescriptor)) {
            return false;
        } else {
            LLVMFunctionDescriptor other = (LLVMFunctionDescriptor) obj;
            return getFunctionIndex() == other.getFunctionIndex();
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(LLVMFunctionDescriptor.class, new Factory10() {

            @Override
            public CallTarget accessIsNull() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessIsExecutable() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
            }

            @Override
            public CallTarget accessIsBoxed() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessHasSize() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessGetSize() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessUnbox() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessRead() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessWrite() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessExecute(int argumentsLength) {
                return Truffle.getRuntime().createCallTarget(new ForeignCallNode());
            }

            @Override
            public CallTarget accessInvoke(int argumentsLength) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessNew(int argumentsLength) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CallTarget accessMessage(Message unknown) {
                throw new UnsupportedOperationException();
            }

        });
    }

    private static class ForeignCallNode extends RootNode {

        private final LLVMStack stack;

        @Child private IndirectCallNode callNode;

        protected ForeignCallNode() {
            super(getLLVMLanguage(), null, new FrameDescriptor());
            stack = getLLVMStack();
            callNode = Truffle.getRuntime().createIndirectCallNode();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final LLVMFunctionDescriptor function = (LLVMFunctionDescriptor) ForeignAccess.getReceiver(frame);
            assert function.getReturnType() != LLVMRuntimeType.STRUCT;
            final CallTarget callTarget = getCallTarget(function);
            final List<Object> arguments = ForeignAccess.getArguments(frame);
            final Object[] packedArguments = new Object[1 + arguments.size()];
            packedArguments[0] = stack.getUpperBounds();
            System.arraycopy(arguments.toArray(), 0, packedArguments, 1, arguments.size());
            return callNode.call(frame, callTarget, packedArguments);
        }

        // TODO No static access to these classes at the moment

        @SuppressWarnings("unchecked")
        private static Class<? extends TruffleLanguage<?>> getLLVMLanguage() {
            try {
                return (Class<? extends TruffleLanguage<?>>) Class.forName("com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        private static LLVMStack getLLVMStack() {
            try {
                final Class<?> contextClass = Class.forName("com.oracle.truffle.llvm.nodes.impl.base.LLVMContext");
                final Method getStaticStackMethod = contextClass.getMethod("getStaticStack");
                return (LLVMStack) getStaticStackMethod.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static CallTarget getCallTarget(LLVMFunctionDescriptor function) {
            try {
                final Class<?> contextClass = Class.forName("com.oracle.truffle.llvm.nodes.impl.base.LLVMContext");
                final Method getCallTargetMethod = contextClass.getMethod("getCallTarget", LLVMFunctionDescriptor.class);
                return (CallTarget) getCallTargetMethod.invoke(null, function);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

}

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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import com.oracle.truffle.llvm.runtime.interop.LLVMFunctionMessageResolutionForeign;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public final class LLVMFunctionDescriptor implements LLVMFunction, TruffleObject, Comparable<LLVMFunctionDescriptor> {

    private final String functionName;
    private final FunctionType type;
    private final int functionId;

    @CompilationFinal private RootCallTarget callTarget;
    @CompilationFinal private TruffleObject nativeSymbol;
    @CompilationFinal private RootCallTarget intrinsic;
    @CompilationFinal private LazyToTruffleConverter lazyConverter;
    @CompilationFinal private CyclicAssumption functionDescriptorState;

    private final LLVMContext context;

    private LLVMFunctionDescriptor(LLVMContext context, String name, FunctionType type, int functionId) {
        CompilerAsserts.neverPartOfCompilation();
        this.context = context;
        this.functionName = name;
        this.type = type;
        this.functionId = functionId;
        this.callTarget = null;
        this.nativeSymbol = null;
        this.functionDescriptorState = new CyclicAssumption(name);
    }

    public static LLVMFunctionDescriptor create(LLVMContext context, String name, FunctionType type, int functionId) {
        LLVMFunctionDescriptor func = new LLVMFunctionDescriptor(context, name, type, functionId);
        return func;
    }

    public interface LazyToTruffleConverter {
        void convert();
    }

    public void setLazyToTruffleConverter(LazyToTruffleConverter lazyToTruffleConverterImpl) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.lazyConverter = lazyToTruffleConverterImpl;
        functionDescriptorState.invalidate();
    }

    public RootCallTarget getCallTarget() {
        if (lazyConverter != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // lazy conversion
            lazyConverter.convert();
            lazyConverter = null;
            functionDescriptorState.invalidate();
        }
        if (!functionDescriptorState.getAssumption().isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        if (callTarget == null && intrinsic != null) {
            return intrinsic;
        }
        return callTarget;
    }

    public void setCallTarget(RootCallTarget callTarget) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert this.nativeSymbol == null;
        this.callTarget = callTarget;
        functionDescriptorState.invalidate();
    }

    public void setIntrinsicCallTarget(RootCallTarget callTarget) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.intrinsic = callTarget;
        functionDescriptorState.invalidate();
    }

    public TruffleObject getNativeSymbol() {
        if (!functionDescriptorState.getAssumption().isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return nativeSymbol;
    }

    public void setNativeSymbol(TruffleObject nativeSymbol) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert this.callTarget == null && this.nativeSymbol == null && this.lazyConverter == null;
        this.nativeSymbol = nativeSymbol;
        functionDescriptorState.invalidate();
    }

    public String getName() {
        return functionName;
    }

    public FunctionType getType() {
        return type;
    }

    /**
     * Gets an unique index for a function descriptor.
     *
     * @return the function's index
     */
    @Override
    public int getFunctionIndex() {
        return functionId;
    }

    public boolean isNullFunction() {
        return functionId == 0;
    }

    @Override
    public String toString() {
        if (functionName != null) {
            return String.format("function@%d '%s'", functionId, functionName);
        } else {
            return String.format("function@%d (anonymous)", functionId);
        }
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

    public static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMFunctionDescriptor;
    }

    public LLVMContext getContext() {
        return context;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMFunctionMessageResolutionForeign.ACCESS;
    }

}

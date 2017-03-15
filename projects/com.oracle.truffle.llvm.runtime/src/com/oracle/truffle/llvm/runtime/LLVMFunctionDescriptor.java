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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public final class LLVMFunctionDescriptor extends LLVMFunction implements Comparable<LLVMFunctionDescriptor> {

    private final String functionName;
    private final FunctionType type;
    private final int functionId;

    private RootCallTarget callTarget;
    private TruffleObject nativeSymbol;
    private RootCallTarget intrinsic;

    private LLVMFunctionDescriptor(String name, FunctionType type, int functionId) {
        this.functionName = name;
        this.type = type;
        this.functionId = functionId;
        this.callTarget = null;
        this.nativeSymbol = null;
    }

    public static LLVMFunctionDescriptor create(String name, FunctionType type, int functionId) {
        LLVMFunctionDescriptor func = new LLVMFunctionDescriptor(name, type, functionId);
        return func;
    }

    public RootCallTarget getCallTarget() {
        if (callTarget == null && intrinsic != null) {
            return intrinsic;
        }
        return callTarget;
    }

    public void setCallTarget(RootCallTarget callTarget) {
        assert this.nativeSymbol == null;
        this.callTarget = callTarget;
    }

    public void setIntrinsicCallTarget(RootCallTarget callTarget) {
        this.intrinsic = callTarget;
    }

    public TruffleObject getNativeSymbol() {
        return nativeSymbol;
    }

    public void setNativeSymbol(TruffleObject nativeSymbol) {
        assert this.callTarget == null && this.nativeSymbol == null;
        this.nativeSymbol = nativeSymbol;
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
}

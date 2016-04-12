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

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

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

    private static final Map<String, LLVMFunctionDescriptor> referenceMap = new HashMap<>();
    @CompilationFinal private static LLVMFunctionDescriptor[] functions = new LLVMFunctionDescriptor[0];

    // arbitrary number
    public static final int FUNCTION_START_ADDR = 1000;
    private static int functionCounter = 0;

    // cache function headers for identity comparison

    private final String functionName;
    private final LLVMRuntimeType returnType;
    private final LLVMRuntimeType[] parameterTypes;
    private final boolean hasVarArgs;
    private final LLVMAddress functionAddress;

    public static void reset() {
        referenceMap.clear();
    }

    private LLVMFunctionDescriptor(String name, LLVMRuntimeType llvmReturnType, LLVMRuntimeType[] llvmParamTypes, boolean varArgs) {
        this.functionName = name;
        this.returnType = llvmReturnType;
        this.parameterTypes = llvmParamTypes;
        this.hasVarArgs = varArgs;
        this.functionAddress = LLVMAddress.fromLong(FUNCTION_START_ADDR + functionCounter++);
    }

    public static LLVMFunctionDescriptor create(String name, LLVMRuntimeType llvmReturnType, LLVMRuntimeType[] llvmParamTypes, boolean varArgs) {
        final LLVMFunctionDescriptor result;
        CompilerAsserts.neverPartOfCompilation();
        if (referenceMap.containsKey(name)) {
            result = referenceMap.get(name);
        } else {
            LLVMFunctionDescriptor func = new LLVMFunctionDescriptor(name, llvmReturnType, llvmParamTypes, varArgs);
            // FIXME instead finish initialization at the end
            LLVMFunctionDescriptor[] newFunctions = new LLVMFunctionDescriptor[functions.length + 1];
            System.arraycopy(functions, 0, newFunctions, 0, functions.length);
            newFunctions[func.getFunctionIndex()] = func;
            functions = newFunctions;
            referenceMap.put(name, func);
            result = func;
        }
        return result;
    }

    public static LLVMFunctionDescriptor createFromName(String name) {
        return create(name, LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
    }

    public static LLVMFunctionDescriptor createUndefinedFunction() {
        return createFromName("<undefined function>");
    }

    public static LLVMFunctionDescriptor createZeroFunction() {
        return createFromName("<zero function>");
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
     * Gets an index for a function.
     *
     * @return an index between 0 and {@link #getNumberRegisteredFunctions()}
     */
    public int getFunctionIndex() {
        return getFunctionIndex(getFunctionAddress());
    }

    public LLVMAddress getFunctionAddress() {
        return functionAddress;
    }

    public static LLVMFunctionDescriptor createFromAddress(LLVMAddress addr) {
        LLVMFunctionDescriptor llvmFunction = functions[getFunctionIndex(addr)];
        assert llvmFunction != null;
        return llvmFunction;
    }

    private static int getFunctionIndex(LLVMAddress addr) {
        long functionAddr = addr.getVal() - FUNCTION_START_ADDR;
        return (int) functionAddr;
    }

    @Override
    public String toString() {
        return getName() + " " + getFunctionAddress();
    }

    @Override
    public ForeignAccess getForeignAccess() {
        throw new AssertionError();
    }

    public static int getNumberRegisteredFunctions() {
        return functionCounter;
    }

    @Override
    public int compareTo(LLVMFunctionDescriptor o) {
        return getName().compareTo(o.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode() + 11 * getReturnType().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVMFunctionDescriptor)) {
            return false;
        } else {
            LLVMFunctionDescriptor other = (LLVMFunctionDescriptor) obj;
            if (!getName().equals(other.getName())) {
                return false;
            } else if (!getReturnType().equals(other.getReturnType())) {
                return false;
            } else if (getParameterTypes().length != other.getParameterTypes().length) {
                return false;
            } else {
                for (int i = 0; i < getParameterTypes().length; i++) {
                    if (!getParameterTypes()[i].equals(other.getParameterTypes()[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

}

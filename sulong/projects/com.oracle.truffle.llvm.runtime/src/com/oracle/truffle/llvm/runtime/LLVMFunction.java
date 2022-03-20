/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.Function;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

/**
 * {@link LLVMFunctionCode} represents the symbol for a function.
 *
 */
public final class LLVMFunction extends LLVMSymbol {

    public static final LLVMFunction[] EMPTY = {};

    private final FunctionType type;
    private final Function function;
    private final String path;
    private LLVMSourceLocation sourceLocation;

    private final Assumption fixedCodeAssumption = Truffle.getRuntime().createAssumption();
    @CompilationFinal private LLVMFunctionCode fixedCode;

    /**
     * Used in {@link com.oracle.truffle.llvm.runtime.nodes.func.LLVMDispatchNode} to bind it with
     * the eagerly initialized fixed signature.
     */
    @CompilationFinal private Object nfiSymbol;

    public static LLVMFunction create(String name, Function function, FunctionType type, BitcodeID bitcodeID, int symbolIndex, boolean exported, String path, boolean externalWeak) {
        return new LLVMFunction(name, function, type, bitcodeID, symbolIndex, exported, path, externalWeak);
    }

    public LLVMFunction(String name, Function function, FunctionType type, BitcodeID bitcodeID, int symbolIndex, boolean exported, String path, boolean externalWeak) {
        super(name, bitcodeID, symbolIndex, exported, externalWeak);
        this.type = type;
        this.function = function;
        this.path = path;
    }

    public LLVMSourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(LLVMSourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public String getStringPath() {
        return path;
    }

    public FunctionType getType() {
        return type;
    }

    public Function getFunction() {
        return function;
    }

    @Override
    public boolean isFunction() {
        return true;
    }

    @Override
    public boolean isGlobalVariable() {
        return false;
    }

    @Override
    public boolean isAlias() {
        return false;
    }

    @Override
    public LLVMFunction asFunction() {
        return this;
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        throw new IllegalStateException("Function " + getName() + " is not a global variable.");
    }

    public Assumption getFixedCodeAssumption() {
        return fixedCodeAssumption;
    }

    public LLVMFunctionCode getFixedCode() {
        return fixedCode;
    }

    public void setValue(LLVMPointer pointer) {
        if (fixedCodeAssumption.isValid()) {
            if (LLVMManagedPointer.isInstance(pointer)) {
                Object value = LLVMManagedPointer.cast(pointer).getObject();
                if (value instanceof LLVMFunctionDescriptor) {
                    LLVMFunctionDescriptor descriptor = (LLVMFunctionDescriptor) value;
                    LLVMFunctionCode code = descriptor.getFunctionCode();
                    if (fixedCode == null) {
                        fixedCode = code;
                        return;
                    } else if (fixedCode == code) {
                        return;
                    }
                }
            }
            fixedCode = null;
            fixedCodeAssumption.invalidate();
        }
    }

    @Override
    public boolean isElemPtrExpression() {
        return false;
    }

    @Override
    public LLVMElemPtrSymbol asElemPtrExpression() {
        throw new IllegalStateException("Function " + getName() + " is not a getElementPointer symbol.");
    }

    public void setNFISymbol(Object symbol) {
        this.nfiSymbol = symbol;
    }

    public Object getNFISymbol() {
        return this.nfiSymbol;
    }

    @Override
    public boolean isThreadLocalSymbol() {
        return false;
    }

    @Override
    public LLVMThreadLocalSymbol asThreadLocalSymbol() {
        throw new IllegalStateException("GetElementPointerConstant " + getName() + " has to be resolved and might not be a thread local global variable.");
    }
}

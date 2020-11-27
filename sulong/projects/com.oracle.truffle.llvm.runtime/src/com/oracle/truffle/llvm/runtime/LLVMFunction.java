/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.Function;
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

    private final Assumption fixedCodeAssumption = Truffle.getRuntime().createAssumption();
    @CompilationFinal private LLVMFunctionCode fixedCode;

    public static LLVMFunction create(String name, Function function, FunctionType type, int bitcodeID, int symbolIndex, boolean exported, String path) {
        return new LLVMFunction(name, function, type, bitcodeID, symbolIndex, exported, path);
    }

    public LLVMFunction(String name, Function function, FunctionType type, int bitcodeID, int symbolIndex, boolean exported, String path) {
        super(name, bitcodeID, symbolIndex, exported);
        this.type = type;
        this.function = function;
        this.path = path;
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
}

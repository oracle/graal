/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

public class LLVMThreadLocalSymbol extends LLVMSymbol {

    private final String name;
    private final LLVMSourceSymbol sourceSymbol;

    public static final LLVMThreadLocalSymbol[] EMPTY = {};

    public LLVMThreadLocalSymbol(String name, LLVMSourceSymbol sourceSymbol, IDGenerater.BitcodeID bitcodeID, int symbolIndex, boolean exported, boolean externalWeak) {
        super(name, bitcodeID, symbolIndex, exported, externalWeak);
        this.name = name;
        this.sourceSymbol = sourceSymbol;
    }

    public static LLVMThreadLocalSymbol create(String symbolName, LLVMSourceSymbol sourceSymbol, IDGenerater.BitcodeID bitcodeID, int symbolIndex, boolean exported, boolean externalWeak) {
        return new LLVMThreadLocalSymbol(symbolName, sourceSymbol, bitcodeID, symbolIndex, exported, externalWeak);
    }

    public String getSourceName() {
        return sourceSymbol != null ? sourceSymbol.getName() : name;
    }

    @Override
    public boolean isFunction() {
        return false;
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
        throw new IllegalStateException("Thread local global " + name + " is not a function.");
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        throw new IllegalStateException("Thread local global " + name + " is not a global.");
    }

    @Override
    public boolean isElemPtrExpression() {
        return false;
    }

    @Override
    public boolean isThreadLocalSymbol() {
        return true;
    }

    @Override
    public LLVMElemPtrSymbol asElemPtrExpression() {
        throw new IllegalStateException("Thread local global " + name + " is not a GetElementPointer symbol.");
    }

    @Override
    public LLVMThreadLocalSymbol asThreadLocalSymbol() {
        return this;
    }

    @Override
    public String toString() {
        return "(" + sourceSymbol + ")" + name;
    }
}

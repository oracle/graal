/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

public abstract class LLVMSymbol {

    @CompilationFinal private String name;
    @CompilationFinal private ExternalLibrary library;
    private final int bitcodeID;
    private final int symbolIndex;

    public LLVMSymbol(String name, ExternalLibrary library, int bitcodeID, int symbolIndex) {
        this.name = name;
        this.library = library;
        this.bitcodeID = bitcodeID;
        this.symbolIndex = symbolIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExternalLibrary getLibrary() {
        return library;
    }

    public void setLibrary(ExternalLibrary library) {
        this.library = library;
    }

    public int getSymbolIndex(boolean illegalOK) {
        if (symbolIndex >= 0 || illegalOK) {
            return symbolIndex;
        }
        throw new IllegalStateException("Invalid function index: " + symbolIndex);
    }

    public int getBitcodeID(boolean illegalOK) {
        if (bitcodeID >= 0 || illegalOK) {
            return bitcodeID;
        }
        throw new IllegalStateException("Invalid function ID: " + bitcodeID);
    }

    public abstract boolean isDefined();

    public abstract boolean isGlobalVariable();

    public abstract boolean isFunction();

    public abstract boolean isAlias();

    public abstract LLVMFunction asFunction();

    public abstract LLVMGlobal asGlobalVariable();
}

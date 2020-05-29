/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.except.LLVMIllegalSymbolIndexException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

public abstract class LLVMSymbol {

    @CompilationFinal private String name;
    @CompilationFinal private ExternalLibrary library;
    private final int moduleId;
    private final int symbolIndex;
    private final boolean exported;
    static final LLVMSymbol[] EMPTY = {};

    // Index for non-parsed symbols, such as alias, and function symbol for inline assembly.
    public static final int INVALID_INDEX = -1;

    // ID for non-parsed symbols, such as alias, function symbol for inline assembly.
    public static final int INVALID_ID = -1;

    // ID reserved for non-parsed miscellaneous functions.
    public static final int MISCFUNCTION_ID = 0;

    // Index reserved for non-parsed miscellaneous functions.
    private static int miscFunctionIndex = 0;

    public LLVMSymbol(String name, ExternalLibrary library, int bitcodeID, int symbolIndex, boolean exported) {
        this.name = name;
        this.library = library;
        this.moduleId = bitcodeID;
        this.symbolIndex = symbolIndex;
        this.exported = exported;
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

    public static int getMiscSymbolIndex() {
        int index = miscFunctionIndex;
        miscFunctionIndex++;
        return index;
    }

    public String getKind() {
        return this.getClass().getSimpleName();
    }

    public boolean isExported() {
        return exported;
    }

    /**
     * Get the unique index of the symbol. The index is assigned during parsing. Symbols that are
     * not created from parsing or that are alias have the value of -1.
     *
     * @param illegalOK if symbols created not from bitcode files can be retrieved.
     */
    public int getSymbolIndex(boolean illegalOK) {
        if (symbolIndex >= 0 || illegalOK) {
            return symbolIndex;
        }
        CompilerDirectives.transferToInterpreter();
        throw new LLVMIllegalSymbolIndexException("Invalid function index: " + symbolIndex);
    }

    /**
     * Get the unique module ID for the symbol. The ID is assigned during parsing. The module ID is
     * unqiue per bitcode file. Symbols that are not created from parsing or that are alias have the
     * value of -1.
     *
     * @param illegalOK if symbols created not from bitcode files can be retrieved.
     */
    public int getBitcodeID(boolean illegalOK) {
        if (moduleId >= 0 || illegalOK) {
            return moduleId;
        }
        CompilerDirectives.transferToInterpreter();
        throw new LLVMIllegalSymbolIndexException("Invalid function ID: " + moduleId);
    }

    public boolean hasValidIndexAndID() {
        return symbolIndex >= 0 && moduleId >= 0;
    }

    public abstract boolean isDefined();

    public abstract boolean isGlobalVariable();

    public abstract boolean isFunction();

    public abstract boolean isAlias();

    public abstract LLVMFunction asFunction();

    public abstract LLVMGlobal asGlobalVariable();
}

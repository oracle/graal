/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.except.LLVMIllegalSymbolIndexException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

public abstract class LLVMSymbol {

    public static final LLVMSymbol[] EMPTY = new LLVMSymbol[0];

    private final String name;
    private final BitcodeID bitcodeID;
    private final int symbolIndex;
    private final boolean exported;
    private final boolean externalWeak;

    // Index for non-parsed symbols, such as alias, and function symbol for inline assembly.
    public static final int INVALID_INDEX = -1;

    public LLVMSymbol(String name, BitcodeID bitcodeID, int symbolIndex, boolean exported, boolean externalWeak) {
        this.name = name;
        this.bitcodeID = bitcodeID;
        this.symbolIndex = symbolIndex;
        this.exported = exported;
        this.externalWeak = externalWeak;
    }

    public final String getName() {
        return name;
    }

    public final String getKind() {
        CompilerAsserts.neverPartOfCompilation();
        return this.getClass().getSimpleName();
    }

    public final boolean isExported() {
        return exported;
    }

    public final boolean isExternalWeak() {
        return externalWeak;
    }

    /**
     * Get the unique index of the symbol. The index is assigned during parsing. Symbols that are
     * not created from parsing or that are alias have the value of -1.
     */
    public final int getSymbolIndexIllegalOk() {
        return symbolIndex;
    }

    /**
     * Get the unique index of the symbol. The index is assigned during parsing. Symbols that are
     * not created from parsing or that are alias throw an {@link LLVMIllegalSymbolIndexException}.
     *
     * @param exception a {@link BranchProfile} for the exception case
     */
    public final int getSymbolIndex(BranchProfile exception) throws LLVMIllegalSymbolIndexException {
        if (symbolIndex >= 0) {
            return symbolIndex;
        }
        exception.enter();
        throw new LLVMIllegalSymbolIndexException("invalid symbol index");
    }

    /**
     * Get the unique index of the symbol. The index is assigned during parsing. Symbols that are
     * not created from parsing or that are alias throw an {@link LLVMIllegalSymbolIndexException}.
     *
     * This function does not profile the exception case, and can not be used from compiled code.
     */
    public final int getSymbolIndexUncached() throws LLVMIllegalSymbolIndexException {
        CompilerAsserts.neverPartOfCompilation();
        return getSymbolIndex(BranchProfile.getUncached());
    }

    /**
     * Get the unique module ID for the symbol. The ID is assigned during parsing. The module ID is
     * unqiue per bitcode file. Symbols that are not created from parsing or that are alias have the
     * value of null.
     */
    public final BitcodeID getBitcodeIDIllegalOk() {
        return bitcodeID;
    }

    /**
     * Get the unique module ID for the symbol. The ID is assigned during parsing. The module ID is
     * unqiue per bitcode file. Symbols that are not created from parsing or that are alias throw an
     * {@link LLVMIllegalSymbolIndexException}.
     *
     * @param exception a {@link BranchProfile} for the exception case
     */
    public final BitcodeID getBitcodeID(BranchProfile exception) throws LLVMIllegalSymbolIndexException {
        if (bitcodeID != null) {
            return bitcodeID;
        }
        exception.enter();
        throw new LLVMIllegalSymbolIndexException("invalid bitcode ID");
    }

    /**
     * Get the unique module ID for the symbol. The ID is assigned during parsing. The module ID is
     * unqiue per bitcode file. Symbols that are not created from parsing or that are alias throw an
     * {@link LLVMIllegalSymbolIndexException}.
     *
     * This function does not profile the exception case, and can not be used from compiled code.
     */
    public final BitcodeID getBitcodeIDUncached() throws LLVMIllegalSymbolIndexException {
        CompilerAsserts.neverPartOfCompilation();
        return getBitcodeID(BranchProfile.getUncached());
    }

    public final boolean hasValidIndexAndID() {
        return symbolIndex >= 0 && bitcodeID != null;
    }

    public abstract boolean isGlobalVariable();

    public abstract boolean isFunction();

    public abstract boolean isAlias();

    public abstract boolean isElemPtrExpression();

    public abstract LLVMFunction asFunction();

    public abstract LLVMGlobal asGlobalVariable();

    public abstract LLVMElemPtrSymbol asElemPtrExpression();
}

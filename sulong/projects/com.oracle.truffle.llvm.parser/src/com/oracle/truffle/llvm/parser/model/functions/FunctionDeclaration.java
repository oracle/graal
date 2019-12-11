/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model.functions;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

public final class FunctionDeclaration extends FunctionSymbol implements Constant {

    public FunctionDeclaration(FunctionType type, String name, Linkage linkage, AttributesCodeEntry paramAttr, int index) {
        super(type, name, linkage, paramAttr, index);
    }

    public FunctionDeclaration(FunctionType type, Linkage linkage, AttributesCodeEntry paramAttr, int index) {
        this(type, LLVMIdentifier.UNKNOWN, linkage, paramAttr, index);
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("%s %s ;", getType(), getName());
    }

    @Override
    public boolean isExported() {
        return true;
    }

    @Override
    public boolean isOverridable() {
        return true;
    }

    @Override
    public boolean isExternal() {
        return true;
    }
}

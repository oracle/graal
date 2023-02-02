/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model.symbols.globals;

import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.Visibility;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;

public final class GlobalVariable extends GlobalValueSymbol {

    private final boolean isReadOnly;

    private final int align;

    private final String sectionName;

    private final boolean isThreadLocal;

    private GlobalVariable(boolean isReadOnly, PointerType type, int align, String sectionName, Linkage linkage, Visibility visibility, boolean threadLocal, SymbolTable symbolTable, int value,
                    int index) {
        super(type, linkage, visibility, symbolTable, value, index);
        this.isReadOnly = isReadOnly;
        this.align = align;
        this.isThreadLocal = threadLocal;
        this.sectionName = sectionName;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public int getAlign() {
        return align;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public String getSectionName() {
        return sectionName;
    }

    public static GlobalVariable create(boolean isReadOnly, PointerType type, int align, String sectionName, long linkage, long visibility, long threadLocal, SymbolTable symbolTable, int value,
                    int index) {
        return new GlobalVariable(isReadOnly, type, align, sectionName, Linkage.decode(linkage), Visibility.decode(visibility), threadLocal > 0, symbolTable, value, index);
    }

    public boolean isThreadLocal() {
        return isThreadLocal;
    }

    @Override
    public LLVMExpressionNode createNode(LLVMParserRuntime runtime, DataLayout dataLayout, GetStackSpaceFactory stackFactory) {
        LLVMSymbol symbol = runtime.lookupSymbol(getName());
        if (symbol.isGlobalVariable()) {
            symbol = symbol.asGlobalVariable();
        } else if (symbol.isThreadLocalSymbol()) {
            symbol = symbol.asThreadLocalSymbol();
        } else {
            throw new AssertionError(symbol.getClass());
        }
        return CommonNodeFactory.createLiteral(symbol, new PointerType(getType()));
    }
}

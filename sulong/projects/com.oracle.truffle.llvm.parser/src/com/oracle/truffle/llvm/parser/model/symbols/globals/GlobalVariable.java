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
package com.oracle.truffle.llvm.parser.model.symbols.globals;

import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.Visibility;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.types.PointerType;

public final class GlobalVariable extends GlobalValueSymbol {

    private final boolean isReadOnly;

    private final int align;

    private GlobalVariable(boolean isReadOnly, PointerType type, int align, Linkage linkage, Visibility visibility, SymbolTable symbolTable, int value, int index) {
        super(type, linkage, visibility, symbolTable, value, index);
        this.isReadOnly = isReadOnly;
        this.align = align;
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

    public static GlobalVariable create(boolean isReadOnly, PointerType type, int align, long linkage, long visibility, SymbolTable symbolTable, int value, int index) {
        return new GlobalVariable(isReadOnly, type, align, Linkage.decode(linkage), Visibility.decode(visibility), symbolTable, value, index);
    }
}

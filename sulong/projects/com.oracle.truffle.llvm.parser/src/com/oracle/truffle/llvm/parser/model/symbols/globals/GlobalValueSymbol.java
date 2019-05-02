/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.ValueSymbol;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.Visibility;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

public abstract class GlobalValueSymbol implements ValueSymbol, MetadataAttachmentHolder {

    private final PointerType type;

    private final int align;

    private String name = LLVMIdentifier.UNKNOWN;

    private SymbolImpl value = null;

    private final Linkage linkage;

    private final Visibility visibility;

    private List<MDAttachment> mdAttachments = null;

    private LLVMSourceSymbol sourceSymbol;

    GlobalValueSymbol(PointerType type, int align, Linkage linkage, Visibility visibility, SymbolTable symbolTable, int value) {
        this.type = type;
        this.align = align;
        this.linkage = linkage;
        this.visibility = visibility;
        this.value = value > 0 ? symbolTable.getForwardReferenced(value - 1, this) : null;
        this.sourceSymbol = null;
    }

    public abstract void accept(ModelVisitor visitor);

    @Override
    public int getAlign() {
        return align;
    }

    public boolean isInitialized() {
        return value != null;
    }

    public int getInitialiser() {
        return isInitialized() ? 1 : 0;
    }

    public Linkage getLinkage() {
        return linkage;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PointerType getType() {
        return type;
    }

    public SymbolImpl getValue() {
        return value;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public LLVMSourceSymbol getSourceSymbol() {
        return sourceSymbol;
    }

    public void setSourceSymbol(LLVMSourceSymbol sourceSymbol) {
        this.sourceSymbol = sourceSymbol;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean hasAttachedMetadata() {
        return mdAttachments != null;
    }

    @Override
    public List<MDAttachment> getAttachedMetadata() {
        if (mdAttachments == null) {
            mdAttachments = new ArrayList<>(1);
        }
        return mdAttachments;
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
        if (value == oldValue) {
            value = newValue;
        }
    }

    public boolean isExported() {
        return Linkage.isExported(linkage, visibility);
    }

    public boolean isOverridable() {
        return Linkage.isOverridable(linkage, visibility);
    }

    public boolean isExternal() {
        return getInitialiser() == 0 && isExported();
    }
}

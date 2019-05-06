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
package com.oracle.truffle.llvm.parser.model.functions;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.parser.model.ValueSymbol;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public abstract class FunctionSymbol implements ValueSymbol {

    private final FunctionType type;
    private String name;
    private final AttributesCodeEntry paramAttr;
    private final Linkage linkage;

    public FunctionSymbol(FunctionType type, String name, Linkage linkage, AttributesCodeEntry paramAttr) {
        this.type = type;
        this.name = name;
        this.paramAttr = paramAttr;
        this.linkage = linkage;
    }

    @Override
    public final FunctionType getType() {
        return type;
    }

    public final AttributesGroup getFunctionAttributesGroup() {
        CompilerAsserts.neverPartOfCompilation();
        return paramAttr.getFunctionAttributesGroup();
    }

    public final AttributesGroup getReturnAttributesGroup() {
        CompilerAsserts.neverPartOfCompilation();
        return paramAttr.getReturnAttributesGroup();
    }

    public final AttributesGroup getParameterAttributesGroup(int idx) {
        CompilerAsserts.neverPartOfCompilation();
        return paramAttr.getParameterAttributesGroup(idx);
    }

    public final Linkage getLinkage() {
        return linkage;
    }

    @Override
    public final String getName() {
        assert name != null;
        return name;
    }

    @Override
    public final void setName(String name) {
        this.name = name;
    }

    public abstract boolean isExported();

    public abstract boolean isOverridable();

    public abstract boolean isExternal();
}

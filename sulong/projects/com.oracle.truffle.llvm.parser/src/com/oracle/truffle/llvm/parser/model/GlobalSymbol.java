/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model;

import com.oracle.truffle.llvm.parser.model.enums.Linkage;

public abstract class GlobalSymbol implements ValueSymbol {

    public static final String CONSTRUCTORS_VARNAME = "llvm.global_ctors";
    public static final String DESTRUCTORS_VARNAME = "llvm.global_dtors";

    private String name;
    private final Linkage linkage;
    private final int index;
    private boolean isSpecialInternalSymbol;

    // Index for alias symbol from bitcode.
    public static final int ALIAS_INDEX = -1;

    public GlobalSymbol(String name, Linkage linkage, int index) {
        this.name = name;
        this.linkage = linkage;
        this.index = index;
    }

    public final Linkage getLinkage() {
        return linkage;
    }

    @Override
    public final String getName() {
        assert name != null;
        return name;
    }

    /**
     * Returns true if the symbol is an
     * <a href="https://llvm.org/docs/LangRef.html#intrinsic-global-variables">Intrinsic Global
     * Variables</a>.
     * 
     * @see #setName
     */
    public final boolean isIntrinsicGlobalVariable() {
        return isSpecialInternalSymbol;
    }

    @Override
    public final void setName(String name) {
        this.isSpecialInternalSymbol = CONSTRUCTORS_VARNAME.equals(name) || DESTRUCTORS_VARNAME.equals(name);
        this.name = name;
    }

    /**
     * Get the unique index of the symbol. Symbols that are alias have the value of
     * {@link GlobalSymbol#ALIAS_INDEX}, and should not be retrieved.
     *
     * @return Index of the global symbol.
     */
    public final int getIndex() {
        assert index != ALIAS_INDEX;
        return index;
    }

    public abstract boolean isExported();

    public abstract boolean isOverridable();

    public abstract boolean isExternal();

}

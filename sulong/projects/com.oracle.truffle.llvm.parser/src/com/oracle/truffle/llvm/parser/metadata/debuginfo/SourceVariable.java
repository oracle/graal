/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class SourceVariable implements SymbolImpl {

    private final LLVMSourceSymbol symbol;
    private List<ValueFragment> fragments;
    private boolean hasFullDefinition;

    SourceVariable(LLVMSourceSymbol symbol) {
        this.symbol = symbol;
        this.fragments = null;
        this.hasFullDefinition = false;
    }

    public LLVMSourceSymbol getSymbol() {
        return symbol;
    }

    public String getName() {
        return symbol.getName();
    }

    public LLVMSourceType getSourceType() {
        return symbol.getType();
    }

    public boolean hasFragments() {
        return fragments != null;
    }

    public int getFragmentIndex(int offset, int length) {
        if (fragments != null) {
            for (int i = 0; i < fragments.size(); i++) {
                final ValueFragment fragment = fragments.get(i);
                if (fragment.getOffset() == offset && fragment.getLength() == length) {
                    return i;
                }
            }
        }
        return -1;
    }

    public List<ValueFragment> getFragments() {
        return fragments != null ? fragments : Collections.emptyList();
    }

    void addFullDefinition() {
        hasFullDefinition = true;
    }

    void addFragment(ValueFragment fragment) {
        if (fragments == null) {
            fragments = new ArrayList<>();
        }

        if (!fragments.contains(fragment)) {
            fragments.add(fragment);
        }
    }

    void processFragments() {
        if (fragments != null) {
            if (hasFullDefinition) {
                addFragment(ValueFragment.create(0, (int) symbol.getType().getSize()));
            }
            Collections.sort(fragments);
        }
    }

    @Override
    public Type getType() {
        return MetaType.DEBUG;
    }

    @Override
    public String toString() {
        return symbol.getName();
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }
}

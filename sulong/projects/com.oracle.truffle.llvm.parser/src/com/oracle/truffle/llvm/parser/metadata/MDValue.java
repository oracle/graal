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
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;

public final class MDValue implements MDBaseNode {

    private SymbolImpl value;

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private MDValue() {
    }

    public SymbolImpl getValue() {
        return value;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
    }

    @Override
    public String toString() {
        return String.format("Value (%s)", value);
    }

    public static MDBaseNode create(long arg, IRScope scope) {
        final MDValue value = new MDValue();
        scope.getSymbols().onParse((int) arg, s -> value.value = s);
        return value;
    }

    public static MDValue create(SymbolImpl symbol) {
        final MDValue value = new MDValue();
        value.value = symbol;
        return value;
    }

    public static SymbolImpl getIfInstance(MDBaseNode node) {
        if (node instanceof MDValue) {
            return ((MDValue) node).getValue();
        } else {
            return null;
        }
    }
}

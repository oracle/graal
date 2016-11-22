/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.base.model.symbols.instructions;

import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.base.model.symbols.ValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.visitors.InstructionVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GetElementPointerInstruction extends ValueInstruction {

    private Symbol base;

    private final List<Symbol> indices;

    private final boolean isInbounds;

    private String referenceName = null;

    private GetElementPointerInstruction(Type type, boolean isInbounds) {
        super(type);
        this.indices = new ArrayList<>();
        this.isInbounds = isInbounds;
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int getAlign() {
        if (base instanceof ValueSymbol) {
            return ((ValueSymbol) base).getAlign();
        } else if (base instanceof GetElementPointerConstant) {
            return ((ValueSymbol) ((GetElementPointerConstant) base).getBasePointer()).getAlign();
        } else {
            throw new IllegalStateException("Unknown Source of Alignment: " + base.getClass());
        }
    }

    public Symbol getBasePointer() {
        return base;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public List<Symbol> getIndices() {
        return Collections.unmodifiableList(indices);
    }

    public boolean isInbounds() {
        return isInbounds;
    }

    @Override
    public void replace(Symbol original, Symbol replacement) {
        if (base == original) {
            base = replacement;
        }
        for (int i = 0; i < indices.size(); i++) {
            if (indices.get(i) == original) {
                indices.set(i, replacement);
            }
        }
    }

    public static GetElementPointerInstruction fromSymbols(Symbols symbols, Type type, int pointer, int[] indices, boolean isInbounds) {
        final GetElementPointerInstruction inst = new GetElementPointerInstruction(type, isInbounds);
        inst.base = symbols.getSymbol(pointer, inst);
        for (int index : indices) {
            inst.indices.add(symbols.getSymbol(index, inst));
        }
        return inst;
    }
}

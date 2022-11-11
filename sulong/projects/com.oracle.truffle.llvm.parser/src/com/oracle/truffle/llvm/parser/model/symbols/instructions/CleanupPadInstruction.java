/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model.symbols.instructions;

import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class CleanupPadInstruction extends ValueInstruction {

    private SymbolImpl value;
    private SymbolImpl[] clauseSymbols;
    private final Type[] argTypes;

    private CleanupPadInstruction(Type[] argTypes) {
        super(PrimitiveType.I64);
        this.argTypes = argTypes;
    }

    public SymbolImpl[] getClauseSymbols() {
        return clauseSymbols;
    }

    public Type[] getArgTypes() {
        return argTypes;
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
        if (value == oldValue) {
            value = newValue;
        }
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public static CleanupPadInstruction generate(SymbolTable table, int index, Type[] argTypes, int[] argValues) {
        CleanupPadInstruction l = new CleanupPadInstruction(argTypes);
        SymbolImpl[] clauseSymbols = new SymbolImpl[argValues.length];
        for (int i = 0; i < argValues.length; i++) {
            clauseSymbols[i] = table.getForwardReferenced(argValues[i], l);
        }
        l.value = table.getForwardReferencedOrNull(index, l);
        l.clauseSymbols = clauseSymbols;
        return l;
    }
}

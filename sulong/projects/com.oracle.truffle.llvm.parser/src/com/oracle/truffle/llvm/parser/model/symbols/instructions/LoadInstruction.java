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
package com.oracle.truffle.llvm.parser.model.symbols.instructions;

import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.enums.AtomicOrdering;
import com.oracle.truffle.llvm.parser.model.enums.SynchronizationScope;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;

public final class LoadInstruction extends ValueInstruction {

    private final AtomicOrdering atomicOrdering;
    private final boolean isVolatile;
    private final SynchronizationScope synchronizationScope;
    private SymbolImpl source;

    private LoadInstruction(Type type, boolean isVolatile, AtomicOrdering ordering, SynchronizationScope scope) {
        super(type);
        this.isVolatile = isVolatile;
        this.atomicOrdering = ordering;
        this.synchronizationScope = scope;
    }

    private static LoadInstruction fromSymbols(SymbolTable symbols, Type type, int source, boolean isVolatile, AtomicOrdering atomicOrdering, SynchronizationScope synchronizationScope) {
        final LoadInstruction inst = new LoadInstruction(type, isVolatile, atomicOrdering, synchronizationScope);
        inst.source = symbols.getForwardReferenced(source, inst);
        return inst;
    }

    public static LoadInstruction fromSymbols(SymbolTable symbols, Type type, int source, boolean isVolatile) {
        return fromSymbols(symbols, type, source, isVolatile, AtomicOrdering.NOT_ATOMIC, SynchronizationScope.CROSS_THREAD);
    }

    public static LoadInstruction fromSymbols(SymbolTable symbols, Type type, int source, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        return fromSymbols(symbols, type, source, isVolatile, AtomicOrdering.decode(atomicOrdering), SynchronizationScope.decode(synchronizationScope));
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public AtomicOrdering getAtomicOrdering() {
        return atomicOrdering;
    }

    public SymbolImpl getSource() {
        return source;
    }

    public SynchronizationScope getSynchronizationScope() {
        return synchronizationScope;
    }

    public boolean isVolatile() {
        return isVolatile;
    }

    @Override
    public void replace(SymbolImpl original, SymbolImpl replacement) {
        if (source == original) {
            source = replacement;
        }
    }
}

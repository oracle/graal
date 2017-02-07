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
package com.oracle.truffle.llvm.parser.model.symbols.instructions;

import com.oracle.truffle.llvm.parser.model.enums.AtomicOrdering;
import com.oracle.truffle.llvm.parser.model.enums.SynchronizationScope;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class LoadInstruction extends ValueInstruction {

    private final int align;
    private final AtomicOrdering atomicOrdering;
    private final boolean isVolatile;
    private final SynchronizationScope synchronizationScope;
    private Symbol source;

    private LoadInstruction(Type type, int align, boolean isVolatile, AtomicOrdering ordering, SynchronizationScope scope) {
        super(type);
        this.align = align;
        this.isVolatile = isVolatile;
        this.atomicOrdering = ordering;
        this.synchronizationScope = scope;
    }

    private static LoadInstruction fromSymbols(Symbols symbols, Type type, int source, int align, boolean isVolatile, AtomicOrdering atomicOrdering, SynchronizationScope synchronizationScope) {
        final LoadInstruction inst = new LoadInstruction(type, align, isVolatile, atomicOrdering, synchronizationScope);
        inst.source = symbols.getSymbol(source, inst);
        return inst;
    }

    public static LoadInstruction fromSymbols(Symbols symbols, Type type, int source, int align, boolean isVolatile) {
        return fromSymbols(symbols, type, source, align, isVolatile, AtomicOrdering.NOT_ATOMIC, SynchronizationScope.CROSS_THREAD);
    }

    public static LoadInstruction fromSymbols(Symbols symbols, Type type, int source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        return fromSymbols(symbols, type, source, align, isVolatile, AtomicOrdering.decode(atomicOrdering), SynchronizationScope.decode(synchronizationScope));
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int getAlign() {
        return align;
    }

    public AtomicOrdering getAtomicOrdering() {
        return atomicOrdering;
    }

    public Symbol getSource() {
        return source;
    }

    public SynchronizationScope getSynchronizationScope() {
        return synchronizationScope;
    }

    public boolean isVolatile() {
        return isVolatile;
    }

    @Override
    public void replace(Symbol original, Symbol replacement) {
        if (source == original) {
            source = replacement;
        }
    }
}

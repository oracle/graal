/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.parser.model.enums.ReadModifyWriteOperator;
import com.oracle.truffle.llvm.parser.model.enums.SynchronizationScope;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;

public final class ReadModifyWriteInstruction extends ValueInstruction {

    private final ReadModifyWriteOperator operator;

    private final AtomicOrdering atomicOrdering;
    private final boolean isVolatile;
    private final SynchronizationScope synchronizationScope;

    private SymbolImpl ptr;
    private SymbolImpl value;

    private ReadModifyWriteInstruction(Type type, ReadModifyWriteOperator operator, boolean isVolatile, AtomicOrdering atomicOrdering, SynchronizationScope synchronizationScope) {
        super(type);
        this.operator = operator;
        this.atomicOrdering = atomicOrdering;
        this.isVolatile = isVolatile;
        this.synchronizationScope = synchronizationScope;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public SymbolImpl getPtr() {
        return ptr;
    }

    public SymbolImpl getValue() {
        return value;
    }

    public ReadModifyWriteOperator getOperator() {
        return operator;
    }

    public AtomicOrdering getAtomicOrdering() {
        return atomicOrdering;
    }

    public boolean isVolatile() {
        return isVolatile;
    }

    public SynchronizationScope getSynchronizationScope() {
        return synchronizationScope;
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
        if (ptr == oldValue) {
            ptr = newValue;
        }
        if (value == oldValue) {
            value = newValue;
        }
    }

    public static ReadModifyWriteInstruction fromSymbols(SymbolTable symbols, Type type, int ptr, int value, int opcode, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        final ReadModifyWriteOperator operator = ReadModifyWriteOperator.decode(opcode);
        final ReadModifyWriteInstruction inst = new ReadModifyWriteInstruction(type, operator, isVolatile, AtomicOrdering.decode(atomicOrdering), SynchronizationScope.decode(synchronizationScope));
        inst.ptr = symbols.getForwardReferenced(ptr, inst);
        inst.value = symbols.getForwardReferenced(value, inst);
        return inst;
    }
}

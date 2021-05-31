/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.types.Type;

import java.util.Collection;
import java.util.Collections;

public final class ExtractValueInstruction extends ValueInstruction {

    private SymbolImpl aggregate;
    private final Collection<Long> indices;

    private ExtractValueInstruction(Type type, Collection<Long> indices) {
        super(type);
        this.indices = indices;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public SymbolImpl getAggregate() {
        return aggregate;
    }

    public Collection<Long> getIndices() {
        return indices;
    }

    @Override
    public void replace(SymbolImpl original, SymbolImpl replacement) {
        if (aggregate == original) {
            this.aggregate = replacement;
        }
    }

    public static ExtractValueInstruction create(SymbolImpl aggreggate, Type type, int index) {
        final ExtractValueInstruction inst = new ExtractValueInstruction(type, Collections.singletonList((long) index));
        inst.aggregate = aggreggate;
        return inst;
    }

    /**
     * @param indices the indices list in the reverse order (as expected by
     *            CommonNodeFactory.getTargetAddress called from
     *            LLVMBitcodeInstructionVisitor.visit(ExtractValueInstruction)).
     */
    public static ExtractValueInstruction fromSymbols(SymbolTable symbols, Type type, int aggregate, Collection<Long> indices) {
        final ExtractValueInstruction inst = new ExtractValueInstruction(type, indices);
        inst.aggregate = symbols.getForwardReferenced(aggregate, inst);
        return inst;
    }
}

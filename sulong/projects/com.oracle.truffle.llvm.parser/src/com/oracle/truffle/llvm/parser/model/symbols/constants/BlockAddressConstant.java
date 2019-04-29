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
package com.oracle.truffle.llvm.parser.model.symbols.constants;

import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class BlockAddressConstant extends AbstractConstant {

    private FunctionDefinition function;

    private final int block;

    private BlockAddressConstant(Type type, int block) {
        super(type);
        this.block = block;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public int getBlockIndex() {
        return block;
    }

    @Override
    public void replace(SymbolImpl original, SymbolImpl replacement) {
        if (function == original) {
            if (replacement instanceof FunctionDefinition) {
                function = (FunctionDefinition) replacement;
            } else {
                throw new LLVMParserException("Illegal symbol for function in BlockAddressConstant: " + replacement);
            }
        }
    }

    public static BlockAddressConstant fromSymbols(SymbolTable symbols, Type type, int function, int block) {
        final BlockAddressConstant constant = new BlockAddressConstant(type, block);
        final SymbolImpl functionSymbol = symbols.getForwardReferenced(function, constant);
        if (functionSymbol instanceof FunctionDefinition) {
            constant.function = (FunctionDefinition) functionSymbol;
        } else {
            throw new LLVMParserException("Illegal symbol for function in BlockAddressConstant: " + functionSymbol);
        }
        return constant;
    }
}

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
package com.oracle.truffle.llvm.parser.model;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.ValueList;
import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class IRScope {

    private static final int GLOBAL_SCOPE_START = -1;

    private final SymbolTable symbols;
    private final List<Type> valueTypes;
    private final List<Instruction> instructions;
    private final MetadataValueList metadata;

    private FunctionDefinition currentFunction;
    private int valueTypesScopeStart;

    public IRScope() {
        symbols = new SymbolTable();
        valueTypes = new ArrayList<>();
        instructions = new ArrayList<>();
        metadata = new MetadataValueList();
        currentFunction = null;
        valueTypesScopeStart = GLOBAL_SCOPE_START;
    }

    public void addSymbol(SymbolImpl symbol, Type type) {
        symbols.add(symbol);
        valueTypes.add(type);
    }

    public boolean isValueForwardRef(long index) {
        return index >= valueTypes.size();
    }

    public int getNextValueIndex() {
        return valueTypes.size();
    }

    public Type getValueType(int i) {
        if (i < valueTypes.size()) {
            return valueTypes.get(i);
        } else {
            return null;
        }
    }

    public void addInstruction(Instruction ins) {
        instructions.add(ins);
    }

    public void nameSymbol(int index, String argName) {
        symbols.nameSymbol(index, argName);
    }

    public SymbolTable getSymbols() {
        return symbols;
    }

    public void nameBlock(int index, String name) {
        if (currentFunction != null) {
            currentFunction.nameBlock(index, name);
        }
    }

    public void startLocalScope(FunctionDefinition function) {
        this.currentFunction = function;
        metadata.startScope();
        symbols.startScope();
        valueTypesScopeStart = valueTypes.size();
        instructions.clear();
    }

    public void exitLocalScope() {
        metadata.endScope();
        symbols.endScope();

        ValueList.dropLocalScope(valueTypesScopeStart, valueTypes);
        valueTypesScopeStart = GLOBAL_SCOPE_START;
        instructions.clear();
    }

    public MetadataValueList getMetadata() {
        return metadata;
    }

    public void attachInstructionMetadata(int index, MDAttachment attachment) {
        if (index < instructions.size()) {
            instructions.get(index).attachMetadata(attachment);
        }
    }

    public void attachGlobalMetadata(int index, MDAttachment attachment) {
        symbols.attachMetadata(index, attachment);
    }

    public void attachFunctionMetadata(MDAttachment attachment) {
        if (currentFunction != null) {
            currentFunction.attachMetadata(attachment);
        }
    }
}

/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

public final class SourceFunction {

    private final Map<Instruction, LLVMSourceLocation> instructions = new HashMap<>();

    private Map<LLVMSourceSymbol, SourceVariable> locals;

    private final LLVMSourceLocation lexicalScope;

    private final LLVMSourceFunctionType sourceType;

    SourceFunction(LLVMSourceLocation lexicalScope, LLVMSourceFunctionType sourceType) {
        this.lexicalScope = lexicalScope;
        this.sourceType = sourceType;
    }

    public SourceSection getSourceSection() {
        return lexicalScope.getSourceSection();
    }

    public LLVMSourceLocation getSourceLocation(Instruction instruction) {
        return instructions.get(instruction);
    }

    public LLVMSourceLocation getLexicalScope() {
        return lexicalScope;
    }

    public LLVMSourceFunctionType getSourceType() {
        return sourceType;
    }

    SourceVariable getLocal(LLVMSourceSymbol symbol) {
        if (locals == null) {
            locals = new HashMap<>();
        } else if (locals.containsKey(symbol)) {
            return locals.get(symbol);
        }

        final SourceVariable variable = new SourceVariable(symbol);
        locals.put(symbol, variable);
        return variable;
    }

    void addInstruction(Instruction instruction, LLVMSourceLocation scope) {
        instructions.put(instruction, scope);
    }

    public Collection<SourceVariable> getVariables() {
        return locals == null ? Collections.emptySet() : locals.values();
    }
}

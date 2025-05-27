/*
 * Copyright (c) 2025, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;

public class DebugInstruction extends VoidInstruction {

    public enum DebugInstructionKind {
        VALUE,
        DECLARE
    }

    private final DebugInstructionKind kind;

    private final MDLocalVariable variable;
    private final MDExpression expression;
    private final MDBaseNode value;

    public DebugInstruction(DebugInstructionKind kind, MDLocation location, MDLocalVariable variable, MDExpression expression, MDBaseNode value) {
        super(location);
        this.kind = kind;
        this.variable = variable;
        this.expression = expression;
        this.value = value;
    }

    public DebugInstructionKind getKind() {
        return kind;
    }

    public MDLocalVariable getVariable() {
        return variable;
    }

    public MDExpression getExpression() {
        return expression;
    }

    public MDBaseNode getValue() {
        return value;
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DebugInstruction that = (DebugInstruction) o;

        if (!getKind().equals(that.getKind())) {
            return false;
        }
        if (!getVariable().equals(that.getVariable())) {
            return false;
        }
        if (!getExpression().equals(that.getExpression())) {
            return false;
        }
        if (!getValue().equals(that.getValue())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((kind == null) ? 0 : kind.hashCode());
        result = prime * result + ((variable == null) ? 0 : variable.hashCode());
        result = prime * result + ((expression == null) ? 0 : expression.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }
}

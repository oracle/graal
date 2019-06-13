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

import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;

public final class VoidCallInstruction extends VoidInstruction implements Call {

    private SymbolImpl target;

    private final SymbolImpl[] arguments;

    private final AttributesCodeEntry paramAttr;

    private VoidCallInstruction(AttributesCodeEntry paramAtt, int argCount) {
        this.arguments = argCount == 0 ? NO_ARGS : new SymbolImpl[argCount];
        this.paramAttr = paramAtt;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public SymbolImpl[] getArguments() {
        return arguments;
    }

    @Override
    public SymbolImpl getCallTarget() {
        return target;
    }

    @Override
    public AttributesGroup getFunctionAttributesGroup() {
        return paramAttr.getFunctionAttributesGroup();
    }

    @Override
    public AttributesGroup getReturnAttributesGroup() {
        return paramAttr.getReturnAttributesGroup();
    }

    @Override
    public AttributesGroup getParameterAttributesGroup(int idx) {
        return paramAttr.getParameterAttributesGroup(idx);
    }

    @Override
    public void replace(SymbolImpl original, SymbolImpl replacement) {
        if (target == original) {
            target = replacement;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] == original) {
                arguments[i] = replacement;
            }
        }
    }

    public static VoidCallInstruction fromSymbols(IRScope scope, int targetIndex, int[] arguments, AttributesCodeEntry paramAttr) {
        final VoidCallInstruction inst = new VoidCallInstruction(paramAttr, arguments.length);
        inst.target = scope.getSymbols().getForwardReferenced(targetIndex, inst);
        Call.parseArguments(scope, inst.target, inst, inst.arguments, arguments);
        return inst;
    }

    @Override
    public String toString() {
        return Call.asString(target, arguments);
    }
}

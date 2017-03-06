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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.Visibility;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class VoidCallInstruction extends Instruction implements Call {

    private final Linkage linkage;

    private final Visibility visibility;

    private Symbol target;

    private final List<Symbol> arguments;

    private VoidCallInstruction(Linkage linkage, Visibility visibility) {
        arguments = new ArrayList<>();
        this.linkage = linkage;
        this.visibility = visibility;
    }

    @Override
    public Type getType() {
        return MetaType.VOID;
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public Symbol getArgument(int index) {
        return arguments.get(index);
    }

    @Override
    public int getArgumentCount() {
        return arguments.size();
    }

    @Override
    public Symbol getCallTarget() {
        return target;
    }

    @Override
    public Linkage getLinkage() {
        return linkage;
    }

    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void replace(Symbol original, Symbol replacement) {
        if (target == original) {
            target = replacement;
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i) == original) {
                arguments.set(i, replacement);
            }
        }
    }

    public static VoidCallInstruction fromSymbols(Symbols symbols, int targetIndex, int[] arguments, long visibility, long linkage) {
        final VoidCallInstruction inst = new VoidCallInstruction(Linkage.decode(linkage), Visibility.decode(visibility));
        inst.target = symbols.getSymbol(targetIndex, inst);
        if (inst.target instanceof FunctionType) {
            Type[] types = ((FunctionType) (inst.target)).getArgumentTypes();
            for (int i = 0; i < arguments.length; i++) {
                // TODO: why it's possible to have more arguments than argument types?
                if (types.length > i && types[i] instanceof MetaType) {
                    inst.arguments.add(new MetadataConstant(arguments[i]));
                } else {
                    inst.arguments.add(symbols.getSymbol(arguments[i], inst));
                }
            }
        } else {
            for (final int argument : arguments) {
                inst.arguments.add(symbols.getSymbol(argument, inst));
            }
        }
        return inst;
    }
}

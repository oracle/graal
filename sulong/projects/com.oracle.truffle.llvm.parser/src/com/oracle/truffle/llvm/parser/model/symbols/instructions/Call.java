/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.metadata.MetadataSymbol;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;

public interface Call extends SymbolImpl {

    SymbolImpl[] NO_ARGS = new SymbolImpl[0];

    static void parseArguments(IRScope scope, SymbolImpl callTarget, Instruction inst, SymbolImpl[] target, int[] src) {
        if (src.length == 0) {
            return;
        }

        final int numParams;
        final FunctionType type;
        if (callTarget instanceof FunctionDefinition) {
            type = ((FunctionDefinition) (callTarget)).getType();
            numParams = type.getNumberOfArguments();
        } else if (callTarget instanceof FunctionDeclaration) {
            type = ((FunctionDeclaration) (callTarget)).getType();
            numParams = type.getNumberOfArguments();
        } else {
            type = null;
            numParams = 0;
        }

        final SymbolTable symbols = scope.getSymbols();
        for (int i = Math.min(numParams, src.length) - 1; i >= 0; i--) {
            if (type.getArgumentType(i) == MetaType.METADATA) {
                target[i] = MetadataSymbol.create(scope.getMetadata(), src[i]);
            } else {
                target[i] = symbols.getForwardReferenced(src[i], inst);
            }
        }

        // parse varargs
        for (int i = numParams; i < src.length; i++) {
            target[i] = symbols.getForwardReferenced(src[i], inst);
        }
    }

    static String asString(SymbolImpl target, SymbolImpl[] arguments) {
        StringBuilder sb = new StringBuilder();
        if (target instanceof FunctionDeclaration) {
            sb.append(((FunctionDeclaration) target).getName());
        } else if (target instanceof FunctionDefinition) {
            sb.append(((FunctionDefinition) target).getName());
        } else {
            sb.append(target);
        }
        sb.append('(');
        for (int i = 0; i < arguments.length; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(arguments[i]);
        }
        sb.append(')');
        return sb.toString();
    }

    SymbolImpl[] getArguments();

    default SymbolImpl getArgument(int index) {
        return getArguments()[index];
    }

    default int getArgumentCount() {
        return getArguments().length;
    }

    SymbolImpl getCallTarget();

    AttributesGroup getFunctionAttributesGroup();

    AttributesGroup getReturnAttributesGroup();

    AttributesGroup getParameterAttributesGroup(int idx);
}

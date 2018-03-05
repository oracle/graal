/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.util;

import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.ValueSymbol;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SymbolNameMangling {

    public static void demangleGlobals(ModelModule model) {
        final Function<String, String> demangler = getDemangler(model.getTargetDataLayout());
        model.accept(new DemangleVisitor(demangler));
    }

    private static final Function<String, String> DEFAULT_DEMANGLER = name -> {
        if (name.startsWith("\u0001")) {
            return name.substring(1);
        } else {
            return name;
        }
    };

    private static final Function<String, String> DEMANGLE_ELF = name -> {
        if (name.startsWith(".L")) {
            return name.substring(2);
        } else {
            return name;
        }
    };

    private static final Function<String, String> DEMANGLE_MIPS = name -> {
        if (name.startsWith("$")) {
            return name.substring(1);
        } else {
            return name;
        }
    };

    private static final Function<String, String> DEMANGLE_MACHO = name -> {
        if (name.startsWith("L") || name.startsWith("_")) {
            return name.substring(1);
        } else {
            return name;
        }
    };

    private static final Function<String, String> REMANGLE = LLVMIdentifier::toGlobalIdentifier;

    private static final Pattern LAYOUT_MANGLING_PATTERN = Pattern.compile(".*m:(?<mangling>[\\w]).*");

    private static Function<String, String> getDemangler(TargetDataLayout targetDataLayout) {
        Function<String, String> demangler = DEFAULT_DEMANGLER;

        final Matcher matcher = LAYOUT_MANGLING_PATTERN.matcher(targetDataLayout.getDataLayout());
        if (matcher.matches()) {

            final String mangling = matcher.group("mangling");
            switch (mangling) {
                case "e":
                    demangler = demangler.andThen(DEMANGLE_ELF);
                    break;
                case "m":
                    demangler = demangler.andThen(DEMANGLE_MIPS);
                    break;
                case "o":
                    demangler = demangler.andThen(DEMANGLE_MACHO);
                    break;
                default:
                    throw new AssertionError("Unsupported mangling in TargetDataLayout: " + mangling);
            }
        }

        demangler = demangler.andThen(REMANGLE);
        return demangler;
    }

    private static final class DemangleVisitor implements ModelVisitor {

        private final Function<String, String> demangler;

        private DemangleVisitor(Function<String, String> demangler) {
            this.demangler = demangler;
        }

        private void demangle(ValueSymbol symbol) {
            final String mangledName = symbol.getName();
            final String demangledName = demangler.apply(mangledName);
            symbol.setName(demangledName);
        }

        @Override
        public void visit(GlobalAlias alias) {
            demangle(alias);
        }

        @Override
        public void visit(GlobalConstant constant) {
            demangle(constant);
        }

        @Override
        public void visit(GlobalVariable variable) {
            demangle(variable);
        }

        @Override
        public void visit(FunctionDeclaration function) {
            demangle(function);
        }

        @Override
        public void visit(FunctionDefinition function) {
            demangle(function);
        }
    }

}

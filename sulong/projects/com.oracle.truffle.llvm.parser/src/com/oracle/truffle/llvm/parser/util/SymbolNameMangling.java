/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.ValueSymbol;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

public final class SymbolNameMangling {

    public static void demangleGlobals(ModelModule model) {
        final BiFunction<Linkage, String, String> demangler = getDemangler(model.getTargetDataLayout());
        model.accept(new DemangleVisitor(demangler));
    }

    private static final BiFunction<Linkage, String, String> DEFAULT_DEMANGLER = (linkage, name) -> name;

    private static final BiFunction<Linkage, String, String> DEMANGLE_ELF = (linkage, name) -> {
        if (linkage == Linkage.PRIVATE) {
            if (name.startsWith(".L")) {
                return name.substring(2);
            }
        }

        return name;
    };

    private static final BiFunction<Linkage, String, String> DEMANGLE_MIPS = (linkage, name) -> {
        if (linkage == Linkage.PRIVATE) {
            if (name.startsWith("$")) {
                return name.substring(1);
            }
        }

        return name;
    };

    private static final BiFunction<Linkage, String, String> DEMANGLE_MACHO = (linkage, name) -> {
        String demangled = name;

        if (demangled.startsWith("_")) {
            demangled = demangled.substring(1);
        }

        if (linkage == Linkage.LINKER_PRIVATE || linkage == Linkage.LINKER_PRIVATE_WEAK) {
            if (demangled.startsWith("l")) {
                demangled = demangled.substring(1);
            }
        } else if (linkage == Linkage.PRIVATE) {
            if (demangled.startsWith("L")) {
                demangled = demangled.substring(1);
            }
        }

        return demangled;
    };

    private static final Pattern LAYOUT_MANGLING_PATTERN = Pattern.compile(".*m:(?<mangling>[\\w]).*");

    private static BiFunction<Linkage, String, String> getDemangler(TargetDataLayout targetDataLayout) {
        final Matcher matcher = LAYOUT_MANGLING_PATTERN.matcher(targetDataLayout.getDataLayout());
        if (matcher.matches()) {
            final String mangling = matcher.group("mangling");
            switch (mangling) {
                case "e":
                    return DEMANGLE_ELF;
                case "m":
                    return DEMANGLE_MIPS;
                case "o":
                    return DEMANGLE_MACHO;
                default:
                    throw new LLVMParserException("Unsupported mangling in TargetDataLayout: " + mangling);
            }
        } else {
            return DEFAULT_DEMANGLER;
        }
    }

    private static final String MANGLED_PREFIX = "\u0001";

    private static final class DemangleVisitor implements ModelVisitor {

        private final BiFunction<Linkage, String, String> demangler;

        private DemangleVisitor(BiFunction<Linkage, String, String> demangler) {
            this.demangler = demangler;
        }

        private void demangle(Linkage linkage, ValueSymbol symbol) {
            String name = symbol.getName();

            if (name.startsWith(MANGLED_PREFIX)) {
                name = demangler.apply(linkage, name.substring(MANGLED_PREFIX.length()));
            }

            name = LLVMIdentifier.toGlobalIdentifier(name);
            symbol.setName(name);
        }

        @Override
        public void visit(GlobalAlias alias) {
            demangle(alias.getLinkage(), alias);
        }

        @Override
        public void visit(GlobalVariable variable) {
            demangle(variable.getLinkage(), variable);
        }

        @Override
        public void visit(FunctionDeclaration function) {
            demangle(function.getLinkage(), function);
        }

        @Override
        public void visit(FunctionDefinition function) {
            demangle(function.getLinkage(), function);
        }
    }

}

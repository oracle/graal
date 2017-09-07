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
package com.oracle.truffle.llvm.parser.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.target.TargetInformation;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class ModelModule extends IRScope {

    // when running with Polyglot it can be that there is no layout available - we fall back to this
    // one.
    private static final TargetDataLayout defaultLayout = TargetDataLayout.fromString("e-m:e-i64:64-f80:128-n8:16:32:64-S128");

    private final List<Type> types = new ArrayList<>();
    private final List<GlobalValueSymbol> globals = new ArrayList<>();
    private final List<FunctionDeclaration> declares = new ArrayList<>();
    private final List<FunctionDefinition> defines = new ArrayList<>();
    private final List<TargetInformation> targetInfo = new ArrayList<>();
    private final List<String> libraries = new ArrayList<>();
    private final List<String> paths = new ArrayList<>();
    private int currentFunction = -1;
    private TargetDataLayout targetDataLayout = defaultLayout;

    public ModelModule() {
    }

    public void setTargetDataLayout(TargetDataLayout layout) {
        targetDataLayout = layout;
    }

    public TargetDataLayout getTargetDataLayout() {
        return targetDataLayout;
    }

    public void accept(ModelVisitor visitor) {
        visitor.visit(targetDataLayout);
        targetInfo.forEach(visitor::visit);
        types.forEach(visitor::visit);
        for (GlobalValueSymbol variable : globals) {
            variable.accept(visitor);
        }
        defines.forEach(visitor::visit);
        declares.forEach(visitor::visit);
    }

    public void addFunctionDeclaration(FunctionDeclaration declaration) {
        addSymbol(declaration, declaration.getType());
        declares.add(declaration);
    }

    public void addFunctionDefinition(FunctionDefinition definition) {
        addSymbol(definition, definition.getType());
        defines.add(definition);
    }

    public void addGlobalType(Type type) {
        types.add(type);
    }

    public void addGlobalSymbol(GlobalValueSymbol global) {
        addSymbol(global, global.getType());
        globals.add(global);
    }

    public void addTargetInformation(TargetInformation info) {
        targetInfo.add(info);
    }

    public void exitModule() {
        int globalIndex = 0;
        for (GlobalValueSymbol variable : globals) {
            if (variable.getName().equals(LLVMIdentifier.UNKNOWN)) {
                variable.setName(String.valueOf(globalIndex++));
            }
            variable.initialise(getSymbols());
        }
    }

    public FunctionDefinition generateFunction() {
        while (++currentFunction < getSymbols().getSize()) {
            final Symbol symbol = getSymbols().getSymbol(currentFunction);
            if (symbol instanceof FunctionDefinition) {
                final FunctionDefinition function = (FunctionDefinition) symbol;
                function.initialize(this);
                return function;
            }
        }
        throw new RuntimeException("Trying to generate undefined function");
    }

    @Override
    public void nameBlock(int index, String name) {
    }

    @Override
    public String toString() {
        return String.format("Model (%d defines, %d declares, %d globals, %d types)", defines.size(), declares.size(), globals.size(), types.size());
    }

    @Override
    public boolean hasAttachedMetadata() {
        return false;
    }

    @Override
    public List<MDAttachment> getAttachedMetadata() {
        return Collections.emptyList();
    }

    public void addLibraries(List<String> l) {
        this.libraries.addAll(l);
    }

    public List<String> getLibraries() {
        return Collections.unmodifiableList(libraries);
    }

    public void addLibraryPaths(List<String> p) {
        this.paths.addAll(p);
    }

    public List<String> getLibraryPaths() {
        return Collections.unmodifiableList(paths);
    }
}

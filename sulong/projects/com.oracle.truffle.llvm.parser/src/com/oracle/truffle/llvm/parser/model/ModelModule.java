/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.llvm.parser.metadata.debuginfo.DebugInfoFunctionProcessor;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.LazyFunctionParser;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetInformation;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceFileReference;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class ModelModule {

    // According to the LLVM documentation (https://llvm.org/docs/LangRef.html#data-layout), below
    // is the default datalayout
    public static final String defaultLayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:32:64-f16:16:16-f32:32:32-f64:64:64-f128:128:128";

    private final ArrayList<Type> types = new ArrayList<>();
    private final ArrayList<GlobalVariable> globalVariables = new ArrayList<>();
    private final ArrayList<GlobalAlias> aliases = new ArrayList<>();
    private final ArrayList<FunctionDeclaration> declares = new ArrayList<>();
    private final ArrayList<FunctionDefinition> defines = new ArrayList<>();
    private final ArrayList<TargetInformation> targetInfo = new ArrayList<>();
    private final ArrayList<String> sectionNames = new ArrayList<>();
    private final HashMap<LLVMSourceSymbol, SymbolImpl> sourceGlobals = new HashMap<>();
    private final HashMap<LLVMSourceStaticMemberType, SymbolImpl> sourceStaticMembers = new HashMap<>();
    private final HashMap<FunctionDefinition, LazyFunctionParser> lazyFunctionParsers = new HashMap<>();
    private String targetDataLayout = defaultLayout;
    private DebugInfoFunctionProcessor functionProcessor = null;
    private final ArrayList<LLVMSourceFileReference> sourceFiles = new ArrayList<>();
    private int totalSize;

    public ModelModule() {
    }

    public void setTargetDataLayout(String layout) {
        targetDataLayout = layout;
    }

    public String getTargetDataLayout() {
        return targetDataLayout;
    }

    public void addFunctionDeclaration(FunctionDeclaration declaration) {
        declares.add(declaration);
    }

    public List<FunctionDeclaration> getDeclaredFunctions() {
        return declares;
    }

    public void addFunctionDefinition(FunctionDefinition definition) {
        defines.add(definition);
    }

    public List<FunctionDefinition> getDefinedFunctions() {
        return defines;
    }

    public void addFunctionParser(FunctionDefinition definition, LazyFunctionParser parser) {
        lazyFunctionParsers.put(definition, parser);
    }

    public LazyFunctionParser getFunctionParser(FunctionDefinition functionDefinition) {
        return lazyFunctionParsers.get(functionDefinition);
    }

    public void addGlobalType(Type type) {
        types.add(type);
    }

    public void addGlobalVariable(GlobalVariable global) {
        globalVariables.add(global);
    }

    public void addAlias(GlobalAlias alias) {
        aliases.add(alias);
    }

    public void addTargetInformation(TargetInformation info) {
        targetInfo.add(info);
    }

    public void addSectionName(String sectionName) {
        sectionNames.add(sectionName);
    }

    public List<String> getSectionNames() {
        return sectionNames;
    }

    public List<GlobalVariable> getGlobalVariables() {
        return globalVariables;
    }

    public List<GlobalAlias> getAliases() {
        return aliases;
    }

    public Map<LLVMSourceSymbol, SymbolImpl> getSourceGlobals() {
        return sourceGlobals;
    }

    public Map<LLVMSourceStaticMemberType, SymbolImpl> getSourceStaticMembers() {
        return sourceStaticMembers;
    }

    public DebugInfoFunctionProcessor getFunctionProcessor() {
        return functionProcessor;
    }

    public void setFunctionProcessor(DebugInfoFunctionProcessor functionProcessor) {
        this.functionProcessor = functionProcessor;
    }

    @Override
    public String toString() {
        return String.format("Model (%d defines, %d declares, %d global variables, %d aliases, %d types)", defines.size(), declares.size(), globalVariables.size(), aliases.size(), types.size());
    }

    public void addSourceFileReference(LLVMSourceFileReference sourceFile) {
        sourceFiles.add(sourceFile);
    }

    public ArrayList<LLVMSourceFileReference> getSourceFileReferences() {
        return sourceFiles;
    }

    public <TI extends TargetInformation> TI getTargetInformation(Class<TI> targetInfoClazz) {
        for (TargetInformation info : targetInfo) {
            if (targetInfoClazz.isInstance(info)) {
                return targetInfoClazz.cast(info);
            }
        }
        return null;
    }

    public void setTotalSize(int index) {
        totalSize = index;
    }

    public int getTotalSize() {
        return totalSize;
    }
}

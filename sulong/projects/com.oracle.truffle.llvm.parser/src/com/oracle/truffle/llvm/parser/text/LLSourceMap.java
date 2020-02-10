/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

final class LLSourceMap {

    static final class Function {

        private final String name;
        private final ArrayDeque<Instruction> instructionList;
        private final int startLine;
        private int endLine;

        Function(String name, int startLine) {
            this.name = name;
            this.startLine = startLine;
            this.instructionList = new ArrayDeque<>();
        }

        void add(String var, int line) {
            instructionList.add(new Instruction(var, line));
        }

        void setEndLine(int line) {
            this.endLine = line;
        }

        ArrayDeque<Instruction> getInstructionList() {
            return instructionList;
        }

        String getName() {
            return name;
        }

        LLVMSourceLocation toSourceLocation(LLSourceMap sourceMap, LLVMParserRuntime runtime) {
            final Source llSource = sourceMap.getLLSource();
            final SourceSection startSection = llSource.createSection(startLine);
            final int startCharIndex = startSection.getCharIndex();
            final SourceSection endSection = llSource.createSection(endLine);
            final int charLength = endSection.getCharEndIndex() - startCharIndex;
            final SourceSection totalSection = llSource.createSection(startCharIndex, charLength);
            return LLVMSourceLocation.create(sourceMap.getGlobalScope(runtime.getFileScope()), LLVMSourceLocation.Kind.FUNCTION, name, new LLSourceSection(totalSection), null);
        }
    }

    static final class Instruction {

        private final String descriptor;
        private final int line;

        Instruction(String descriptor, int line) {
            this.descriptor = descriptor;
            this.line = line;
        }

        LLVMSourceLocation toSourceLocation(Source llSource, LLVMSourceLocation parent) {
            final SourceSection sourceSection = llSource.createSection(line);
            return LLVMSourceLocation.createLLInstruction(parent, sourceSection);
        }

        String getDescriptor() {
            return descriptor;
        }
    }

    private final Source llSource;
    private final Map<String, Function> functions;
    private final List<String> globals;
    private LLVMSourceLocation.TextModule globalScope;

    LLSourceMap(Source llSource) {
        this.llSource = llSource;
        this.functions = new HashMap<>();
        this.globals = new ArrayList<>();
        this.globalScope = null;
    }

    void registerFunction(String name, Function function) {
        this.functions.put(name, function);
    }

    void registerGlobal(String name) {
        globals.add(name);
    }

    private LLVMSourceLocation getGlobalScope(LLVMScope moduleScope) {
        if (globalScope == null) {
            globalScope = LLVMSourceLocation.createLLModule(llSource.getName(), llSource.createSection(0, llSource.getLength()));
        }
        if (!globals.isEmpty()) {
            for (String globalName : globals) {
                assert globalName.startsWith("@");
                final LLVMSymbol actualSymbol = moduleScope.get(globalName.substring(1));
                if (actualSymbol != null && actualSymbol.isGlobalVariable()) {
                    globalScope.addGlobal(actualSymbol.asGlobalVariable());
                } else {
                    LLVMGlobal global = LLVMGlobal.createUnavailable(globalName);
                    globalScope.addGlobal(global);
                }
            }
            globals.clear();
        }
        return globalScope;
    }

    Function getFunction(String name) {
        return functions.get(LLVMIdentifier.toGlobalIdentifier(name));
    }

    void clearFunction(Function function) {
        functions.remove(LLVMIdentifier.toGlobalIdentifier(function.getName()));
    }

    Source getLLSource() {
        return llSource;
    }
}

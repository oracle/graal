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
package com.oracle.truffle.llvm.parser.text;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

public final class LLSourceMap {

    static final class Function {

        private final String name;
        private final LinkedList<Instruction> instructionList;
        private final int startLine;
        private int endLine;

        Function(String name, int startLine) {
            this.name = name;
            this.startLine = startLine;
            this.instructionList = new LinkedList<>();
        }

        void add(String var, int line) {
            instructionList.add(new Instruction(var, line));
        }

        void setEndLine(int line) {
            this.endLine = line;
        }

        LinkedList<Instruction> getInstructionList() {
            return instructionList;
        }

        String getName() {
            return name;
        }

        LLVMSourceLocation toSourceLocation(Source llSource) {
            final SourceSection startSection = llSource.createSection(startLine);
            final int startCharIndex = startSection.getCharIndex();
            final SourceSection endSection = llSource.createSection(endLine);
            final int charLength = endSection.getCharEndIndex() - startCharIndex;
            final SourceSection totalSection = llSource.createSection(startCharIndex, charLength);
            return LLVMSourceLocation.create(null, LLVMSourceLocation.Kind.FUNCTION, name, totalSection, null);
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
            return LLVMSourceLocation.create(parent, LLVMSourceLocation.Kind.LINE, "<line>", sourceSection, null);
        }

        String getDescriptor() {
            return descriptor;
        }
    }

    public static LLSourceMap build(Source bcSource) throws IOException {
        final String bcPath = bcSource.getPath();
        if (bcPath == null || !bcPath.endsWith(".bc")) {
            return null;
        }

        final String llPath = bcPath.substring(0, bcPath.length() - ".bc".length()) + ".ll";
        final File llFile = new File(llPath);
        if (!llFile.exists() || !llFile.canRead()) {
            return null;
        }

        final Source llSource = Source.newBuilder(new File(llPath)).mimeType("text/plain").build();
        final LLSourceMap sourceMap = new LLSourceMap(llSource);
        LLScanner.scan(llSource, sourceMap);
        return sourceMap;
    }

    private LLSourceMap(Source llSource) {
        this.llSource = llSource;
        entries = new HashMap<>();
    }

    private final Source llSource;

    private final Map<String, Function> entries;

    void register(String name, Function function) {
        this.entries.put(name, function);
    }

    Function getFunction(String name) {
        return entries.get(name);
    }

    void clearFunction(Function function) {
        entries.remove(function.getName());
    }

    Source getLLSource() {
        return llSource;
    }
}

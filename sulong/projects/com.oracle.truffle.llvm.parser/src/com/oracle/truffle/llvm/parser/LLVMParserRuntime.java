/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMElemPtrSymbol;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceFileReference;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

import java.util.List;

public final class LLVMParserRuntime {
    private final LLVMScope fileScope;
    private final LLVMScope publicFileScope;
    private final NodeFactory nodeFactory;
    private final BitcodeID bitcodeID;
    private final TruffleFile file;
    private final String libName;
    private final List<LLVMSourceFileReference> sourceFileReferences;
    private final LibraryLocator locator;

    public LLVMParserRuntime(LLVMScope fileScope, LLVMScope publicFileScope, NodeFactory nodeFactory, BitcodeID bitcodeID, TruffleFile file, String libName,
                    List<LLVMSourceFileReference> sourceFileReferences,
                    LibraryLocator locator) {
        this.fileScope = fileScope;
        this.publicFileScope = publicFileScope;
        this.nodeFactory = nodeFactory;
        this.bitcodeID = bitcodeID;
        this.file = file;
        this.libName = libName;
        this.sourceFileReferences = sourceFileReferences;
        this.locator = locator;
    }

    public TruffleFile getFile() {
        return file;
    }

    public String getLibraryName() {
        return libName;
    }

    public LLVMScope getFileScope() {
        return fileScope;
    }

    public LLVMScope getPublicFileScope() {
        return publicFileScope;
    }

    public NodeFactory getNodeFactory() {
        return nodeFactory;
    }

    public BitcodeID getBitcodeID() {
        return bitcodeID;
    }

    public LibraryLocator getLocator() {
        return locator;
    }

    public List<LLVMSourceFileReference> getSourceFileReferences() {
        return sourceFileReferences;
    }

    public LLVMFunction lookupFunction(String name) {
        LLVMSymbol symbol = fileScope.get(name);
        if (symbol != null && symbol.isFunction()) {
            return symbol.asFunction();
        }
        throw new IllegalStateException("Retrieving unknown function symbol in LLVMParserRuntime: " + name);
    }

    public LLVMGlobal lookupGlobal(String name) {
        LLVMSymbol symbol = fileScope.get(name);
        if (symbol != null && symbol.isGlobalVariable()) {
            return symbol.asGlobalVariable();
        }
        throw new IllegalStateException("Retrieving unknown global symbol in LLVMParserRuntime: " + name);
    }

    public LLVMElemPtrSymbol lookUpElemPtrExpression(String name) {
        LLVMSymbol symbol = fileScope.get(name);
        if (symbol != null && symbol.isElemPtrExpression()) {
            return symbol.asElemPtrExpression();
        }
        throw new IllegalStateException("Retrieving unknown getElementPointer symbol in LLVMParserRuntime: " + name);
    }

    public LLVMSymbol lookupSymbol(String name) {
        LLVMSymbol symbol = fileScope.get(name);
        if (symbol != null) {
            return symbol;
        }
        throw new IllegalStateException("Unknown symbol: " + name);
    }
}

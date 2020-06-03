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
package com.oracle.truffle.llvm.parser.model.functions;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.listeners.Function;
import com.oracle.truffle.llvm.parser.listeners.FunctionMDOnly;
import com.oracle.truffle.llvm.parser.listeners.MetadataSubprogramOnly.MDSubprogramParsedException;
import com.oracle.truffle.llvm.parser.listeners.ParameterAttributes;
import com.oracle.truffle.llvm.parser.listeners.Types;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.DebugInfoFunctionProcessor;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.parser.text.LLSourceBuilder;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

public final class LazyFunctionParser {

    private final LLVMScanner.LazyScanner scanner;
    public final IRScope scope;
    private final Types types;
    private final FunctionDefinition function;
    private final int mode;
    private final ParameterAttributes paramAttributes;
    private final LLSourceBuilder llSource;

    private boolean isParsed;

    public LazyFunctionParser(LLVMScanner.LazyScanner scanner, IRScope scope, Types types, FunctionDefinition function, int mode, ParameterAttributes paramAttributes, LLSourceBuilder llSource) {
        this.scanner = scanner;
        this.scope = scope;
        this.types = types;
        this.function = function;
        this.mode = mode;
        this.paramAttributes = paramAttributes;
        this.llSource = llSource;
        this.isParsed = false;
    }

    public void parse(DebugInfoFunctionProcessor diProcessor, Source bitcodeSource, LLVMParserRuntime runtime) {
        if (!isParsed) {
            synchronized (scope) {
                Function parser = new Function(scope, types, function, mode, paramAttributes);
                parser.setupScope();
                scanner.scanBlock(parser);
                diProcessor.process(parser.getFunction(), parser.getScope(), bitcodeSource, runtime.getContext());
                if (runtime.getContext().getEnv().getOptions().get(SulongEngineOption.LL_DEBUG)) {
                    llSource.applySourceLocations(parser.getFunction(), runtime);
                }
                isParsed = true;
            }
        }
    }

    public void parseLinkageName(LLVMParserRuntime runtime) {
        synchronized (scope) {
            FunctionMDOnly parser = new FunctionMDOnly(scope, types, function);
            try {
                parser.setupScope();
                scanner.scanBlock(parser);
            } catch (MDSubprogramParsedException e) {
                /*
                 * If linkageName/displayName is found, an exception is thrown (such that
                 * parsing/searching does not have to be continued).
                 */
                final String displayName = e.displayName;
                final String linkageName = e.linkageName;

                if (linkageName != null && runtime.getFileScope().getFunction(displayName) == null) {
                    runtime.getFileScope().registerLinkageName(displayName, linkageName);
                }
            }
        }
    }
}

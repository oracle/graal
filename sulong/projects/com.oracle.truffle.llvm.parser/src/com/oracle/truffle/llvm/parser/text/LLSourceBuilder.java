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
package com.oracle.truffle.llvm.parser.text;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

public final class LLSourceBuilder {

    public static LLSourceBuilder create(Source bcSource) {
        final String bcPath = bcSource != null ? bcSource.getPath() : null;
        return new LLSourceBuilder(bcPath);
    }

    // we only store the path of the bc-file so the truffle source can be dropped after module
    // parsing
    private final String bcPath;
    private LLSourceMap cached;

    private LLSourceBuilder(String bcPath) {
        this.cached = null;
        this.bcPath = bcPath;
    }

    public void applySourceLocations(FunctionDefinition function, LLVMParserRuntime runtime) {
        // to include the map in the LazyFunctionParser we need to instantiate this object during
        // module parsing but we only get an LLVMContext to check whether we will actually need it
        // during function parsing, with this we build the map only on-demand and cache the result
        if (cached == null) {
            final String pathMappings = runtime.getContext().getEnv().getOptions().get(SulongEngineOption.LL_DEBUG_SOURCES);
            cached = LLScanner.findAndScanLLFile(bcPath, pathMappings, runtime.getContext());
            assert cached != null;
        }
        if (cached != LLScanner.NOT_FOUND) {
            LLInstructionMapper.setSourceLocations(cached, function, runtime);
        }
    }
}

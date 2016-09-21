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
package com.oracle.truffle.llvm.parser.base;

import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.types.LLVMFunction;

public class LLVMParserResultImpl implements LLVMParserResult {

    private final RootCallTarget mainFunction;
    private final RootCallTarget globalVarInits;
    private final RootCallTarget globalVarDeallocs;
    private final List<RootCallTarget> constructorFunctions;
    private final List<RootCallTarget> destructorFunctions;
    private final Map<LLVMFunction, RootCallTarget> parsedFunctions;

    public LLVMParserResultImpl(RootCallTarget mainFunction,
                    RootCallTarget globalVarInits,
                    RootCallTarget globalVarDeallocs,
                    List<RootCallTarget> constructorFunctions,
                    List<RootCallTarget> destructorFunctions,
                    Map<LLVMFunction, RootCallTarget> parsedFunctions) {
        this.mainFunction = mainFunction;
        this.globalVarInits = globalVarInits;
        this.globalVarDeallocs = globalVarDeallocs;
        this.constructorFunctions = constructorFunctions;
        this.destructorFunctions = destructorFunctions;
        this.parsedFunctions = parsedFunctions;
    }

    @Override
    public RootCallTarget getMainFunction() {
        return mainFunction;
    }

    @Override
    public Map<LLVMFunction, RootCallTarget> getParsedFunctions() {
        return parsedFunctions;
    }

    @Override
    public RootCallTarget getGlobalVarInits() {
        return globalVarInits;
    }

    @Override
    public RootCallTarget getGlobalVarDeallocs() {
        return globalVarDeallocs;
    }

    @Override
    public List<RootCallTarget> getConstructorFunctions() {
        return constructorFunctions;
    }

    @Override
    public List<RootCallTarget> getDestructorFunctions() {
        return destructorFunctions;
    }
}

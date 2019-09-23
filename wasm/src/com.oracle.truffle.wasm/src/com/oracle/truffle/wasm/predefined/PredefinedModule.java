/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.predefined;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.wasm.binary.WasmContext;
import com.oracle.truffle.wasm.binary.WasmFunction;
import com.oracle.truffle.wasm.binary.WasmLanguage;
import com.oracle.truffle.wasm.binary.WasmModule;
import com.oracle.truffle.wasm.binary.constants.GlobalResolution;
import com.oracle.truffle.wasm.binary.exception.WasmException;
import com.oracle.truffle.wasm.predefined.emscripten.EmscriptenModule;
import com.oracle.truffle.wasm.predefined.testutil.TestutilModule;

public abstract class PredefinedModule {
    private static final Map<String, PredefinedModule> predefinedModules = new HashMap<>();

    static {
        final Map<String, PredefinedModule> pm = predefinedModules;
        pm.put("emscripten", new EmscriptenModule());
        pm.put("testutil", new TestutilModule());
    }

    public static WasmModule createPredefined(WasmLanguage language, WasmContext context, String name, String predefinedModuleName) {
        final PredefinedModule predefinedModule = predefinedModules.get(predefinedModuleName);
        if (predefinedModule == null) {
            throw new WasmException("Unknown predefined module: " + predefinedModuleName);
        }
        return predefinedModule.createModule(language, context, name);
    }

    protected abstract WasmModule createModule(WasmLanguage language, WasmContext context, String name);

    protected WasmFunction defineFunction(WasmModule module, String name, byte[] paramTypes, byte[] retTypes, RootNode rootNode) {
        // We could check if the same function type had already been allocated,
        // but this is just an optimization, and probably not very important,
        // since predefined modules have a relatively small size.
        final int typeIdx = module.symbolTable().allocateFunctionType(paramTypes, retTypes);
        final WasmFunction function = module.symbolTable().declareExportedFunction(typeIdx, name);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        function.setCallTarget(callTarget);
        return function;
    }

    protected int defineGlobal(WasmContext context, WasmModule module, String name, int valueType, int mutability, long value) {
        int index = module.symbolTable().maxGlobalIndex() + 1;
        int address = module.symbolTable().declareExportedGlobal(context, name, index, valueType, mutability, GlobalResolution.DECLARED);
        context.globals().storeLong(address, value);
        return index;
    }

    protected byte[] types(byte... args) {
        return args;
    }
}

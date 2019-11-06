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
package com.oracle.truffle.wasm.predefined.testutil;

import com.oracle.truffle.wasm.WasmContext;
import com.oracle.truffle.wasm.WasmLanguage;
import com.oracle.truffle.wasm.WasmModule;
import com.oracle.truffle.wasm.predefined.PredefinedModule;

public class TestutilModule extends PredefinedModule {
    public static class Names {
        public static final String RESET_CONTEXT = "__testutil_reset_context";
        public static final String SAVE_CONTEXT = "__testutil_save_context";
        public static final String COMPARE_CONTEXTS = "__testutil_compare_contexts";
        public static final String RUN_CUSTOM_INITIALIZATION = "__testutil_run_custom_initialization";
    }

    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        // Note: the types are not important here, since these methods are not accessed by Wasm
        // code.
        WasmModule module = new WasmModule(name, null);
        defineFunction(module, Names.RESET_CONTEXT, types(), types(), new ResetContextNode(language, null, null));
        defineFunction(module, Names.SAVE_CONTEXT, types(), types(), new SaveContextNode(language, null, null));
        defineFunction(module, Names.COMPARE_CONTEXTS, types(), types(), new CompareContextsNode(language, null, null));
        defineFunction(module, Names.RUN_CUSTOM_INITIALIZATION, types(), types(), new RunCustomInitialization(language, null, null));
        return module;
    }
}

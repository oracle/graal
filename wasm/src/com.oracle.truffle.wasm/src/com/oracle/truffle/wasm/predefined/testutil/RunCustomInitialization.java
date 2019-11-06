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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.wasm.WasmCodeEntry;
import com.oracle.truffle.wasm.WasmContext;
import com.oracle.truffle.wasm.WasmLanguage;
import com.oracle.truffle.wasm.WasmVoidResult;
import com.oracle.truffle.wasm.memory.WasmMemory;
import com.oracle.truffle.wasm.predefined.WasmPredefinedRootNode;

import java.util.function.Consumer;

/**
 * Initialize the module using the initialization object provided by the test suite. The
 * initialization will set certain globals and memory locations to specific values. This is done
 * because certain language backends emit non-WebAssembly code that is used to initialize parts of
 * the memory.
 */
public class RunCustomInitialization extends WasmPredefinedRootNode {
    public RunCustomInitialization(WasmLanguage language, WasmCodeEntry codeEntry, WasmMemory memory) {
        super(language, codeEntry, memory);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        initializeModule(frame.getArguments()[0]);
        return WasmVoidResult.getInstance();
    }

    @Override
    public String name() {
        return TestutilModule.Names.RUN_CUSTOM_INITIALIZATION;
    }

    @SuppressWarnings("unchecked")
    @CompilerDirectives.TruffleBoundary
    private void initializeModule(Object initialization) {
        if (initialization != null) {
            WasmContext context = WasmContext.getCurrent();
            ((Consumer<WasmContext>) initialization).accept(context);
        }
    }
}

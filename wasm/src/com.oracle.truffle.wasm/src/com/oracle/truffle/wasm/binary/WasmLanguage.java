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
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import org.graalvm.options.OptionDescriptors;

@TruffleLanguage.Registration(id = "wasm", name = "WebAssembly", defaultMimeType = "application/wasm", byteMimeTypes = "application/wasm", contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE, fileTypeDetectors = WasmFileDetector.class)
public final class WasmLanguage extends TruffleLanguage<WasmContext> {

    @Override
    protected WasmContext createContext(Env env) {
        return new WasmContext(env, this);
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        final WasmContext context = getContextReference().get();
        final String moduleName = request.getSource().getName();
        final WasmModule module = new WasmModule(moduleName);
        final BinaryReader reader = new BinaryReader(this, module, request.getSource().getBytes().toByteArray());
        reader.readModule();
        context.linker().link(module);
        context.registerModule(module);
        // TODO: Should this return an initialization function? Or a start function?
        return Truffle.getRuntime().createCallTarget(new WasmUndefinedFunctionRootNode(this));
    }

    @Override
    protected Iterable<Scope> findTopScopes(WasmContext context) {
        return context.getTopScopes();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new WasmOptionsOptionDescriptors();
    }

    public static WasmContext getCurrentContext() {
        return getCurrentContext(WasmLanguage.class);
    }

}

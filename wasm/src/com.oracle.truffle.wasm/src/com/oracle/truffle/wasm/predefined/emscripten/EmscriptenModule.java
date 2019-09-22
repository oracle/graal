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
package com.oracle.truffle.wasm.predefined.emscripten;

import static com.oracle.truffle.wasm.binary.ValueTypes.F64_TYPE;
import static com.oracle.truffle.wasm.binary.ValueTypes.I32_TYPE;

import com.oracle.truffle.wasm.binary.WasmLanguage;
import com.oracle.truffle.wasm.binary.WasmModule;
import com.oracle.truffle.wasm.predefined.PredefinedModule;

public class EmscriptenModule extends PredefinedModule {
    @Override
    protected WasmModule createModule(WasmLanguage language, String name) {
        WasmModule module = new WasmModule(name);
        defineFunction(module, "abort", types(I32_TYPE), types(), new AbortNode(language, null));
        defineFunction(module, "abortOnCannotGrowMemory", types(I32_TYPE), types(I32_TYPE), new AbortOnCannotGrowMemory(language, null));
        defineFunction(module, "_emscripten_memcpy_big", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new EmscriptenMemcpyBig(language, null));
        defineFunction(module, "_emscripten_get_heap_size", types(), types(I32_TYPE), new EmscriptenGetHeapSize(language, null));
        defineFunction(module, "_emscripten_resize_heap", types(I32_TYPE), types(I32_TYPE), new EmscriptenResizeHeap(language, null));
        defineFunction(module, "_gettimeofday", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new GetTimeOfDay(language, null));
        defineFunction(module, "_llvm_exp2_f64", types(F64_TYPE), types(F64_TYPE), new LLVMExp2F64(language, null));
        defineFunction(module, "___wasi_fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWrite(language, null));
        defineFunction(module, "___lock", types(I32_TYPE), types(), new Lock(language, null));
        defineFunction(module, "___unlock", types(I32_TYPE), types(), new Unlock(language, null));
        defineFunction(module, "___setErrNo", types(I32_TYPE), types(), new SetErrNo(language, null));
        return module;
    }
}

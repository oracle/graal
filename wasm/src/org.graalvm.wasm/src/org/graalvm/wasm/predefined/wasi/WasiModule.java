/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.predefined.wasi;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmOptions;
import org.graalvm.wasm.predefined.BuiltinModule;
import org.graalvm.wasm.predefined.emscripten.UnimplementedNode;

import static org.graalvm.wasm.ValueTypes.I32_TYPE;
import static org.graalvm.wasm.ValueTypes.I64_TYPE;

public class WasiModule extends BuiltinModule {
    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        final WasmOptions.StoreConstantsPolicyEnum storeConstantsPolicy = WasmOptions.StoreConstantsPolicy.getValue(context.environment().getOptions());
        WasmModule module = new WasmModule(name, null, storeConstantsPolicy);
        importMemory(context, module, "main", "memory", 0, 0);
        defineFunction(context, module, "args_sizes_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiArgsSizesGetNode(language, module));
        defineFunction(context, module, "args_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiArgsGetNode(language, module));
        defineFunction(context, module, "clock_time_get", types(I32_TYPE, I64_TYPE, I32_TYPE), types(I32_TYPE), new WasiClockTimeGet(language, module));
        defineFunction(context, module, "proc_exit", types(I32_TYPE), types(), new WasiProcExitNode(language, module));
        defineFunction(context, module, "fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWrite(language, module));
        defineFunction(context, module, "fd_read", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("fd_read", language, module));
        defineFunction(context, module, "fd_close", types(I32_TYPE), types(I32_TYPE), new UnimplementedNode("fd_close", language, module));
        defineFunction(context, module, "fd_seek", types(I32_TYPE, I64_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("fd_seek", language, module));
        return module;
    }
}

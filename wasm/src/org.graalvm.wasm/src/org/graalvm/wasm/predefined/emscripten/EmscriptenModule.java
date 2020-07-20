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
package org.graalvm.wasm.predefined.emscripten;

import static org.graalvm.wasm.ValueTypes.F64_TYPE;
import static org.graalvm.wasm.ValueTypes.I32_TYPE;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmOptions;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.predefined.BuiltinModule;
import org.graalvm.wasm.ReferenceTypes;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.predefined.wasi.WasiFdWrite;

public class EmscriptenModule extends BuiltinModule {
    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        final WasmOptions.StoreConstantsPolicyEnum storeConstantsPolicy = WasmOptions.StoreConstantsPolicy.getValue(context.environment().getOptions());
        WasmModule module = new WasmModule(name, null, storeConstantsPolicy);

        final WasmModule testutil = context.modules().get("testutil");
        if (testutil != null) {
            // Emscripten only allows extern symbols through the 'env' module, so we need to
            // re-export some symbols from the testutil module.
            if (testutil.symbolTable().function(TestutilModule.Names.SAVE_BINARY_FILE) != null) {
                importFunction(context, module, "testutil", TestutilModule.Names.SAVE_BINARY_FILE, types(I32_TYPE, I32_TYPE, I32_TYPE), types(), "" + TestutilModule.Names.SAVE_BINARY_FILE);
            }
        }

        defineFunction(context, module, "abort", types(), types(), new AbortNode(language, module));
        defineFunction(context, module, "abortOnCannotGrowMemory", types(I32_TYPE), types(I32_TYPE), new AbortOnCannotGrowMemory(language, module));
        defineFunction(context, module, "segfault", types(), types(), new Segfault(language, module));
        defineFunction(context, module, "alignfault", types(), types(), new Alignfault(language, module));
        defineFunction(context, module, "emscripten_memcpy_big", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new EmscriptenMemcpyBig(language, module));
        defineFunction(context, module, "emscripten_get_heap_size", types(), types(I32_TYPE), new EmscriptenGetHeapSize(language, module));
        defineFunction(context, module, "emscripten_resize_heap", types(I32_TYPE), types(I32_TYPE), new EmscriptenResizeHeap(language, module));
        defineFunction(context, module, "gettimeofday", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new GetTimeOfDay(language, module));
        defineFunction(context, module, "llvm_exp2_f64", types(F64_TYPE), types(F64_TYPE), new LLVMExp2F64(language, module));
        defineFunction(context, module, "__wasi_fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWrite(language, module));
        defineFunction(context, module, "__lock", types(I32_TYPE), types(), new Lock(language, module));
        defineFunction(context, module, "__unlock", types(I32_TYPE), types(), new Unlock(language, module));
        defineFunction(context, module, "__setErrNo", types(I32_TYPE), types(), new SetErrNo(language, module));
        defineFunction(context, module, "__syscall140", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall140", language, module));
        defineFunction(context, module, "__syscall146", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall146", language, module));
        defineFunction(context, module, "__syscall54", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall54", language, module));
        defineFunction(context, module, "__syscall6", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall6", language, module));
        defineFunction(context, module, "setTempRet0", types(I32_TYPE), types(), new UnimplementedNode("setTempRet0", language, module));
        defineGlobal(context, module, "_table_base", I32_TYPE, (byte) GlobalModifier.CONSTANT, 0);
        defineGlobal(context, module, "_memory_base", I32_TYPE, (byte) GlobalModifier.CONSTANT, 0);
        defineGlobal(context, module, "DYNAMICTOP_PTR", I32_TYPE, (byte) GlobalModifier.CONSTANT, 0);
        defineGlobal(context, module, "DYNAMIC_BASE", I32_TYPE, (byte) GlobalModifier.CONSTANT, 0);
        defineTable(context, module, "table", 0, -1, ReferenceTypes.FUNCREF);
        return module;
    }
}

/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.WasmType.F64_TYPE;
import static org.graalvm.wasm.WasmType.I32_TYPE;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.predefined.BuiltinModule;
import org.graalvm.wasm.predefined.wasi.WasiFdWriteNode;

public class EmscriptenModule extends BuiltinModule {
    private static final int NUMBER_OF_FUNCTIONS = 20;

    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        final WasmModule module = WasmModule.createBuiltin(name);

        defineFunction(context, module, "abort", types(), types(), new AbortNode(language, module));
        defineFunction(context, module, "abortOnCannotGrowMemory", types(I32_TYPE), types(I32_TYPE), new AbortOnCannotGrowMemoryNode(language, module));
        defineFunction(context, module, "segfault", types(), types(), new SegfaultNode(language, module));
        defineFunction(context, module, "alignfault", types(), types(), new AlignfaultNode(language, module));
        defineFunction(context, module, "emscripten_memcpy_big", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new EmscriptenMemcpyBigNode(language, module));
        defineFunction(context, module, "emscripten_get_heap_size", types(), types(I32_TYPE), new EmscriptenGetHeapSizeNode(language, module));
        defineFunction(context, module, "emscripten_resize_heap", types(I32_TYPE), types(I32_TYPE), new EmscriptenResizeHeapNode(language, module));
        defineFunction(context, module, "emscripten_notify_memory_growth", types(I32_TYPE), types(), new EmscriptenNotifyMemoryGrowthNode(language, module));
        defineFunction(context, module, "gettimeofday", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new GetTimeOfDayNode(language, module));
        defineFunction(context, module, "llvm_exp2_f64", types(F64_TYPE), types(F64_TYPE), new LLVMExp2F64Node(language, module));
        defineFunction(context, module, "__wasi_fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWriteNode(language, module));
        defineFunction(context, module, "__lock", types(I32_TYPE), types(), new LockNode(language, module));
        defineFunction(context, module, "__unlock", types(I32_TYPE), types(), new UnlockNode(language, module));
        defineFunction(context, module, "__setErrNo", types(I32_TYPE), types(), new SetErrNoNode(language, module));
        defineFunction(context, module, "__syscall140", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall140", language, module));
        defineFunction(context, module, "__syscall146", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall146", language, module));
        defineFunction(context, module, "__syscall54", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall54", language, module));
        defineFunction(context, module, "__syscall6", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall6", language, module));
        defineFunction(context, module, "setTempRet0", types(I32_TYPE), types(), new UnimplementedNode("setTempRet0", language, module));
        defineGlobal(module, "_table_base", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(module, "_memory_base", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(module, "DYNAMICTOP_PTR", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(module, "DYNAMIC_BASE", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineTable(context, module, "table", 0, -1, WasmType.FUNCREF_TYPE);
        assert module.numFunctions() == NUMBER_OF_FUNCTIONS;
        return module;
    }
}

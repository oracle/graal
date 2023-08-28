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
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.predefined.BuiltinModule;
import org.graalvm.wasm.predefined.wasi.WasiFdWriteNode;

public class EmscriptenModule extends BuiltinModule {
    private static final int NUMBER_OF_FUNCTIONS = 20;

    @Override
    protected WasmInstance createInstance(WasmLanguage language, WasmContext context, String name) {
        WasmInstance instance = new WasmInstance(context, WasmModule.createBuiltin(name), NUMBER_OF_FUNCTIONS);

        defineFunction(instance, "abort", types(), types(), new AbortNode(language, instance));
        defineFunction(instance, "abortOnCannotGrowMemory", types(I32_TYPE), types(I32_TYPE), new AbortOnCannotGrowMemoryNode(language, instance));
        defineFunction(instance, "segfault", types(), types(), new SegfaultNode(language, instance));
        defineFunction(instance, "alignfault", types(), types(), new AlignfaultNode(language, instance));
        defineFunction(instance, "emscripten_memcpy_big", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new EmscriptenMemcpyBigNode(language, instance));
        defineFunction(instance, "emscripten_get_heap_size", types(), types(I32_TYPE), new EmscriptenGetHeapSizeNode(language, instance));
        defineFunction(instance, "emscripten_resize_heap", types(I32_TYPE), types(I32_TYPE), new EmscriptenResizeHeapNode(language, instance));
        defineFunction(instance, "emscripten_notify_memory_growth", types(I32_TYPE), types(), new EmscriptenNotifyMemoryGrowthNode(language, instance));
        defineFunction(instance, "gettimeofday", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new GetTimeOfDayNode(language, instance));
        defineFunction(instance, "llvm_exp2_f64", types(F64_TYPE), types(F64_TYPE), new LLVMExp2F64Node(language, instance));
        defineFunction(instance, "__wasi_fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWriteNode(language, instance));
        defineFunction(instance, "__lock", types(I32_TYPE), types(), new LockNode(language, instance));
        defineFunction(instance, "__unlock", types(I32_TYPE), types(), new UnlockNode(language, instance));
        defineFunction(instance, "__setErrNo", types(I32_TYPE), types(), new SetErrNoNode(language, instance));
        defineFunction(instance, "__syscall140", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall140", language, instance));
        defineFunction(instance, "__syscall146", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall146", language, instance));
        defineFunction(instance, "__syscall54", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall54", language, instance));
        defineFunction(instance, "__syscall6", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall6", language, instance));
        defineFunction(instance, "setTempRet0", types(I32_TYPE), types(), new UnimplementedNode("setTempRet0", language, instance));
        defineGlobal(instance, "_table_base", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(instance, "_memory_base", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(instance, "DYNAMICTOP_PTR", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(instance, "DYNAMIC_BASE", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineTable(instance, "table", 0, -1, WasmType.FUNCREF_TYPE);
        return instance;
    }
}

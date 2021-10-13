/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.ModuleLimits;
import org.graalvm.wasm.ReferenceTypes;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.runtime.WasmFunctionInstance;
import org.graalvm.wasm.runtime.WasmModuleInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.parser.module.WasmExternalValue;
import org.graalvm.wasm.predefined.BuiltinModule;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.predefined.wasi.WasiFdWriteNode;
import org.graalvm.wasm.runtime.WasmGlobal;

import static org.graalvm.wasm.WasmType.F64_TYPE;
import static org.graalvm.wasm.WasmType.I32_TYPE;

public class EmscriptenModule extends BuiltinModule {

    @Override
    protected WasmModuleInstance createInstance(WasmLanguage language, WasmContext context, String name, ModuleLimits limits) {
        WasmModuleInstance instance = new WasmModuleInstance(
                        null,
                        null,
                        new WasmFunctionInstance[19],
                        new WasmGlobal[4],
                        24,
                        name,
                        null);

        final WasmModuleInstance testutil = context.moduleInstances().get("testutil");
        if (testutil != null) {
            // Emscripten only allows extern symbols through the 'env' module, so we need to
            // re-export some symbols from the testutil module.
            WasmExternalValue saveBinaryFile = testutil.getExport(TestutilModule.Names.SAVE_BINARY_FILE);
            if (saveBinaryFile != null && saveBinaryFile.isFunction() && saveBinaryFile.functionExists(testutil)) {
                reexportFunction(instance, TestutilModule.Names.SAVE_BINARY_FILE, saveBinaryFile.getFunction(instance));
            }
        }

        exportFunction(context, instance, "abort", types(), types(), new AbortNode(language, instance.getInstance()));
        exportFunction(context, instance, "abortOnCannotGrowMemory", types(I32_TYPE), types(I32_TYPE), new AbortOnCannotGrowMemoryNode(language, instance.getInstance()));
        exportFunction(context, instance, "segfault", types(), types(), new SegfaultNode(language, instance.getInstance()));
        exportFunction(context, instance, "alignfault", types(), types(), new AlignfaultNode(language, instance.getInstance()));
        exportFunction(context, instance, "emscripten_memcpy_big", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new EmscriptenMemcpyBigNode(language, instance.getInstance()));
        exportFunction(context, instance, "emscripten_get_heap_size", types(), types(I32_TYPE), new EmscriptenGetHeapSizeNode(language, instance.getInstance()));
        exportFunction(context, instance, "emscripten_resize_heap", types(I32_TYPE), types(I32_TYPE), new EmscriptenResizeHeapNode(language, instance.getInstance()));
        exportFunction(context, instance, "gettimeofday", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new GetTimeOfDayNode(language, instance.getInstance()));
        exportFunction(context, instance, "llvm_exp2_f64", types(F64_TYPE), types(F64_TYPE), new LLVMExp2F64Node(language, instance.getInstance()));
        exportFunction(context, instance, "__wasi_fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWriteNode(language, instance.getInstance()));
        exportFunction(context, instance, "__lock", types(I32_TYPE), types(), new LockNode(language, instance.getInstance()));
        exportFunction(context, instance, "__unlock", types(I32_TYPE), types(), new UnlockNode(language, instance.getInstance()));
        exportFunction(context, instance, "__setErrNo", types(I32_TYPE), types(), new SetErrNoNode(language, instance.getInstance()));
        exportFunction(context, instance, "__syscall140", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall140", language, instance.getInstance()));
        exportFunction(context, instance, "__syscall146", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall146", language, instance.getInstance()));
        exportFunction(context, instance, "__syscall54", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall54", language, instance.getInstance()));
        exportFunction(context, instance, "__syscall6", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("__syscall6", language, instance.getInstance()));
        exportFunction(context, instance, "setTempRet0", types(I32_TYPE), types(), new UnimplementedNode("setTempRet0", language, instance.getInstance()));
        exportGlobal(instance, "_table_base", I32_TYPE, (byte) GlobalModifier.CONSTANT, 0);
        exportGlobal(instance, "_memory_base", I32_TYPE, (byte) GlobalModifier.CONSTANT, 0);
        exportGlobal(instance, "DYNAMICTOP_PTR", I32_TYPE, (byte) GlobalModifier.CONSTANT, 0);
        exportGlobal(instance, "DYNAMIC_BASE", I32_TYPE, (byte) GlobalModifier.CONSTANT, 0);
        exportTable(instance, "table", 0, -1, ReferenceTypes.FUNCREF, limits);
        return instance;
    }
}

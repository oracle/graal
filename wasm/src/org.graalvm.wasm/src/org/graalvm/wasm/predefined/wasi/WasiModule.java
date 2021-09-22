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
package org.graalvm.wasm.predefined.wasi;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.BuiltinModule;

import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.I64_TYPE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_DECLARATION_SIZE;

public final class WasiModule extends BuiltinModule {
    private static final int NUMBER_OF_FUNCTIONS = 16;

    @Override
    protected WasmInstance createInstance(WasmLanguage language, WasmContext context, String name) {
        WasmInstance instance = new WasmInstance(context, WasmModule.createBuiltin(name), NUMBER_OF_FUNCTIONS);
        importMemory(instance, "main", "memory", 0, MAX_MEMORY_DECLARATION_SIZE);
        defineFunction(instance, "args_sizes_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiArgsSizesGetNode(language, instance));
        defineFunction(instance, "args_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiArgsGetNode(language, instance));
        defineFunction(instance, "environ_sizes_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiEnvironSizesGetNode(language, instance));
        defineFunction(instance, "environ_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiEnvironGetNode(language, instance));
        defineFunction(instance, "clock_time_get", types(I32_TYPE, I64_TYPE, I32_TYPE), types(I32_TYPE), new WasiClockTimeGetNode(language, instance));
        defineFunction(instance, "proc_exit", types(I32_TYPE), types(), new WasiProcExitNode(language, instance));
        defineFunction(instance, "fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWriteNode(language, instance));
        defineFunction(instance, "fd_read", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdReadNode(language, instance));
        defineFunction(instance, "fd_close", types(I32_TYPE), types(I32_TYPE), new WasiFdCloseNode(language, instance));
        defineFunction(instance, "fd_seek", types(I32_TYPE, I64_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdSeekNode(language, instance));
        defineFunction(instance, "fd_fdstat_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdFdstatGetNode(language, instance));
        defineFunction(instance, "fd_fdstat_set_flags", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdFdstatSetFlagsNode(language, instance));
        defineFunction(instance, "fd_prestat_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdPrestatGetNode(language, instance));
        defineFunction(instance, "fd_prestat_dir_name", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdPrestatDirNameNode(language, instance));
        defineFunction(instance, "fd_filestat_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdFilestatGetNode(language, instance));
        defineFunction(instance, "path_open", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I64_TYPE, I64_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE),
                        new WasiPathOpenNode(language, instance));
        return instance;
    }

}

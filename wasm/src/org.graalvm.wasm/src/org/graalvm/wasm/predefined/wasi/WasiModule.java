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
package org.graalvm.wasm.predefined.wasi;

import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.I64_TYPE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_64_DECLARATION_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_DECLARATION_SIZE;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.BuiltinModule;

public final class WasiModule extends BuiltinModule {

    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        WasmModule module = WasmModule.createBuiltin(name);
        if (context.getContextOptions().supportMemory64()) {
            importMemory(context, module, "main", "memory", 0, MAX_MEMORY_64_DECLARATION_SIZE, true, false);
        } else {
            importMemory(context, module, "main", "memory", 0, MAX_MEMORY_DECLARATION_SIZE, false, false);
        }
        defineFunction(context, module, "args_sizes_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiArgsSizesGetNode(language, module));
        defineFunction(context, module, "args_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiArgsGetNode(language, module));
        defineFunction(context, module, "environ_sizes_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiEnvironSizesGetNode(language, module));
        defineFunction(context, module, "environ_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiEnvironGetNode(language, module));
        defineFunction(context, module, "clock_time_get", types(I32_TYPE, I64_TYPE, I32_TYPE), types(I32_TYPE), new WasiClockTimeGetNode(language, module));
        defineFunction(context, module, "proc_exit", types(I32_TYPE), types(), new WasiProcExitNode(language, module));
        defineFunction(context, module, "fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWriteNode(language, module));
        defineFunction(context, module, "fd_read", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdReadNode(language, module));
        defineFunction(context, module, "fd_close", types(I32_TYPE), types(I32_TYPE), new WasiFdCloseNode(language, module));
        defineFunction(context, module, "fd_seek", types(I32_TYPE, I64_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdSeekNode(language, module));
        defineFunction(context, module, "fd_fdstat_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdFdstatGetNode(language, module));
        defineFunction(context, module, "fd_fdstat_set_flags", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdFdstatSetFlagsNode(language, module));
        defineFunction(context, module, "fd_prestat_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdPrestatGetNode(language, module));
        defineFunction(context, module, "fd_prestat_dir_name", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdPrestatDirNameNode(language, module));
        defineFunction(context, module, "fd_filestat_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdFilestatGetNode(language, module));
        defineFunction(context, module, "path_open", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I64_TYPE, I64_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE),
                        new WasiPathOpenNode(language, module));
        defineFunction(context, module, "path_create_directory", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiPathCreateDirectoryNode(language, module));
        defineFunction(context, module, "path_remove_directory", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiPathRemoveDirectoryNode(language, module));
        defineFunction(context, module, "path_filestat_set_times", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I64_TYPE, I64_TYPE, I32_TYPE), types(I32_TYPE),
                        new WasiPathFilestatSetTimesNode(language, module));
        defineFunction(context, module, "path_link", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiPathLinkNode(language, module));
        defineFunction(context, module, "path_rename", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiPathRenameNode(language, module));
        defineFunction(context, module, "path_symlink", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiPathSymlinkNode(language, module));
        defineFunction(context, module, "path_unlink_file", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiPathUnlinkFileNode(language, module));
        defineFunction(context, module, "path_readlink", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiPathReadLinkNode(language, module));
        defineFunction(context, module, "path_filestat_get", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiPathFileStatGetNode(language, module));
        defineFunction(context, module, "sched_yield", types(), types(I32_TYPE), new WasiSchedYieldNode(language, module));
        if (context.getContextOptions().constantRandomGet()) {
            defineFunction(context, module, "random_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiConstantRandomGetNode(language, module));
        } else {
            defineFunction(context, module, "random_get", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiRandomGetNode(language, module));
        }
        return module;
    }

}

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

@SuppressWarnings("unused")
public final class WasiModule extends BuiltinModule {

    // The WASI preview1 module's API is expressed using an IDL called witx. witx uses a C-like type
    // system which is then mapped onto WebAssembly's core types. The following constants give first
    // the mappings from witx types to WebAssembly types and then they use those witx types to
    // define the WASI types used in the signature of the WASI module.

    // (@witx pointer) is lowered to i32
    private static final byte POINTER_TYPE = I32_TYPE;
    // (@witx const_pointer) is lowered to i32
    private static final byte CONST_POINTER_TYPE = I32_TYPE;
    // (record) arguments are lowered to i32 addresses unless there is a flags representation
    private static final byte RECORD_TYPE = I32_TYPE;
    // (union) arguments are lowered to i32 addresses unless they are simple enums
    private static final byte UNION_TYPE = I32_TYPE;
    // (handle) arguments are lowered to i32 values
    private static final byte HANDLE_TYPE = I32_TYPE;
    // (list) arguments are lowered to an address (i32) argument and a length (i32) argument
    private static final byte LIST_ADDRESS_TYPE = I32_TYPE;
    private static final byte LIST_LENGTH_TYPE = I32_TYPE;
    // (string) arguments are lowered the same way as (list) arguments
    private static final byte STRING_ADDRESS_TYPE = LIST_ADDRESS_TYPE;
    private static final byte STRING_LENGTH_TYPE = LIST_LENGTH_TYPE;
    private static final byte U8_TYPE = I32_TYPE;
    private static final byte U16_TYPE = I32_TYPE;
    private static final byte U32_TYPE = I32_TYPE;
    private static final byte U64_TYPE = I64_TYPE;
    private static final byte S64_TYPE = I64_TYPE;
    // (result $error (expected X (error $errno))) return values are implemented by returning a
    // value of type $errno and accepting as arguments pointers for writing any elements of the
    // expected return value X
    private static final byte RETURN_VALUE_ADDRESS_TYPE = I32_TYPE;

    private static final byte SIZE_TYPE = U32_TYPE; // u32
    private static final byte FILESIZE_TYPE = U64_TYPE; // u64
    private static final byte TIMESTAMP_TYPE = U64_TYPE; // u64
    private static final byte CLOCKID_TYPE = U32_TYPE; // (enum (@witx tag u32))
    private static final byte ERRNO_TYPE = U16_TYPE; // (enum (@witx tag u16))
    private static final byte RIGHTS_TYPE = U64_TYPE; // (flags (@witx repr u64))
    private static final byte FD_TYPE = HANDLE_TYPE; // (handle)
    private static final byte IOVEC_TYPE = RECORD_TYPE; // (record)
    private static final byte CIOVEC_TYPE = RECORD_TYPE; // (record)
    private static final byte IOVEC_ARRAY_ADDRESS_TYPE = LIST_ADDRESS_TYPE; // (list)
    private static final byte IOVEC_ARRAY_LENGTH_TYPE = LIST_LENGTH_TYPE; // (list)
    private static final byte CIOVEC_ARRAY_ADDRESS_TYPE = LIST_ADDRESS_TYPE; // (list)
    private static final byte CIOVEC_ARRAY_LENGTH_TYPE = LIST_LENGTH_TYPE; // (list)
    private static final byte FILEDELTA_TYPE = S64_TYPE; // s64
    private static final byte WHENCE_TYPE = U8_TYPE; // (enum (@witx tag u8))
    private static final byte DIRCOOKIE_TYPE = U64_TYPE; // u64
    private static final byte DIRNAMLEN_TYPE = U32_TYPE; // u32
    private static final byte INODE_TYPE = U64_TYPE; // u64
    private static final byte FILETYPE_TYPE = U8_TYPE; // (enum (@witx tag u8))
    private static final byte DIRENT_TYPE = RECORD_TYPE; // (record)
    private static final byte ADVICE_TYPE = U8_TYPE; // (enum (@witx tag u8))
    private static final byte FDFLAGS_TYPE = U16_TYPE; // (flags (@witx repr u16))
    private static final byte FDSTAT_TYPE = RECORD_TYPE; // (record)
    private static final byte DEVICE_TYPE = U64_TYPE; // u64
    private static final byte FSTFLAGS_TYPE = U16_TYPE; // (flags (@witx repr u16))
    private static final byte LOOKUPFLAGS_TYPE = U32_TYPE; // (flags (@witx repr u32))
    private static final byte OFLAGS_TYPE = U16_TYPE; // (flags (@witx repr u16))
    private static final byte LINKCOUNT_TYPE = U64_TYPE; // u64
    private static final byte FILESTAT_TYPE = RECORD_TYPE; // (record)
    private static final byte USERDATA_TYPE = U64_TYPE; // u64
    private static final byte EVENTTYPE_TYPE = U8_TYPE; // (enum (@witx tag u8))
    private static final byte EVENTRWFLAGS_TYPE = U16_TYPE; // (flags (@witx repr u16))
    private static final byte EVENT_FD_READWRITE_TYPE = RECORD_TYPE; // (record)
    private static final byte EVENT_TYPE = RECORD_TYPE; // (record)
    private static final byte SUBCLOCKFLAGS_TYPE = U16_TYPE; // (flags (@witx repr u16))
    private static final byte SUBSCRIPTION_CLOCK_TYPE = RECORD_TYPE; // (record)
    private static final byte SUBSCRIPTION_FD_READWRITE_TYPE = RECORD_TYPE; // (record)
    private static final byte SUBSCRIPTION_U_TYPE = UNION_TYPE; // (union)
    private static final byte SUBSCRIPTION_TYPE = RECORD_TYPE; // (record)
    private static final byte EXITCODE_TYPE = U32_TYPE; // u32
    private static final byte SIGNAL_TYPE = U8_TYPE; // (enum (@witx tag u8))
    private static final byte RIFLAGS_TYPE = U16_TYPE; // (flags (@witx repr u16))
    private static final byte ROFLAGS_TYPE = U16_TYPE; // (flags (@witx repr u16))
    private static final byte SIFLAGS_TYPE = U16_TYPE; // u16
    private static final byte SDFLAGS_TYPE = U8_TYPE; // (flags (@witx repr u8))
    private static final byte PREOPENTYPE_TYPE = U8_TYPE; // (enum (@witx tag u8))
    private static final byte PRESTAT_DIR_TYPE = RECORD_TYPE; // (record)
    private static final byte PRESTAT_TYPE = UNION_TYPE; // (union)

    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        WasmModule module = WasmModule.createBuiltin(name);
        if (context.getContextOptions().supportMemory64()) {
            importMemory(context, module, "main", "memory", 0, MAX_MEMORY_64_DECLARATION_SIZE, true, false);
        } else {
            importMemory(context, module, "main", "memory", 0, MAX_MEMORY_DECLARATION_SIZE, false, false);
        }

        defineFunction(context, module, "args_get", types(POINTER_TYPE, POINTER_TYPE), types(ERRNO_TYPE), new WasiArgsGetNode(language, module));
        defineFunction(context, module, "args_sizes_get", types(RETURN_VALUE_ADDRESS_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiArgsSizesGetNode(language, module));
        defineFunction(context, module, "environ_get", types(POINTER_TYPE, POINTER_TYPE), types(ERRNO_TYPE), new WasiEnvironGetNode(language, module));
        defineFunction(context, module, "environ_sizes_get", types(RETURN_VALUE_ADDRESS_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiEnvironSizesGetNode(language, module));
        defineFunction(context, module, "clock_res_get", types(CLOCKID_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiClockResGetNode(language, module));
        defineFunction(context, module, "clock_time_get", types(CLOCKID_TYPE, TIMESTAMP_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiClockTimeGetNode(language, module));
        defineFunction(context, module, "fd_advise", types(FD_TYPE, FILESIZE_TYPE, FILESIZE_TYPE, ADVICE_TYPE), types(ERRNO_TYPE),
                        new WasiFdAdviseNode(language, module));
        defineFunction(context, module, "fd_allocate", types(FD_TYPE, FILESIZE_TYPE, FILESIZE_TYPE), types(ERRNO_TYPE), new WasiUnsupportedFunctionNode(language, module, "__wasi_fd_allocate"));
        defineFunction(context, module, "fd_close", types(FD_TYPE), types(ERRNO_TYPE), new WasiFdCloseNode(language, module));
        defineFunction(context, module, "fd_datasync", types(FD_TYPE), types(ERRNO_TYPE), new WasiFdDatasyncNode(language, module));
        defineFunction(context, module, "fd_fdstat_get", types(FD_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiFdFdstatGetNode(language, module));
        defineFunction(context, module, "fd_fdstat_set_flags", types(FD_TYPE, FDFLAGS_TYPE), types(ERRNO_TYPE), new WasiFdFdstatSetFlagsNode(language, module));
        defineFunction(context, module, "fd_fdstat_set_rights", types(FD_TYPE, RIGHTS_TYPE, RIGHTS_TYPE), types(ERRNO_TYPE),
                        new WasiFdFdstatSetRightsNode(language, module));
        defineFunction(context, module, "fd_filestat_get", types(FD_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiFdFilestatGetNode(language, module));
        defineFunction(context, module, "fd_filestat_set_size", types(FD_TYPE, FILESIZE_TYPE), types(ERRNO_TYPE), new WasiFdFilestatSetSizeNode(language, module));
        defineFunction(context, module, "fd_filestat_set_times", types(FD_TYPE, TIMESTAMP_TYPE, TIMESTAMP_TYPE, FSTFLAGS_TYPE), types(ERRNO_TYPE), new WasiFdFilestatSetTimesNode(language, module));
        defineFunction(context, module, "fd_pread", types(FD_TYPE, IOVEC_ARRAY_ADDRESS_TYPE, IOVEC_ARRAY_LENGTH_TYPE, FILESIZE_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiFdPreadNode(language, module));
        defineFunction(context, module, "fd_prestat_get", types(FD_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiFdPrestatGetNode(language, module));
        defineFunction(context, module, "fd_prestat_dir_name", types(FD_TYPE, POINTER_TYPE, SIZE_TYPE), types(ERRNO_TYPE), new WasiFdPrestatDirNameNode(language, module));
        defineFunction(context, module, "fd_pwrite", types(FD_TYPE, CIOVEC_ARRAY_ADDRESS_TYPE, CIOVEC_ARRAY_LENGTH_TYPE, FILESIZE_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiFdPwriteNode(language, module));
        defineFunction(context, module, "fd_read", types(FD_TYPE, IOVEC_ARRAY_ADDRESS_TYPE, IOVEC_ARRAY_LENGTH_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiFdReadNode(language, module));
        defineFunction(context, module, "fd_readdir", types(FD_TYPE, POINTER_TYPE, SIZE_TYPE, DIRCOOKIE_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiFdReaddirNode(language, module));
        defineFunction(context, module, "fd_renumber", types(FD_TYPE, FD_TYPE), types(ERRNO_TYPE), new WasiFdRenumberNode(language, module));
        defineFunction(context, module, "fd_seek", types(FD_TYPE, FILEDELTA_TYPE, WHENCE_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiFdSeekNode(language, module));
        defineFunction(context, module, "fd_sync", types(FD_TYPE), types(ERRNO_TYPE), new WasiFdSyncNode(language, module));
        defineFunction(context, module, "fd_tell", types(FD_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE), new WasiFdTellNode(language, module));
        defineFunction(context, module, "fd_write", types(FD_TYPE, CIOVEC_ARRAY_ADDRESS_TYPE, CIOVEC_ARRAY_LENGTH_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiFdWriteNode(language, module));
        defineFunction(context, module, "path_create_directory", types(FD_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE), types(ERRNO_TYPE), new WasiPathCreateDirectoryNode(language, module));
        defineFunction(context, module, "path_filestat_get", types(FD_TYPE, LOOKUPFLAGS_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiPathFileStatGetNode(language, module));
        defineFunction(context, module, "path_filestat_set_times", types(FD_TYPE, LOOKUPFLAGS_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE, TIMESTAMP_TYPE, TIMESTAMP_TYPE, FSTFLAGS_TYPE),
                        types(ERRNO_TYPE),
                        new WasiPathFilestatSetTimesNode(language, module));
        defineFunction(context, module, "path_link", types(FD_TYPE, LOOKUPFLAGS_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE, FD_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE), types(ERRNO_TYPE),
                        new WasiPathLinkNode(language, module));
        defineFunction(context, module, "path_open",
                        types(FD_TYPE, LOOKUPFLAGS_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE, OFLAGS_TYPE, RIGHTS_TYPE, RIGHTS_TYPE, FDFLAGS_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiPathOpenNode(language, module));
        defineFunction(context, module, "path_readlink", types(FD_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE, POINTER_TYPE, SIZE_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiPathReadLinkNode(language, module));
        defineFunction(context, module, "path_remove_directory", types(FD_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE), types(ERRNO_TYPE), new WasiPathRemoveDirectoryNode(language, module));
        defineFunction(context, module, "path_rename", types(FD_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE, FD_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE), types(ERRNO_TYPE),
                        new WasiPathRenameNode(language, module));
        defineFunction(context, module, "path_symlink", types(STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE, FD_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE), types(ERRNO_TYPE),
                        new WasiPathSymlinkNode(language, module));
        defineFunction(context, module, "path_unlink_file", types(FD_TYPE, STRING_ADDRESS_TYPE, STRING_LENGTH_TYPE), types(ERRNO_TYPE), new WasiPathUnlinkFileNode(language, module));
        defineFunction(context, module, "poll_oneoff", types(CONST_POINTER_TYPE, POINTER_TYPE, SIZE_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiUnsupportedFunctionNode(language, module, "__wasi_poll_oneoff"));
        defineFunction(context, module, "proc_exit", types(EXITCODE_TYPE), types(), new WasiProcExitNode(language, module));
        defineFunction(context, module, "proc_raise", types(SIGNAL_TYPE), types(ERRNO_TYPE), new WasiUnsupportedFunctionNode(language, module, "__wasi_proc_raise"));
        defineFunction(context, module, "sched_yield", types(), types(ERRNO_TYPE), new WasiSchedYieldNode(language, module));
        if (context.getContextOptions().constantRandomGet()) {
            defineFunction(context, module, "random_get", types(POINTER_TYPE, SIZE_TYPE), types(ERRNO_TYPE), new WasiConstantRandomGetNode(language, module));
        } else {
            defineFunction(context, module, "random_get", types(POINTER_TYPE, SIZE_TYPE), types(ERRNO_TYPE), new WasiRandomGetNode(language, module));
        }
        defineFunction(context, module, "sock_accept", types(FD_TYPE, FDFLAGS_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiUnsupportedFunctionNode(language, module, "__wasi_sock_accept"));
        defineFunction(context, module, "sock_recv", types(FD_TYPE, IOVEC_ARRAY_ADDRESS_TYPE, IOVEC_ARRAY_LENGTH_TYPE, RIFLAGS_TYPE, RETURN_VALUE_ADDRESS_TYPE, RETURN_VALUE_ADDRESS_TYPE),
                        types(ERRNO_TYPE), new WasiUnsupportedFunctionNode(language, module, "__wasi_sock_recv"));
        defineFunction(context, module, "sock_send", types(FD_TYPE, CIOVEC_ARRAY_ADDRESS_TYPE, CIOVEC_ARRAY_LENGTH_TYPE, SIFLAGS_TYPE, RETURN_VALUE_ADDRESS_TYPE), types(ERRNO_TYPE),
                        new WasiUnsupportedFunctionNode(language, module, "__wasi_sock_send"));
        defineFunction(context, module, "sock_shutdown", types(FD_TYPE, SDFLAGS_TYPE), types(ERRNO_TYPE), new WasiUnsupportedFunctionNode(language, module, "__wasi_sock_shutdown"));
        return module;
    }
}

/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.predefined.wasi.fd;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.wasi.types.Errno;
import org.graalvm.wasm.predefined.wasi.types.Filetype;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

final class FdUtils {

    private FdUtils() {

    }

    static Errno writeToStream(Node node, WasmMemory memory, OutputStream stream, int iov, int iovcnt, int sizeAddress) {
        if (stream == null) {
            return Errno.Acces;
        }

        int totalBytesWritten = 0;
        try {
            for (int i = 0; i < iovcnt; i++) {
                final int start = memory.load_i32(node, iov + (i * 8 + 0));
                final int len = memory.load_i32(node, iov + (i * 8 + 4));
                for (int j = 0; j < len; j++) {
                    stream.write(memory.load_i32_8u(node, start + j));
                    ++totalBytesWritten;
                }
            }
        } catch (IOException e) {
            return Errno.Io;
        }

        memory.store_i32(null, sizeAddress, totalBytesWritten);
        return Errno.Success;
    }

    static Errno readFromStream(Node node, WasmMemory memory, InputStream stream, int iov, int iovcnt, int sizeAddress) {
        if (stream == null) {
            return Errno.Acces;
        }

        int totalBytesRead = 0;
        int byteRead = 0;
        try {
            for (int i = 0; i < iovcnt && byteRead != -1; i++) {
                final int start = memory.load_i32(node, iov + (i * 8 + 0));
                final int len = memory.load_i32(node, iov + (i * 8 + 4));
                for (int j = 0; j < len; j++) {
                    byteRead = stream.read();
                    if (byteRead == -1) {
                        break;
                    }
                    memory.store_i32_8(node, start + j, (byte) byteRead);
                    ++totalBytesRead;
                }
            }
        } catch (IOException e) {
            return Errno.Io;
        }

        memory.store_i32(null, sizeAddress, totalBytesRead);
        return Errno.Success;
    }

    /**
     * Writes an <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-fdstat-struct"><code>fdstat</code></a>
     * structure to memory.
     */
    static Errno writeFdstat(Node node, WasmMemory memory, int resultAddress, Filetype type, short fdflags, long fsRightsBase, long fsRightsInherting) {
        memory.store_i32_8(node, resultAddress, (byte) type.ordinal());
        memory.store_i32_16(node, resultAddress + 2, fdflags);
        memory.store_i64(node, resultAddress + 8, fsRightsBase);
        memory.store_i64(node, resultAddress + 16, fsRightsInherting);
        return Errno.Success;
    }

    /**
     * Writes an <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-filestat-struct"><code>filestat</code></a>
     * structure to memory.
     */
    static Errno writeFilestat(Node node, WasmMemory memory, int resultAddress, TruffleFile file) {
        // Write filestat structure
        // https://github.com/WebAssembly/WASI/blob/main/phases/snapshot/docs.md#-filestat-struct
        try {
            memory.store_i64(node, resultAddress, file.getAttribute(TruffleFile.UNIX_DEV));
            memory.store_i64(node, resultAddress + 8, file.getAttribute(TruffleFile.UNIX_INODE));
            memory.store_i32_8(node, resultAddress + 16, (byte) getType(file).ordinal());
            memory.store_i64(node, resultAddress + 24, file.getAttribute(TruffleFile.UNIX_NLINK));
            memory.store_i64(node, resultAddress + 32, file.getAttribute(TruffleFile.SIZE));
            memory.store_i64(node, resultAddress + 40, file.getAttribute(TruffleFile.LAST_ACCESS_TIME).to(TimeUnit.SECONDS));
            memory.store_i64(node, resultAddress + 48, file.getAttribute(TruffleFile.LAST_MODIFIED_TIME).to(TimeUnit.SECONDS));
            memory.store_i64(node, resultAddress + 56, file.getAttribute(TruffleFile.UNIX_CTIME).to(TimeUnit.SECONDS));
        } catch (IOException e) {
            return Errno.Io;
        }
        return Errno.Success;
    }

    static Filetype getType(TruffleFile file) throws IOException {
        if (file.getAttribute(TruffleFile.IS_DIRECTORY)) {
            return Filetype.Directory;
        } else if (file.getAttribute(TruffleFile.IS_REGULAR_FILE)) {
            return Filetype.RegularFile;
        } else if (file.getAttribute(TruffleFile.IS_SYMBOLIC_LINK)) {
            return Filetype.SymbolicLink;
        } else {
            return Filetype.Unknown;
        }
    }

}

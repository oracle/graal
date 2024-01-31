/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.wasm.predefined.wasi.types.Fdstat;
import org.graalvm.wasm.predefined.wasi.types.Filestat;
import org.graalvm.wasm.predefined.wasi.types.Filetype;
import org.graalvm.wasm.predefined.wasi.types.Iovec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

final class FdUtils {

    private FdUtils() {

    }

    static Errno writeToStream(Node node, WasmMemory memory, OutputStream stream, int iovecArrayAddress, int iovecCount, int sizeAddress) {
        if (stream == null) {
            return Errno.Acces;
        }

        int totalBytesWritten = 0;
        try {
            for (int i = 0; i < iovecCount; i++) {
                final int iovecAddress = iovecArrayAddress + i * Iovec.BYTES;
                final int start = Iovec.readBuf(node, memory, iovecAddress);
                final int len = Iovec.readBufLen(node, memory, iovecAddress);
                memory.copyToStream(node, stream, start, len);
                totalBytesWritten += len;
            }
        } catch (IOException e) {
            return Errno.Io;
        }

        memory.store_i32(node, sizeAddress, totalBytesWritten);
        return Errno.Success;
    }

    static Errno readFromStream(Node node, WasmMemory memory, InputStream stream, int iovecArrayAddress, int iovecCount, int sizeAddress) {
        if (stream == null) {
            return Errno.Acces;
        }

        int totalBytesRead = 0;
        try {
            for (int i = 0; i < iovecCount; i++) {
                final int iovecAddress = iovecArrayAddress + i * Iovec.BYTES;
                final int start = Iovec.readBuf(node, memory, iovecAddress);
                final int len = Iovec.readBufLen(node, memory, iovecAddress);
                final int bytesRead = memory.copyFromStream(node, stream, start, len);
                if (bytesRead == -1) {
                    break;
                }
                totalBytesRead += bytesRead;
            }
        } catch (IOException e) {
            return Errno.Io;
        }

        memory.store_i32(node, sizeAddress, totalBytesRead);
        return Errno.Success;
    }

    /**
     * Writes an <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-fdstat-struct"><code>fdstat</code></a>
     * structure to memory.
     */
    static Errno writeFdstat(Node node, WasmMemory memory, int address, Filetype type, short fsFlags, long fsRightsBase, long fsRightsInherting) {
        Fdstat.writeFsFiletype(node, memory, address, type);
        Fdstat.writeFsFlags(node, memory, address, fsFlags);
        Fdstat.writeFsRightsBase(node, memory, address, fsRightsBase);
        Fdstat.writeFsRightsInheriting(node, memory, address, fsRightsInherting);
        return Errno.Success;
    }

    /**
     * Writes an <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-filestat-struct"><code>filestat</code></a>
     * structure to memory.
     */
    static Errno writeFilestat(Node node, WasmMemory memory, int address, TruffleFile file) {
        // Write filestat structure
        // https://github.com/WebAssembly/WASI/blob/main/phases/snapshot/docs.md#-filestat-struct
        try {
            Filestat.writeFiletype(node, memory, address, getType(file));
            Filestat.writeSize(node, memory, address, file.getAttribute(TruffleFile.SIZE));
            Filestat.writeAtim(node, memory, address, file.getAttribute(TruffleFile.LAST_ACCESS_TIME).to(TimeUnit.NANOSECONDS));
            Filestat.writeMtim(node, memory, address, file.getAttribute(TruffleFile.LAST_MODIFIED_TIME).to(TimeUnit.NANOSECONDS));

            try {
                Filestat.writeDev(node, memory, address, file.getAttribute(TruffleFile.UNIX_DEV));
                Filestat.writeIno(node, memory, address, file.getAttribute(TruffleFile.UNIX_INODE));
                Filestat.writeNlink(node, memory, address, file.getAttribute(TruffleFile.UNIX_NLINK));
                Filestat.writeCtim(node, memory, address, file.getAttribute(TruffleFile.UNIX_CTIME).to(TimeUnit.NANOSECONDS));
            } catch (UnsupportedOperationException e) {
                // GR-29297: these attributes are currently not supported on non-Unix platforms.
            }
        } catch (IOException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
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

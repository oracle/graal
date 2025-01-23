/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.graalvm.wasm.predefined.wasi.types.Dirent;
import org.graalvm.wasm.predefined.wasi.types.Errno;
import org.graalvm.wasm.predefined.wasi.types.Fdstat;
import org.graalvm.wasm.predefined.wasi.types.Filestat;
import org.graalvm.wasm.predefined.wasi.types.Filetype;
import org.graalvm.wasm.predefined.wasi.types.Iovec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.TimeUnit;

final class FdUtils {

    private FdUtils() {

    }

    static Errno writeToStream(Node node, WasmMemory memory, OutputStream stream, int iovecArrayAddress, int iovecCount, int sizeAddress) {
        if (stream == null) {
            return Errno.Acces;
        }

        WasmMemoryLibrary memoryLib = WasmMemoryLibrary.getUncached();
        int totalBytesWritten = 0;
        try {
            for (int i = 0; i < iovecCount; i++) {
                final int iovecAddress = iovecArrayAddress + i * Iovec.BYTES;
                final int start = Iovec.readBuf(node, memoryLib, memory, iovecAddress);
                final int len = Iovec.readBufLen(node, memoryLib, memory, iovecAddress);
                memoryLib.copyToStream(memory, node, stream, start, len);
                totalBytesWritten += len;
            }
        } catch (IOException e) {
            return Errno.Io;
        }

        WasmMemoryLibrary.getUncached().store_i32(memory, node, sizeAddress, totalBytesWritten);
        return Errno.Success;
    }

    static Errno readFromStream(Node node, WasmMemory memory, InputStream stream, int iovecArrayAddress, int iovecCount, int sizeAddress) {
        if (stream == null) {
            return Errno.Acces;
        }

        WasmMemoryLibrary memoryLib = WasmMemoryLibrary.getUncached();
        int totalBytesRead = 0;
        try {
            for (int i = 0; i < iovecCount; i++) {
                final int iovecAddress = iovecArrayAddress + i * Iovec.BYTES;
                final int start = Iovec.readBuf(node, memoryLib, memory, iovecAddress);
                final int len = Iovec.readBufLen(node, memoryLib, memory, iovecAddress);
                final int bytesRead = memoryLib.copyFromStream(memory, node, stream, start, len);
                if (bytesRead == -1) {
                    break;
                }
                totalBytesRead += bytesRead;
            }
        } catch (IOException e) {
            return Errno.Io;
        }

        WasmMemoryLibrary.getUncached().store_i32(memory, node, sizeAddress, totalBytesRead);
        return Errno.Success;
    }

    static Errno writeToStreamAt(Node node, WasmMemory memory, OutputStream stream, int iovecArrayAddress, int iovecCount, SeekableByteChannel channel, long offset, int sizeAddress) {
        try {
            long currentOffset = channel.position();
            try {
                channel.position(offset);
                return writeToStream(node, memory, stream, iovecArrayAddress, iovecCount, sizeAddress);
            } finally {
                channel.position(currentOffset);
            }
        } catch (IOException e) {
            return Errno.Io;
        }
    }

    static Errno readFromStreamAt(Node node, WasmMemory memory, InputStream stream, int iovecArrayAddress, int iovecCount, SeekableByteChannel channel, long offset, int sizeAddress) {
        try {
            long currentOffset = channel.position();
            try {
                channel.position(offset);
                return readFromStream(node, memory, stream, iovecArrayAddress, iovecCount, sizeAddress);
            } finally {
                channel.position(currentOffset);
            }
        } catch (IOException e) {
            return Errno.Io;
        }
    }

    /**
     * Writes an <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-fdstat-struct"><code>fdstat</code></a>
     * structure to memory.
     */
    static Errno writeFdstat(Node node, WasmMemory memory, int address, Filetype type, short fsFlags, long fsRightsBase, long fsRightsInherting) {
        WasmMemoryLibrary memoryLib = WasmMemoryLibrary.getUncached();
        Fdstat.writeFsFiletype(node, memoryLib, memory, address, type);
        Fdstat.writeFsFlags(node, memoryLib, memory, address, fsFlags);
        Fdstat.writeFsRightsBase(node, memoryLib, memory, address, fsRightsBase);
        Fdstat.writeFsRightsInheriting(node, memoryLib, memory, address, fsRightsInherting);
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
        WasmMemoryLibrary memoryLib = WasmMemoryLibrary.getUncached();
        try {
            Filestat.writeFiletype(node, memoryLib, memory, address, getType(file));
            Filestat.writeSize(node, memoryLib, memory, address, file.getAttribute(TruffleFile.SIZE));
            Filestat.writeAtim(node, memoryLib, memory, address, file.getAttribute(TruffleFile.LAST_ACCESS_TIME).to(TimeUnit.NANOSECONDS));
            Filestat.writeMtim(node, memoryLib, memory, address, file.getAttribute(TruffleFile.LAST_MODIFIED_TIME).to(TimeUnit.NANOSECONDS));

            try {
                Filestat.writeDev(node, memoryLib, memory, address, file.getAttribute(TruffleFile.UNIX_DEV));
                Filestat.writeIno(node, memoryLib, memory, address, file.getAttribute(TruffleFile.UNIX_INODE));
                Filestat.writeNlink(node, memoryLib, memory, address, file.getAttribute(TruffleFile.UNIX_NLINK));
                Filestat.writeCtim(node, memoryLib, memory, address, file.getAttribute(TruffleFile.UNIX_CTIME).to(TimeUnit.NANOSECONDS));
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

    static int writeDirent(Node node, WasmMemory memory, int address, TruffleFile file, int nameLength, long offset) throws IOException {
        WasmMemoryLibrary memoryLib = WasmMemoryLibrary.getUncached();
        Dirent.writeDNext(node, memoryLib, memory, address, offset);
        try {
            Dirent.writeDIno(node, memoryLib, memory, address, file.getAttribute(TruffleFile.UNIX_INODE));
        } catch (UnsupportedOperationException e) {
            // GR-29297: these attributes are currently not supported on non-Unix platforms.
        }
        Dirent.writeDNamlen(node, memoryLib, memory, address, nameLength);
        Dirent.writeDType(node, memoryLib, memory, address, FdUtils.getType(file));
        return Dirent.BYTES;
    }

    static byte[] writeDirentToByteArray(TruffleFile file, int nameLength, long offset) throws IOException {
        byte[] buffer = new byte[Dirent.BYTES];
        Dirent.writeDNextToByteArray(buffer, 0, offset);
        try {
            Dirent.writeDInoToByteArray(buffer, 0, file.getAttribute(TruffleFile.UNIX_INODE));
        } catch (UnsupportedOperationException e) {
            // GR-29297: these attributes are currently not supported on non-Unix platforms.
        }
        Dirent.writeDNamlen(buffer, 0, nameLength);
        Dirent.writeDType(buffer, 0, FdUtils.getType(file));
        return buffer;
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

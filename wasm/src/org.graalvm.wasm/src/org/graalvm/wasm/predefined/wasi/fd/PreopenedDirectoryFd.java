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
import org.graalvm.wasm.predefined.wasi.types.Preopentype;
import org.graalvm.wasm.predefined.wasi.types.Prestat;
import org.graalvm.wasm.predefined.wasi.types.PrestatDir;
import org.graalvm.wasm.predefined.wasi.types.Rights;

import static org.graalvm.wasm.predefined.wasi.FlagUtils.allFlagsSet;

/**
 * File descriptor representing a pre-opened directory.
 */
final class PreopenedDirectoryFd extends DirectoryFd {

    private static final long FS_RIGHTS_BASE = allFlagsSet(Rights.class);
    private static final long FS_RIGHTS_INHERITING = FS_RIGHTS_BASE;
    private static final short FS_FLAGS = 0;

    private final String virtualPath;

    PreopenedDirectoryFd(FdManager fdManager, TruffleFile hostFile, TruffleFile virtualFile, String virtualPath) {
        super(fdManager, virtualFile, new PreopenedDirectory(hostFile, virtualFile), FS_RIGHTS_BASE, FS_RIGHTS_INHERITING, FS_FLAGS);
        this.virtualPath = virtualPath;
    }

    @Override
    public Errno prestatGet(Node node, WasmMemory memory, int prestatAddress) {
        Prestat.writeTag(node, memory, prestatAddress, Preopentype.Dir);
        final int prestatDirAddress = prestatAddress + Prestat.CONTENTSOFFSET;
        PrestatDir.writePrNameLen(node, memory, prestatDirAddress, WasmMemory.encodedStringLength(virtualPath));
        return Errno.Success;
    }

    @Override
    public Errno prestatDirName(Node node, WasmMemory memory, int pathAddress, int pathLength) {
        memory.writeString(node, virtualPath, pathAddress, pathLength);
        return Errno.Success;
    }

}

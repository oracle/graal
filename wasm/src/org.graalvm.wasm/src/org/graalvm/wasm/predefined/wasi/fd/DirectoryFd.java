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
import org.graalvm.wasm.predefined.wasi.types.Fdflags;
import org.graalvm.wasm.predefined.wasi.types.Filetype;
import org.graalvm.wasm.predefined.wasi.types.Lookupflags;
import org.graalvm.wasm.predefined.wasi.types.Oflags;
import org.graalvm.wasm.predefined.wasi.types.Rights;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

import static org.graalvm.wasm.predefined.wasi.FlagUtils.isSet;
import static org.graalvm.wasm.predefined.wasi.FlagUtils.isSubsetOf;

/**
 * File descriptor representing a directory.
 */
class DirectoryFd extends Fd {

    private final FdManager fdManager;
    private final PreopenedDirectory preopenedRoot;
    private final TruffleFile virtualFile;

    protected DirectoryFd(FdManager fdManager, TruffleFile virtualFile, PreopenedDirectory preopenedRoot, long fsRightsBase, long fsRightsInheriting, short fsFlags) {
        super(Filetype.Directory, fsRightsBase, fsRightsInheriting, fsFlags);
        this.fdManager = fdManager;
        this.virtualFile = virtualFile.normalize();
        this.preopenedRoot = preopenedRoot;
    }

    @Override
    public Errno pathOpen(Node node, WasmMemory memory, int dirFlags, int pathAddress, int pathLength, short childOflags, long childFsRightsBase, long childFsRightsInheriting, short childFdFlags,
                    int fdAddress) {
        // Check that the rights of the newly created fd are both a subset of fsRightsBase and
        // fsRightsInheriting.
        if (!isSet(fsRightsBase, Rights.PathOpen) || !isSubsetOf(childFsRightsBase, fsRightsBase) || !isSubsetOf(childFsRightsBase, fsRightsInheriting)) {
            return Errno.Notcapable;
        }

        final String path = memory.readString(pathAddress, pathLength, node);
        final TruffleFile virtualChildFile = preopenedRoot.containedVirtualFile(virtualFile.resolve(path));
        if (virtualChildFile == null) {
            return Errno.Notcapable;
        }

        TruffleFile hostChildFile = preopenedRoot.virtualFileToHostFile(virtualChildFile);
        if (hostChildFile == null) {
            return Errno.Notcapable;
        }

        if (isSet(dirFlags, Lookupflags.SymlinkFollow) && hostChildFile.exists()) {
            try {
                // Follow symbolic links, and make sure that the target is still contained in
                // preopenedRoot.
                hostChildFile = preopenedRoot.containedHostFile(hostChildFile.getCanonicalFile());
                if (hostChildFile == null) {
                    return Errno.Notcapable;
                }
            } catch (IOException e) {
                return Errno.Io;
            } catch (SecurityException e) {
                return Errno.Acces;
            }
        }

        // As they are non-null, virtualChildFile and hostChildFile are guaranteed to be
        // contained in preopenedRoot.

        if (isSet(childFdFlags, Fdflags.Rsync)) {
            // Not supported.
            return Errno.Inval;
        }

        if (isSet(childOflags, Oflags.Directory)) {
            if (hostChildFile.isDirectory()) {
                final int fd = fdManager.put(new DirectoryFd(fdManager, virtualChildFile, preopenedRoot, childFsRightsBase, childFsRightsInheriting, childFdFlags));
                memory.store_i32(node, fdAddress, fd);
                return Errno.Success;
            } else {
                return Errno.Notdir;
            }
        } else {
            try {
                final int fd = fdManager.put(new FileFd(hostChildFile, childOflags, childFsRightsBase, childFsRightsInheriting, childFdFlags));
                memory.store_i32(node, fdAddress, fd);
                return Errno.Success;
            } catch (FileAlreadyExistsException e) {
                return Errno.Exist;
            } catch (IOException | UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
                return Errno.Io;
            }
        }
    }

}

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

import static org.graalvm.wasm.predefined.wasi.FlagUtils.isSet;
import static org.graalvm.wasm.predefined.wasi.FlagUtils.isSubsetOf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.graalvm.wasm.predefined.wasi.WasiClockTimeGetNode;
import org.graalvm.wasm.predefined.wasi.types.Dirent;
import org.graalvm.wasm.predefined.wasi.types.Errno;
import org.graalvm.wasm.predefined.wasi.types.Fdflags;
import org.graalvm.wasm.predefined.wasi.types.Filetype;
import org.graalvm.wasm.predefined.wasi.types.Fstflags;
import org.graalvm.wasm.predefined.wasi.types.Lookupflags;
import org.graalvm.wasm.predefined.wasi.types.Oflags;
import org.graalvm.wasm.predefined.wasi.types.Rights;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.Node;

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

    private TruffleFile resolveVirtualFile(Node node, WasmMemory memory, int pathAddress, int pathLength) {
        final String path = memory.readString(pathAddress, pathLength, node);
        return preopenedRoot.containedVirtualFile(virtualFile.resolve(path));
    }

    private static TruffleFile resolveVirtualFile(Node node, WasmMemory memory, DirectoryFd fd, int pathAddress, int pathLength) {
        final String path = memory.readString(pathAddress, pathLength, node);
        return fd.preopenedRoot.containedVirtualFile(fd.virtualFile.resolve(path));
    }

    private TruffleFile resolveHostFile(Node node, WasmMemory memory, int pathAddress, int pathLength) {
        final TruffleFile virtualChildFile = resolveVirtualFile(node, memory, pathAddress, pathLength);
        if (virtualChildFile == null) {
            return null;
        }

        return preopenedRoot.virtualFileToHostFile(virtualChildFile);
    }

    private static TruffleFile resolveHostFile(Node node, WasmMemory memory, DirectoryFd fd, int pathAddress, int pathLength) {
        final TruffleFile virtualChildFile = resolveVirtualFile(node, memory, fd, pathAddress, pathLength);
        if (virtualChildFile == null) {
            return null;
        }
        return fd.preopenedRoot.virtualFileToHostFile(virtualChildFile);
    }

    private TruffleFile resolveHostFile(Node node, WasmMemory memory, int pathAddress, int pathLength, int lookupFlags) throws IOException, SecurityException {
        TruffleFile hostFile = resolveHostFile(node, memory, pathAddress, pathLength);
        if (hostFile == null) {
            return null;
        }

        if (isSet(lookupFlags, Lookupflags.SymlinkFollow) && hostFile.exists()) {
            // Follow symbolic links, and make sure that the target is still contained in
            // preopenedRoot.
            hostFile = preopenedRoot.containedHostFile(hostFile.getCanonicalFile());
        }
        return hostFile;
    }

    private TruffleFile resolveHostFile(TruffleFile virtualChildFile, int lookupFlags) throws IOException, SecurityException {
        if (virtualChildFile == null) {
            return null;
        }
        TruffleFile hostFile = preopenedRoot.virtualFileToHostFile(virtualChildFile);
        if (hostFile == null) {
            return null;
        }
        if (isSet(lookupFlags, Lookupflags.SymlinkFollow) && hostFile.exists()) {
            // Follow symbolic links, and make sure that the target is still contained in
            // preopenedRoot.
            hostFile = preopenedRoot.containedHostFile(hostFile.getCanonicalFile());
        }
        return hostFile;
    }

    @Override
    public Errno filestatGet(Node node, WasmMemory memory, int resultAddress) {
        if (!isSet(fsRightsBase, Rights.FdFilestatGet)) {
            return Errno.Notcapable;
        }
        return FdUtils.writeFilestat(node, memory, resultAddress, virtualFile);
    }

    @Override
    public Errno pathCreateDirectory(Node node, WasmMemory memory, int pathAddress, int pathLength) {
        if (!isSet(fsRightsBase, Rights.PathCreateDirectory)) {
            return Errno.Notcapable;
        }

        final TruffleFile hostChildFile = resolveHostFile(node, memory, pathAddress, pathLength);
        if (hostChildFile == null) {
            return Errno.Noent;
        }
        try {
            hostChildFile.createDirectory();
        } catch (FileAlreadyExistsException e) {
            return Errno.Exist;
        } catch (IOException | UnsupportedOperationException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        return Errno.Success;
    }

    @Override
    public Errno pathFilestatGet(Node node, WasmMemory memory, int flags, int pathAddress, int pathLength, int resultAddress) {
        if (!isSet(fsRightsBase, Rights.PathFilestatGet)) {
            return Errno.Notcapable;
        }

        final TruffleFile hostChildFile;
        try {
            hostChildFile = resolveHostFile(node, memory, pathAddress, pathLength, flags);
        } catch (IOException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        if (hostChildFile == null) {
            return Errno.Noent;
        }
        return FdUtils.writeFilestat(node, memory, resultAddress, hostChildFile);
    }

    @Override
    public Errno pathFilestatSetTimes(Node node, WasmMemory memory, int flags, int pathAddress, int pathLength, long atim, long mtim, int fstFlags) {
        if (!isSet(fsRightsBase, Rights.PathFilestatSetTimes)) {
            return Errno.Notcapable;
        }
        final TruffleFile hostChildFile;
        try {
            hostChildFile = resolveHostFile(node, memory, pathAddress, pathLength, flags);
        } catch (IOException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        if (hostChildFile == null) {
            return Errno.Noent;
        }
        try {
            if (isSet(fstFlags, Fstflags.Atim)) {
                hostChildFile.setLastAccessTime(FileTime.from(atim, TimeUnit.NANOSECONDS));
            }
            if (isSet(fstFlags, Fstflags.AtimNow)) {
                hostChildFile.setLastAccessTime(FileTime.from(WasiClockTimeGetNode.realtimeNow(), TimeUnit.NANOSECONDS));
            }
            if (isSet(fstFlags, Fstflags.Mtim)) {
                hostChildFile.setLastModifiedTime(FileTime.from(mtim, TimeUnit.NANOSECONDS));
            }
            if (isSet(fstFlags, Fstflags.MtimNow)) {
                hostChildFile.setLastModifiedTime(FileTime.from(WasiClockTimeGetNode.realtimeNow(), TimeUnit.NANOSECONDS));
            }
        } catch (IOException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        return Errno.Success;
    }

    @Override
    public Errno pathLink(Node node, WasmMemory memory, int oldFlags, int oldPathAddress, int oldPathLength, Fd newFd, int newPathAddress, int newPathLength) {
        if (!isSet(fsRightsBase, Rights.PathLinkSource) || !isSet(newFd.fsRightsBase, Rights.PathLinkTarget)) {
            return Errno.Notcapable;
        }

        final TruffleFile oldHostChildFile;
        try {
            oldHostChildFile = resolveHostFile(node, memory, oldPathAddress, oldPathLength, oldFlags);
        } catch (IOException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        if (oldHostChildFile == null) {
            return Errno.Noent;
        }

        if (!(newFd instanceof DirectoryFd)) {
            return Errno.Notdir;
        }
        final TruffleFile newHostChildFile = resolveHostFile(node, memory, (DirectoryFd) newFd, newPathAddress, newPathLength);
        if (newHostChildFile == null) {
            return Errno.Noent;
        }
        try {
            newHostChildFile.createLink(oldHostChildFile);
        } catch (FileAlreadyExistsException e) {
            return Errno.Exist;
        } catch (IOException | UnsupportedOperationException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        return Errno.Success;
    }

    @Override
    public Errno pathOpen(Node node, WasmMemory memory, int dirFlags, int pathAddress, int pathLength, short childOflags, long childFsRightsBase, long childFsRightsInheriting, short childFdFlags,
                    int fdAddress) {
        // Check that the rights of the newly created fd and any derived fd are both a subset of
        // fsRightsInheriting. Note that childFsRightsInheriting is not necessarily a subset of
        // childFsRightsBase. See the javadoc for Fd#fsRightsInheriting.
        if (!isSet(fsRightsBase, Rights.PathOpen) || !isSubsetOf(childFsRightsBase, fsRightsInheriting) || !isSubsetOf(childFsRightsInheriting, fsRightsInheriting)) {
            return Errno.Notcapable;
        }

        final TruffleFile virtualChildFile = resolveVirtualFile(node, memory, pathAddress, pathLength);
        if (virtualChildFile == null) {
            return Errno.Noent;
        }

        final TruffleFile hostChildFile;
        try {
            hostChildFile = resolveHostFile(virtualChildFile, dirFlags);
        } catch (IOException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        if (hostChildFile == null) {
            return Errno.Noent;
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
                WasmMemoryLibrary.getUncached().store_i32(memory, node, fdAddress, fd);
                return Errno.Success;
            } else {
                return Errno.Notdir;
            }
        } else {
            try {
                final int fd = fdManager.put(new FileFd(hostChildFile, childOflags, childFsRightsBase, childFsRightsInheriting, childFdFlags));
                WasmMemoryLibrary.getUncached().store_i32(memory, node, fdAddress, fd);
                return Errno.Success;
            } catch (FileAlreadyExistsException e) {
                return Errno.Exist;
            } catch (IOException | UnsupportedOperationException e) {
                return Errno.Io;
            } catch (IllegalArgumentException e) {
                return Errno.Inval;
            } catch (SecurityException e) {
                return Errno.Acces;
            }
        }
    }

    @Override
    public Errno readdir(Node node, WasmMemory memory, int bufAddress, int bufLength, long cookie, int sizeAddress) {
        if (!isSet(fsRightsBase, Rights.FdReaddir)) {
            return Errno.Notcapable;
        }
        try {
            Collection<TruffleFile> children = virtualFile.list();
            List<TruffleFile> entries = new ArrayList<>(children.size() + 2);
            entries.add(virtualFile.resolve("."));
            entries.add(virtualFile.resolve(".."));
            entries.addAll(children);

            int bufPointer = bufAddress;
            int bufEnd = bufAddress + bufLength;
            long currentEntry = 0;

            WasmMemoryLibrary memories = WasmMemoryLibrary.getUncached();

            for (TruffleFile file : entries) {
                // Only write entries whose index is past the received "cookie"
                if (currentEntry >= cookie) {
                    byte[] name = file.getName().getBytes(StandardCharsets.UTF_8);

                    if (bufEnd - bufPointer >= Dirent.BYTES) {
                        bufPointer += FdUtils.writeDirent(node, memory, bufPointer, file, name.length, currentEntry + 1);
                    } else {
                        // Write dirent to temp buffer and truncate
                        byte[] dirent = FdUtils.writeDirentToByteArray(file, name.length, currentEntry + 1);
                        for (int i = 0; bufPointer < bufEnd; i++, bufPointer++) {
                            assert i < dirent.length;
                            memories.store_i32_8(memory, node, bufPointer, dirent[i]);
                        }
                        assert bufPointer == bufEnd;
                        break;
                    }

                    if (bufEnd - bufPointer >= name.length) {
                        bufPointer += memory.writeString(node, file.getName(), bufPointer);
                    } else {
                        // Truncate file name
                        for (int i = 0; bufPointer < bufEnd; i++, bufPointer++) {
                            assert i < name.length;
                            memories.store_i32_8(memory, node, bufPointer, name[i]);
                        }
                        assert bufPointer == bufEnd;
                        break;
                    }
                }
                currentEntry++;
            }
            memories.store_i32(memory, node, sizeAddress, bufPointer - bufAddress);
        } catch (IOException e) {
            return Errno.Io;
        }
        return Errno.Success;
    }

    @Override
    public int pathReadLink(Node node, WasmMemory memory, int pathAddress, int pathLength, int buf, int bufLen, int sizeAddress) {
        if (!isSet(fsRightsBase, Rights.PathReadlink)) {
            return Errno.Notcapable.ordinal();
        }
        final TruffleFile hostChildFile = resolveHostFile(node, memory, pathAddress, pathLength);
        if (hostChildFile == null) {
            return Errno.Noent.ordinal();
        }
        try {
            final TruffleFile link = hostChildFile.readSymbolicLink();
            final TruffleFile virtualLink = preopenedRoot.hostFileToVirtualFile(link);
            if (virtualLink == null) {
                return Errno.Noent.ordinal();
            }
            final String content = virtualLink.getPath();
            int bytesWritten = memory.writeString(node, content, buf, bufLen);
            WasmMemoryLibrary.getUncached().store_i32(memory, node, sizeAddress, bytesWritten);
            return Errno.Success.ordinal();
        } catch (NotLinkException e) {
            return Errno.Nolink.ordinal();
        } catch (IOException | UnsupportedOperationException e) {
            return Errno.Io.ordinal();
        } catch (SecurityException e) {
            return Errno.Acces.ordinal();
        }
    }

    @Override
    public Errno pathRemoveDirectory(Node node, WasmMemory memory, int pathAddress, int pathLength) {
        if (!isSet(fsRightsBase, Rights.PathRemoveDirectory)) {
            return Errno.Notcapable;
        }
        final TruffleFile hostChildFile = resolveHostFile(node, memory, pathAddress, pathLength);
        if (hostChildFile == null) {
            return Errno.Noent;
        }
        if (!hostChildFile.isDirectory()) {
            return Errno.Notdir;
        }
        try {
            hostChildFile.delete();
        } catch (DirectoryNotEmptyException e) {
            return Errno.Notempty;
        } catch (NoSuchFileException e) {
            return Errno.Noent;
        } catch (IOException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        return Errno.Success;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public Errno pathRename(Node node, WasmMemory memory, int oldPathAddress, int oldPathLength, Fd newFd, int newPathAddress, int newPathLength) {
        if (!isSet(fsRightsBase, Rights.PathRenameSource) || !isSet(newFd.fsRightsBase, Rights.PathRenameTarget)) {
            return Errno.Notcapable;
        }
        final TruffleFile oldHostChildFile = resolveHostFile(node, memory, oldPathAddress, oldPathLength);
        if (oldHostChildFile == null) {
            return Errno.Noent;
        }

        if (!(newFd instanceof DirectoryFd)) {
            return Errno.Notdir;
        }

        final TruffleFile newHostChildFile = resolveHostFile(node, memory, (DirectoryFd) newFd, newPathAddress, newPathLength);
        if (newHostChildFile == null) {
            return Errno.Noent;
        }

        try {
            oldHostChildFile.move(newHostChildFile);
        } catch (FileAlreadyExistsException e) {
            return Errno.Exist;
        } catch (IOException | UnsupportedOperationException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        return Errno.Success;
    }

    @Override
    public Errno pathSymlink(Node node, WasmMemory memory, int oldPathAddress, int oldPathLength, int newPathAddress, int newPathLength) {
        if (!isSet(fsRightsBase, Rights.PathSymlink)) {
            return Errno.Notcapable;
        }

        final TruffleFile oldHostChildFile = resolveHostFile(node, memory, oldPathAddress, oldPathLength);
        if (oldHostChildFile == null) {
            return Errno.Noent;
        }

        final TruffleFile newHostChildFile = resolveHostFile(node, memory, newPathAddress, newPathLength);
        if (newHostChildFile == null) {
            return Errno.Noent;
        }

        try {
            newHostChildFile.createSymbolicLink(oldHostChildFile);
        } catch (FileAlreadyExistsException e) {
            return Errno.Exist;
        } catch (IOException | UnsupportedOperationException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        return Errno.Success;
    }

    @Override
    public Errno pathUnlinkFile(Node node, WasmMemory memory, int pathAddress, int pathLength) {
        if (!isSet(fsRightsBase, Rights.PathUnlinkFile)) {
            return Errno.Notcapable;
        }

        final TruffleFile hostChildFile = resolveHostFile(node, memory, pathAddress, pathLength);
        if (hostChildFile == null) {
            return Errno.Noent;
        }
        if (hostChildFile.isDirectory()) {
            return Errno.Isdir;
        }
        try {
            hostChildFile.delete();
        } catch (NoSuchFileException e) {
            return Errno.Noent;
        } catch (IOException e) {
            return Errno.Io;
        } catch (SecurityException e) {
            return Errno.Acces;
        }
        return Errno.Success;
    }
}

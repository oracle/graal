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
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.wasi.types.Errno;
import org.graalvm.wasm.predefined.wasi.types.Fdflags;
import org.graalvm.wasm.predefined.wasi.types.Filetype;
import org.graalvm.wasm.predefined.wasi.types.Oflags;
import org.graalvm.wasm.predefined.wasi.types.Rights;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.graalvm.wasm.predefined.wasi.FlagUtils.isSet;

/**
 * File descriptor representing a file ({@link TruffleFile}).
 */
class FileFd extends SeekableByteChannelFd {

    private final TruffleFile file;
    private final short oflags;

    /**
     * @throws FileAlreadyExistsException if {@link StandardOpenOption#CREATE_NEW} option is set and
     *             a file already exists on given path
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the attributes contain an attribute which cannot be
     *             set atomically
     * @throws IllegalArgumentException in case of invalid options combination
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @see TruffleFile#newByteChannel(Set, FileAttribute[])
     */
    FileFd(TruffleFile file, short oflags, long fsRightsBase, long fsRightsInheriting, short fdFlags) throws IOException {
        super(file.newByteChannel(parseOptions(oflags, fdFlags)), Filetype.RegularFile, fsRightsBase, fsRightsInheriting, fdFlags);
        this.file = file;
        this.oflags = oflags;
    }

    private static Set<? extends OpenOption> parseOptions(short oflags, short fdFlags) {
        final Set<OpenOption> openOptions = new LinkedHashSet<>();

        // In WASI, the file access mode (read vs. write) is not specified when opening a file.
        // Therefore, we set both READ and WRITE so that the opened file can be used for both
        // reading and writing.
        openOptions.add(StandardOpenOption.READ);
        openOptions.add(StandardOpenOption.WRITE);
        if (isSet(oflags, Oflags.Creat)) {
            openOptions.add(StandardOpenOption.CREATE);
        }
        assert !isSet(oflags, Oflags.Directory);
        if (isSet(oflags, Oflags.Excl)) {
            openOptions.add(StandardOpenOption.CREATE_NEW);
        }
        if (isSet(oflags, Oflags.Trunc)) {
            openOptions.add(StandardOpenOption.TRUNCATE_EXISTING);
        }

        if (isSet(fdFlags, Fdflags.Append)) {
            openOptions.add(StandardOpenOption.APPEND);
            // If TRUNCATE_EXISTING is already set, the creation of the ByteChannel will throw an
            // IllegalArgumentException, which will be caught in DirectoryFd#pathOpen and converted
            // to the IO error number.

            // GR-29268: Java NIO does not allow setting both APPEND and READ flags. Therefore, we
            // remove the READ flag if APPEND is set. The effect is that reading will result in an
            // error if the APPEND flag is set. It seems however that there is nothing in WASI or
            // POSIX preventing from reading from a file descriptor opened with the APPEND flag, so
            // this might be a bug.
            // TODO: check what is the behavior in other Wasm engines and POSIX, and fix if needed.
            openOptions.remove(StandardOpenOption.READ);
        }
        if (isSet(fdFlags, Fdflags.Dsync)) {
            openOptions.add(StandardOpenOption.DSYNC);
        }
        if (isSet(fdFlags, Fdflags.Nonblock)) {
            // This flag is currently underspecified in WASI. In POSIX, its behavior is only
            // specified for FIFO and "block special or character special files that support
            // non-blocking opens". It doesn't apply here as we are opening a normal file.
            // See https://pubs.opengroup.org/onlinepubs/007904875/functions/open.html.
        }
        if (isSet(fdFlags, Fdflags.Sync)) {
            openOptions.add(StandardOpenOption.SYNC);
        }
        if (isSet(fdFlags, Fdflags.Rsync)) {
            // This flag is currently underspecified in WASI. In POSIX, support for this flag is
            // optional and Linux does not support it. Therefore, we do the same and return EINVAL
            // error in this case. See DirectoryFd#pathOpen method.
            throw new IllegalArgumentException("Rsync is not supported");
        }

        return openOptions;
    }

    @Override
    public Errno filestatGet(Node node, WasmMemory memory, int resultAddress) {
        if (!isSet(fsRightsBase, Rights.FdFilestatGet)) {
            return Errno.Notcapable;
        }
        return FdUtils.writeFilestat(node, memory, resultAddress, file);
    }

    @Override
    public Errno fdstatSetFlags(Node node, WasmMemory memory, short newFsflags) {
        if (!isSet(fsRightsBase, Rights.FdFdstatSetFlags)) {
            return Errno.Notcapable;
        }
        try {
            close();
            fdFlags = newFsflags;
            setChannel(file.newByteChannel(parseOptions(oflags, newFsflags)));
            return Errno.Success;
        } catch (IOException e) {
            return Errno.Io;
        }
    }

}

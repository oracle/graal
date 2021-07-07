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

import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.wasi.types.Errno;
import org.graalvm.wasm.predefined.wasi.types.Fdflags;
import org.graalvm.wasm.predefined.wasi.types.Filetype;
import org.graalvm.wasm.predefined.wasi.types.Lookupflags;
import org.graalvm.wasm.predefined.wasi.types.Oflags;
import org.graalvm.wasm.predefined.wasi.types.Rights;
import org.graalvm.wasm.predefined.wasi.types.Whence;

import java.io.Closeable;
import java.io.IOException;

import static org.graalvm.wasm.predefined.wasi.FlagUtils.isSet;
import static org.graalvm.wasm.predefined.wasi.FlagUtils.isSubsetOf;

/**
 * Generic class to represent all WASI file descriptors. Currently, these can be normal files,
 * streams like stdin and stdout, or directories.
 * <p>
 * Mapping from integers to {@link Fd} objects is handled by the {@link FdManager} class.
 *
 * <h2 id="preopened">Pre-opened directories</h2>
 * <p>
 * WASI API differs from POSIX in that it is capability-based, on a per-resource basis. One
 * implication is that it is not possible to obtain a file descriptor for any path. Instead,
 * {@link #pathOpen(Node, WasmMemory, int, int, int, short, long, long, short, int) path_open}
 * requires an existing parent file descriptor similarly to POSIX's {@code openat}. Therefore, in
 * order to do any IO, a program must be launched with a set of <em>pre-opened directories</em> from
 * which it is possible to open other directories or files.
 * <p>
 * By convention, pre-opened file descriptors have the numbers {@code 3} and onward. Therefore,
 * WebAssembly code can discover them by iterating through file descriptors and calling
 * {@link #prestatGet(Node, WasmMemory, int) prestat_get} repeatedly until it returns
 * {@link Errno#Badf}.
 * <p>
 * In WASI libc for example, this is done by the {@code __wasilibc_populate_preopens} (see <a href=
 * "https://github.com/WebAssembly/wasi-libc/blob/main/libc-bottom-half/sources/preopens.c">preopens.c</a>)
 * constructor that is called on module instantiation (the code is simplified):
 * 
 * <pre>
 * <code>// Skip stdin, stdout, and stderr, and count up until we reach an invalid
 * // file descriptor.
 * for (__wasi_fd_t fd = 3; fd != 0; ++fd) {
 *         __wasi_prestat_t prestat;
 *         // Get pre-opened info using {@link #prestatGet(Node, WasmMemory, int)  prestat_get}:
 *         __wasi_errno_t ret = __wasi_fd_prestat_get(fd, &prestat);
 *         if (ret == __WASI_ERRNO_BADF) break;
 *         switch (prestat.tag) {
 *         case __WASI_PREOPENTYPE_DIR: {
 *             // Get pre-opened path (prefix) using {@link #prestatDirName(Node, WasmMemory, int, int) prestat_dir_name}:
 *             __wasi_fd_prestat_dir_name(fd, (uint8_t *)prefix, prestat.u.dir.pr_name_len);
 *             // Register fd as a pre-opened file descriptor with the given path
 *             internal_register_preopened_fd(fd, prefix) != 0
 *             break;
 *         }
 *         default:
 *             break;
 *         }
 * }</code>
 * </pre>
 */
public abstract class Fd implements Closeable {

    /**
     * WASI file type.
     */
    protected final Filetype type;

    /**
     * Rights that will apply to operations using this file descriptor.
     */
    protected long fsRightsBase;

    /**
     * Rights that will apply to file descriptors derived from this.
     * <p>
     * This is <strong>not</strong> a subset of {@link #fsRightsBase}. This is an additional
     * constraint. Rights of derived file descriptors are subsets of or equal to
     * <code>({@link #fsRightsInheriting} & {@link #fsRightsBase})</code>.
     */
    protected long fsRightsInheriting;

    /**
     * Bitmap of {@link Fdflags}.
     */
    protected short fdFlags;

    /**
     * Constructor to be called by child classes to set common attributes.
     *
     * @param type see {@link #type}
     * @param fsRightsBase see {@link #fsRightsBase}
     * @param fsRightsInheriting see {@link #fsRightsInheriting}
     * @param fdFlags see {@link #fdFlags}
     */
    protected Fd(Filetype type, long fsRightsBase, long fsRightsInheriting, short fdFlags) {
        this.type = type;
        this.fsRightsBase = fsRightsBase;
        this.fsRightsInheriting = fsRightsInheriting;
        this.fdFlags = fdFlags;
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#path_open"><code>path_open</code></a>:
     * opens a file or directory that is a child of the directory represented by this file
     * descriptor.
     * <p>
     * Similar to POSIX <a href="https://linux.die.net/man/2/openat"><code>openat</code></a>.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param dirflags bitmap of {@link Lookupflags}
     * @param pathAddress {@code u8*}: start address of the path encoded in UTF-8
     * @param pathLength length of the path, in bytes, without trailing null character
     * @param oflags bitmap of {@link Oflags}
     * @param childFsRightsBase bitmap of {@link Rights}: initial rights of the newly created file
     *            descriptor, see {@link #fsRightsBase}
     * @param childFsRightsInheriting bitmap of {@link Rights}: rights that will be inherited when
     *            opening file descriptors from the newly created file descriptor, see
     *            {@link #fsRightsInheriting}
     * @param fdflags bitmap of {@link Fdflags}
     * @param fdAddress {@code u32*}: the address at which to write the newly created file
     *            descriptor number
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     * @see <a href=
     *      "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#path_open">WASI
     *      <code>path_open</code> documentation</a>
     */
    public Errno pathOpen(Node node, WasmMemory memory, int dirflags, int pathAddress, int pathLength, short oflags, long childFsRightsBase, long childFsRightsInheriting, short fdflags,
                    int fdAddress) {
        if (!isSet(fsRightsBase, Rights.PathOpen) || !isSubsetOf(childFsRightsBase, fsRightsBase) || !isSubsetOf(childFsRightsBase, fsRightsInheriting)) {
            return Errno.Notcapable;
        }
        return Errno.Acces;
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#fd_write"><code>fd_write</code></a>:
     * writes {@code iovecCount} buffers of data described by {@code iovecArrayAddress} to the file
     * represented by this file descriptor.
     * <p>
     * Similar to POSIX <a href="https://linux.die.net/man/2/writev"><code>writev</code></a>.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param iovecArrayAddress {@code {buf: u8*, buf_len: u32}*}: start address of an array of
     *            <a href=
     *            "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-iovec-struct"><code>iovc</code></a>
     * @param iovecCount number of <a href=
     *            "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-iovec-struct"><code>iovc</code></a>
     * @param sizeAddress {@code u32*}: the address at which to write the number of bytes written
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     */
    public Errno write(Node node, WasmMemory memory, int iovecArrayAddress, int iovecCount, int sizeAddress) {
        if (!isSet(fsRightsBase, Rights.FdWrite)) {
            return Errno.Notcapable;
        }
        return Errno.Acces;
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#fd_read"><code>fd_read</code></a>:
     * reads {@code iovecCount} buffers of data described by {@code iovecArrayAddress} from the file
     * represented by this file descriptor.
     * <p>
     * Similar to POSIX <a href="https://linux.die.net/man/2/readv"><code>readv</code></a>.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param iovecArrayAddress {@code {buf: u8*, buf_len: u32}*}: start address of an array of
     *            <a href=
     *            "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-iovec-struct"><code>iovc</code></a>
     * @param iovecCount number of <a href=
     *            "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-iovec-struct"><code>iovc</code></a>
     * @param sizeAddress {@code u32*}: the address at which to write the number of bytes written
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     */
    public Errno read(Node node, WasmMemory memory, int iovecArrayAddress, int iovecCount, int sizeAddress) {
        if (!isSet(fsRightsBase, Rights.FdRead)) {
            return Errno.Notcapable;
        }
        return Errno.Acces;
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#fd_seek"><code>fd_seek</code></a>:
     * moves the offset of this file descriptor.
     * <p>
     * Similar to POSIX <a href="https://linux.die.net/man/2/lseek"><code>lseek</code></a>.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param offset the number of bytes to move
     * @param whence the base from which the {@code offset} is relative
     * @param newOffsetAddress {@code u32*}: the address at which to write the new offset of this
     *            file descriptor, relative to the start of the file.
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     */
    public Errno seek(Node node, WasmMemory memory, long offset, Whence whence, int newOffsetAddress) {
        if (!isSet(fsRightsBase, Rights.FdSeek)) {
            return Errno.Notcapable;
        }
        return Errno.Acces;
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#fd_fdstat_get"><code>fd_fdstat_get</code></a>:
     * gets the <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#fdstat"><code>fdstat</code></a>
     * attributes of this file descriptor.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param resultAddress {@code fdstat*}: address at which to write the <a href=
     *            "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#fdstat"><code>fdstat</code></a>
     *            structure
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     */
    public Errno fdstatGet(Node node, WasmMemory memory, int resultAddress) {
        // There is no right needed to call this method.
        return FdUtils.writeFdstat(node, memory, resultAddress, type, fdFlags, fsRightsBase, fsRightsInheriting);
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-fd_fdstat_set_flagsfd-fd-flags-fdflags---errno"><code>fd_fdstat_set_flags</code></a>:
     * adjusts the flags associated with this file descriptor.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param fdflags bitmap of {@link Fdflags}
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     */
    public Errno fdstatSetFlags(Node node, WasmMemory memory, short fdflags) {
        if (!isSet(fsRightsBase, Rights.FdFdstatSetFlags)) {
            return Errno.Notcapable;
        }
        return Errno.Acces;
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#fd_filestat_get"><code>fd_filestat_get</code></a>:
     * gets the <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#filestat"><code>filestat</code></a>
     * attributes of this file descriptor.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param resultAddress {@code filestat*}: address at which to write the <a href=
     *            "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#filestat"><code>filestat</code></a>
     *            structure
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     */
    public Errno filestatGet(Node node, WasmMemory memory, int resultAddress) {
        if (!isSet(fsRightsBase, Rights.FdFilestatGet)) {
            return Errno.Notcapable;
        }
        return Errno.Acces;
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#-fd_prestat_getfd-fd---errno-prestat"><code>fd_prestat_get</code></a>:
     * returns a description of this preopened file descriptor.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param resultAddress {@code filestat*}: address at which to write the <a href=
     *            "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#prestat"><code>prestat</code></a>
     *            structure
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     * @see <a href="#preopened">Pre-opened directories</a>
     */
    public Errno prestatGet(Node node, WasmMemory memory, int resultAddress) {
        // There is no right needed to call this method.
        return Errno.Acces;
    }

    /**
     * Implementation of WASI <a href=
     * "https://github.com/WebAssembly/WASI/blob/a206794fea66118945a520f6e0af3754cc51860b/phases/snapshot/docs.md#fd_prestat_dir_name"><code>fd_prestat_dir_name</code></a>:
     * return the path of this preopened file descriptor.
     *
     * @param node the calling node, used as location for any thrown {@link WasmException}
     * @param memory the {@link WasmMemory} from which to read and write
     * @param pathAddress {@code u8*}: start address from which to write the path encoded in UTF-8
     * @param pathLength length of the path to get, in bytes, including the trailing null character
     * @return {@link Errno#Success} in case of success, or another {@link Errno} in case of error
     * @throws WasmException if an error happens while writing or reading to {@code memory}
     * @see <a href="#preopened">Pre-opened directories</a>
     */
    public Errno prestatDirName(Node node, WasmMemory memory, int pathAddress, int pathLength) {
        // There is no right needed to call this method.
        return Errno.Acces;
    }

    @Override
    public void close() throws IOException {
    }

}

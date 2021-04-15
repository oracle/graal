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

/*
 * This file has been automatically generated from wasi_snapshot_preview1.witx.
 */

package org.graalvm.wasm.predefined.wasi.types;

/** File descriptor rights, determining which actions may be performed. */
public enum Rights {

    /**
     * 0: The right to invoke {@code fd_datasync}. If {@code path_open} is set, includes the right
     * to invoke {@code path_open} with {@code fdflags::dsync}.
     */
    FdDatasync,

    /**
     * 1: The right to invoke {@code fd_read} and {@code sock_recv}. If {@code rights::fd_seek} is
     * set, includes the right to invoke {@code fd_pread}.
     */
    FdRead,

    /** 2: The right to invoke {@code fd_seek}. This flag implies {@code rights::fd_tell}. */
    FdSeek,

    /** 3: The right to invoke {@code fd_fdstat_set_flags}. */
    FdFdstatSetFlags,

    /**
     * 4: The right to invoke {@code fd_sync}. If {@code path_open} is set, includes the right to
     * invoke {@code path_open} with {@code fdflags::rsync} and {@code fdflags::dsync}.
     */
    FdSync,

    /**
     * 5: The right to invoke {@code fd_seek} in such a way that the file offset remains unaltered
     * (i.e., {@code whence::cur} with offset zero), or to invoke {@code fd_tell}.
     */
    FdTell,

    /**
     * 6: The right to invoke {@code fd_write} and {@code sock_send}. If {@code rights::fd_seek} is
     * set, includes the right to invoke {@code fd_pwrite}.
     */
    FdWrite,

    /** 7: The right to invoke {@code fd_advise}. */
    FdAdvise,

    /** 8: The right to invoke {@code fd_allocate}. */
    FdAllocate,

    /** 9: The right to invoke {@code path_create_directory}. */
    PathCreateDirectory,

    /**
     * 10: If {@code path_open} is set, the right to invoke {@code path_open} with
     * {@code oflags::creat}.
     */
    PathCreateFile,

    /**
     * 11: The right to invoke {@code path_link} with the file descriptor as the source directory.
     */
    PathLinkSource,

    /**
     * 12: The right to invoke {@code path_link} with the file descriptor as the target directory.
     */
    PathLinkTarget,

    /** 13: The right to invoke {@code path_open}. */
    PathOpen,

    /** 14: The right to invoke {@code fd_readdir}. */
    FdReaddir,

    /** 15: The right to invoke {@code path_readlink}. */
    PathReadlink,

    /**
     * 16: The right to invoke {@code path_rename} with the file descriptor as the source directory.
     */
    PathRenameSource,

    /**
     * 17: The right to invoke {@code path_rename} with the file descriptor as the target directory.
     */
    PathRenameTarget,

    /** 18: The right to invoke {@code path_filestat_get}. */
    PathFilestatGet,

    /**
     * 19: The right to change a file's size (there is no {@code path_filestat_set_size}). If
     * {@code path_open} is set, includes the right to invoke {@code path_open} with
     * {@code oflags::trunc}.
     */
    PathFilestatSetSize,

    /** 20: The right to invoke {@code path_filestat_set_times}. */
    PathFilestatSetTimes,

    /** 21: The right to invoke {@code fd_filestat_get}. */
    FdFilestatGet,

    /** 22: The right to invoke {@code fd_filestat_set_size}. */
    FdFilestatSetSize,

    /** 23: The right to invoke {@code fd_filestat_set_times}. */
    FdFilestatSetTimes,

    /** 24: The right to invoke {@code path_symlink}. */
    PathSymlink,

    /** 25: The right to invoke {@code path_remove_directory}. */
    PathRemoveDirectory,

    /** 26: The right to invoke {@code path_unlink_file}. */
    PathUnlinkFile,

    /**
     * 27: If {@code rights::fd_read} is set, includes the right to invoke {@code poll_oneoff} to
     * subscribe to {@code eventtype::fd_read}. If {@code rights::fd_write} is set, includes the
     * right to invoke {@code poll_oneoff} to subscribe to {@code eventtype::fd_write}.
     */
    PollFdReadwrite,

    /** 28: The right to invoke {@code sock_shutdown}. */
    SockShutdown;

}

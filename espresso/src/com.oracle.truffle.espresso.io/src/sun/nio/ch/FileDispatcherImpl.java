/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.SelectableChannel;

/**
 * Low-level implementation of {@link sun.nio.ch.FileDispatcher} for the Truffle VFS.
 *
 * <p>
 * This class is injected early on the guest VM, replacing the platform's implementation. File
 * descriptors used this class are purely virtual and should not be passed down to native code.
 *
 * <p>
 * This file must be compatible with 21+
 */
final class FileDispatcherImpl extends sun.nio.ch.FileDispatcher {

    static {
        sun.nio.ch.IOUtil.load();
    }

    @Override
    long seek(FileDescriptor fd, long offset) throws IOException {
        return seek0(fd, offset);
    }

    @Override
    int force(FileDescriptor fd, boolean metaData) throws IOException {
        return force0(fd, metaData);
    }

    @Override
    int truncate(FileDescriptor fd, long size) throws IOException {
        return truncate0(fd, size);
    }

    @Override
    long size(FileDescriptor fd) throws IOException {
        return size0(fd);
    }

    @Override
    int lock(FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException {
        return lock0(fd, blocking, pos, size, shared);
    }

    @Override
    void release(FileDescriptor fd, long pos, long size) throws IOException {
        release0(fd, pos, size);
    }

    @Override
    FileDescriptor duplicateForMapping(FileDescriptor fd) throws IOException {
        return new FileDescriptor();
    }

    @Override
    boolean canTransferToDirectly(SelectableChannel sc) {
        return false;
    }

    @Override
    boolean transferToDirectlyNeedsPositionLock() {
        return false;
    }

    @Override
    boolean canTransferToFromOverlappedMap() {
        return false;
    }

    @Override
    int setDirectIO(FileDescriptor fd, String path) {
        int result = -1;
        try {
            result = setDirect0(fd, path);
        } catch (IOException e) {
            throw new UnsupportedOperationException("Error setting up DirectIO", e);
        }
        return result;
    }

    @Override
    int read(FileDescriptor fd, long address, int len) throws IOException {
        return read0(fd, address, len);
    }

    @Override
    long readv(FileDescriptor fd, long address, int len) throws IOException {
        return readv0(fd, address, len);
    }

    @Override
    int write(FileDescriptor fd, long address, int len) throws IOException {
        return write0(fd, address, len);
    }

    @Override
    long writev(FileDescriptor fd, long address, int len) throws IOException {
        return writev0(fd, address, len);
    }

    @Override
    void close(FileDescriptor fd) throws IOException {
        close0(fd);
    }

    @Override
    int pread(FileDescriptor fd, long address, int len, long position) throws IOException {
        return pread0(fd, address, len, position);
    }

    @Override
    int pwrite(FileDescriptor fd, long address, int len, long position) throws IOException {
        return pwrite0(fd, address, len, position);
    }

    @Override
    boolean needsPositionLock() {
        return true; // be conservative
    }

    @Override
    void dup(FileDescriptor fd1, FileDescriptor fd2) throws IOException {
        dup0(fd1, fd2);
    }

    @Override
    long allocationGranularity() {
        return allocationGranularity0();
    }

    @Override
    long map(FileDescriptor fd, int prot, long position, long length, boolean isSync) throws IOException {
        return map0(fd, prot, position, length, isSync);
    }

    @Override
    int unmap(long address, long length) {
        return unmap0(address, length);
    }

    @Override
    int maxDirectTransferSize() {
        return maxDirectTransferSize0();
    }

    @Override
    long transferTo(FileDescriptor src, long position, long count, FileDescriptor dst, boolean append) {
        return transferTo0(src, position, count, dst, append);
    }

    @Override
    long transferFrom(FileDescriptor src, FileDescriptor dst, long position, long count, boolean append) {
        return transferFrom0(src, dst, position, count, append);
    }

    int available(FileDescriptor fd) throws IOException {
        return available0(fd);
    }

    boolean isOther(FileDescriptor fd) throws IOException {
        return isOther0(fd);
    }

    // region native methods

    private static native long allocationGranularity0();

    private static native long map0(FileDescriptor fd, int prot, long position, long length, boolean isSync) throws IOException;

    private static native int unmap0(long address, long length);

    private static native int maxDirectTransferSize0();

    private static native long transferTo0(FileDescriptor src, long position, long count, FileDescriptor dst, boolean append);

    private static native long transferFrom0(FileDescriptor src, FileDescriptor dst, long position, long count, boolean append);

    private static native long seek0(FileDescriptor fd, long offset) throws IOException;

    private static native int force0(FileDescriptor fd, boolean metadata) throws IOException;

    private static native int truncate0(FileDescriptor fd, long size) throws IOException;

    private static native int lock0(FileDescriptor fd, boolean blocking, long pos, long size, boolean shared) throws IOException;

    private static native void release0(FileDescriptor fd, long pos, long size) throws IOException;

    private static native long size0(FileDescriptor fd) throws IOException;

    private static native int read0(FileDescriptor fd, long address, int len) throws IOException;

    private static native int readv0(FileDescriptor fd, long address, int len) throws IOException;

    private static native int write0(FileDescriptor fd, long address, int len) throws IOException;

    private static native int writev0(FileDescriptor fd, long address, int len) throws IOException;

    private static native void close0(FileDescriptor fd) throws IOException;

    private static native int pread0(FileDescriptor fd, long address, int len, long position) throws IOException;

    private static native int pwrite0(FileDescriptor fd, long address, int len, long position) throws IOException;

    private static native void dup0(FileDescriptor fd1, FileDescriptor fd2) throws IOException;

    private static native int setDirect0(FileDescriptor fd, String path) throws IOException;

    private static native int available0(FileDescriptor fd) throws IOException;

    private static native boolean isOther0(FileDescriptor fd) throws IOException;

    // endregion native methods
}

/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.svm.webimage.substitute.system;

import java.io.FileDescriptor;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/*
 * Checkstyle: stop method name check
 * Method names have to match the target class and are not under our control
 */

/**
 * Substitutions for Unix-specific implementation of IO classes.
 * <p>
 * May also contain linux- and mac-specific substitutions when necessary.
 */
public class WebImageUnixIOSubstitutions {
    // dummy
}

@TargetClass(className = "sun.nio.fs.UnixNativeDispatcher", onlyWith = IsUnix.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_UnixNativeDispatcher_Web {
    @Substitute
    static byte[] strerror(int errnum) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.strerror");
    }

    @Substitute
    static byte[] realpath0(long l) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.realpath0");
    }

    @Substitute
    static int openat(int i, byte[] b, int k, int l) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.openat");
    }

    @Substitute
    static int open0(long l, int i1, int i2) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.open0");
    }

    @Substitute
    static void unlinkat(int i, byte[] b, int i2) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.unlinkat");
    }

    @Substitute
    static void unlink0(long l) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.unlink");
    }

    @Substitute
    static void fchmod(int fd, int mode) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.fchmod");
    }

    @Substitute
    static void fchown(int fd, int uid, int gid) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.fchown");
    };

    @Substitute
    static byte[] readdir(long dir) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.readdir");
    };

    @Substitute
    static void close0(int fd) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.close0");
    };

    @Substitute
    static void closedir(long dir) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.closedir");
    };

    @Substitute
    static byte[] readlink0(long pathAddress) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.readlink0");
    }

    @Substitute
    static void mkdir0(long pathAddress, int mode) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.mkdir0");
    };

    @Substitute
    static int stat0(long pathAddress, Target_sun_nio_fs_UnixFileAttributes attrs) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.stat0");
    }

    @Substitute
    static void rmdir0(long pathAddress) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.rmdir0");
    }

    @Substitute
    static void lstat0(long pathAddress, Target_sun_nio_fs_UnixFileAttributes attrs) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.lstat0");
    }

    @Substitute
    static byte[] getpwuid(int uid) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.getpwuid");
    }

    @Substitute
    static byte[] getgrgid(int gid) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.getgrgid");
    }

    @Substitute
    static void fstat(int fd, Target_sun_nio_fs_UnixFileAttributes attrs) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.fsat");
    }

    @Substitute
    static int fgetxattr0(int filedes, long nameAddress, long valueAddress, int valueLen) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.fgetxattr0");
    }

    @Substitute
    static int access0(long pathAddress, int amode) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.access0");
    }

    @Substitute
    private static void utimensat0(int fd, long pathAddress, long times0, long times1, int flags) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.utimensat0");
    }

    @Substitute
    static void symlink0(long name1, long name2) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.symlink0");
    }

    @Substitute
    static long opendir0(long pathAddress) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.opendir0");
    }

    @Substitute
    static long fdopendir(int dfd) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.fopendir");
    }

    @Substitute
    static int read(int fildes, long buf, int nbyte) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.read");
    }

    @Substitute
    static int write(int fildes, long buf, int nbyte) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.write");
    }

    @Substitute
    static void mknod0(long pathAddress, int mode, long dev) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.mknod0");
    }

    @Substitute
    static void lchown0(long pathAddress, int uid, int gid) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.lchown0");
    }

    @Substitute
    static void futimens(int fd, long times0, long times1) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.futimens");
    }

    @Substitute
    static void fsetxattr0(int filedes, long nameAddress, long valueAddress, int valueLen) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.fsetxattr0");
    }

    @Substitute
    static void fremovexattr0(int filedes, long nameAddress) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.fremovexattr0");
    }

    @Substitute
    static int flistxattr(int filedes, long listAddress, int size) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.flistxattr");
    }

    @Substitute
    static void chown0(long pathAddress, int uid, int gid) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.chown0");
    }

    @Substitute
    static void chmod0(long pathAddress, int mode) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.chmod0");
    }

    @Substitute
    static void rename0(long fromAddress, long toAddress) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.rename0");
    }

    @Substitute
    static int dup(int filedes) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.dup");
    }

    @Substitute
    private static void statvfs0(long pathAddress, Target_sun_nio_fs_UnixFileStoreAttributes_Web attrs) {
        throw new UnsupportedOperationException("UnixNativeDispatcher.statvfs0");
    }
}

@TargetClass(className = "sun.nio.fs.BsdNativeDispatcher", onlyWith = IsDarwin.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_BsdNativeDispatcher_Web {
    @Substitute
    static long getfsstat() {
        throw new UnsupportedOperationException("BsdNativeDispatcher.getfsstat");
    }

    @Substitute
    static int fsstatEntry(long iter, Target_sun_nio_fs_UnixMountEntry_Web entry) {
        throw new UnsupportedOperationException("BsdNativeDispatcher.fsstatEntry");
    }

    @Substitute
    static void endfsstat(long iter) {
        throw new UnsupportedOperationException("BsdNativeDispatcher.endfsstat");
    }

    @Substitute
    static byte[] getmntonname0(long pathAddress) {
        throw new UnsupportedOperationException("BsdNativeDispatcher.getmntonname0");
    }

    @Substitute
    private static int clonefile0(long srcAddress, long dstAddress, int flags) {
        throw new UnsupportedOperationException("BsdNativeDispatcher.clonefile0");
    }

    @Substitute
    private static void setattrlist0(long pathAddress, int commonattr, long modTime, long accTime, long createTime, long options) {
        throw new UnsupportedOperationException("BsdNativeDispatcher.setattrlist0");
    }

    @Substitute
    private static void fsetattrlist0(int fd, int commonattr, long modTime, long accTime, long createTime, long options) {
        throw new UnsupportedOperationException("BsdNativeDispatcher.fsetattrlist0");
    }

    @Substitute
    private static void initIDs() {
        throw new UnsupportedOperationException("BsdNativeDispatcher.initIDs");
    }
}

@TargetClass(className = "sun.nio.fs.UnixFileAttributes", onlyWith = IsUnix.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_UnixFileAttributes {
}

@TargetClass(className = "sun.nio.fs.UnixPath", onlyWith = IsUnix.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_UnixPath_Web {
    @Substitute
    public Path toRealPath(LinkOption[] options) {
        throw new UnsupportedOperationException("UnixPath.toRealPath");
    }

    @Substitute
    public URI toUri() {
        throw new UnsupportedOperationException("UnixPath.toUri");
    }
}

@TargetClass(className = "sun.nio.ch.UnixFileDispatcherImpl", onlyWith = IsUnix.class)
@SuppressWarnings("unused")
final class sun_nio_ch_UnixFileDispatcherImpl_Web {
    @Substitute
    static long allocationGranularity0() {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.allocationGranularity0");
    }

    @Substitute
    static long map0(FileDescriptor fd, int prot, long position, long length, boolean isSync) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.map0");
    }

    @Substitute
    static int pread0(FileDescriptor fd, long address, int len, long position) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.pread0");
    }

    @Substitute
    static int pwrite0(FileDescriptor fd, long address, int len, long position) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.pwrite0");
    }

    @Substitute
    static int read0(FileDescriptor fd, long address, int len) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.read0");
    }

    @Substitute
    static long seek0(FileDescriptor fd, long offset) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.seek0");
    }

    @Substitute
    static int setDirect0(FileDescriptor fd) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.setDirect0");
    }

    @Substitute
    static long size0(FileDescriptor fd) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.size0");
    }

    @Substitute
    static int truncate0(FileDescriptor fd, long size) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.truncate0");
    }

    @Substitute
    static int unmap0(long address, long length) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.unmap0");
    }

    @Substitute
    static int write0(FileDescriptor fd, long address, int len) {
        throw new UnsupportedOperationException("UnixFileDispatcherImpl.write0");
    }
}

@TargetClass(className = "sun.nio.fs.LinuxNativeDispatcher", onlyWith = IsLinux.class)
@SuppressWarnings("unused")
final class sun_nio_fs_LinuxNativeDispatcher_Web {
    @Substitute
    static int directCopy0(int dst, int src, long addressToPollForCancel) {
        throw new UnsupportedOperationException("LinuxNativeDispatcher.directCopy0");
    }

    @Substitute
    static int posix_fadvise(int fd, long offset, long len, int advice) {
        throw new UnsupportedOperationException("LinuxNativeDispatcher.posix_fadvise");
    }
}

@TargetClass(className = "sun.nio.fs.UnixFileSystem", onlyWith = IsUnix.class)
@SuppressWarnings("unused")
final class sun_nio_fs_UnixFileSystem_Web {
    @Substitute
    private static void bufferedCopy0(int dst, int src, long address, int size, long addressToPollForCancel) {
        throw new UnsupportedOperationException("UnixFileSystem.bufferedCopy0");
    }
}

@TargetClass(className = "sun.nio.fs.UnixFileStoreAttributes", onlyWith = IsUnix.class)
final class Target_sun_nio_fs_UnixFileStoreAttributes_Web {
}

@TargetClass(className = "sun.nio.fs.UnixMountEntry", onlyWith = IsUnix.class)
final class Target_sun_nio_fs_UnixMountEntry_Web {
}

@TargetClass(value = sun.nio.ch.NativeThread.class, onlyWith = IsUnix.class)
@SuppressWarnings("all")
final class Target_sun_nio_ch_NativeThread_Web {
    @Substitute
    public static void signal(long unused) {
        throw new UnsupportedOperationException("NativeThread.signal");
    }

    @Substitute
    public static long current0() {
        throw new UnsupportedOperationException("NativeThread.current");
    }
}

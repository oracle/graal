/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.NewInstance;
import static com.oracle.svm.core.headers.Errno.EACCES;
import static com.oracle.svm.core.headers.Errno.EEXIST;
import static com.oracle.svm.core.headers.Errno.ENOENT;
import static com.oracle.svm.core.headers.Errno.ENOTDIR;
import static com.oracle.svm.core.headers.Errno.errno;
import static com.oracle.svm.core.posix.headers.Dirent.closedir;
import static com.oracle.svm.core.posix.headers.Dirent.opendir;
import static com.oracle.svm.core.posix.headers.Dirent.readdir_r;
import static com.oracle.svm.core.posix.headers.Fcntl.O_APPEND;
import static com.oracle.svm.core.posix.headers.Fcntl.O_CREAT;
import static com.oracle.svm.core.posix.headers.Fcntl.O_DSYNC;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDONLY;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDWR;
import static com.oracle.svm.core.posix.headers.Fcntl.O_SYNC;
import static com.oracle.svm.core.posix.headers.Fcntl.O_TRUNC;
import static com.oracle.svm.core.posix.headers.Fcntl.O_WRONLY;
import static com.oracle.svm.core.posix.headers.Fcntl.open;
import static com.oracle.svm.core.posix.headers.Ioctl.FIONREAD;
import static com.oracle.svm.core.posix.headers.Ioctl.ioctl;
import static com.oracle.svm.core.posix.headers.Limits.MAXPATHLEN;
import static com.oracle.svm.core.posix.headers.Limits.PATH_MAX;
import static com.oracle.svm.core.posix.headers.Stat.S_IFCHR;
import static com.oracle.svm.core.posix.headers.Stat.S_IFDIR;
import static com.oracle.svm.core.posix.headers.Stat.S_IFIFO;
import static com.oracle.svm.core.posix.headers.Stat.S_IFMT;
import static com.oracle.svm.core.posix.headers.Stat.S_IFREG;
import static com.oracle.svm.core.posix.headers.Stat.S_IFSOCK;
import static com.oracle.svm.core.posix.headers.Stat.S_IRGRP;
import static com.oracle.svm.core.posix.headers.Stat.S_IROTH;
import static com.oracle.svm.core.posix.headers.Stat.S_IRUSR;
import static com.oracle.svm.core.posix.headers.Stat.S_IWGRP;
import static com.oracle.svm.core.posix.headers.Stat.S_IWOTH;
import static com.oracle.svm.core.posix.headers.Stat.S_IWUSR;
import static com.oracle.svm.core.posix.headers.Stat.S_IXGRP;
import static com.oracle.svm.core.posix.headers.Stat.S_IXOTH;
import static com.oracle.svm.core.posix.headers.Stat.S_IXUSR;
import static com.oracle.svm.core.posix.headers.Stat.chmod;
import static com.oracle.svm.core.posix.headers.Stat.fstat;
import static com.oracle.svm.core.posix.headers.Stat.mkdir;
import static com.oracle.svm.core.posix.headers.Stdio.remove;
import static com.oracle.svm.core.posix.headers.Stdio.rename;
import static com.oracle.svm.core.posix.headers.Stdlib.realpath;
import static com.oracle.svm.core.posix.headers.Time.utimes;
import static com.oracle.svm.core.posix.headers.Unistd.R_OK;
import static com.oracle.svm.core.posix.headers.Unistd.SEEK_CUR;
import static com.oracle.svm.core.posix.headers.Unistd.SEEK_END;
import static com.oracle.svm.core.posix.headers.Unistd.SEEK_SET;
import static com.oracle.svm.core.posix.headers.Unistd.W_OK;
import static com.oracle.svm.core.posix.headers.Unistd.X_OK;
import static com.oracle.svm.core.posix.headers.Unistd.access;
import static com.oracle.svm.core.posix.headers.Unistd.close;
import static com.oracle.svm.core.posix.headers.Unistd.ftruncate;
import static com.oracle.svm.core.posix.headers.Unistd.lseek;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.headers.Dirent.DIR;
import com.oracle.svm.core.posix.headers.Dirent.dirent;
import com.oracle.svm.core.posix.headers.Dirent.direntPointer;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Stat;
import com.oracle.svm.core.posix.headers.Stat.stat;
import com.oracle.svm.core.posix.headers.Statvfs;
import com.oracle.svm.core.posix.headers.Statvfs.statvfs;
import com.oracle.svm.core.posix.headers.Termios;
import com.oracle.svm.core.posix.headers.Time.timeval;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.VMError;

@Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
@AutomaticFeature
@CLibrary(value = "java", requireStatic = true)
class PosixJavaIOSubstituteFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        JNIRuntimeAccess.register(access.findClassByName("java.io.UnixFileSystem"));
    }
}

@TargetClass(className = "java.io.ExpiringCache")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_io_ExpiringCache {
}

@TargetClass(className = "java.io.UnixFileSystem")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@SuppressWarnings("static-method")
final class Target_java_io_UnixFileSystem {

    @TargetClass(className = "java.io.FileSystem")
    static final class Target_java_io_FileSystem {
        // Checkstyle: stop
        @Alias static int BA_EXISTS;
        @Alias static int BA_REGULAR;
        @Alias static int BA_DIRECTORY;
        @Alias static int ACCESS_EXECUTE;
        @Alias static int ACCESS_READ;
        @Alias static int ACCESS_WRITE;
        @Alias static int SPACE_FREE;
        @Alias static int SPACE_TOTAL;
        @Alias static int SPACE_USABLE;
        // Checkstyle: resume
    }

    @Alias @RecomputeFieldValue(kind = NewInstance, declClassName = "java.io.ExpiringCache") private Target_java_io_ExpiringCache cache;
    @Alias @RecomputeFieldValue(kind = NewInstance, declClassName = "java.io.ExpiringCache") private Target_java_io_ExpiringCache javaHomePrefixCache;

    @Substitute
    public int getBooleanAttributes0(File f) {
        Stat.stat stat = StackValue.get(Stat.stat.class);
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            if (Stat.stat(pathPtr, stat) == 0) {
                int fmt = stat.st_mode() & S_IFMT();
                return Target_java_io_FileSystem.BA_EXISTS | ((fmt == S_IFREG()) ? Target_java_io_FileSystem.BA_REGULAR : 0) | ((fmt == S_IFDIR()) ? Target_java_io_FileSystem.BA_DIRECTORY : 0);
            } else {
                return 0;
            }
        }
    }

    @Substitute
    private boolean delete0(File f) {
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            return remove(pathPtr) == 0;
        }
    }

    @Substitute
    private boolean rename0(File f1, File f2) {
        try (CCharPointerHolder pathPin1 = CTypeConversion.toCString(f1.getPath())) {
            CCharPointer pathPtr1 = pathPin1.get();
            try (CCharPointerHolder pathPin2 = CTypeConversion.toCString(f2.getPath())) {
                CCharPointer pathPtr2 = pathPin2.get();
                return rename(pathPtr1, pathPtr2) == 0;
            }
        }
    }

    @Substitute
    private long getLength(File f) {
        Stat.stat stat = StackValue.get(Stat.stat.class);
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            if (Stat.stat(pathPtr, stat) == 0) {
                return stat.st_size();
            } else {
                return 0;
            }
        }
    }

    @Substitute
    public String[] list(File f) {
        DIR dir;
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            dir = opendir(pathPtr);
        }

        if (dir.isNull()) {
            return null;
        }

        List<String> entries = new ArrayList<>();
        dirent dirent = StackValue.get(SizeOf.get(dirent.class) + PATH_MAX() + 1);
        direntPointer resultDirent = StackValue.get(direntPointer.class);

        while (readdir_r(dir, dirent, resultDirent) == 0 && !resultDirent.read().isNull()) {
            String name = CTypeConversion.toJavaString(dirent.d_name());
            if (name.equals(".") || name.equals("..")) {
                continue;
            }
            entries.add(name);
        }

        closedir(dir);
        return entries.toArray(new String[entries.size()]);
    }

    @Substitute
    private String canonicalize0(String path) throws IOException {
        final int maxPathLen = MAXPATHLEN();
        if (path.length() > maxPathLen) {
            throw new IOException("Bad pathname");
        }

        CCharPointer resolved = StackValue.get(maxPathLen);
        // first try realpath() on the entire path

        try (CCharPointerHolder pathPin = CTypeConversion.toCString(path)) {
            CCharPointer pathPtr = pathPin.get();
            if (realpath(pathPtr, resolved).notEqual(WordFactory.zero())) {
                // that worked, so return it
                return PosixUtils.collapse(CTypeConversion.toJavaString(resolved));
            }
        }

        // something's bogus in the original path, so remove names from the end
        // until either some subpath works or we run out of names
        String resolvedPart = path;
        String unresolvedPart = "";
        CCharPointer r = WordFactory.nullPointer();

        int lastSep = resolvedPart.lastIndexOf('/');
        while (lastSep != -1) {
            // prepend the last part of the resolved part to the unresolved part
            unresolvedPart = resolvedPart.substring(lastSep) + unresolvedPart;
            // remove the last part from the resolved part
            resolvedPart = lastSep == 0 ? "" : resolvedPart.substring(0, lastSep);
            lastSep = resolvedPart.lastIndexOf('/');
            if (lastSep == -1) {
                break;
            }

            try (CCharPointerHolder pathPin = CTypeConversion.toCString(resolvedPart)) {
                CCharPointer resolvedPartPtr = pathPin.get();
                r = realpath(resolvedPartPtr, resolved);
            }
            int errno = errno();
            if (r.notEqual(WordFactory.zero())) {
                // the subpath has a canonical path
                break;
            } else if (errno == ENOENT() || errno == ENOTDIR() || errno == EACCES()) {
                // If the lookup of a particular subpath fails because the file
                // does not exist, because it is of the wrong type, or because
                // access is denied, then remove its last name and try again.
                // Other I/O problems cause an error return.
                continue;
            } else {
                throw PosixUtils.newIOExceptionWithLastError("Bad pathname");
            }
        }

        if (r.notEqual(WordFactory.zero())) {
            // append unresolved subpath to resolved subpath
            String rs = CTypeConversion.toJavaString(r);
            if (rs.length() + 1 + unresolvedPart.length() > maxPathLen) {
                throw PosixUtils.newIOExceptionWithLastError("Bad pathname");
            }
            return PosixUtils.collapse(rs + "/" + unresolvedPart);
        } else {
            // nothing resolved, so just return the original path
            return PosixUtils.collapse(path);
        }

    }

    @Substitute
    public boolean createDirectory(File f) {
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            return mkdir(pathPtr, 0777) == 0;
        }
    }

    @Substitute
    public boolean checkAccess(File f, int access) {
        // can't use a switch because fields are aliased
        int mode = access == Target_java_io_FileSystem.ACCESS_READ ? R_OK()
                        : access == Target_java_io_FileSystem.ACCESS_WRITE ? W_OK()
                                        : access == Target_java_io_FileSystem.ACCESS_EXECUTE ? X_OK()
                                                        : -1;
        if (mode == -1) {
            throw VMError.shouldNotReachHere("illegal access mode");
        }
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer path = pathPin.get();
            return access(path, mode) == 0;
        }
    }

    @Substitute
    public long getSpace(File f, int t) {
        statvfs statvfs = StackValue.get(statvfs.class);
        LibC.memset(statvfs, WordFactory.zero(), WordFactory.unsigned(SizeOf.get(statvfs.class)));

        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            if (Statvfs.statvfs(pathPtr, statvfs) == 0) {
                final long frsize = statvfs.f_frsize();
                if (t == Target_java_io_FileSystem.SPACE_TOTAL) {
                    return frsize * statvfs.f_blocks();
                } else if (t == Target_java_io_FileSystem.SPACE_FREE) {
                    return frsize * statvfs.f_bfree();
                } else if (t == Target_java_io_FileSystem.SPACE_USABLE) {
                    return frsize * statvfs.f_bavail();
                } else {
                    throw VMError.shouldNotReachHere("illegal space mode");
                }
            }
        }
        return 0L;
    }

    @Substitute
    public boolean setReadOnly(File f) {
        stat stat = StackValue.get(stat.class);
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            if (Stat.stat(pathPtr, stat) == 0) {
                if (chmod(pathPtr, stat.st_mode() & ~(S_IWUSR() | S_IWGRP() | S_IWOTH())) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Substitute
    public boolean setPermission(File f, int access, boolean enable, boolean owneronly) {
        int amode = 0;
        if (access == Target_java_io_FileSystem.ACCESS_READ) {
            amode = owneronly ? S_IRUSR() : S_IRUSR() | S_IRGRP() | S_IROTH();
        } else if (access == Target_java_io_FileSystem.ACCESS_WRITE) {
            amode = owneronly ? S_IWUSR() : S_IWUSR() | S_IWGRP() | S_IWOTH();
        } else if (access == Target_java_io_FileSystem.ACCESS_EXECUTE) {
            amode = owneronly ? S_IXUSR() : S_IXUSR() | S_IXGRP() | S_IXOTH();
        } else {
            throw VMError.shouldNotReachHere("illegal access mode");
        }

        stat stat = StackValue.get(stat.class);
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            if (Stat.stat(pathPtr, stat) == 0) {
                int newMode;
                if (enable) {
                    newMode = stat.st_mode() | amode;
                } else {
                    newMode = stat.st_mode() & ~amode;
                }
                if (chmod(pathPtr, newMode) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Substitute
    public boolean createFileExclusively(String path) throws IOException {
        int fd;
        if (path.equals("/")) {
            return false;
        } else {
            try (CCharPointerHolder pathPin = CTypeConversion.toCString(PosixUtils.removeTrailingSlashes(path))) {
                CCharPointer pathPtr = pathPin.get();
                fd = open(pathPtr, O_RDWR() | O_CREAT(), 0666);
            }
        }
        if (fd < 0) {
            if (fd != EEXIST()) {
                throw PosixUtils.newIOExceptionWithLastError(path);
            }
        } else {
            close(fd);
            return true;
        }
        return false;
    }

    @Substitute
    public long getLastModifiedTime(File f) {
        stat stat = StackValue.get(stat.class);
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            if (Stat.stat(pathPtr, stat) == 0) {
                return 1000 * stat.st_mtime();
            }
        }
        return 0L;
    }

    @Substitute
    public boolean setLastModifiedTime(File f, long time) {
        stat stat = StackValue.get(stat.class);
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(f.getPath())) {
            CCharPointer pathPtr = pathPin.get();
            if (Stat.stat(pathPtr, stat) == 0) {
                timeval timeval = StackValue.get(2, timeval.class);

                // preserve access time
                timeval access = timeval.addressOf(0);
                access.set_tv_sec(stat.st_atime());
                access.set_tv_usec(0L);

                // change last-modified time
                timeval last = timeval.addressOf(1);
                last.set_tv_sec(time / 1000);
                last.set_tv_usec((time % 1000) * 1000);

                if (utimes(pathPtr, timeval) == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

@TargetClass(java.io.FileInputStream.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_io_FileInputStream {

    @Alias private FileDescriptor fd;

    @Substitute
    private int readBytes(byte[] b, int off, int len) throws IOException {
        return PosixUtils.readBytes(b, off, len, fd);
    }

    @Substitute
    private void open(String name) throws FileNotFoundException {
        PosixUtils.fileOpen(name, fd, O_RDONLY());
    }

    @Substitute //
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private void close0() throws IOException {
        PosixUtils.fileClose(fd);
    }

    @Substitute
    public int read() throws IOException {
        return PosixUtils.readSingle(fd);
    }

    @Substitute
    public int available() throws IOException {
        int handle = PosixUtils.getFDHandle(fd);

        SignedWord ret = WordFactory.zero();
        boolean av = false;
        stat stat = StackValue.get(stat.class);
        if (fstat(handle, stat) >= 0) {
            int mode = stat.st_mode();
            if (Util_java_io_FileInputStream.isChr(mode) || Util_java_io_FileInputStream.isFifo(mode) || Util_java_io_FileInputStream.isSock(mode)) {
                CIntPointer np = StackValue.get(CIntPointer.class);
                if (ioctl(handle, FIONREAD(), np) >= 0) {
                    ret = WordFactory.signed(np.read());
                    av = true;
                }
            }
        }

        if (!av) {
            SignedWord cur;
            SignedWord end;
            if ((cur = lseek(handle, WordFactory.zero(), SEEK_CUR())).equal(WordFactory.signed(-1))) {
                av = false;
            } else if ((end = lseek(handle, WordFactory.zero(), SEEK_END())).equal(WordFactory.signed(-1))) {
                av = false;
            } else if (lseek(handle, cur, SEEK_SET()).equal(WordFactory.signed(-1))) {
                av = false;
            } else {
                ret = end.subtract(cur);
                av = true;
            }
        }

        if (av) {
            long r = ret.rawValue();
            if (r > Integer.MAX_VALUE) {
                r = Integer.MAX_VALUE;
            }
            return (int) r;
        }
        throw PosixUtils.newIOExceptionWithLastError("");
    }

    @Substitute
    public long skip(long n) throws IOException {
        SignedWord cur = WordFactory.zero();
        SignedWord end = WordFactory.zero();
        int handle = PosixUtils.getFDHandle(fd);

        if ((cur = lseek(handle, WordFactory.zero(), SEEK_CUR())).equal(WordFactory.signed(-1))) {
            throw PosixUtils.newIOExceptionWithLastError("Seek error");
        } else if ((end = lseek(handle, WordFactory.signed(n), SEEK_CUR())).equal(WordFactory.signed(-1))) {
            throw PosixUtils.newIOExceptionWithLastError("Seek error");
        }

        return end.subtract(cur).rawValue();
    }
}

final class Util_java_io_FileInputStream {

    static boolean isChr(int mode) {
        return ((mode) & S_IFMT()) == S_IFCHR();
    }

    static boolean isFifo(int mode) {
        return ((mode) & S_IFMT()) == S_IFIFO();
    }

    static boolean isSock(int mode) {
        return ((mode) & S_IFMT()) == S_IFSOCK();
    }
}

@TargetClass(java.io.FileOutputStream.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_io_FileOutputStream {

    @Substitute
    protected void writeBytes(byte[] bytes, int off, int len, boolean append) throws IOException {
        PosixUtils.writeBytes(SubstrateUtil.getFileDescriptor(SubstrateUtil.cast(this, FileOutputStream.class)), bytes, off, len, append);
    }

    @Substitute
    private void open(String name, boolean append) throws FileNotFoundException {
        PosixUtils.fileOpen(name, SubstrateUtil.getFileDescriptor(SubstrateUtil.cast(this, FileOutputStream.class)), O_WRONLY() | O_CREAT() | (append ? O_APPEND() : O_TRUNC()));
    }

    @Substitute //
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private void close0() throws IOException {
        PosixUtils.fileClose(SubstrateUtil.getFileDescriptor(SubstrateUtil.cast(this, FileOutputStream.class)));
    }

    @Substitute
    private void write(int b, boolean append) throws IOException {
        PosixUtils.writeSingle(SubstrateUtil.getFileDescriptor(SubstrateUtil.cast(this, FileOutputStream.class)), b, append);
    }
}

@TargetClass(java.io.RandomAccessFile.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_io_RandomAccessFile {

    // Checkstyle: stop
    @Alias private static int O_RDONLY;
    @Alias private static int O_RDWR;
    @Alias private static int O_SYNC;
    @Alias private static int O_DSYNC;
    // Checkstyle: resume

    @Alias private FileDescriptor fd;

    @Substitute
    public int read() throws IOException {
        return PosixUtils.readSingle(fd);
    }

    @Substitute
    private int readBytes(byte[] b, int off, int len) throws IOException {
        return PosixUtils.readBytes(b, off, len, fd);
    }

    @Substitute
    public void write(int b) throws IOException {
        PosixUtils.writeSingle(fd, b, false);
    }

    @Substitute
    private void writeBytes(byte[] b, int off, int len) throws IOException {
        PosixUtils.writeBytes(fd, b, off, len, false);
    }

    @Substitute
    private void seek(long pos) throws IOException {
        int handle = PosixUtils.getFDHandle(fd);
        if (pos < 0L) {
            throw PosixUtils.newIOExceptionWithLastError("Negative seek offset");
        } else if (lseek(handle, WordFactory.signed(pos), SEEK_SET()).equal(WordFactory.signed(-1))) {
            throw PosixUtils.newIOExceptionWithLastError("Seek failed");
        }

    }

    @Substitute
    public long getFilePointer() throws IOException {
        SignedWord ret;
        int handle = PosixUtils.getFDHandle(fd);
        if ((ret = lseek(handle, WordFactory.zero(), SEEK_CUR())).equal(WordFactory.signed(-1))) {
            throw PosixUtils.newIOExceptionWithLastError("Seek failed");
        }
        return ret.rawValue();
    }

    @Substitute
    private void open(String name, int mode) throws FileNotFoundException {
        int flags = 0;
        if ((mode & O_RDONLY) != 0) {
            flags |= O_RDONLY();
        } else if ((mode & O_RDWR) != 0) {
            flags |= O_RDWR();
            flags |= O_CREAT();
            if ((mode & O_SYNC) != 0) {
                flags |= O_SYNC();
            } else if ((mode & O_DSYNC) != 0) {
                flags |= O_DSYNC();
            }
        }
        PosixUtils.fileOpen(name, fd, flags);
    }

    @Substitute //
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private void close0() throws IOException {
        PosixUtils.fileClose(fd);
    }

    @Substitute
    public long length() throws IOException {
        SignedWord cur = WordFactory.zero();
        SignedWord end = WordFactory.zero();
        int handle = PosixUtils.getFDHandle(fd);

        if ((cur = lseek(handle, WordFactory.zero(), SEEK_CUR())).equal(WordFactory.signed(-1))) {
            throw PosixUtils.newIOExceptionWithLastError("Seek failed");
        } else if ((end = lseek(handle, WordFactory.zero(), SEEK_END())).equal(WordFactory.signed(-1))) {
            throw PosixUtils.newIOExceptionWithLastError("Seek failed");
        } else if (lseek(handle, cur, SEEK_SET()).equal(WordFactory.signed(-1))) {
            throw PosixUtils.newIOExceptionWithLastError("Seek failed");
        }
        return end.rawValue();
    }

    @Substitute
    public void setLength(long newLength) throws IOException {
        SignedWord cur;
        int handle = PosixUtils.getFDHandle(fd);

        if ((cur = lseek(handle, WordFactory.zero(), SEEK_CUR())).equal(WordFactory.signed(-1))) {
            throw PosixUtils.newIOExceptionWithLastError("setLength failed");
        }
        if (ftruncate(handle, newLength) == -1) {
            throw PosixUtils.newIOExceptionWithLastError("setLength failed");
        }
        if (cur.greaterThan(WordFactory.signed(newLength))) {
            if (lseek(handle, WordFactory.zero(), SEEK_END()).equal(WordFactory.signed(-1))) {
                throw PosixUtils.newIOExceptionWithLastError("setLength failed");
            }
        } else {
            if (lseek(handle, cur, SEEK_SET()).equal(WordFactory.signed(-1))) {
                throw PosixUtils.newIOExceptionWithLastError("setLength failed");
            }
        }
    }

}

@TargetClass(java.io.Console.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_io_Console {

    @Alias //
    Charset cs;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    static boolean echoOff;

    @Alias
    Target_java_io_Console() {
        /* The empty body on an alias does not substitute. */
    }

    @Substitute
    static String encoding() {
        return null;
    }

    @Substitute
    static boolean istty() {
        return Unistd.isatty(PosixUtils.getFD(java.io.FileDescriptor.in)) == 1 && Unistd.isatty(PosixUtils.getFD(java.io.FileDescriptor.out)) == 1;
    }

    /* { Do not re-format commented out C code: @formatter:off */
    // 047 JNIEXPORT jboolean JNICALL
    // 048 Java_java_io_Console_echo(JNIEnv *env,
    // 049                           jclass cls,
    // 050                           jboolean on) {
    @Substitute
    static boolean echo(boolean on) throws IOException {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            /* Initialize the echo shut down hook, once. */
            Util_java_io_Console_JDK8OrEarlier.addShutdownHook();
        }
        // 052     struct termios tio;
        final Termios.termios tio = StackValue.get(Termios.termios.class);
        // 053     jboolean old;
        boolean old;
        // 054     int tty = fileno(stdin);
        /* TODO: Cf. in istty(): Util_java_io_FileDescriptor.getFD(java.io.FileDescriptor.in). */
        final int tty = Unistd.STDIN_FILENO();
        // 055     if (tcgetattr(tty, &tio) == -1) {
        if (Termios.tcgetattr(tty, tio) == -1) {
            // 056         JNU_ThrowIOExceptionWithLastError(env, "tcgetattr failed");
            throw PosixUtils.newIOExceptionWithLastError("tcgetattr failed");
            // 057         return !on;
            /* Unreachable code. */
        }
        // 059     old = (tio.c_lflag & ECHO);
        old = (tio.get_c_lflag() & Termios.ECHO()) != 0;
        // 060     if (on) {
        if (on) {
            // 061         tio.c_lflag |= ECHO;
            tio.set_c_lflag(tio.get_c_lflag() | Termios.ECHO());
        } else {
            // 063         tio.c_lflag &= ~ECHO;
            tio.set_c_lflag(tio.get_c_lflag() & ~Termios.ECHO());
        }
        // 065     if (tcsetattr(tty, TCSANOW, &tio) == -1) {
        if (Termios.tcsetattr(tty, Termios.TCSANOW(), tio) == -1) {
            // 066         JNU_ThrowIOExceptionWithLastError(env, "tcsetattr failed");
            throw PosixUtils.newIOExceptionWithLastError("tcsetattr failed");
        }
        // 068     return old;
        return old;
    }
    /* } Do not re-format commented out C code: @formatter:on */
}

/** Utility methods for {@link Target_java_io_Console}. */
/* onlyWith = JDK8OrEarlier.class. */
class Util_java_io_Console_JDK8OrEarlier {

    /** An initialization flag. */
    static volatile boolean initialized = false;

    /** A lock to protect the initialization flag. */
    static ReentrantLock lock = new ReentrantLock();

    static void addShutdownHook() {
        if (!initialized) {
            lock.lock();
            try {
                if (!initialized) {
                    try {
                        /*
                         * Compare this code to the static initialization code of {@link
                         * java.io.Console}, except I am short-circuiting the trampoline through
                         * {@link sun.misc.SharedSecrets#getJavaLangAccess()}.
                         */
                        /*
                         * The {@code add} method is declared in {@code
                         * com.oracle.svm.core.jdk.Target_java_lang_Shutdown} rather than in {@code
                         * com.oracle.svm.core.posix.Target_java_lang_Shutdown}, so I have to
                         * fully-qualify the reference.
                         */
                        com.oracle.svm.core.jdk.Target_java_lang_Shutdown.add(
                                        0 /* shutdown hook invocation order */,
                                        false /* only register if shutdown is not in progress */,
                                        new Runnable() {/* hook */
                                            @Override
                                            public void run() {
                                                try {
                                                    if (Target_java_io_Console.echoOff) {
                                                        Target_java_io_Console.echo(true);
                                                    }
                                                } catch (IOException x) {
                                                    /* Ignored. */
                                                }
                                            }
                                        });
                        initialized = true;
                    } catch (InternalError ie) {
                        /* Someone already registered the shutdown hook at slot 0. */
                    } catch (IllegalStateException e) {
                        /* Too late to register this shutdown hook. */
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}

@TargetClass(java.io.FileInputStream.class)
@Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
final class Target_java_io_FileInputStream_jni {

    @Alias
    static native void initIDs();
}

@TargetClass(java.io.RandomAccessFile.class)
@Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
final class Target_java_io_RandomAccessFile_jni {

    @Alias
    static native void initIDs();
}

@TargetClass(java.io.FileDescriptor.class)
@Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
final class Target_java_io_FileDescriptor_jni {

    @Alias
    static native void initIDs();

    @Alias static FileDescriptor in;
    @Alias static FileDescriptor out;
    @Alias static FileDescriptor err;

}

@TargetClass(java.io.FileOutputStream.class)
@Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
final class Target_java_io_FileOutputStream_jni {

    @Alias
    static native void initIDs();
}

@TargetClass(className = "java.io.UnixFileSystem")
@Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
final class Target_java_io_UnixFileSystem_jni {

    @Alias
    static native void initIDs();
}

@Platforms({Platform.LINUX.class, InternalPlatform.LINUX_JNI.class, Platform.DARWIN.class, InternalPlatform.DARWIN_JNI.class})
public final class PosixJavaIOSubstitutions {

    /** Private constructor: No instances. */
    private PosixJavaIOSubstitutions() {
    }

    @Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
    public static boolean initIDs() {
        try {
            /*
             * java.dll is normally loaded by the VM. After loading java.dll, the VM then calls
             * initializeSystemClasses which loads zip.dll.
             *
             * We might want to consider calling System.initializeSystemClasses instead of
             * explicitly loading the builtin zip library.
             */

            System.loadLibrary("java");

            Target_java_io_FileDescriptor_jni.initIDs();
            Target_java_io_FileInputStream_jni.initIDs();
            Target_java_io_FileOutputStream_jni.initIDs();
            Target_java_io_UnixFileSystem_jni.initIDs();

            System.setIn(new BufferedInputStream(new FileInputStream(FileDescriptor.in)));
            System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 128), true));
            System.setErr(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err), 128), true));

            System.loadLibrary("zip");
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.log().string("System.loadLibrary failed, " + e).newline();
            return false;
        }
    }
}

/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.jvmstat;

import static com.oracle.svm.core.jvmstat.PerfManager.Options.PerfDataMemoryMappedFile;
import static com.oracle.svm.core.posix.PosixStat.S_IRGRP;
import static com.oracle.svm.core.posix.PosixStat.S_IROTH;
import static com.oracle.svm.core.posix.PosixStat.S_IRWXU;
import static com.oracle.svm.core.posix.PosixStat.S_IXGRP;
import static com.oracle.svm.core.posix.PosixStat.S_IXOTH;
import static com.oracle.svm.core.posix.headers.Errno.EEXIST;
import static com.oracle.svm.core.posix.headers.Errno.EPERM;
import static com.oracle.svm.core.posix.headers.Errno.ESRCH;
import static com.oracle.svm.core.posix.headers.Fcntl.O_CREAT;
import static com.oracle.svm.core.posix.headers.Fcntl.O_NOFOLLOW;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDONLY;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDWR;

import java.nio.ByteBuffer;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.DirectByteBufferUtil;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.jvmstat.PerfMemoryPrologue;
import com.oracle.svm.core.jvmstat.PerfMemoryProvider;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.posix.PosixStat;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Dirent;
import com.oracle.svm.core.posix.headers.Dirent.DIR;
import com.oracle.svm.core.posix.headers.Dirent.dirent;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Mman;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Unistd;

/**
 * This class uses high-level JDK features at the moment. In the future, we will need to rewrite
 * this code so that it can be executed during the isolate startup (i.e., in uninterruptible code),
 * see GR-40601.
 * <p>
 * Based on JDK 19 (git commit hash: 967a28c3d85fdde6d5eb48aa0edd8f7597772469, JDK tag: jdk-19+36.
 */
class PosixPerfMemoryProvider implements PerfMemoryProvider {
    private static final String PERFDATA_NAME = "hsperfdata";

    private String backingFilePath;

    @Platforms(Platform.HOSTED_ONLY.class)
    PosixPerfMemoryProvider() {
    }

    /**
     * Create a named shared memory region. Returns the address of the memory region on success or
     * null on failure. A return value of null will ultimately disable the shared memory feature.
     */
    @Override
    public ByteBuffer create() {
        assert backingFilePath == null;

        int size = NumUtil.roundUp(PerfManager.Options.PerfDataMemorySize.getValue(), Unistd.getpagesize());
        if (size <= PerfMemoryPrologue.getPrologueSize()) {
            return null;
        }

        int vmId = Unistd.getpid();
        String userName = PosixUtils.getUserName(Unistd.NoTransitions.geteuid());
        if (userName == null) {
            return null;
        }

        String dirName = getUserTmpDir(userName, vmId, -1);
        String fileName = getSharedMemFileName(vmId, -1);

        int fd;
        try (CTypeConversion.CCharPointerHolder d = CTypeConversion.toCString(dirName)) {
            cleanupSharedMemResources(d.get(), vmId);

            try (CTypeConversion.CCharPointerHolder f = CTypeConversion.toCString(fileName)) {
                fd = createSharedMemResources(d.get(), f.get(), size);
            }
        }

        if (fd == -1) {
            return null;
        }

        Pointer mapAddress = Mman.mmap(WordFactory.nullPointer(), WordFactory.unsigned(size), Mman.PROT_READ() | Mman.PROT_WRITE(), Mman.MAP_SHARED(), fd, 0);

        int result = Unistd.NoTransitions.close(fd);
        assert result != -1;

        String filePath = dirName + "/" + fileName;
        if (mapAddress == Mman.MAP_FAILED()) {
            restartableUnlink(filePath);
            return null;
        }

        backingFilePath = filePath;

        /* Clear the shared memory region. */
        LibC.memset(mapAddress, WordFactory.signed(0), WordFactory.unsigned(size));
        return DirectByteBufferUtil.allocate(mapAddress.rawValue(), size);
    }

    private static String getUserTmpDir(String user, int vmId, int nsPid) {
        String tmpDir = Target_jdk_internal_vm_VMSupport.getVMTemporaryDirectory();
        if (Platform.includedIn(Platform.LINUX.class)) {
            if (nsPid != -1) {
                /* Use different directory if we have a containerized process on Linux. */
                tmpDir = "/proc/" + vmId + "/root" + tmpDir;
            }
        }
        return tmpDir + "/" + PERFDATA_NAME + "_" + user;
    }

    private static String getSharedMemFileName(int vmId, int nspid) {
        int pid = vmId;
        if (Platform.includedIn(Platform.LINUX.class) && nspid != -1) {
            pid = nspid;
        }
        return Integer.toString(pid);
    }

    /**
     * This method attempts to remove stale shared memory files in the user's temp directory. It
     * scans for files matching the pattern ^$[0-9]*$. For each file found, the process id is
     * extracted from the file name and a test is run to determine if the process is alive. If the
     * process is not alive, any stale file resources are removed.
     */
    private static void cleanupSharedMemResources(CCharPointer directoryPath, int selfPid) {
        try (SecureDirectory s = openDirectorySecure(directoryPath)) {
            if (s == null) {
                return;
            }

            dirent entry;
            while ((entry = Dirent.readdir(s.dir)).isNonNull()) {
                String name = CTypeConversion.toJavaString(entry.d_name());
                int pid = filenameToPid(name);
                if (pid == 0) {
                    if (!".".equals(name) && !"..".equals(name)) {
                        /* Attempt to remove all unexpected files. */
                        Fcntl.NoTransitions.unlinkat(s.fd, entry.d_name(), 0);
                    }
                    continue;
                }

                /*
                 * We now have a file name that converts to a valid integer that could represent a
                 * process id. If this process id matches the current process id or the process is
                 * not running, then remove the stale file resources.
                 */
                if (pid == selfPid || canFileBeDeleted(pid)) {
                    Fcntl.NoTransitions.unlinkat(s.fd, entry.d_name(), 0);
                }
            }
        }
    }

    private static int createSharedMemResources(CCharPointer directoryPath, CCharPointer filename, int size) {
        if (!makeUserTmpDir(directoryPath)) {
            return -1;
        }

        int fd = tryCreatePerfFile(directoryPath, filename);
        if (fd == -1) {
            return -1;
        }

        if (!isFileSecure(fd)) {
            Unistd.NoTransitions.close(fd);
            return -1;
        }

        /* Truncate the file to get rid of any existing data. */
        int result = restartableFtruncate(fd, 0);
        if (result == -1) {
            Unistd.NoTransitions.close(fd);
            return -1;
        }

        /* Set the file size. */
        result = restartableFtruncate(fd, size);
        if (result == -1) {
            Unistd.NoTransitions.close(fd);
            return -1;
        }

        /*
         * Verify that we have enough disk space for this file. We'll get random SIGBUS crashes on
         * memory accesses if we don't.
         */
        RawFileOperationSupport fs = RawFileOperationSupport.nativeByteOrder();
        RawFileDescriptor rawFd = WordFactory.signed(fd);
        int pageSize = NumUtil.safeToInt(VirtualMemoryProvider.get().getGranularity().rawValue());

        boolean success = true;
        for (int pos = 0; pos < size; pos += pageSize) {
            success = fs.seek(rawFd, pos);
            if (!success) {
                break;
            }
            success = fs.writeInt(rawFd, 0);
            if (!success) {
                break;
            }
        }

        if (!success) {
            Unistd.NoTransitions.close(fd);
            return -1;
        }

        return fd;
    }

    private static int tryCreatePerfFile(CCharPointer directoryPath, CCharPointer filename) {
        try (SecureDirectory s = openDirectorySecure(directoryPath)) {
            if (s == null) {
                return -1;
            }

            /*
             * Open the filename in the current directory. Cannot use O_TRUNC here; truncation of an
             * existing file has to happen after the is_file_secure() check below.
             */
            return restartableOpenat(s.fd, filename, O_RDWR() | O_CREAT() | O_NOFOLLOW(), PosixStat.S_IRUSR() | PosixStat.S_IWUSR());
        }
    }

    /**
     * Convert the given file name into a process id. If the file does not meet the file naming
     * constraints, return 0.
     */
    private static int filenameToPid(String filename) {
        try {
            return Integer.parseInt(filename);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static boolean makeUserTmpDir(CCharPointer directory) {
        /*
         * Create the directory with 0755 permissions. note that the directory will be owned by
         * euid::egid, which may not be the same as uid::gid.
         */
        if (PosixStat.NoTransitions.mkdir(directory, S_IRWXU() | S_IRGRP() | S_IXGRP() | S_IROTH() | S_IXOTH()) == -1) {
            if (LibC.errno() == EEXIST()) {
                /*
                 * The directory already exists and was probably created by another JVM instance.
                 * However, this could also be the result of a deliberate symlink. Verify that the
                 * existing directory is safe.
                 */
                return isDirectorySecure(directory);
            } else {
                return false;
            }
        }
        return true;
    }

    private static SecureDirectory openDirectorySecure(CCharPointer directory) {
        int fd = restartableOpen(directory, O_RDONLY() | O_NOFOLLOW(), 0);
        if (fd == -1) {
            return null;
        }

        if (!isDirFdSecure(fd)) {
            Unistd.NoTransitions.close(fd);
            return null;
        }

        DIR dir = Dirent.fdopendir(fd);
        if (dir.isNull()) {
            Unistd.NoTransitions.close(fd);
            return null;
        }

        return new SecureDirectory(fd, dir);
    }

    /**
     * Check if the given dir is considered a secure directory for the backing store files. Returns
     * true if the directory is considered a secure location. Returns false if the dir is a symbolic
     * link or if an error occurred.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isDirectorySecure(CCharPointer directory) {
        PosixStat.stat buf = StackValue.get(PosixStat.sizeOfStatStruct());
        int result = restartableLstat(directory, buf);
        if (result == -1) {
            return false;
        }
        return isStatBufSecure(buf);
    }

    /**
     * Check if the given directory file descriptor is considered a secure directory for the backing
     * store files. Returns true if the directory exists and is considered a secure location.
     * Returns false if the path is a symbolic link or if an error occurred.
     */
    private static boolean isDirFdSecure(int dirFd) {
        PosixStat.stat buf = StackValue.get(PosixStat.sizeOfStatStruct());
        int result = restartableFstat(dirFd, buf);
        if (result == -1) {
            return false;
        }
        return isStatBufSecure(buf);
    }

    private static boolean isFileSecure(int fd) {
        PosixStat.stat buf = StackValue.get(PosixStat.sizeOfStatStruct());
        int result = restartableFstat(fd, buf);
        if (result == -1) {
            return false;
        }
        if (PosixStat.st_nlink(buf).aboveThan(1)) {
            return false;
        }
        return true;
    }

    /**
     * Check if the given statbuf is considered a secure directory for the backing store files.
     * Returns true if the directory is considered a secure location. Returns false if the statbuf
     * is a symbolic link or if an error occurred.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isStatBufSecure(PosixStat.stat statp) {
        if (PosixStat.S_ISLNK(statp) || !PosixStat.S_ISDIR(statp)) {
            /*
             * The path represents a link or some non-directory file type, which is not what we
             * expected. Declare it insecure.
             */
            return false;
        }

        if (PosixStat.st_mode(statp).and(PosixStat.S_IWGRP() | PosixStat.S_IWOTH()).notEqual(0)) {
            /*
             * The directory is open for writing and could be subjected to a symlink or a hard link
             * attack. Declare it insecure.
             */
            return false;
        }

        /*
         * If user is not root then see if the uid of the directory matches the effective uid of the
         * process.
         */
        int euid = Unistd.NoTransitions.geteuid();
        if (euid != 0 && PosixStat.st_uid(statp) != euid) {
            /* The directory was not created by this user, declare it insecure. */
            return false;
        }
        return true;
    }

    /**
     * Process liveness is detected by sending signal number 0 to the process id. If kill determines
     * that the process does not exist, then the file resources are removed. If kill determines that
     * we don't have permission to signal the process, then the file resources are assumed to be
     * stale and are removed because the resources for such a process should be in a different user
     * specific directory.
     */
    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static boolean canFileBeDeleted(int pid) {
        int ret = Signal.NoTransitions.kill(pid, 0);
        if (ret == -1) {
            int errno = LibC.errno();
            return errno == ESRCH() || errno == EPERM();
        }
        return false;
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableOpen(CCharPointer directory, int flags, int mode) {
        int result;
        do {
            result = Fcntl.NoTransitions.open(directory, flags, mode);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableOpenat(int fd, CCharPointer filename, int flags, int mode) {
        int result;
        do {
            result = Fcntl.NoTransitions.openat(fd, filename, flags, mode);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    private static int restartableUnlink(String pathname) {
        try (CTypeConversion.CCharPointerHolder f = CTypeConversion.toCString(pathname)) {
            return restartableUnlink(f.get());
        }
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableUnlink(CCharPointer pathname) {
        int result;
        do {
            result = Fcntl.NoTransitions.unlink(pathname);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableFtruncate(int fd, int size) {
        int result;
        do {
            result = Unistd.NoTransitions.ftruncate(fd, WordFactory.signed(size));
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableFstat(int fd, PosixStat.stat buf) {
        int result;
        do {
            result = PosixStat.NoTransitions.fstat(fd, buf);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableLstat(CCharPointer directory, PosixStat.stat buf) {
        int result;
        do {
            result = PosixStat.NoTransitions.lstat(directory, buf);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @Override
    public void teardown() {
        if (backingFilePath != null) {
            restartableUnlink(backingFilePath);
            backingFilePath = null;
        }
    }

    private static class SecureDirectory implements AutoCloseable {
        private final int fd;
        private final DIR dir;

        SecureDirectory(int fd, DIR dir) {
            this.fd = fd;
            this.dir = dir;
        }

        @Override
        public void close() {
            /* Close the directory (and implicitly the file descriptor). */
            Dirent.closedir(dir);
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class PosixPerfMemoryFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJvmstatSupport() && PerfDataMemoryMappedFile.getValue();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PerfMemoryProvider.class, new PosixPerfMemoryProvider());
    }
}

@TargetClass(className = "jdk.internal.vm.VMSupport")
final class Target_jdk_internal_vm_VMSupport {

    @Alias
    public static native String getVMTemporaryDirectory();
}

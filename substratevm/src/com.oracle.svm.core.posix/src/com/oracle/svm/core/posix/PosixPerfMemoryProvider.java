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
package com.oracle.svm.core.posix;

import static com.oracle.svm.core.jvmstat.PerfManager.Options.PerfDataMemoryMappedFile;
import static com.oracle.svm.core.posix.PosixStat.S_IRGRP;
import static com.oracle.svm.core.posix.PosixStat.S_IROTH;
import static com.oracle.svm.core.posix.PosixStat.S_IRWXU;
import static com.oracle.svm.core.posix.PosixStat.S_IXGRP;
import static com.oracle.svm.core.posix.PosixStat.S_IXOTH;
import static com.oracle.svm.core.posix.headers.Errno.EACCES;
import static com.oracle.svm.core.posix.headers.Errno.EEXIST;
import static com.oracle.svm.core.posix.headers.Errno.EISDIR;
import static com.oracle.svm.core.posix.headers.Errno.ENOENT;
import static com.oracle.svm.core.posix.headers.Errno.EPERM;
import static com.oracle.svm.core.posix.headers.Errno.ESRCH;
import static com.oracle.svm.core.posix.headers.Fcntl.O_CLOEXEC;
import static com.oracle.svm.core.posix.headers.Fcntl.O_CREAT;
import static com.oracle.svm.core.posix.headers.Fcntl.O_NOFOLLOW;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDONLY;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDWR;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.DirectByteBufferUtil;
import com.oracle.svm.core.jdk.UninterruptibleUtils.ASCII;
import com.oracle.svm.core.jdk.management.Target_jdk_internal_vm_VMSupport;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.jvmstat.PerfMemoryPrologue;
import com.oracle.svm.core.jvmstat.ExternalPerfDataProvider;
import com.oracle.svm.core.jvmstat.PerfMemoryProvider;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.posix.headers.Dirent;
import com.oracle.svm.core.posix.headers.Dirent.DIR;
import com.oracle.svm.core.posix.headers.Dirent.dirent;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Mman;
import com.oracle.svm.core.posix.headers.PosixFile;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.BasedOnJDKFile;

import jdk.graal.compiler.core.common.NumUtil;

/// Local perfdata creation still uses JDK services; running that path during uninterruptible
/// isolate startup requires a rewrite (GR-40601).
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
class PosixPerfMemoryProvider implements PerfMemoryProvider, ExternalPerfDataProvider {
    /// Prefix shared by perfdata directory lookup and creation.
    private static final String PERFDATA_NAME = "hsperfdata";

    private String backingFilePath;

    @Platforms(Platform.HOSTED_ONLY.class)
    PosixPerfMemoryProvider() {
    }

    /// Attaches to another process through the POSIX shared-memory implementation.
    @Override
    public ByteBuffer attach(int vmId) throws IOException {
        return mmapAttachShared(vmId);
    }

    /// Attaches through the secure directory and file checks used by HotSpot perfMemory.
    /// The file is opened with `O_NOFOLLOW`, validated and mapped through the same descriptor.
    /// Reopening the pathname after validation would allow a replacement file to bypass the checks.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.4-b23/src/hotspot/os/posix/perfMemory_posix.cpp#L1162-L1236")
    private static ByteBuffer mmapAttachShared(int vmId) throws IOException {
        int[] namespacePid = {Platform.includedIn(LINUX.class) ? getNamespacePid(vmId) : -1};
        String user = getUserName(vmId, namespacePid);
        if (user == null) {
            throw new IllegalArgumentException("Could not map vmid to user Name");
        }
        String directory = getUserTmpDir(user, vmId, namespacePid[0]);
        try (CTypeConversion.CCharPointerHolder dir = CTypeConversion.toCString(directory)) {
            if (!isDirectorySecure(dir.get())) {
                throw new IllegalArgumentException("Process not found");
            }
        }
        String filename = directory + "/" + getSharedMemFileName(vmId, namespacePid[0]);
        return mapSharedMemFile(filename);
    }

    /// Finds the owner of the JVM identified by `vmId`, updating `namespacePid[0]` for the Linux fallback.
    /// The namespace PID is kept in Java memory because the directory search can unmount a virtual thread.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L637-L650")
    private static String getUserName(int vmId, int[] namespacePid) throws IOException {
        String result = getUserNameSlow(vmId, namespacePid[0]);
        if (result == null && Platform.includedIn(LINUX.class)) {
            result = getUserNameSlow(vmId, vmId);
            if (result != null) {
                namespacePid[0] = vmId;
            }
        }
        return result;
    }

    /// Captures the liveness check's error before any safepoint or native transition can overwrite it.
    @Uninterruptible(reason = "The native call and errno read must be in the same uninterruptible region.")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L500-L508")
    private static int checkProcessExists(int vmId) {
        return Signal.NoTransitions.kill(vmId, 0) == -1 ? LibC.errno() : 0;
    }

    /// Finds the user directory containing the newest matching perfdata file.
    /// Matching directory names must contain an underscore before extracting the username.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L497-L633")
    private static String getUserNameSlow(int vmId, int namespaceVmId) throws IOException {
        // Repeat this check for the namespace fallback, as get_user_name_slow does in HotSpot.
        int errno = checkProcessExists(vmId);
        if (errno == ESRCH()) {
            throw new IllegalArgumentException("Process not found");
        } else if (errno != 0) {
            throw new IOException(PosixUtils.strerror(errno));
        }
        String tmpDirName = Target_jdk_internal_vm_VMSupport.getVMTemporaryDirectory();
        if (Platform.includedIn(LINUX.class) && namespaceVmId != -1) {
            tmpDirName = "/proc/" + vmId + "/root" + tmpDirName;
        }
        int searchPid = namespaceVmId == -1 ? vmId : namespaceVmId;
        int tmpDirFd;
        try (CTypeConversion.CCharPointerHolder path = CTypeConversion.toCString(tmpDirName)) {
            tmpDirFd = Fcntl.NoTransitions.restartableOpen(path.get(), O_RDONLY() | O_CLOEXEC(), 0);
        }
        if (tmpDirFd == -1) {
            return null;
        }
        DIR tmpDir = Dirent.fdopendir(tmpDirFd);
        if (tmpDir.isNull()) {
            Unistd.NoTransitions.close(tmpDirFd);
            return null;
        }

        String newestUser = null;
        long newestCreation = 0;
        try {
            dirent entry;
            while ((entry = Dirent.readdir(tmpDir)).isNonNull()) {
                // Filter native names before allocating Java strings for matching directories.
                if (!ASCII.startsWith(entry.d_name(), PERFDATA_NAME)) {
                    continue;
                }
                String name = CTypeConversion.toJavaString(entry.d_name());
                int separator = name.indexOf('_');
                if (separator == -1) {
                    continue;
                }
                String userDirectory = tmpDirName + "/" + name;
                try (CTypeConversion.CCharPointerHolder path = CTypeConversion.toCString(userDirectory);
                                SecureDirectory directory = openDirectorySecure(path.get())) {
                    if (directory == null || !isDirectorySecure(path.get())) {
                        continue;
                    }
                    dirent fileEntry;
                    while ((fileEntry = Dirent.readdir(directory.dir)).isNonNull()) {
                        if (filenameToPid(CTypeConversion.toJavaString(fileEntry.d_name())) != searchPid) {
                            continue;
                        }
                        Path file = Paths.get(userDirectory, CTypeConversion.toJavaString(fileEntry.d_name()));
                        try (CTypeConversion.CCharPointerHolder filePath = CTypeConversion.toCString(file.toString())) {
                            long creationTime = getPerfDataFileCreationTime(filePath.get());
                            if (creationTime > newestCreation) {
                                newestCreation = creationTime;
                                newestUser = name.substring(separator + 1);
                            }
                        }
                    }
                }
            }
        } finally {
            Dirent.closedir(tmpDir);
        }
        return newestUser;
    }

    /// Gets the change time of a nonempty regular perfdata file, or zero if it is not a candidate.
    /// The stat buffer and all reads from it must stay in one uninterruptible region so a virtual
    /// thread cannot unmount and invalidate the native stack address.
    @Uninterruptible(reason = "Keep the stack-allocated stat buffer valid when called from a virtual thread.")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L497-L633")
    private static long getPerfDataFileCreationTime(CCharPointer filename) {
        PosixStat.stat stat = StackValue.get(PosixStat.sizeOfStatStruct());
        if (PosixStat.restartableLstat(filename, stat) == -1 || !PosixStat.S_ISREG(stat) || stat.st_size() <= 0) {
            return 0;
        }
        return stat.st_ctime();
    }

    /// Maps the checked descriptor read-only, with HotSpot's single attempt and failure exception.
    /// The cleaner owns the mapping only after buffer construction succeeds; until then this method
    /// must unmap it on failure. The descriptor is closed on every exit, including invalid sizes.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.4-b23/src/hotspot/os/posix/perfMemory_posix.cpp#L1162-L1236")
    private static ByteBuffer mapSharedMemFile(String filename) throws IOException {
        int fd;
        try (CTypeConversion.CCharPointerHolder path = CTypeConversion.toCString(filename)) {
            fd = openSharedMemFile(path.get());
        }
        if (fd < 0) {
            int error = -fd;
            if (error == ENOENT()) {
                throw new IllegalArgumentException("Process not found");
            } else if (error == EACCES()) {
                throw new IllegalArgumentException("Permission denied");
            }
            throw new IOException(PosixUtils.strerror(error));
        }
        try {
            if (!isFileSecure(fd)) {
                // HotSpot returns no mapping, without an exception, for an insecure file.
                return DirectByteBufferUtil.allocate(0, 0);
            }
            long size = sharedMemFileSize(fd);
            if (size == -1) {
                throw new IOException("Could not determine PerfMemory size");
            }
            if (size == 0 || size % Unistd.getpagesize() != 0) {
                throw new IOException("Invalid PerfMemory size");
            }
            Pointer address = Mman.mmap(Word.nullPointer(), Word.unsigned(size), Mman.PROT_READ(), Mman.MAP_SHARED(), fd, 0);
            if (address == Mman.MAP_FAILED()) {
                throw new OutOfMemoryError("Could not map PerfMemory");
            }
            try {
                if (size > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("PerfMemory size exceeds ByteBuffer capacity");
                }
                return DirectByteBufferUtil.allocateMapped((int) size, address.rawValue(), new CleanerAction(address.rawValue(), size));
            } catch (Throwable t) {
                Mman.munmap(address, Word.unsigned(size));
                throw t;
            }
        } finally {
            Unistd.NoTransitions.close(fd);
        }
    }

    /// Opens an attachment file, returning its descriptor or the negated native error number.
    /// Native calls and error reads must stay uninterruptible, including error capture before close.
    /// `O_CLOEXEC` prevents inheritance across exec; directories are rejected as in HotSpot's `os::open`.
    @Uninterruptible(reason = "The native call and errno read must be in the same uninterruptible region.")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L973-L1001")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/linux/os_linux.cpp#L4971-L5044")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/bsd/os_bsd.cpp#L2343-L2393")
    private static int openSharedMemFile(CCharPointer filename) {
        int fd = Fcntl.NoTransitions.restartableOpen(filename, O_RDONLY() | O_NOFOLLOW() | O_CLOEXEC(), 0);
        if (fd == -1) {
            return -LibC.errno();
        }
        PosixStat.stat stat = StackValue.get(PosixStat.sizeOfStatStruct());
        int result = PosixStat.restartableFstat(fd, stat);
        int error = result == -1 ? LibC.errno() : PosixStat.S_ISDIR(stat) ? EISDIR() : 0;
        if (error != 0) {
            Unistd.NoTransitions.close(fd);
            return -error;
        }
        return fd;
    }

    /// Reads the size from the same descriptor that was checked and will be mapped.
    @Uninterruptible(reason = "Keep the stack-allocated stat buffer valid when called from a virtual thread.")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L1133-L1154")
    private static long sharedMemFileSize(int fd) {
        PosixStat.stat stat = StackValue.get(PosixStat.sizeOfStatStruct());
        return PosixStat.restartableFstat(fd, stat) == -1 ? -1 : stat.st_size();
    }

    /// Unmaps an external shared region whose lifetime has ended.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.4-b23/src/hotspot/os/posix/perfMemory_posix.cpp#L1090-L1104")
    private static void unmapShared(long address, long size) {
        Mman.munmap(Word.pointer(address), Word.unsigned(size));
    }

    /// Releases an external mapping without retaining the buffer watched by its cleaner.
    /// Only the native address and size are retained: keeping the buffer or one of its views here
    /// would prevent the cleaner's referent from becoming phantom reachable.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.4-b23/src/java.base/share/classes/jdk/internal/perf/Perf.java#L144-L160")
    private static final class CleanerAction implements Runnable {
        /// Address of the mapping owned by this cleaner.
        private final long address;

        /// Size of the mapping owned by this cleaner.
        private final long size;

        /// Records the mapping whose lifetime is controlled by the buffer cleaner.
        CleanerAction(long address, long size) {
            this.address = address;
            this.size = size;
        }

        /// Unmaps once and contains unexpected exceptions, as the JDK's CleanerAction does.
        @Override
        public void run() {
            try {
                unmapShared(address, size);
            } catch (Throwable th) {
                assert false : th.toString();
            }
        }
    }

    /// Gets the PID in the first nested namespace, or indicates that no such namespace exists.
    @Platforms(LINUX.class)
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/linux/os_linux.cpp#L4331-L4361")
    private static int getNamespacePid(int vmId) {
        Path status = Paths.get("/proc", Integer.toString(vmId), "status");
        try (BufferedReader reader = Files.newBufferedReader(status)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("NSpid:")) {
                    int index = "NSpid:".length();
                    for (int column = 0; column < 2; column++) {
                        while (index < line.length() && (line.charAt(index) == ' ' || line.charAt(index) == '\t')) {
                            index++;
                        }
                        int start = index;
                        while (index < line.length() && line.charAt(index) >= '0' && line.charAt(index) <= '9') {
                            index++;
                        }
                        if (index == start) {
                            return -1;
                        }
                        if (column == 1) {
                            return Integer.parseInt(line, start, index, 10);
                        }
                    }
                    return -1;
                }
            }
        } catch (IOException | NumberFormatException e) {
        }
        return -1;
    }

    /**
     * Create a named shared memory region. Returns the address of the memory region on success or
     * null on failure. A return value of null will ultimately disable the shared memory feature.
     */
    @Override
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L1015-L1082")
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
            cleanupSharedMemFiles(d.get(), vmId);

            try (CTypeConversion.CCharPointerHolder f = CTypeConversion.toCString(fileName)) {
                fd = createSharedMemFile(d.get(), f.get(), size);
            }
        }

        if (fd == -1) {
            return null;
        }

        Pointer mapAddress = Mman.mmap(Word.nullPointer(), Word.unsigned(size), Mman.PROT_READ() | Mman.PROT_WRITE(), Mman.MAP_SHARED(), fd, 0);

        int result = Unistd.NoTransitions.close(fd);
        assert result != -1;

        String filePath = dirName + "/" + fileName;
        if (mapAddress == Mman.MAP_FAILED()) {
            restartableUnlink(filePath);
            return null;
        }

        backingFilePath = filePath;

        /* Clear the shared memory region. */
        LibC.memset(mapAddress, Word.signed(0), Word.unsigned(size));
        return DirectByteBufferUtil.allocate(mapAddress.rawValue(), size);
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L134-L157")
    private static String getUserTmpDir(String user, int vmId, int nsPid) {
        String tmpDir = Target_jdk_internal_vm_VMSupport.getVMTemporaryDirectory();
        if (Platform.includedIn(LINUX.class)) {
            if (nsPid != -1) {
                /* Use different directory if we have a containerized process on Linux. */
                tmpDir = "/proc/" + vmId + "/root" + tmpDir;
            }
        }
        return tmpDir + "/" + PERFDATA_NAME + "_" + user;
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L657-L668")
    private static String getSharedMemFileName(int vmId, int nspid) {
        int pid = vmId;
        if (Platform.includedIn(LINUX.class) && nspid != -1) {
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
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L703-L807")
    private static void cleanupSharedMemFiles(CCharPointer directoryPath, int selfPid) {
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

                int fileFd = -1;
                if (Platform.includedIn(LINUX.class)) {
                    /*
                     * On Linux, different containerized processes may have the same pid and share
                     * the /tmp directory. So, use file locking to claim ownership of the file.
                     */
                    fileFd = Fcntl.NoTransitions.restartableOpenat(s.fd, entry.d_name(), O_RDONLY(), 0);
                    if (fileFd == -1) {
                        continue;
                    }

                    int n = PosixFile.restartableFlock(fileFd, PosixFile.LOCK_EX() | PosixFile.LOCK_NB());
                    if (n != 0) {
                        Unistd.NoTransitions.close(fileFd);
                        continue;
                    }
                }

                /*
                 * We now have a file name that converts to a valid integer that could represent a
                 * process id. If this process id matches the current process id or the process is
                 * not running, then remove the stale file resources.
                 */
                if (pid == selfPid || canFileBeDeleted(pid)) {
                    Fcntl.NoTransitions.unlinkat(s.fd, entry.d_name(), 0);
                }

                if (Platform.includedIn(LINUX.class)) {
                    /* Close the file and release the lock. */
                    Unistd.NoTransitions.close(fileFd);
                }
            }
        }
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/hotspot/os/posix/perfMemory_posix.cpp#L852-L966")
    private static int createSharedMemFile(CCharPointer directoryPath, CCharPointer filename, int size) {
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

        if (Platform.includedIn(LINUX.class)) {
            /*
             * On Linux, different containerized processes may have the same pid and share the /tmp
             * directory. So, try to claim exclusive ownership of the file before manipulating the
             * file.
             */
            int n = PosixFile.restartableFlock(fd, PosixFile.LOCK_EX() | PosixFile.LOCK_NB());
            if (n != 0) {
                Unistd.NoTransitions.close(fd);
                return -1;
            }
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
        RawFileDescriptor rawFd = Word.signed(fd);
        int pageSize = NumUtil.safeToInt(VirtualMemoryProvider.get().getGranularity().rawValue());

        boolean success = true;
        for (int pos = 0; pos < size; pos += pageSize) {
            success = fs.seek(rawFd, pos);
            if (!success) {
                break;
            }
            success = fs.writeInt(rawFd, 0);
            if (!success) {
                removePerfFile(directoryPath, filename);
                break;
            }
        }

        if (!success) {
            Unistd.NoTransitions.close(fd);
            return -1;
        }

        return fd;
    }

    private static void removePerfFile(CCharPointer directoryPath, CCharPointer filename) {
        try (SecureDirectory directory = openDirectorySecure(directoryPath)) {
            if (directory != null) {
                Fcntl.NoTransitions.unlinkat(directory.fd, filename, 0);
            }
        }
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
            return Fcntl.NoTransitions.restartableOpenat(s.fd, filename, O_RDWR() | O_CREAT() | O_NOFOLLOW(), PosixStat.S_IRUSR() | PosixStat.S_IWUSR());
        }
    }

    /// Converts an ASCII decimal file name to a positive process ID, or returns zero for a different name.
    /// Rejects overflow rather than truncating a native `long` to `pid_t`, which could produce a
    /// negative ID and make a later liveness check target a process group instead of one process.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L162-L191")
    private static int filenameToPid(String filename) {
        if (filename.isEmpty()) {
            return 0;
        }
        // Integer.parseInt accepts Unicode digits, unlike HotSpot's PID file-name parser.
        for (int i = 0; i < filename.length(); i++) {
            char digit = filename.charAt(i);
            if (digit < '0' || digit > '9') {
                return 0;
            }
        }
        try {
            return Integer.parseInt(filename);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L814-L845")
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

    /// Opens and checks a directory without following its final symlink.
    /// `fdopendir` must consume the checked descriptor so a pathname replacement cannot redirect
    /// the scan. This avoids HotSpot's second open and the comparison of the two descriptors.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L291-L341")
    private static SecureDirectory openDirectorySecure(CCharPointer directory) {
        int fd = Fcntl.NoTransitions.restartableOpen(directory, O_RDONLY() | O_NOFOLLOW() | O_CLOEXEC(), 0);
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
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L230-L241")
    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static boolean isDirectorySecure(CCharPointer directory) {
        PosixStat.stat buf = StackValue.get(PosixStat.sizeOfStatStruct());
        int result = PosixStat.restartableLstat(directory, buf);
        if (result == -1) {
            return false;
        }
        return isStatBufSecure(buf);
    }

    /// Checks the type, permissions and owner of an already opened directory.
    /// The caller must open with `O_NOFOLLOW`: `fstat` cannot detect a symlink followed during open.
    /// The stack buffer must remain in one uninterruptible region so virtual-thread unmounts cannot
    /// invalidate its native address.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L249-L260")
    @Uninterruptible(reason = "Keep the stack-allocated stat buffer valid when called from a virtual thread.")
    private static boolean isDirFdSecure(int dirFd) {
        PosixStat.stat buf = StackValue.get(PosixStat.sizeOfStatStruct());
        int result = PosixStat.restartableFstat(dirFd, buf);
        if (result == -1) {
            return false;
        }
        return isStatBufSecure(buf);
    }

    /// Rejects files with multiple hard links, as HotSpot's `is_file_secure` does.
    /// The caller must reject directories and open with `O_NOFOLLOW` before using this check.
    /// The stack buffer must remain in one uninterruptible region so virtual-thread unmounts cannot
    /// invalidate its native address.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L408-L429")
    @Uninterruptible(reason = "Keep the stack-allocated stat buffer valid when called from a virtual thread.")
    private static boolean isFileSecure(int fd) {
        PosixStat.stat buf = StackValue.get(PosixStat.sizeOfStatStruct());
        int result = PosixStat.restartableFstat(fd, buf);
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
    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L199-L222")
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

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L675-L691")
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
            result = Unistd.NoTransitions.ftruncate(fd, Word.signed(size));
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @Override
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L1112-L1128")
    public void teardown() {
        if (backingFilePath != null) {
            restartableUnlink(backingFilePath);
            backingFilePath = null;
        }
    }

    private static final class SecureDirectory implements AutoCloseable {
        /// Descriptor validated before it is passed to fdopendir.
        final int fd;

        /// Directory stream owning the validated descriptor.
        final DIR dir;

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
class PosixPerfMemoryFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            return false;
        }
        return VMInspectionOptions.hasJvmstatSupport();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        PosixPerfMemoryProvider provider = new PosixPerfMemoryProvider();
        // External attachment is independent of whether this process exports a mapped perfdata file.
        ImageSingletons.add(ExternalPerfDataProvider.class, provider);
        if (PerfDataMemoryMappedFile.getValue()) {
            ImageSingletons.add(PerfMemoryProvider.class, provider);
        }
    }
}

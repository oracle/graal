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
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.jvmstat.PerfMemoryPrologue;
import com.oracle.svm.core.jvmstat.PerfMemoryProvider;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

/**
 * This class uses high-level JDK features at the moment. In the future, we will need to rewrite
 * this code so that it can be executed during the isolate startup (i.e., in uninterruptible code),
 * see GR-40601.
 */
class PosixPerfMemoryProvider implements PerfMemoryProvider {
    // Prefix of performance data file.
    private static final String PERFDATA_NAME = "hsperfdata";

    private Path backingStoreFile;

    @Platforms(Platform.HOSTED_ONLY.class)
    PosixPerfMemoryProvider() {
    }

    /**
     * Create a named shared memory region. Returns the address of the memory region on success or
     * NULL on failure. A return value of NULL will ultimately disable the shared memory feature.
     *
     * On Solaris and Bsd, the name space for shared memory objects is the file system name space.
     *
     * A monitoring application attaching to a SubstrateVM does not need to know the file system
     * name of the shared memory object. However, it may be convenient for applications to discover
     * the existence of newly created and terminating SubstrateVMs by watching the file system name
     * space for files being created or removed.
     */
    @Override
    public MappedByteBuffer create() {
        assert backingStoreFile == null;

        int size = NumUtil.roundUp(PerfManager.Options.PerfDataMemorySize.getValue(), Unistd.getpagesize());
        if (size <= PerfMemoryPrologue.getPrologueSize()) {
            return null;
        }

        String shortName = String.valueOf(Unistd.getpid());
        String user = System.getProperty("user.name");
        String tmpdir = Target_jdk_internal_vm_VMSupport.getVMTemporaryDirectory();

        if (user.equals("?")) {
            return null;
        }
        String dirname = String.format("%s_%s", PERFDATA_NAME, user);
        Path perfDir = Paths.get(tmpdir, dirname);
        Path filename = perfDir.resolve(shortName);

        // cleanup any stale jmvstat files
        cleanupOldJvmstatFiles(perfDir, shortName);

        assert size > 0 : "unexpected PerfDataBuffer region size";

        MappedByteBuffer buffer = createSharedBuffer(perfDir, filename, user, size);
        if (buffer == null) {
            return null;
        }
        // save the file name for use in teardown()
        backingStoreFile = filename;
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    private static void cleanupOldJvmstatFiles(Path perfDir, String selfName) {
        File[] files = perfDir.toFile().listFiles();

        if (files == null) {
            return;
        }
        for (File f : files) {
            String name = f.getName();
            int pid = getPidFromFile(f);

            if (pid == 0) {
                // attempt to remove all unexpected files
                f.delete();
                continue;
            }

            /*
             * We now have a file name that converts to a valid integer that could represent a
             * process id. If this process id matches the current process id or the process is not
             * running, then remove the stale file resources.
             * 
             * Process liveness is detected by sending signal number 0 to the process id (see
             * kill(2)). If kill determines that the process does not exist, then the file resources
             * are removed. If kill determines that we don't have permission to signal the process,
             * then the file resources are assumed to be stale and are removed because the resources
             * for such a process should be in a different user specific directory.
             */
            if (name.equals(selfName)) {
                f.delete();
            } else {
                int ret = Signal.kill(pid, 0);

                if (ret == -1) {
                    int errno = LibC.errno();
                    if (errno == Errno.ESRCH() || errno == Errno.EPERM()) {
                        f.delete();
                    }
                }
            }
        }
    }

    /**
     * Convert the given file name into a process id. If the file does not meet the file naming
     * constraints, return 0.
     */
    private static int getPidFromFile(File file) {
        String name = file.getName();

        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Create the shared memory file resources.
     *
     * This method creates the shared memory file with the given size. This method also creates the
     * user specific temporary directory, if it does not yet exist.
     */
    private static MappedByteBuffer createSharedBuffer(Path perfDir, Path file, String userName, int size) {

        // make the user temporary directory
        if (!createUserDir(perfDir, userName)) {
            // could not make/find the directory or the found directory
            // was not secure
            return null;
        }

        // create a file with a set of specified attributes
        Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE);
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
        EnumSet<StandardOpenOption> options = EnumSet.of(CREATE_NEW, WRITE, READ);
        try {
            FileChannel channel = FileChannel.open(file, options, attr);
            return channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
        } catch (IOException ex) {
            // log exception
            return null;
        }
    }

    private static boolean createUserDir(Path userDir, String userName) {
        Path dir = userDir;
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
                            GROUP_READ, GROUP_EXECUTE,
                            OTHERS_READ, OTHERS_EXECUTE);
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
            dir = Files.createDirectories(dir, attr);
            return isDirSecure(dir, userName);
        } catch (IOException ex) {
            // log exception
            return false;
        }
    }

    /**
     * Check if the given dir is considered a secure directory for the backing store files. Returns
     * true if the directory is considered a secure location. Returns false if the dir is a symbolic
     * link or if an error occurred.
     */
    private static boolean isDirSecure(Path dir, String userName) {
        try {
            if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) {
                // The path represents a link or some non-directory file type,
                // which is not what we expected. Declare it insecure.
                return false;
            }
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(dir, NOFOLLOW_LINKS);

            if (perms.contains(GROUP_WRITE) || perms.contains(OTHERS_WRITE)) {
                // The directory is open for writing and could be subjected
                // to a symlink or a hard link attack. Declare it insecure.
                return false;
            }
            // If user is not root then see if the uid of the directory matches the effective uid of
            // the process.
            int euid = Unistd.getegid();
            UserPrincipal euser = Files.getOwner(dir, NOFOLLOW_LINKS);
            if ((euid != 0) && (!euser.getName().equals(userName))) {
                // The directory was not created by this user. Declare it insecure.
                return false;
            }
        } catch (IOException ex) {
            // log exception
            return false;
        }
        return true;
    }

    @Override
    public void teardown() {
        if (backingStoreFile != null) {
            backingStoreFile.toFile().delete();
            backingStoreFile = null;
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

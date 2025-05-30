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

package com.oracle.svm.core.jdk.runtimeinit;

import java.nio.file.FileSystem;
import java.nio.file.spi.FileSystemProvider;
import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDKInitializedAtRunTime;

/**
 * This file contains substitutions that are required for initializing {@link FileSystemProvider} at
 * image run time. Other related functionality (general and build time initialization) can be found
 * in {@link com.oracle.svm.core.jdk.FileSystemProviderSupport}.
 *
 * @see JDKInitializedAtRunTime
 * @see com.oracle.svm.core.jdk.FileSystemProviderSupport
 */
final class FileSystemProviderRuntimeInitSupport {
}

// java.io

@TargetClass(className = "java.io.FileSystem", onlyWith = JDKInitializedAtRunTime.class)
final class Target_java_io_FileSystem_RunTime {
}

@TargetClass(className = "java.io.File", onlyWith = JDKInitializedAtRunTime.class)
@SuppressWarnings("unused")
final class Target_java_io_File_RunTime {
    @Alias //
    @InjectAccessors(DefaultFileSystemAccessor.class) //
    private static Target_java_io_FileSystem_RunTime FS;
}

@TargetClass(className = "java.io.DefaultFileSystem", onlyWith = JDKInitializedAtRunTime.class)
final class Target_java_io_DefaultFileSystem_RunTime {
    @Alias
    static native Target_java_io_FileSystem_RunTime getFileSystem();
}

/**
 * Holds the default java.io file system. Initialized at run time via
 * {@code JDKInitializationFeature}.
 */
class DefaultFileSystemHolder {
    static final Target_java_io_FileSystem_RunTime FS;
    static {
        if (SubstrateUtil.HOSTED) {
            // unused - layered images might want to initialize during image build
            FS = null;
        } else {
            FS = Target_java_io_DefaultFileSystem_RunTime.getFileSystem();
        }
    }
}

class DefaultFileSystemAccessor {
    @SuppressWarnings("unused")
    static Target_java_io_FileSystem_RunTime get() {
        return Objects.requireNonNull(DefaultFileSystemHolder.FS);
    }
}

// sun.nio.fs

@TargetClass(className = "sun.nio.fs.DefaultFileSystemProvider", onlyWith = JDKInitializedAtRunTime.class)
final class Target_sun_nio_fs_DefaultFileSystemProvider_RunTime {
    @Alias
    static native FileSystem theFileSystem();
}

/**
 * Holds the default sun.nio.fs file system. Initialized at run time via
 * {@code JDKInitializationFeature}.
 */
class SunNioFsDefaultFileSystemHolder {
    static final FileSystem FS;

    static {
        if (SubstrateUtil.HOSTED) {
            // unused - layered images might want to initialize during image build
            FS = null;
        } else {
            FS = Target_sun_nio_fs_DefaultFileSystemProvider_RunTime.theFileSystem();
        }
    }
}

@TargetClass(className = "sun.nio.fs.UnixFileSystem", onlyWith = JDKInitializedAtRunTime.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_nio_fs_UnixFileSystem_RunTime {
}

@TargetClass(className = "sun.nio.fs.UnixPath", onlyWith = JDKInitializedAtRunTime.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_nio_fs_UnixPath_RunTime {
    @Alias //
    @InjectAccessors(UnixFileSystemAccessor.class) //
    private Target_sun_nio_fs_UnixFileSystem_RunTime fs;
}

@SuppressWarnings("unused")
class UnixFileSystemAccessor {
    static Target_sun_nio_fs_UnixFileSystem_RunTime get(Target_sun_nio_fs_UnixPath_RunTime that) {
        return Objects.requireNonNull(SubstrateUtil.cast(SunNioFsDefaultFileSystemHolder.FS, Target_sun_nio_fs_UnixFileSystem_RunTime.class));
    }

    static void set(Target_sun_nio_fs_UnixPath_RunTime that, Target_sun_nio_fs_UnixFileSystem_RunTime value) {
        /*
         * `value` should always be DefaultFileSystemProvider.INSTANCE.theFileSystem() but we cannot
         * check that here because it would introduce a class initialization cycle.
         */
    }
}

@TargetClass(className = "sun.nio.fs.WindowsFileSystem", onlyWith = JDKInitializedAtRunTime.class)
@Platforms(Platform.WINDOWS.class)
final class Target_sun_nio_fs_WindowsFileSystem_RunTime {
}

@TargetClass(className = "sun.nio.fs.WindowsPath", onlyWith = JDKInitializedAtRunTime.class)
@Platforms(Platform.WINDOWS.class)
final class Target_sun_nio_fs_WindowsPath_RunTime {
    @Alias //
    @InjectAccessors(WindowsFileSystemAccessor.class) //
    private Target_sun_nio_fs_WindowsFileSystem_RunTime fs;
}

@SuppressWarnings("unused")
class WindowsFileSystemAccessor {
    static Target_sun_nio_fs_WindowsFileSystem_RunTime get(Target_sun_nio_fs_WindowsPath_RunTime that) {
        return Objects.requireNonNull(SubstrateUtil.cast(SunNioFsDefaultFileSystemHolder.FS, Target_sun_nio_fs_WindowsFileSystem_RunTime.class));
    }

    static void set(Target_sun_nio_fs_WindowsPath_RunTime that, Target_sun_nio_fs_WindowsFileSystem_RunTime value) {
        /*
         * `value` should always be DefaultFileSystemProvider.INSTANCE.theFileSystem() but we cannot
         * check that here because it would introduce a class initialization cycle.
         */
    }
}

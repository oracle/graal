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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.FileSystemProvidersInitializedAtRunTime;
import com.oracle.svm.core.jdk.buildtimeinit.FileSystemProviderBuildTimeInitSupport;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystemProvider;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

/**
 * This file contains substitutions that are required for initializing {@link FileSystemProvider} at
 * image {@linkplain FileSystemProvidersInitializedAtRunTime run time}. Build-time initialization
 * related functionality can be found in {@link FileSystemProviderBuildTimeInitSupport}.
 */
public final class FileSystemProviderRunTimeInitSupport {
}

@AutomaticallyRegisteredFeature
final class FileSystemProviderRunTimeInitFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return FutureDefaultsOptions.fileSystemProvidersInitializedAtRunTime();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (FutureDefaultsOptions.fileSystemProvidersInitializedAtRunTime()) {
            /*
             * Explicitly register NativeImageResourceFileSystemProvider for reflective
             * instantiation. Normally, the ServiceLoaderFeature does this as well, but in case it
             * is turned off (-H:-UseServiceLoaderFeature), we should still at least register our
             * own provider.
             */
            RuntimeReflection.register(NativeImageResourceFileSystemProvider.class);
            RuntimeReflection.registerForReflectiveInstantiation(NativeImageResourceFileSystemProvider.class);
        }
    }
}

// java.io

@TargetClass(className = "java.io.FileSystem", onlyWith = FileSystemProvidersInitializedAtRunTime.class)
final class Target_java_io_FileSystem_RunTime {
}

@TargetClass(className = "java.io.File", onlyWith = FileSystemProvidersInitializedAtRunTime.class)
@SuppressWarnings("unused")
final class Target_java_io_File_RunTime {
    @Alias //
    @InjectAccessors(DefaultFileSystemAccessor.class) //
    private static Target_java_io_FileSystem_RunTime FS;
}

@TargetClass(className = "java.io.DefaultFileSystem", onlyWith = FileSystemProvidersInitializedAtRunTime.class)
final class Target_java_io_DefaultFileSystem_RunTime {
    @Alias
    static native Target_java_io_FileSystem_RunTime getFileSystem();
}

/**
 * Holds the default java.io file system. Initialized at run time via
 * {@code JDKInitializationFeature}. This cache is needed because
 * {@link Target_java_io_DefaultFileSystem_RunTime#getFileSystem()} creates a new instance for every
 * time. In the JDK, this method is called only once.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+25/src/java.base/unix/classes/java/io/DefaultFileSystem.java#L39-L41")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+25/src/java.base/windows/classes/java/io/DefaultFileSystem.java#L39-L41")
class DefaultFileSystemHolder {
    static final Object FS;
    static {
        if (SubstrateUtil.HOSTED) {
            /*
             * Should be unused, but layered images might want to initialize it during image build.
             * We set it to the real default file system instead of null to guard against
             * unintentional usages. In run-time init mode, we don't allow FileSystems in the image
             * heap, so we would fail the image build with an exception if it happens, which helps
             * to detect problems.
             */
            var defaultFileSystem = ReflectionUtil.lookupClass("java.io.DefaultFileSystem");
            var getFileSystem = ReflectionUtil.lookupMethod(defaultFileSystem, "getFileSystem");
            FS = ReflectionUtil.invokeMethod(getFileSystem, null);
        } else {
            FS = Target_java_io_DefaultFileSystem_RunTime.getFileSystem();
        }
    }
}

class DefaultFileSystemAccessor {
    @SuppressWarnings("unused")
    static Target_java_io_FileSystem_RunTime get() {
        VMError.guarantee(DefaultFileSystemHolder.FS != null, "DefaultFileSystemHolder.FS is null");
        return SubstrateUtil.cast(DefaultFileSystemHolder.FS, Target_java_io_FileSystem_RunTime.class);
    }
}

// sun.nio.fs

@TargetClass(className = "sun.nio.fs.DefaultFileSystemProvider", onlyWith = FileSystemProvidersInitializedAtRunTime.class)
final class Target_sun_nio_fs_DefaultFileSystemProvider_RunTime {
    @Alias
    static native FileSystem theFileSystem();
}

@TargetClass(className = "sun.nio.fs.UnixFileSystem", onlyWith = FileSystemProvidersInitializedAtRunTime.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_nio_fs_UnixFileSystem_RunTime {
}

@TargetClass(className = "sun.nio.fs.UnixPath", onlyWith = FileSystemProvidersInitializedAtRunTime.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_nio_fs_UnixPath_RunTime {
    @Alias //
    @InjectAccessors(UnixFileSystemAccessor.class) //
    private Target_sun_nio_fs_UnixFileSystem_RunTime fs;
}

@SuppressWarnings("unused")
class UnixFileSystemAccessor {
    static Target_sun_nio_fs_UnixFileSystem_RunTime get(Target_sun_nio_fs_UnixPath_RunTime that) {
        FileSystem theFileSystem = Target_sun_nio_fs_DefaultFileSystemProvider_RunTime.theFileSystem();
        VMError.guarantee(theFileSystem != null, "DefaultFileSystemProvider.theFileSystem() is null");
        return SubstrateUtil.cast(theFileSystem, Target_sun_nio_fs_UnixFileSystem_RunTime.class);
    }

    static void set(Target_sun_nio_fs_UnixPath_RunTime that, Target_sun_nio_fs_UnixFileSystem_RunTime value) {
        /*
         * `value` should always be DefaultFileSystemProvider.INSTANCE.theFileSystem() but we cannot
         * check that here because it would introduce a class initialization cycle.
         */
    }
}

@TargetClass(className = "sun.nio.fs.WindowsFileSystem", onlyWith = FileSystemProvidersInitializedAtRunTime.class)
@Platforms(Platform.WINDOWS.class)
final class Target_sun_nio_fs_WindowsFileSystem_RunTime {
}

@TargetClass(className = "sun.nio.fs.WindowsPath", onlyWith = FileSystemProvidersInitializedAtRunTime.class)
@Platforms(Platform.WINDOWS.class)
final class Target_sun_nio_fs_WindowsPath_RunTime {
    @Alias //
    @InjectAccessors(WindowsFileSystemAccessor.class) //
    private Target_sun_nio_fs_WindowsFileSystem_RunTime fs;
}

@SuppressWarnings("unused")
class WindowsFileSystemAccessor {
    static Target_sun_nio_fs_WindowsFileSystem_RunTime get(Target_sun_nio_fs_WindowsPath_RunTime that) {
        FileSystem theFileSystem = Target_sun_nio_fs_DefaultFileSystemProvider_RunTime.theFileSystem();
        VMError.guarantee(theFileSystem != null, "DefaultFileSystemProvider.theFileSystem() is null");
        return SubstrateUtil.cast(theFileSystem, Target_sun_nio_fs_WindowsFileSystem_RunTime.class);
    }

    static void set(Target_sun_nio_fs_WindowsPath_RunTime that, Target_sun_nio_fs_WindowsFileSystem_RunTime value) {
        /*
         * `value` should always be DefaultFileSystemProvider.INSTANCE.theFileSystem() but we cannot
         * check that here because it would introduce a class initialization cycle.
         */
    }
}

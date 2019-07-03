/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.spi.FileSystemProvider;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.util.VMError;

@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
@CLibrary(value = "nio", requireStatic = true)
class WindowsJavaNIOSubstituteFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher"), "required for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.nio.fs.WindowsSecurity"), "required for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.nio.ch.ServerSocketChannelImpl"), "required for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.nio.ch.IOUtil"), "required for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.nio.ch.FileChannelImpl"), "required for substitutions");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$FirstFile"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$FirstFile").getDeclaredField("handle"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$FirstFile").getDeclaredField("name"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$FirstFile").getDeclaredField("attributes"));

            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$FirstStream"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$FirstStream").getDeclaredField("handle"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$FirstStream").getDeclaredField("name"));

            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$VolumeInformation"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$VolumeInformation").getDeclaredField("fileSystemName"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$VolumeInformation").getDeclaredField("volumeName"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$VolumeInformation").getDeclaredField("volumeSerialNumber"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$VolumeInformation").getDeclaredField("flags"));

            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace").getDeclaredField("freeBytesAvailable"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace").getDeclaredField("totalNumberOfBytes"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace").getDeclaredField("totalNumberOfFreeBytes"));

            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$Account"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$Account").getDeclaredField("domain"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$Account").getDeclaredField("name"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$Account").getDeclaredField("use"));

            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$AclInformation"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$AclInformation").getDeclaredField("aceCount"));

            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$CompletionStatus"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$CompletionStatus").getDeclaredField("error"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$CompletionStatus").getDeclaredField("bytesTransferred"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$CompletionStatus").getDeclaredField("completionKey"));

            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$BackupResult"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$BackupResult").getDeclaredField("bytesTransferred"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsNativeDispatcher$BackupResult").getDeclaredField("context"));

            JNIRuntimeAccess.register(access.findClassByName("sun.nio.ch.FileChannelImpl"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.ch.FileChannelImpl").getDeclaredField("fd"));

            JNIRuntimeAccess.register(access.findClassByName("java.lang.Exception"));
            JNIRuntimeAccess.register(access.findClassByName("java.lang.Exception").getDeclaredConstructor());
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsException"));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsException").getDeclaredConstructor(int.class));
            JNIRuntimeAccess.register(access.findClassByName("sun.nio.fs.WindowsException").getDeclaredConstructor(String.class));

        } catch (NoSuchFieldException | NoSuchMethodException e) {
            VMError.shouldNotReachHere("JNIRuntimeAccess.register failed: ", e);

        }

    }
}

@TargetClass(className = "sun.nio.fs.WindowsFileSystemProvider")
@Platforms(Platform.WINDOWS.class)
final class Target_sun_nio_fs_WindowsFileSystemProvider {
    @Alias
    Target_sun_nio_fs_WindowsFileSystemProvider() {
    }

    @Alias
    native FileSystem getFileSystem(URI uri);
}

@Platforms(Platform.WINDOWS.class)
public final class WindowsJavaNIOSubstitutions {

    public static boolean initIDs() {
        return true;
    }

    static final class Util_Target_java_nio_file_FileSystems {
        static FileSystemProvider defaultProvider;
        static FileSystem defaultFilesystem;
    }

    private static URI uri = createURI();

    private static URI createURI() {
        try {
            return new URI("file:/");
        } catch (URISyntaxException e) {
            throw VMError.shouldNotReachHere();
        }
    }

    @TargetClass(FileSystems.class)
    @Platforms(Platform.WINDOWS.class)
    static final class Target_java_nio_file_FileSystems {
        @Substitute
        static FileSystem getDefault() {
            if (Util_Target_java_nio_file_FileSystems.defaultFilesystem == null) {
                Target_sun_nio_fs_WindowsFileSystemProvider provider = new Target_sun_nio_fs_WindowsFileSystemProvider();
                Util_Target_java_nio_file_FileSystems.defaultProvider = SubstrateUtil.cast(provider, FileSystemProvider.class);
                Util_Target_java_nio_file_FileSystems.defaultFilesystem = provider.getFileSystem(uri);
            }
            return Util_Target_java_nio_file_FileSystems.defaultFilesystem;
        }

        @Delete
        @TargetClass(value = FileSystems.class, innerClass = "DefaultFileSystemHolder")
        static final class Target_java_nio_file_FileSystems_DefaultFileSystemHolder {
        }
    }

    @TargetClass(className = "sun.nio.fs.Cancellable")
    @Platforms({Platform.WINDOWS.class})
    static final class Target_sun_nio_fs_Cancellable {
        @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Manual)//
        private long pollingAddress;
    }
}

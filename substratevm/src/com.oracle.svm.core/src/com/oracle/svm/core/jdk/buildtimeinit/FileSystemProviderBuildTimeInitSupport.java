/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.buildtimeinit;

import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.FileSystemProvidersInitializedAtBuildTime;
import com.oracle.svm.core.jdk.JRTSupport;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.jdk.UserSystemProperty;
import com.oracle.svm.core.jdk.runtimeinit.FileSystemProviderRunTimeInitSupport;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.options.Option;
import jdk.internal.util.StaticProperty;

/**
 * This file contains substitutions that are required for initializing {@link FileSystemProvider} at
 * image {@linkplain FileSystemProvidersInitializedAtBuildTime build time}. Run-time initialization
 * related functionality can be found in {@link FileSystemProviderRunTimeInitSupport}.
 */
public final class FileSystemProviderBuildTimeInitSupport {

    public static class Options {
        @Option(help = "Make all supported providers returned by FileSystemProvider.installedProviders() available at run time.")//
        public static final HostedOptionKey<Boolean> AddAllFileSystemProviders = new HostedOptionKey<>(true);
    }

    final List<FileSystemProvider> installedProvidersMutable;
    final List<FileSystemProvider> installedProvidersImmutable;

    @Platforms(Platform.HOSTED_ONLY.class)
    FileSystemProviderBuildTimeInitSupport(List<FileSystemProvider> installedProviders) {
        this.installedProvidersMutable = installedProviders;
        this.installedProvidersImmutable = Collections.unmodifiableList(installedProviders);
    }

    /**
     * Registers the provided {@link FileSystemProvider}. If a provider with the same
     * {@link FileSystemProvider#getScheme()} is already registered, the old provider is
     * overwritten.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void register(FileSystemProvider provider) {
        List<FileSystemProvider> installedProviders = ImageSingletons.lookup(FileSystemProviderBuildTimeInitSupport.class).installedProvidersMutable;

        String scheme = provider.getScheme();
        for (int i = 0; i < installedProviders.size(); i++) {
            /*
             * The "equalsIgnoreCase" check corresponds to the way the initial list of
             * installedProviders is built in
             * java.nio.file.spi.FileSystemProvider.loadInstalledProviders().
             */
            if (installedProviders.get(i).getScheme().equalsIgnoreCase(scheme)) {
                installedProviders.set(i, provider);
                return;
            }
        }
        installedProviders.add(provider);
    }

    /**
     * Removes the {@link FileSystemProvider} with the provided
     * {@link FileSystemProvider#getScheme() scheme} name.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void remove(String scheme) {
        List<FileSystemProvider> installedProviders = ImageSingletons.lookup(FileSystemProviderBuildTimeInitSupport.class).installedProvidersMutable;

        for (int i = 0; i < installedProviders.size(); i++) {
            /*
             * The "equalsIgnoreCase" check corresponds to the way the initial list of
             * installedProviders is built in
             * java.nio.file.spi.FileSystemProvider.loadInstalledProviders().
             */
            if (installedProviders.get(i).getScheme().equalsIgnoreCase(scheme)) {
                installedProviders.remove(i);
                return;
            }
        }
    }
}

@AutomaticallyRegisteredFeature
final class FileSystemProviderBuildTimeInitFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !FutureDefaultsOptions.fileSystemProvidersInitializedAtRunTime();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        List<FileSystemProvider> installedProviders = new ArrayList<>();
        if (FileSystemProviderBuildTimeInitSupport.Options.AddAllFileSystemProviders.getValue()) {
            /*
             * The first invocation of FileSystemProvider.installedProviders() causes the default
             * provider to be initialized (if not already initialized) and loads any other installed
             * providers as described by the {@link FileSystems} class.
             *
             * Even though it is not specified, it seems as if the order of elements in the list
             * matters. At least the JDK implementation explicitly adds the "file" provider as the
             * first element of the list. Therefore, we preserve the order of the providers observed
             * during image generation.
             */
            installedProviders.addAll(FileSystemProvider.installedProviders());
        }
        ImageSingletons.add(FileSystemProviderBuildTimeInitSupport.class, new FileSystemProviderBuildTimeInitSupport(installedProviders));

        /* Access to Java modules (jimage/jrtfs access) in images is experimental. */
        if (!JRTSupport.Options.AllowJRTFileSystem.getValue()) {
            FileSystemProviderBuildTimeInitSupport.remove("jrt");
        }
    }
}

@TargetClass(value = java.nio.file.spi.FileSystemProvider.class, onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
final class Target_java_nio_file_spi_FileSystemProvider_BuildTime {
    @Substitute
    public static List<FileSystemProvider> installedProviders() {
        return ImageSingletons.lookup(FileSystemProviderBuildTimeInitSupport.class).installedProvidersImmutable;
    }
}

/**
 * UnixFileSystem caches the current working directory, which must be initialized at run time and
 * not be cached in the image heap. We need runtime initialization of that state. This is
 * complicated by the fact that UnixFileSystemProvider and UnixFileSystem have a 1:1 relationship:
 * UnixFileSystemProvider has a final reference to UnixFileSystem.
 *
 * We considered different options:
 *
 * a) Disallow UnixFileSystem and UnixFileSystemProvider in the image heap, i.e., create all
 * instances at run time. This is undesirable because then all file system providers need to be
 * loaded at run time, i.e., the caching in {@link FileSystemProviderBuildTimeInitSupport} would no
 * longer work.
 *
 * b) Disallow UnixFileSystem in the image heap, but have the UnixFileSystemProvider instance in the
 * image heap. This requires a recomputation of the field UnixFileSystemProvider.theFileSystem at
 * run time on first access. However, this approach is undesirable because the UnixFileSystem
 * instance is reachable from many places, for example UnixPath. Disallowing Path instances is a
 * severe restriction because relative Path instances are often stored in static final fields.
 *
 * c) Allow UnixFileSystem in the image heap and recompute state at run time on first acccess. This
 * approach is implemented here.
 */
@TargetClass(className = "sun.nio.fs.UnixFileSystem", onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_nio_fs_UnixFileSystem_BuildTime {

    @Alias //
    Target_sun_nio_fs_UnixFileSystemProvider_BuildTime provider;

    /*
     * All fields of UnixFileSystem that contain state. At this point, the subclasses
     * LinuxFileSystem and MacOSXFileSystem do not contain additional state. All accesses of these
     * fields are intercepted with get and set accessors, i.e., these fields are no longer present
     * at run time.
     */

    @Alias //
    @InjectAccessors(UnixFileSystemAccessors.class) //
    private byte[] defaultDirectory;
    @Alias //
    @InjectAccessors(UnixFileSystemAccessors.class) //
    private boolean needToResolveAgainstDefaultDirectory;
    @Alias //
    @InjectAccessors(UnixFileSystemAccessors.class) //
    private Target_sun_nio_fs_UnixPath_BuildTime rootDirectory;

    /**
     * Flag to check if reinitialization at run time is needed. For objects in the image heap, this
     * field is initially non-zero and set to zero after reinitialization. If UnixFileSystem
     * instances are allocated at run time, the field will be set to zero right away (default value)
     * as there is no need for reinitialization. Note that UnixFileSystem instances should not be
     * allocated at run time, as only the singleton from the image heap should exist. However, there
     * were JDK bugs in various JDK versions where unwanted allocations happened.
     */
    @Inject //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = NeedsReinitializationProvider.class)//
    volatile int needsReinitialization;

    /* Replacement injected fields that store the state at run time. */

    @Inject //
    @RecomputeFieldValue(kind = Kind.Reset)//
    byte[] injectedDefaultDirectory;
    @Inject //
    @RecomputeFieldValue(kind = Kind.Reset)//
    boolean injectedNeedToResolveAgainstDefaultDirectory;
    @Inject //
    @RecomputeFieldValue(kind = Kind.Reset)//
    Target_sun_nio_fs_UnixPath_BuildTime injectedRootDirectory;

    @Alias
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    native void originalConstructor(Target_sun_nio_fs_UnixFileSystemProvider_BuildTime p, String dir);
}

@TargetClass(className = "sun.nio.fs.UnixFileSystemProvider", onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_nio_fs_UnixFileSystemProvider_BuildTime {
}

@TargetClass(className = "sun.nio.fs.UnixPath", onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_nio_fs_UnixPath_BuildTime {
}

class NeedsReinitializationProvider implements FieldValueTransformer {
    static final int STATUS_NEEDS_REINITIALIZATION = 2;
    static final int STATUS_IN_REINITIALIZATION = 1;
    /*
     * This constant must remain 0 so that objects allocated at run time do not go through
     * re-initialization.
     */
    static final int STATUS_REINITIALIZED = 0;

    @Override
    public Object transform(Object receiver, Object originalValue) {
        return STATUS_NEEDS_REINITIALIZATION;
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class UnixFileSystemAccessors {

    /*
     * Get-accessors for the fields of UnixFileSystem. Re-initialization of all fields happen on the
     * first access of any of the fields.
     */

    static byte[] getDefaultDirectory(Target_sun_nio_fs_UnixFileSystem_BuildTime that) {
        if (that.needsReinitialization != NeedsReinitializationProvider.STATUS_REINITIALIZED) {
            reinitialize(that);
        }
        return that.injectedDefaultDirectory;
    }

    static boolean getNeedToResolveAgainstDefaultDirectory(Target_sun_nio_fs_UnixFileSystem_BuildTime that) {
        if (that.needsReinitialization != NeedsReinitializationProvider.STATUS_REINITIALIZED) {
            reinitialize(that);
        }
        return that.injectedNeedToResolveAgainstDefaultDirectory;
    }

    static Target_sun_nio_fs_UnixPath_BuildTime getRootDirectory(Target_sun_nio_fs_UnixFileSystem_BuildTime that) {
        if (that.needsReinitialization != NeedsReinitializationProvider.STATUS_REINITIALIZED) {
            reinitialize(that);
        }
        return that.injectedRootDirectory;
    }

    /*
     * Set-accessors for the fields of UnixFileSystem. These methods are invoked by the original
     * constructor. Providing these set-accessors is less error prone than doing a copy-paste-modify
     * of the constructor to write to the injected fields directly.
     */

    static void setDefaultDirectory(Target_sun_nio_fs_UnixFileSystem_BuildTime that, byte[] value) {
        that.injectedDefaultDirectory = value;
    }

    static void setNeedToResolveAgainstDefaultDirectory(Target_sun_nio_fs_UnixFileSystem_BuildTime that, boolean value) {
        that.injectedNeedToResolveAgainstDefaultDirectory = value;
    }

    static void setRootDirectory(Target_sun_nio_fs_UnixFileSystem_BuildTime that, Target_sun_nio_fs_UnixPath_BuildTime value) {
        that.injectedRootDirectory = value;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/linux/classes/sun/nio/fs/LinuxFileSystem.java#L44-L46")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/linux/classes/sun/nio/fs/LinuxFileSystemProvider.java#L45-L47")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/unix/classes/sun/nio/fs/UnixFileSystemProvider.java#L75-L77")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/unix/classes/sun/nio/fs/UnixFileSystem.java#L78-L108")
    private static synchronized void reinitialize(Target_sun_nio_fs_UnixFileSystem_BuildTime that) {
        if (that.needsReinitialization != NeedsReinitializationProvider.STATUS_NEEDS_REINITIALIZATION) {
            /* Field initialized is volatile, so double-checked locking is OK. */
            return;
        }
        /*
         * The original constructor reads fields immediately after writing, so we need to make sure
         * that we do not enter this re-initialization code recursively.
         */
        that.needsReinitialization = NeedsReinitializationProvider.STATUS_IN_REINITIALIZATION;

        /*
         * We invoke the original constructor of UnixFileSystem. This overwrites the provider field
         * with the same value it is already set to, so this is harmless. All other field writes are
         * redirected to the set-accessors of this class and write the injected fields.
         *
         * Note that the `StaticProperty.userDir()` value is always used when re-initializing a
         * UnixFileSystem, which is not the case with the WindowsFileSystem (JDK-8066709).
         */
        that.originalConstructor(that.provider, StaticProperty.userDir());

        /*
         * Now the object is completely re-initialized and can be used by any thread without
         * entering the synchronized slow path again.
         */
        that.needsReinitialization = NeedsReinitializationProvider.STATUS_REINITIALIZED;
    }
}

/*
 * WindowsFilesSystem implementation follows the same approach as UnixFileSystem, but with different
 * fields so we cannot re-use the substitutions.
 */

@TargetClass(className = "sun.nio.fs.WindowsFileSystem", onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
@Platforms({Platform.WINDOWS.class})
final class Target_sun_nio_fs_WindowsFileSystem_BuildTime {

    @Alias //
    Target_sun_nio_fs_WindowsFileSystemProvider_BuildTime provider;

    @Alias //
    @InjectAccessors(WindowsFileSystemAccessors.class) //
    private String defaultDirectory;
    @Alias //
    @InjectAccessors(WindowsFileSystemAccessors.class) //
    private String defaultRoot;

    @Inject //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = NeedsReinitializationProvider.class)//
    volatile int needsReinitialization;

    @Inject //
    @RecomputeFieldValue(kind = Kind.Reset)//
    String injectedDefaultDirectory;
    @Inject //
    @RecomputeFieldValue(kind = Kind.Reset)//
    String injectedDefaultRoot;

    @Alias
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    native void originalConstructor(Target_sun_nio_fs_WindowsFileSystemProvider_BuildTime p, String dir);
}

@TargetClass(className = "sun.nio.fs.WindowsFileSystemProvider", onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
@Platforms({Platform.WINDOWS.class})
final class Target_sun_nio_fs_WindowsFileSystemProvider_BuildTime {
}

@Platforms({Platform.WINDOWS.class})
class WindowsFileSystemAccessors {
    static String getDefaultDirectory(Target_sun_nio_fs_WindowsFileSystem_BuildTime that) {
        if (that.needsReinitialization != NeedsReinitializationProvider.STATUS_REINITIALIZED) {
            reinitialize(that);
        }
        return that.injectedDefaultDirectory;
    }

    static String getDefaultRoot(Target_sun_nio_fs_WindowsFileSystem_BuildTime that) {
        if (that.needsReinitialization != NeedsReinitializationProvider.STATUS_REINITIALIZED) {
            reinitialize(that);
        }
        return that.injectedDefaultRoot;
    }

    static void setDefaultDirectory(Target_sun_nio_fs_WindowsFileSystem_BuildTime that, String value) {
        that.injectedDefaultDirectory = value;
    }

    static void setDefaultRoot(Target_sun_nio_fs_WindowsFileSystem_BuildTime that, String value) {
        that.injectedDefaultRoot = value;
    }

    private static synchronized void reinitialize(Target_sun_nio_fs_WindowsFileSystem_BuildTime that) {
        if (that.needsReinitialization != NeedsReinitializationProvider.STATUS_NEEDS_REINITIALIZATION) {
            return;
        }
        that.needsReinitialization = NeedsReinitializationProvider.STATUS_IN_REINITIALIZATION;
        that.originalConstructor(that.provider, SystemPropertiesSupport.singleton().getInitialProperty(UserSystemProperty.DIR));
        that.needsReinitialization = NeedsReinitializationProvider.STATUS_REINITIALIZED;
    }
}

@TargetClass(className = "java.io.UnixFileSystem", onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_io_UnixFileSystem_BuildTime {

    @Alias //
    @InjectAccessors(UserDirAccessors.class) //
    private String userDir;
}

@TargetClass(className = "java.io.FileSystem", onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
final class Target_java_io_FileSystem_BuildTime {

    @Alias
    native String normalize(String path);
}

class UserDirAccessors {
    @SuppressWarnings("unused")
    static String getUserDir(Target_java_io_FileSystem_BuildTime that) {
        if (Platform.includedIn(InternalPlatform.WINDOWS_BASE.class)) {
            /*
             * Note that on Windows, we normalize the property value (JDK-8198997) and do not use
             * the `StaticProperty.userDir()` like the rest (JDK-8066709).
             */
            return that.normalize(System.getProperty(UserSystemProperty.DIR));
        }
        return StaticProperty.userDir();
    }

    @SuppressWarnings("unused")
    static void setUserDir(Target_java_io_FileSystem_BuildTime that, String value) {
        throw VMError.shouldNotReachHere("Field userDir is initialized at build time");
    }
}

@TargetClass(className = "java.io.WinNTFileSystem", onlyWith = FileSystemProvidersInitializedAtBuildTime.class)
@Platforms(Platform.WINDOWS.class)
final class Target_java_io_WinNTFileSystem_BuildTime {

    @Alias //
    @InjectAccessors(UserDirAccessors.class) //
    private String userDir;
}

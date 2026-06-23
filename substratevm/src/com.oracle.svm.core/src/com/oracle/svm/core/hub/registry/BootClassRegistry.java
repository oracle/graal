/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.registry;

import static com.oracle.svm.core.jdk.JRTSupport.Options.AllowJRTFileSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.jdk.BootLoaderClassPathSupport;
import com.oracle.svm.core.jdk.BootLoaderClassPathSupport.ClassFileBytes;
import com.oracle.svm.core.jdk.BootLoaderPackageAccess;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;

/**
 * This class registry corresponds to the {@code null} class loader when runtime class loading is
 * supported.
 */
public final class BootClassRegistry extends AbstractRuntimeClassRegistry {
    private static final String JRTFS_UNAVAILABLE_MESSAGE = String.format("""
                    The boot class loader is unavailable. In order to allow loading classes from the boot class loader, you must:
                    - Build the image with %s
                    - Set the `java.home` system property at run-time.
                      This can be done as a run-time command line argument `-Djava.home=`, or programmatically with `System.setProperty("java.home", ...`""",
                    SubstrateOptionsParser.commandArgument(AllowJRTFileSystem, "+")).replace("\n", System.lineSeparator());

    private static final String JAVA_HOME_UNAVAILABLE_MESSAGE = """
                    The boot class loader is unavailable. In order to allow loading classes from the boot class loader, you must:
                    - Set the `java.home` system property at run-time.
                      This can be done as a run-time command line argument `-Djava.home=`, or programmatically with `System.setProperty("java.home", ...`""".replace("\n", System.lineSeparator());
    private static final Object NO_JRT_FS = new Object();
    private volatile Object jrtFS;

    /// Maps loaded boot-append package names in internal form (e.g. `org/example`) to the
    /// `-Xbootclasspath/a:` entry (e.g. `/path/to/boot-append.jar`) that supplied the first
    /// successfully defined class in the package.
    private static final ConcurrentHashMap<String, String> loadedBootAppendPackageLocations = new ConcurrentHashMap<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    public BootClassRegistry() {
    }

    private FileSystem getFileSystem() {
        // jrtFS is lazily initialized to avoid having this FileSystem in the image heap.
        Object maybeFs = jrtFS;
        if (maybeFs == null) {
            synchronized (this) {
                if ((maybeFs = jrtFS) == null) {
                    if (System.getProperty("java.home") == null) {
                        LogUtils.warning(JAVA_HOME_UNAVAILABLE_MESSAGE);
                        jrtFS = NO_JRT_FS;
                    } else {
                        try {
                            jrtFS = maybeFs = FileSystems.getFileSystem(URI.create("jrt:/"));
                        } catch (ProviderNotFoundException | FileSystemNotFoundException e) {
                            if (!AllowJRTFileSystem.getValue()) {
                                LogUtils.warning(JRTFS_UNAVAILABLE_MESSAGE);
                            } else {
                                LogUtils.warning("The boot class loader is unavailable: " + e);
                            }
                            jrtFS = NO_JRT_FS;
                        }
                    }
                }
            }
        }
        if (maybeFs == NO_JRT_FS) {
            return null;
        }
        return (FileSystem) maybeFs;
    }

    // synchronized until parallel class loading is implemented (GR-62338)
    @Override
    public synchronized Class<?> doLoadClass(Symbol<Type> type) {
        String internalPackageName = packageFromType(type);
        try {
            byte[] bytes = internalPackageName == null ? null : loadFromJImage(type, internalPackageName);
            ClassFileBytes classFileBytes = null;
            if (bytes == null) {
                /* Preserve boot class path append semantics by looking there after the jimage. */
                classFileBytes = loadFromAppendedBootClassPathBytes(type);
                bytes = classFileBytes == null ? null : classFileBytes.bytes();
            }
            if (bytes == null) {
                return null;
            }
            Class<?> loaded = defineClass(type, bytes, 0, bytes.length, ClassDefinitionInfo.EMPTY);
            if (classFileBytes != null) {
                recordBootAppendPackageLocation(TypeSymbols.typeToName(type).toString(), classFileBytes.packageLocation());
            } else {
                Module module = ModuleLayer.boot().findModule(BootLoaderPackageAccess.bootModuleNameForPackage(internalPackageName)).orElseThrow();
                BootLoaderPackageAccess.ensureNamedPackageExists(internalPackageName, module);
            }
            CremaSupport.singleton().recordLoadingConstraint(type, DynamicHub.fromClass(loaded), null);
            return loaded;
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private byte[] loadFromJImage(Symbol<Type> type, String internalPackageName) throws IOException {
        String moduleName = BootLoaderPackageAccess.bootModuleNameForPackage(internalPackageName);
        if (moduleName == null) {
            return null;
        }
        FileSystem fileSystem = getFileSystem();
        if (fileSystem == null) {
            return null;
        }
        var typeName = TypeSymbols.typeToName(type);
        Path classPath = fileSystem.getPath("/modules/" + moduleName + "/" + typeName + ".class");
        if (!Files.exists(classPath)) {
            return null;
        }
        return Files.readAllBytes(classPath);
    }

    private static ClassFileBytes loadFromAppendedBootClassPathBytes(Symbol<Type> type) throws IOException {
        return BootLoaderClassPathSupport.getClassBytes(TypeSymbols.typeToName(type).toString());
    }

    /// Returns the boot loader package location in the format expected by `BootLoader.PackageHelper`.
    ///
    /// @param internalPackageName package name in internal form (e.g. `org/foo/impl`)
    public static String getSystemPackageLocation(String internalPackageName) {
        String module = BootLoaderPackageAccess.definedBootModuleNameForPackage(internalPackageName);
        if (module != null) {
            return "jrt:/" + module;
        }
        return loadedBootAppendPackageLocations.get(internalPackageName);
    }

    /// Records the package source for `internalClassName` after a boot-append class has loaded.
    private static void recordBootAppendPackageLocation(String internalClassName, String location) {
        int lastSlash = internalClassName.lastIndexOf('/');
        if (lastSlash != -1 && location != null) {
            loadedBootAppendPackageLocations.putIfAbsent(internalClassName.substring(0, lastSlash), location);
        }
    }

    /// Returns boot loader package names in internal form, matching `BootLoader.getSystemPackageNames`.
    public static String[] getSystemPackageNames() {
        Set<String> systemPackageNames = new HashSet<>();
        BootLoaderPackageAccess.addSystemPackageNames(systemPackageNames);
        systemPackageNames.addAll(loadedBootAppendPackageLocations.keySet());
        return systemPackageNames.toArray(String[]::new);
    }

    /**
     * Extracts an internal package name from a type descriptor.
     */
    private static String packageFromType(Symbol<Type> type) {
        int lastSlash = type.lastIndexOf((byte) '/');
        if (lastSlash == -1) {
            return null;
        }
        return type.subSequence(1, lastSlash).toString();
    }

    @Override
    protected boolean loaderIsBootOrPlatform() {
        return true;
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }
}

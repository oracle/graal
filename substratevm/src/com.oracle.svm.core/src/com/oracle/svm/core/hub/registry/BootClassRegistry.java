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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.util.LogUtils;

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

    @Platforms(Platform.HOSTED_ONLY.class)
    public BootClassRegistry() {
    }

    private FileSystem getFileSystem() {
        // jrtFS is lazily initialized to avoid having this FileSystem in the image heap.
        Object maybeFs = jrtFS;
        if (maybeFs == null) {
            synchronized (this) {
                if ((maybeFs = jrtFS) == null) {
                    try {
                        jrtFS = maybeFs = FileSystems.getFileSystem(URI.create("jrt:/"));
                    } catch (ProviderNotFoundException | FileSystemNotFoundException e) {
                        if (!AllowJRTFileSystem.getValue()) {
                            LogUtils.warning(JRTFS_UNAVAILABLE_MESSAGE);
                        } else if (System.getProperty("java.home") == null) {
                            LogUtils.warning(JAVA_HOME_UNAVAILABLE_MESSAGE);
                        } else {
                            LogUtils.warning("The boot class loader is unavailable: " + e);
                        }
                        jrtFS = NO_JRT_FS;
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
        // Only looking into the jimage for now. There could be appended elements.
        // see GraalServices.getSavedProperty("jdk.boot.class.path.append")
        String pkg = packageFromType(type);
        if (pkg == null) {
            return null;
        }
        try {
            String moduleName = ClassRegistries.getBootModuleForPackage(pkg);
            if (moduleName == null) {
                return null;
            }
            FileSystem fileSystem = getFileSystem();
            if (fileSystem == null) {
                return null;
            }
            var jrtTypePath = TypeSymbols.typeToName(type);
            Path classPath = fileSystem.getPath("/modules/" + moduleName + "/" + jrtTypePath + ".class");
            if (!Files.exists(classPath)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(classPath);
            return defineClass(type, bytes, 0, bytes.length, ClassDefinitionInfo.EMPTY);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static String packageFromType(Symbol<Type> type) {
        int lastSlash = type.lastIndexOf((byte) '/');
        if (lastSlash == -1) {
            return null;
        }
        return type.subSequence(1, lastSlash).toString().replace('/', '.');
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

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

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;

/**
 * This class registry corresponds to the {@code null} class loader when runtime class loading is
 * supported.
 */
public final class BootClassRegistry extends AbstractRuntimeClassRegistry {
    private volatile FileSystem jrtFS;

    @Platforms(Platform.HOSTED_ONLY.class)
    public BootClassRegistry() {
    }

    private FileSystem getFileSystem() {
        // jrtFS is lazily initialized to avoid having this FileSystem in the image heap.
        FileSystem fs = jrtFS;
        if (fs == null) {
            synchronized (this) {
                if ((fs = jrtFS) == null) {
                    jrtFS = fs = FileSystems.getFileSystem(URI.create("jrt:/"));
                }
            }
        }
        return fs;
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
            Path classPath = getFileSystem().getPath("/modules/" + moduleName + "/" + type + ".class");
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
        return type.subSequence(0, lastSlash).toString();
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

/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk15;

import java.io.File;
import java.util.Deque;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK15OrLater;
import com.oracle.svm.core.jdk.NativeLibrarySupport;

import jdk.internal.loader.NativeLibraries;
import jdk.internal.loader.NativeLibrary;

@TargetClass(value = ClassLoader.class, onlyWith = JDK15OrLater.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_lang_ClassLoader_JDK15OrLater {

    @Substitute
    static NativeLibrary loadLibrary(Class<?> fromClass, String name) {
        NativeLibrarySupport.singleton().loadLibraryRelative(name);
        // We don't use the JDK's NativeLibraries or NativeLibrary implementations
        return null;
    }

    @Substitute
    static NativeLibrary loadLibrary(Class<?> fromClass, File file) {
        NativeLibrarySupport.singleton().loadLibraryAbsolute(file);
        // We don't use the JDK's NativeLibraries or NativeLibrary implementations
        return null;
    }
}

@TargetClass(value = jdk.internal.loader.NativeLibraries.class, onlyWith = JDK15OrLater.class)
final class Target_jdk_internal_loader_NativeLibraries_JDK15OrLater {

    /**
     * The NativeLibraries is only used by the `loadLibrary` methods that are substituted, so we do
     * not need an instance for now.
     */
    @Substitute
    public static NativeLibraries jniNativeLibraries(ClassLoader loader) {
        return null;
    }

    /*
     * Fields capturing state from the image generator that must not leak into the image heap.
     */

    @Delete //
    private Map<?, ?> libraries;
    @Delete //
    private static Set<String> loadedLibraryNames;
    @Delete //
    private static Deque<?> nativeLibraryContext;

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    private static native boolean load(Target_jdk_internal_loader_NativeLibraries_NativeLibraryImpl_JDK15OrLater impl, String name, boolean isBuiltin, boolean isJNI);

    @Delete
    private static native void unload(String name, boolean isBuiltin, boolean isJNI, long handle);

    @Delete
    private static native String findBuiltinLib(String name);

    @Delete
    private static native long findEntry0(Target_jdk_internal_loader_NativeLibraries_NativeLibraryImpl_JDK15OrLater lib, String name);
}

@TargetClass(value = jdk.internal.loader.NativeLibraries.class, innerClass = "NativeLibraryImpl", onlyWith = JDK15OrLater.class)
final class Target_jdk_internal_loader_NativeLibraries_NativeLibraryImpl_JDK15OrLater {
}

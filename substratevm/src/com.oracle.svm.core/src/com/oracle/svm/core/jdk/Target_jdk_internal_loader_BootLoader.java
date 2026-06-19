/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.registry.BootClassRegistry;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.internal.loader.ClassLoaderValue;

@TargetClass(value = jdk.internal.loader.BootLoader.class)
final class Target_jdk_internal_loader_BootLoader {
    // Checkstyle: stop
    @Delete //
    static String JAVA_HOME;
    // Checkstyle: resume

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    public static Stream<Package> packages() {
        Target_jdk_internal_loader_BuiltinClassLoader bootClassLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        Target_java_lang_ClassLoader systemClassLoader = SubstrateUtil.cast(bootClassLoader, Target_java_lang_ClassLoader.class);
        return systemClassLoader.packages();
    }

    @Delete("only used by #packages()")
    @TargetElement(name = "getSystemPackageNames", onlyWith = ClassRegistries.IgnoresClassLoader.class)
    private static native String[] getSystemPackageNamesDeleted();

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/java.base/share/native/libjava/BootLoader.c#L37-L41")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L3003-L3007")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/hotspot/share/classfile/classLoader.cpp#L907-L924")
    private static String[] getSystemPackageNames() {
        return BootClassRegistry.getSystemPackageNames();
    }

    /**
     * Looks up the source location for a package already known to the boot loader.
     *
     * This method returns a non-null location only after at least one boot-loaded class in
     * {@code internalPackageName} has been loaded. For appended boot class path entries, the
     * package source is recorded when runtime class loading reads a class from that package; this
     * method does not scan the boot class path for packages with no loaded classes.
     *
     * @param internalPackageName package name in internal form (e.g. "org/foo/impl")
     */
    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/java.base/share/native/libjava/BootLoader.c#L44-L52")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L3011-L3015")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+16/src/hotspot/share/classfile/classLoader.cpp#L928-L935")
    private static String getSystemPackageLocation(String internalPackageName) {
        return BootClassRegistry.getSystemPackageLocation(internalPackageName);
    }

    @SuppressWarnings({"unused", "restricted"})
    @Substitute
    private static void loadLibrary(String name) {
        System.loadLibrary(name);
    }

    @Substitute
    private static boolean hasClassPath() {
        return true;
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    public static URL findResource(String name) {
        return ResourcesHelper.nameToResourceURL(name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    public static Enumeration<URL> findResources(String name) {
        return ResourcesHelper.nameToResourceEnumerationURLs(name);
    }

    /**
     * Most {@link ClassLoaderValue}s are reset. For the list of preserved transformers see
     * {@link ClassLoaderValueMapFieldValueTransformer}.
     */
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ClassLoaderValueMapFieldValueTransformer.class, isFinal = true)//
    static ConcurrentHashMap<?, ?> CLASS_LOADER_VALUE_MAP;
    // Checkstyle: resume
}

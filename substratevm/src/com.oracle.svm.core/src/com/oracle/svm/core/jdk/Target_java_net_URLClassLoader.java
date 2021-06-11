/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.util.VMError;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.WeakHashMap;

@TargetClass(classNameProvider = Package_jdk_internal_loader.class, className = "URLClassPath")
@SuppressWarnings({"unused", "static-method"})
final class Target_jdk_internal_loader_URLClassPath {

    /* Reset fields that can store a Zip file via sun.misc.URLClassPath$JarLoader.jar. */

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ArrayList.class)//
    private ArrayList<?> loaders;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = HashMap.class)//
    private HashMap<String, ?> lmap;

    /* The original locations of the .jar files are no longer available at run time. */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ArrayList.class)//
    private ArrayList<URL> path;

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native URL[] getLookupCacheURLs(ClassLoader loader);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native int[] getLookupCacheForClassLoader(ClassLoader loader, String name);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native boolean knownToNotExist0(ClassLoader loader, String className);

    @Substitute
    @TargetElement(name = "getResource", onlyWith = JDK11OrLater.class)
    public Target_jdk_internal_loader_Resource_JDK11OrLater getResourceJDK11OrLater(String name, boolean check) {
        return ResourcesHelper.nameToResource(name);
    }

    @Substitute
    @TargetElement(name = "getResources", onlyWith = JDK11OrLater.class)
    public Enumeration<Target_jdk_internal_loader_Resource_JDK11OrLater> getResourcesJDK11OrLater(final String name,
                    final boolean check) {
        return ResourcesHelper.nameToResources(name);
    }

    @Substitute
    @TargetElement(name = "getResource", onlyWith = JDK8OrEarlier.class)
    public Target_sun_misc_Resource_JDK8OrEarlier getResourceJDK8OrEarlier(String name, boolean check) {
        return ResourcesHelper.nameToResource(name);
    }

    @Substitute
    @TargetElement(name = "getResources", onlyWith = JDK8OrEarlier.class)
    public Enumeration<Target_sun_misc_Resource_JDK8OrEarlier> getResourcesJDK8OrEarlier(final String name,
                    final boolean check) {
        return ResourcesHelper.nameToResources(name);
    }
}

@TargetClass(URLClassLoader.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_java_net_URLClassLoader {
    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = WeakHashMap.class)//
    private WeakHashMap<Closeable, Void> closeables;

    @Substitute
    public InputStream getResourceAsStream(String name) throws IOException {
        return Resources.createInputStream(name);
    }

    @Substitute
    @SuppressWarnings("unused")
    protected Class<?> findClass(final String name) {
        throw VMError.unsupportedFeature("Loading bytecodes.");
    }
}

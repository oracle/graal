/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.WeakHashMap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.internal.loader.URLClassPath")
@SuppressWarnings({"unused", "static-method"})
final class Target_jdk_internal_loader_URLClassPath {

    /*
     * The image heap can contain builder-created class loaders, in particular the JDK app,
     * platform and boot loaders that SVM intentionally reuses as runtime class loader identity
     * objects. Reset their URLClassPath state so the image does not embed builder class path URLs,
     * JarFile/ZipFile caches or other host-side loader state.
     */

    @Alias
    public native URL findResource(String name);

    @Alias
    public native Enumeration<URL> findResources(String name);

    @Alias
    public native Target_jdk_internal_loader_Resource getResource(String name);

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ArrayList.class)//
    private ArrayList<?> loaders;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = HashMap.class)//
    private HashMap<String, ?> lmap;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ArrayList.class)//
    private ArrayList<URL> path;
}

@TargetClass(className = "jdk.internal.loader.Resource")
@SuppressWarnings({"unused", "static-method"})
final class Target_jdk_internal_loader_Resource {
    @Alias
    public native byte[] getBytes() throws java.io.IOException;

    @Alias
    public native URL getCodeSourceURL();
}

@TargetClass(URLClassLoader.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_java_net_URLClassLoader {
    /* Drop build-time resource streams and closeables from URLClassLoader instances in the image heap. */
    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = WeakHashMap.class)//
    private WeakHashMap<Closeable, Void> closeables;
}

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
package com.oracle.svm.core.jdk;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.util.GuardedAnnotationAccess;

/**
 * This class stores information about which packages need to be stored within each ClassLoader.
 * This information is used by {@link Target_java_lang_ClassLoader} to reset the package
 * information. The package information must be reset because:
 * <ol>
 * <li>Many more classes may be loaded during native-image generation time than will be reachable
 * during a normal execution.</li>
 * <li>Each ClassLoader should only initially store packages for classes which are initialized at
 * build-time. Classes (re)initialized during runtime should have their respective package linked
 * then.</li>
 * <li>During native-image generation time, a custom system class loader is used
 * (NativeImageSystemClassLoader) to load application classes. However, before heap creation,
 * classes which were loaded by NativeImageSystemClassLoader are updated to point to the default
 * system class loader via ClassLoaderFeature. Hence, the default system class loader must be
 * updated to contain references to all appropriate packages.</li>
 * </ol>
 */
public class ClassLoaderSupport {

    private static final ConcurrentMap<ClassLoader, ConcurrentHashMap<String, Package>> registeredPackages = new ConcurrentHashMap<>();

    public static void registerPackage(ClassLoader classLoader, String packageName, Package packageValue) {
        assert classLoader != null;
        assert packageName != null;
        assert packageValue != null;

        /*
         * Eagerly initialize the field Package.packageInfo, which stores the .package-info class
         * (if present) with the annotations for the package. We want that class to be available
         * without having to register it manually in the reflection configuration file.
         * 
         * Note that we either need to eagerly initialize that field (the approach chosen) or
         * force-reset it to null for all packages, otherwise there can be transient problems when
         * the lazy initialization happens in the image builder after the static analysis.
         */
        GuardedAnnotationAccess.getAnnotations(packageValue);

        ConcurrentMap<String, Package> classPackages = registeredPackages.computeIfAbsent(classLoader, k -> new ConcurrentHashMap<>());
        classPackages.putIfAbsent(packageName, packageValue);
    }

    public static ConcurrentHashMap<String, Package> getRegisteredPackages(ClassLoader classLoader) {
        return registeredPackages.get(classLoader);
    }
}

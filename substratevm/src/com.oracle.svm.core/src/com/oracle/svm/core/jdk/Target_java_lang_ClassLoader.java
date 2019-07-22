/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.JavaLangSubstitutions.ClassLoaderSupport;
import com.oracle.svm.core.util.VMError;

@TargetClass(ClassLoader.class)
@Substitute
@SuppressWarnings("static-method")
public final class Target_java_lang_ClassLoader {

    @Substitute //
    private Target_java_lang_ClassLoader parent;

    @Substitute @TargetElement(onlyWith = JDK11OrLater.class) private final ConcurrentHashMap<String, Target_java_lang_NamedPackage> packages = new ConcurrentHashMap<>();

    @Substitute
    public Target_java_lang_ClassLoader() {
        parent = null;
    }

    @Substitute
    public Target_java_lang_ClassLoader getParent() {
        return parent;
    }

    @Substitute
    public Target_java_lang_ClassLoader(Target_java_lang_ClassLoader parent) {
        this.parent = parent;
    }

    /*
     * Substituting the whole class allows us to have fields of declared type ClassLoader, but still
     * get an error if anyone tries to access a field or call a method on it that we have not
     * explicitly substituted below.
     */

    @Substitute
    private InputStream getResourceAsStream(String name) {
        return getSystemResourceAsStream(name);
    }

    @Substitute
    private static InputStream getSystemResourceAsStream(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : new ByteArrayInputStream(arr.get(0));
    }

    @Substitute
    private URL getResource(String name) {
        return getSystemResource(name);
    }

    @Substitute
    private static URL getSystemResource(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : Resources.createURL(name, arr.get(0));
    }

    @Substitute
    private Enumeration<URL> getResources(String name) {
        return getSystemResources(name);
    }

    @Substitute
    private static Enumeration<URL> getSystemResources(String name) {
        List<byte[]> arr = Resources.get(name);
        if (arr == null) {
            return Collections.emptyEnumeration();
        }
        List<URL> res = new ArrayList<>(arr.size());
        for (byte[] data : arr) {
            res.add(Resources.createURL(name, data));
        }
        return Collections.enumeration(res);
    }

    @Substitute
    public static ClassLoader getSystemClassLoader() {
        return SubstrateUtil.cast(ClassLoaderSupport.getInstance().systemClassLoader, ClassLoader.class);
    }

    @Substitute
    @SuppressWarnings("unused")
    static void loadLibrary(Class<?> fromClass, String name, boolean isAbsolute) {
        NativeLibrarySupport.singleton().loadLibrary(name, isAbsolute);
    }

    @Substitute
    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return ClassForNameSupport.forName(name, false);
    }

    @Substitute
    @SuppressWarnings("unused")
    static void checkClassLoaderPermission(ClassLoader cl, Class<?> caller) {
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    public static ClassLoader getPlatformClassLoader() {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.getPlatformClassLoader()");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    Class<?> loadClass(Target_java_lang_Module module, String name) {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.loadClass(Target_java_lang_Module, String)");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    ConcurrentHashMap<?, ?> createOrGetClassLoaderValueMap() {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.createOrGetClassLoaderValueMap()");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    private boolean trySetObjectField(String name, Object obj) {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.trySetObjectField(String name, Object obj)");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    protected URL findResource(String moduleName, String name) throws IOException {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.findResource(String, String)");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    static ClassLoader getBuiltinPlatformClassLoader() {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.getBuiltinPlatformClassLoader()");
    }

    @Substitute //
    @SuppressWarnings({"unused"})
    Object getClassLoadingLock(String className) {
        throw VMError.unsupportedFeature("Target_java_lang_ClassLoader.getClassLoadingLock(String)");
    }

    @Substitute //
    @SuppressWarnings({"unused"}) //
    private Class<?> findLoadedClass0(String name) {
        /* See open/src/hotspot/share/prims/jvm.cpp#958. */
        throw VMError.unsupportedFeature("Target_java_lang_ClassLoader.findLoadedClass0(String)");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    protected Class<?> findClass(String moduleName, String name) {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.findClass(String moduleName, String name)");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    public Package getDefinedPackage(String name) {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.getDefinedPackage(String name)");
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDK11OrLater.class) //
    public native Package definePackage(Class<?> clazz);

    @KeepOriginal //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    public native Package definePackage(String name, Target_java_lang_Module module);

    @KeepOriginal
    @TargetElement(onlyWith = JDK11OrLater.class) //
    private native Package toPackage(String name, Target_java_lang_NamedPackage p, Target_java_lang_Module m);

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings({"unused"})
    public Target_java_lang_Module getUnnamedModule() {
        return DynamicHub.singleModuleReference.get();
    }
}

@TargetClass(className = "java.lang.NamedPackage", onlyWith = JDK11OrLater.class) //
final class Target_java_lang_NamedPackage {
}

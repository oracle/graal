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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;

@TargetClass(classNameProvider = Package_jdk_internal_loader.class, className = "URLClassPath")
@SuppressWarnings("static-method")
final class Target_jdk_internal_loader_URLClassPath {

    /* Reset fields that can store a Zip file via sun.misc.URLClassPath$JarLoader.jar. */

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ArrayList.class)//
    private ArrayList<?> loaders;

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = HashMap.class)//
    private HashMap<String, ?> lmap;

    /* The original locations of the .jar files are no longer available at run time. */
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ArrayList.class)//
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
}

@TargetClass(URLClassLoader.class)
@SuppressWarnings("static-method")
final class Target_java_net_URLClassLoader {
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = WeakHashMap.class)//
    private WeakHashMap<Closeable, Void> closeables;

    @Substitute
    private InputStream getResourceAsStream(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : new ByteArrayInputStream(arr.get(0));
    }

    @Substitute
    @SuppressWarnings("unused")
    protected Class<?> findClass(final String name) {
        throw VMError.unsupportedFeature("Loading bytecodes.");
    }
}

@TargetClass(className = "jdk.internal.loader.BuiltinClassLoader", onlyWith = JDK11OrLater.class)
@SuppressWarnings("static-method")
final class Target_jdk_internal_loader_BuiltinClassLoader {

    @Substitute
    public URL findResource(@SuppressWarnings("unused") String mn, String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : Resources.createURL(name, arr.get(0));
    }

    @Substitute
    public URL findResource(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : Resources.createURL(name, arr.get(0));
    }

    @Substitute
    public InputStream findResourceAsStream(@SuppressWarnings("unused") String mn, String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : new ByteArrayInputStream(arr.get(0));
    }

    @Substitute
    public Enumeration<URL> findResources(String name) {
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
}

@TargetClass(ClassLoader.class)
@SuppressWarnings("static-method")
final class Target_java_lang_ClassLoader {

    /**
     * This field can be safely deleted, but that would require substituting the entire constructor
     * of ClassLoader, so we just reset it. The original javadoc mentions: "The classes loaded by
     * this class loader. The only purpose of this table is to keep the classes from being GC'ed
     * until the loader is GC'ed". This field is only accessed by ClassLoader.addClass() which is "
     * invoked by the VM to record every loaded class with this loader".
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    private Vector<Class<?>> classes;

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    private ConcurrentHashMap<String, Object> parallelLockMap;

    /**
     * Reset ClassLoader.packages; accessing packages via ClassLoader is currently not supported and
     * the SystemClassLoader may capture some hosted packages.
     */
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = HashMap.class)//
    @TargetElement(name = "packages", onlyWith = JDK8OrEarlier.class)//
    private HashMap<String, Package> packagesJDK8;
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @TargetElement(name = "packages", onlyWith = JDK11OrLater.class)//
    private ConcurrentHashMap<String, Package> packagesJDK11;

    @Alias //
    private static ClassLoader scl;

    @Substitute
    public static ClassLoader getSystemClassLoader() {
        VMError.guarantee(scl != null);
        return scl;
    }

    @Delete
    private static native void initSystemClassLoader();

    @Substitute
    private URL getResource(String name) {
        return getSystemResource(name);
    }

    @Substitute
    private InputStream getResourceAsStream(String name) {
        return getSystemResourceAsStream(name);
    }

    @Substitute
    private Enumeration<URL> getResources(String name) {
        return getSystemResources(name);
    }

    @Substitute
    private static URL getSystemResource(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : Resources.createURL(name, arr.get(0));
    }

    @Substitute
    private static InputStream getSystemResourceAsStream(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : new ByteArrayInputStream(arr.get(0));
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
    @SuppressWarnings("unused")
    @TargetElement(onlyWith = JDK14OrEarlier.class) //
    static void loadLibrary(Class<?> fromClass, String name, boolean isAbsolute) {
        NativeLibrarySupport.singleton().loadLibrary(name, isAbsolute);
    }

    @Substitute
    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return ClassForNameSupport.forName(name, false);
    }

    @Delete
    native Class<?> loadClass(String name, boolean resolve);

    @Delete
    native Class<?> findBootstrapClassOrNull(String name);

    @Substitute
    @SuppressWarnings("unused")
    static void checkClassLoaderPermission(ClassLoader cl, Class<?> caller) {
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    Class<?> loadClass(Target_java_lang_Module module, String name) {
        return ClassForNameSupport.forNameOrNull(name, false);
    }

    /**
     * All ClassLoaderValue are reset at run time for now. See also
     * {@link Target_jdk_internal_loader_BootLoader#CLASS_LOADER_VALUE_MAP} for resetting of the
     * boot class loader.
     */
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @TargetElement(onlyWith = JDK11OrLater.class)//
    ConcurrentHashMap<?, ?> classLoaderValueMap;

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

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings({"unused"})
    public Target_java_lang_Module getUnnamedModule() {
        return DynamicHub.singleModuleReference.get();
    }

    /*
     * The assertion status of classes is fixed at image build time because it is baked into the AOT
     * compiled code. All methods that modify the assertion status are substituted to throw an
     * error.
     *
     * Note that the assertion status can be queried at run time, see the relevant method in
     * DynamicHub.
     */

    @Substitute
    @SuppressWarnings({"unused"})
    private void setDefaultAssertionStatus(boolean enabled) {
        throw VMError.unsupportedFeature("The assertion status of classes is fixed at image build time.");
    }

    @Substitute
    @SuppressWarnings({"unused"})
    private void setPackageAssertionStatus(String packageName, boolean enabled) {
        throw VMError.unsupportedFeature("The assertion status of classes is fixed at image build time.");
    }

    @Substitute
    @SuppressWarnings({"unused"})
    private void setClassAssertionStatus(String className, boolean enabled) {
        throw VMError.unsupportedFeature("The assertion status of classes is fixed at image build time.");
    }

    @Substitute
    @SuppressWarnings({"unused"})
    private void clearAssertionStatus() {
        throw VMError.unsupportedFeature("The assertion status of classes is fixed at image build time.");
    }

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    private static native void registerNatives();

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native Class<?> defineClass0(String name, byte[] b, int off, int len, ProtectionDomain pd);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native Class<?> defineClass1(String name, byte[] b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native Class<?> defineClass2(String name, java.nio.ByteBuffer b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native void resolveClass0(Class<?> c);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native Class<?> defineClass1(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native Class<?> defineClass2(ClassLoader loader, String name, java.nio.ByteBuffer b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    private native Class<?> findBootstrapClass(String name);

    @Delete
    @TargetElement(onlyWith = JDK14OrEarlier.class)
    private static native String findBuiltinLib(String name);

    @Delete
    private static native Target_java_lang_AssertionStatusDirectives retrieveDirectives();
}

@TargetClass(value = ClassLoader.class, innerClass = "NativeLibrary", onlyWith = JDK14OrEarlier.class)
final class Target_java_lang_ClassLoader_NativeLibrary {

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native void load(String name, boolean isBuiltin);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native long find(String name);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native void unload(String name, boolean isBuiltin);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private native boolean load0(String name, boolean isBuiltin);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private native long findEntry(String name);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native void unload(String name, boolean isBuiltin, long handle);
}

@TargetClass(className = "java.lang.AssertionStatusDirectives") //
final class Target_java_lang_AssertionStatusDirectives {
}

@TargetClass(className = "java.lang.NamedPackage", onlyWith = JDK11OrLater.class) //
final class Target_java_lang_NamedPackage {
}
